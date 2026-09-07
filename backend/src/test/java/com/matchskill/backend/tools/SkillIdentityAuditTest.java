package com.matchskill.backend.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matchskill.backend.tools.SkillIdentityAudit.AuditReport;
import com.matchskill.backend.tools.SkillIdentityAudit.Status;
import com.matchskill.backend.util.Slugs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SkillIdentityAuditTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private record ExportRow(String id, String name, String slug, String identityKey) {}

    @Test
    void proposesOnlyTheExactIdentityKeyForAnUnambiguousArbitraryLegacySlug() throws IOException {
        ExportRow exported = row("C++", "imported-old-course-17", null);

        AuditReport report = audit(exported);

        assertThat(report.hasIssues()).isFalse();
        assertThat(report.counts()).containsEntry(Status.READY, 1L);
        assertThat(report.rows()).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(exported.id());
            assertThat(result.name()).isEqualTo(exported.name());
            assertThat(result.slug()).isEqualTo(exported.slug());
            assertThat(result.identityKey()).isNull();
            assertThat(result.normalizedIdentity()).isEqualTo(Slugs.normalizedIdentity("C++"));
            assertThat(result.proposedIdentityKey()).isEqualTo(Slugs.identityKey("C++"));
            assertThat(result.status()).isEqualTo(Status.READY);
        });
    }

    @Test
    void marksBothAccentedAliasesAmbiguousEvenWhenOneAlreadyHasTheCorrectKey() throws IOException {
        AuditReport report = audit(
                row("  Caf\u00e9  ", "old-import-a", null),
                row("CAFE", "old-import-b", Slugs.identityKey("CAFE")));

        assertThat(report.hasIssues()).isTrue();
        assertThat(report.counts()).containsEntry(Status.AMBIGUOUS_IDENTITY, 2L);
        assertThat(report.rows()).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(Status.AMBIGUOUS_IDENTITY);
            assertThat(result.proposedIdentityKey()).isNull();
            assertThat(result.issues()).anyMatch(issue -> issue.contains("shared by IDs"));
        });
    }

    @Test
    void distinguishesSymbolNamesAndPreservesUnicodeIdentities() throws IOException {
        AuditReport report = audit(row("C", "c", null), row("C++", "c-plus-legacy", null),
                row("C#", "c-sharp-legacy", null), row("C plus plus", "literal-words", null),
                row("\u65e5\u672c\u8a9e", "japanese-import", null));

        assertThat(report.hasIssues()).isFalse();
        assertThat(report.rows()).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(Status.READY);
            assertThat(result.normalizedIdentity()).isEqualTo(Slugs.normalizedIdentity(result.name()));
            assertThat(result.proposedIdentityKey()).isEqualTo(Slugs.identityKey(result.name()));
        });
        assertThat(report.rows()).extracting(SkillIdentityAudit.RowResult::proposedIdentityKey).doesNotHaveDuplicates();
    }

    @Test
    void recognizesCorrectExistingKeysWithoutProposingUpdates() throws IOException {
        AuditReport report = audit(row("Java", "do-not-rename", Slugs.identityKey("JAVA")));

        assertThat(report.hasIssues()).isFalse();
        assertThat(report.rows()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(Status.KEYED);
            assertThat(result.proposedIdentityKey()).isNull();
            assertThat(result.slug()).isEqualTo("do-not-rename");
        });
    }

    @Test
    void reportsWrongStoredKeysWithoutSilentlyRepairingThem() throws IOException {
        AuditReport report = audit(row("Java", "java", "wrong-existing-key"));

        assertThat(report.hasIssues()).isTrue();
        assertThat(report.rows()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(Status.KEY_MISMATCH);
            assertThat(result.identityKey()).isEqualTo("wrong-existing-key");
            assertThat(result.proposedIdentityKey()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "+++#", "...", "\u0301"})
    void meaninglessNamesNeverProduceBackfillCandidates(String name) throws IOException {
        AuditReport report = audit(row(name, "legacy-slug", null));

        assertThat(report.hasIssues()).isTrue();
        assertThat(report.rows()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(Status.INVALID_NAME);
            assertThat(result.proposedIdentityKey()).isNull();
        });
    }

    @Test
    void blocksAnUnkeyedCandidateWhenAnotherIdentityOccupiesItsExpectedKey() throws IOException {
        AuditReport report = audit(row("C++", "old-c-plus", null),
                row("Rust", "rust", Slugs.identityKey("C++")));

        assertThat(report.hasIssues()).isTrue();
        assertThat(report.rows().get(0).status()).isEqualTo(Status.KEY_COLLISION);
        assertThat(report.rows().get(1).status()).isEqualTo(Status.KEY_MISMATCH);
        assertThat(report.rows()).allSatisfy(result -> assertThat(result.proposedIdentityKey()).isNull());
        assertThat(report.rows().get(0).issues()).anyMatch(issue -> issue.contains("different identities"));
    }

    @Test
    void detectsAStoredKeySharedByTwoDifferentIdentities() throws IOException {
        String occupiedKey = Slugs.identityKey("C#");
        AuditReport report = audit(row("C#", "c-sharp", occupiedKey), row("Python", "python", occupiedKey));

        assertThat(report.rows().get(0).status()).isEqualTo(Status.KEY_COLLISION);
        assertThat(report.rows().get(1).status()).isEqualTo(Status.KEY_MISMATCH);
        assertThat(report.rows()).allSatisfy(result -> {
            assertThat(result.proposedIdentityKey()).isNull();
            assertThat(result.issues()).anyMatch(issue -> issue.contains("Stored key is shared"));
        });
    }

    @Test
    void duplicateIdsFailClearlyIncludingAlternateUuidCase() throws IOException {
        String id = "abcdef12-abcd-4abc-8def-abcdef123456";
        byte[] input = JSON.writeValueAsBytes(List.of(new ExportRow(id, "Java", "java", null),
                new ExportRow(id.toUpperCase(Locale.ROOT), "Rust", "rust", null)));

        JsonNode error = runInvalid(input);

        assertThat(error.at("/error/message").asText()).contains("Row 2 field id", "duplicates");
    }

    @ParameterizedTest
    @MethodSource("invalidExports")
    void invalidInputProducesAnErrorReportInsteadOfPartialCandidates(String invalidExport) throws IOException {
        JsonNode report = runInvalid(invalidExport.getBytes(StandardCharsets.UTF_8));

        assertThat(report.has("rows")).isFalse();
        assertThat(report.at("/error/message").asText()).isNotBlank();
    }

    @Test
    void cliWritesUtf8JsonAndReturnsReviewStatusWithoutChangingTheExport() throws IOException {
        byte[] input = JSON.writeValueAsBytes(List.of(row("\u65e5\u672c\u8a9e", "unchanged-slug", null)));
        byte[] original = input.clone();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exitCode = SkillIdentityAudit.run(new String[] {"-"}, new ByteArrayInputStream(input), output);

        assertThat(exitCode).isZero();
        assertThat(input).isEqualTo(original);
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("\u65e5\u672c\u8a9e");
        assertThat(JSON.readTree(output.toByteArray()).at("/rows/0/status").asText()).isEqualTo("READY");

        output.reset();
        byte[] issueInput = JSON.writeValueAsBytes(List.of(row("+++", "unchanged-slug", null)));
        assertThat(SkillIdentityAudit.run(new String[] {"-"}, new ByteArrayInputStream(issueInput), output)).isEqualTo(1);
        assertThat(JSON.readTree(output.toByteArray()).get("hasIssues").asBoolean()).isTrue();
    }

    @Test
    void cliRejectsMissingArgumentsAndMissingFilesWithJsonErrors() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(SkillIdentityAudit.run(new String[0], InputStream.nullInputStream(), output)).isEqualTo(2);
        assertThat(JSON.readTree(output.toByteArray()).at("/error/message").asText()).contains("Usage:");
        output.reset();

        String absentFile = "missing-export-" + UUID.randomUUID() + ".json";
        assertThat(SkillIdentityAudit.run(new String[] {absentFile}, InputStream.nullInputStream(), output)).isEqualTo(2);
        assertThat(JSON.readTree(output.toByteArray()).at("/error/message").asText()).contains("existing regular JSON file");
    }

    @Test
    void emptyExportProducesACompleteZeroCountReport() throws IOException {
        AuditReport report = SkillIdentityAudit.audit(new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8)));

        assertThat(report.hasIssues()).isFalse();
        assertThat(report.rows()).isEmpty();
        assertThat(report.counts()).hasSize(Status.values().length).allSatisfy((status, count) -> assertThat(count).isZero());
    }

    private static AuditReport audit(ExportRow... rows) throws IOException {
        return SkillIdentityAudit.audit(new ByteArrayInputStream(JSON.writeValueAsBytes(List.of(rows))));
    }

    private static JsonNode runInvalid(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(SkillIdentityAudit.run(new String[] {"-"}, new ByteArrayInputStream(input), output)).isEqualTo(2);
        JsonNode report = JSON.readTree(output.toByteArray());
        assertThat(report.at("/error/code").asText()).isEqualTo("INVALID_INPUT");
        return report;
    }

    private static ExportRow row(String name, String slug, String identityKey) {
        return new ExportRow(UUID.randomUUID().toString(), name, slug, identityKey);
    }

    private static Stream<String> invalidExports() {
        String validRow = "{\"id\":\"abcdef12-abcd-4abc-8def-abcdef123456\",\"name\":\"Java\",\"slug\":\"java\",\"identityKey\":null}";
        return Stream.of("", "{}", "[null]", "[123]", "[{}]", "[" + validRow.replace("\"Java\"", "null") + "]",
                "[" + validRow.replace("\"java\"", "null") + "]",
                "[" + validRow.replace("\"abcdef12-abcd-4abc-8def-abcdef123456\"", "null") + "]",
                "[" + validRow.replace("abcdef12-abcd-4abc-8def-abcdef123456", "1-1-1-1-1") + "]",
                "[" + validRow.replace("\"Java\"", "42") + "]",
                "[" + validRow.replace("\"identityKey\":null", "\"identityKey\":42") + "]",
                "[" + validRow.replace(",\"identityKey\":null", "") + "]",
                "[" + validRow.replace("\"name\":\"Java\"", "\"name\":\"Java\",\"name\":\"Rust\"") + "]",
                "[" + validRow + "] []");
    }

}
