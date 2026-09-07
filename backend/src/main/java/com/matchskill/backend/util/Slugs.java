package com.matchskill.backend.util;

import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic skill identities, with legacy slug lookup kept separate from identity. */
public final class Slugs {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_SLUG_LENGTH = 255;

    private Slugs() {}

    public static String slugify(String value) {
        String identity = normalizedIdentity(value);
        return identity.length() <= MAX_SLUG_LENGTH ? identity : fallbackSlug(value);
    }

    /**
     * Fold case/accents, preserve Unicode letters/digits and distinguish +/#.
     * Other runs become one separator. Whitespace around symbols is not an alias.
     */
    public static String normalizedIdentity(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder identity = new StringBuilder();
        boolean meaningful = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(codePoint)) {
                identity.appendCodePoint(codePoint);
                meaningful = true;
            } else if (codePoint == '+') {
                identity.append("~2b");
            } else if (codePoint == '#') {
                identity.append("~23");
            } else if (!identity.isEmpty() && identity.charAt(identity.length() - 1) != '-') {
                identity.append('-');
            }
        }
        if (!identity.isEmpty() && identity.charAt(identity.length() - 1) == '-') {
            identity.setLength(identity.length() - 1);
        }
        return meaningful ? identity.toString() : "";
    }

    /** A fixed-width database key; callers also compare canonical names on lookup. */
    public static String identityKey(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedIdentity(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Reserved double separator and full digest avoid aliasing an occupied legacy slug. */
    public static String fallbackSlug(String value) {
        String identity = normalizedIdentity(value);
        int end = Math.min(identity.length(), MAX_SLUG_LENGTH - 66);
        if (end > 0 && Character.isHighSurrogate(identity.charAt(end - 1))) {
            end--;
        }
        return identity.substring(0, end) + "--" + identityKey(value);
    }

    /** Previous ASCII normalization, used only to discover existing rows without renaming them. */
    public static String legacySlug(String value) {
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");
        return normalized.replaceAll("^-+|-+$", "");
    }
}
