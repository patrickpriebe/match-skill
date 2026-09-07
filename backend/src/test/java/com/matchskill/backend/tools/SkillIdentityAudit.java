package com.matchskill.backend.tools;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matchskill.backend.util.Slugs;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Offline vocabulary audit. Reads an export and writes JSON to stdout; never connects to a database. */
public final class SkillIdentityAudit {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private SkillIdentityAudit() {}

    public enum Status {
        READY, KEYED, AMBIGUOUS_IDENTITY, INVALID_NAME, KEY_MISMATCH, KEY_COLLISION
    }

    public record RowResult(
            String id, String name, String slug, String identityKey, String normalizedIdentity,
            Status status, String proposedIdentityKey, List<String> issues) {}

    public record AuditReport(List<RowResult> rows, Map<Status, Long> counts, boolean hasIssues) {}

    private record SkillRow(
            String id, String name, String slug, String identityKey, String normalizedIdentity, String expectedKey) {}

    private record ErrorDetail(String code, String message) {}

    private record ErrorReport(ErrorDetail error) {}

    public static void main(String[] args) {
        int exitCode = run(args, System.in, System.out);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /** Exit 0: no issues, 1: review required, 2: invalid input or unreadable export. */
    static int run(String[] args, InputStream standardInput, OutputStream standardOutput) {
        try {
            if (args.length != 1) {
                throw new IllegalArgumentException("Usage: SkillIdentityAudit <export.json|->");
            }
            AuditReport report;
            if ("-".equals(args[0])) {
                report = audit(standardInput);
            } else {
                Path inputPath = Path.of(args[0]);
                if (!Files.isRegularFile(inputPath)) {
                    throw new IllegalArgumentException("Input must be an existing regular JSON file");
                }
                try (InputStream input = Files.newInputStream(inputPath)) {
                    report = audit(input);
                }
            }
            writeJson(standardOutput, report);
            return report.hasIssues() ? 1 : 0;
        } catch (IllegalArgumentException | IOException failure) {
            try {
                writeJson(standardOutput, new ErrorReport(new ErrorDetail("INVALID_INPUT", failure.getMessage())));
            } catch (IOException outputFailure) {
                // A closed output pipe cannot receive an error; do not write to another destination.
            }
            return 2;
        }
    }

    static AuditReport audit(InputStream input) throws IOException {
        List<SkillRow> rows = readRows(JSON.readTree(input));
        Map<String, List<SkillRow>> byIdentity = new HashMap<>();
        Map<String, List<SkillRow>> byExpectedKey = new HashMap<>();
        Map<String, List<SkillRow>> byStoredKey = new HashMap<>();
        for (SkillRow row : rows) {
            if (!row.normalizedIdentity().isEmpty()) {
                byIdentity.computeIfAbsent(row.normalizedIdentity(), ignored -> new ArrayList<>()).add(row);
                byExpectedKey.computeIfAbsent(row.expectedKey(), ignored -> new ArrayList<>()).add(row);
            }
            if (row.identityKey() != null) {
                byStoredKey.computeIfAbsent(row.identityKey(), ignored -> new ArrayList<>()).add(row);
            }
        }

        List<RowResult> results = new ArrayList<>();
        Map<Status, Long> counts = new EnumMap<>(Status.class);
        for (Status status : Status.values()) {
            counts.put(status, 0L);
        }
        for (SkillRow row : rows) {
            List<String> issues = new ArrayList<>();
            boolean invalidName = row.normalizedIdentity().isEmpty();
            List<SkillRow> sameIdentity = byIdentity.getOrDefault(row.normalizedIdentity(), List.of());
            boolean ambiguous = sameIdentity.size() > 1;
            boolean mismatched = row.identityKey() != null && !row.identityKey().equals(row.expectedKey());
            List<SkillRow> storedKeyConflicts = conflictingIdentities(
                    byStoredKey.getOrDefault(row.expectedKey(), List.of()), row);
            List<SkillRow> calculatedKeyConflicts = conflictingIdentities(
                    byExpectedKey.getOrDefault(row.expectedKey(), List.of()), row);
            List<SkillRow> ownKeyConflicts = row.identityKey() == null ? List.of() : conflictingIdentities(
                    byStoredKey.getOrDefault(row.identityKey(), List.of()), row);
            boolean collision = !storedKeyConflicts.isEmpty() || !calculatedKeyConflicts.isEmpty()
                    || !ownKeyConflicts.isEmpty();

            if (invalidName) {
                issues.add("Name contains no meaningful letter or digit under Slugs.normalizedIdentity");
            }
            if (ambiguous) {
                issues.add("Normalized identity is shared by IDs: " + ids(sameIdentity));
            }
            if (mismatched) {
                issues.add("Stored identityKey does not match Slugs.identityKey(name)");
            }
            if (!storedKeyConflicts.isEmpty()) {
                issues.add("Expected key is stored by different identities at IDs: " + ids(storedKeyConflicts));
            }
            if (!calculatedKeyConflicts.isEmpty()) {
                issues.add("Calculated key collides with different identities at IDs: " + ids(calculatedKeyConflicts));
            }
            if (!ownKeyConflicts.isEmpty()) {
                issues.add("Stored key is shared by different identities at IDs: " + ids(ownKeyConflicts));
            }

            Status status = invalidName ? Status.INVALID_NAME
                    : ambiguous ? Status.AMBIGUOUS_IDENTITY
                    : mismatched ? Status.KEY_MISMATCH
                    : collision ? Status.KEY_COLLISION
                    : row.identityKey() == null ? Status.READY : Status.KEYED;
            results.add(new RowResult(row.id(), row.name(), row.slug(), row.identityKey(), row.normalizedIdentity(),
                    status, status == Status.READY ? row.expectedKey() : null, List.copyOf(issues)));
            counts.merge(status, 1L, Long::sum);
        }
        boolean hasIssues = results.stream().anyMatch(row -> row.status() != Status.READY && row.status() != Status.KEYED);
        return new AuditReport(List.copyOf(results), counts, hasIssues);
    }

    private static List<SkillRow> readRows(JsonNode input) {
        if (input == null || !input.isArray()) {
            throw new IllegalArgumentException("Input must be a JSON array of skill objects");
        }
        List<SkillRow> rows = new ArrayList<>();
        Set<UUID> seenIds = new HashSet<>();
        for (int index = 0; index < input.size(); index++) {
            JsonNode row = input.get(index);
            int rowNumber = index + 1;
            if (!row.isObject()) {
                throw invalidField(rowNumber, "row", "must be an object");
            }
            String id = requiredText(row, "id", rowNumber);
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
                if (!uuid.toString().equalsIgnoreCase(id)) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException failure) {
                throw invalidField(rowNumber, "id", "must be a complete UUID string");
            }
            if (!seenIds.add(uuid)) {
                throw invalidField(rowNumber, "id", "duplicates an earlier row");
            }
            String name = requiredText(row, "name", rowNumber);
            String slug = requiredText(row, "slug", rowNumber);
            JsonNode key = row.get("identityKey");
            if (key == null || (!key.isNull() && !key.isTextual())) {
                throw invalidField(rowNumber, "identityKey", "must be present as a string or null");
            }
            String identity = Slugs.normalizedIdentity(name);
            rows.add(new SkillRow(id, name, slug, key.isNull() ? null : key.textValue(), identity, Slugs.identityKey(name)));
        }
        return rows;
    }

    private static String requiredText(JsonNode row, String field, int rowNumber) {
        JsonNode value = row.get(field);
        if (value == null || !value.isTextual()) {
            throw invalidField(rowNumber, field, "must be a non-null string");
        }
        return value.textValue();
    }

    private static IllegalArgumentException invalidField(int rowNumber, String field, String detail) {
        return new IllegalArgumentException("Row " + rowNumber + " field " + field + " " + detail);
    }

    private static List<SkillRow> conflictingIdentities(List<SkillRow> candidates, SkillRow row) {
        return candidates.stream().filter(candidate -> !candidate.normalizedIdentity().equals(row.normalizedIdentity()))
                .toList();
    }

    private static String ids(List<SkillRow> rows) {
        return String.join(", ", rows.stream().map(SkillRow::id).toList());
    }

    private static void writeJson(OutputStream output, Object value) throws IOException {
        output.write(JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        output.write('\n');
        output.flush();
    }
}
