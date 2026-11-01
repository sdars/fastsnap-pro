package com.fastsnap.core;

import java.util.Locale;

/**
 * Mints serial keys that satisfy the FastSnap Pro 3.2.1 checksum.
 *
 * <p>For a fixed 14-character prefix {@code p}, the body is
 * {@code p + h1 + h2}, where {@code h1} and {@code h2} are ASCII hex digits.
 * The checksum must equal the numeric value of that suffix:
 *
 * <pre>
 *   (checksum(p) * 961 + ascii(h1) * 31 + ascii(h2)) &amp; 0xFFFF
 *       == parseInt(h1 + h2, 16)
 * </pre>
 *
 * <p>Only 256 of the 65536 possible prefixes admit a solution, so roughly one
 * prefix in 256 works. The generator first tries the normalized 14-character
 * owner, then varies 1, 2 and finally 3 trailing characters, which makes a
 * solution overwhelmingly likely while preserving the first 11 normalized
 * owner characters.
 */
public final class SerialGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final char[] HEX =
            "0123456789abcdef".toCharArray();
    private static final int PREFIX_LENGTH = 14;
    private static final int MAX_TAIL = 3;
    private static final int SUFFIX_VALUES = 256;

    private SerialGenerator() {
    }

    /**
     * Build a serial for {@code owner}, expiring at the end of year
     * {@code 2000 + expires}.
     *
     * @param owner   human-readable owner; sanitized to {@code [A-Z0-9]}
     * @param expires two-digit year, 0..99
     * @return a 19-character serial
     */
    public static String generate(String owner, int expires) {
        if (expires < Lic.MIN_EXPIRY || expires > Lic.MAX_EXPIRY) {
            throw new IllegalArgumentException("expires must be 0..99");
        }

        String normalized = normalise(owner, PREFIX_LENGTH);
        for (int tail = 0; tail <= MAX_TAIL; tail++) {
            String base = normalized.substring(0, PREFIX_LENGTH - tail);
            String body = search(base, tail);
            if (body != null) {
                return body + Lic.SEPARATOR
                        + String.format(Locale.ROOT, "%02d", expires);
            }
        }
        throw new IllegalStateException("no checksum fixed point for owner " + owner);
    }

    private static String search(String base, int tailLength) {
        int combinations = 1;
        for (int i = 0; i < tailLength; i++) {
            combinations *= ALPHABET.length;
        }
        char[] prefix = new char[PREFIX_LENGTH];
        base.getChars(0, base.length(), prefix, 0);
        for (int n = 0; n < combinations; n++) {
            int value = n;
            for (int i = tailLength - 1; i >= 0; i--) {
                prefix[base.length() + i] = ALPHABET[value % ALPHABET.length];
                value /= ALPHABET.length;
            }
            String body = fixedPoint(new String(prefix));
            if (body != null) {
                return body;
            }
        }
        return null;
    }

    /**
     * Return a valid 16-character body for a 14-character prefix, or
     * {@code null} when the prefix has no fixed point.
     */
    static String fixedPoint(String prefix) {
        if (prefix == null || prefix.length() != PREFIX_LENGTH) {
            throw new IllegalArgumentException("prefix must be 14 characters");
        }
        for (int value = 0; value < SUFFIX_VALUES; value++) {
            String body = prefix + HEX[value >>> 4] + HEX[value & 0x0F];
            if (Lic.checksum(body) == value) {
                return body;
            }
        }
        return null;
    }

    /** Keep A-Z/0-9, uppercase, pad to {@code length} with X, then truncate. */
    static String normalise(String owner, int length) {
        StringBuilder sb = new StringBuilder(length);
        if (owner != null) {
            for (char c : owner.toUpperCase(Locale.ROOT).toCharArray()) {
                if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                    sb.append(c);
                }
                if (sb.length() == length) {
                    break;
                }
            }
        }
        while (sb.length() < length) {
            sb.append('X');
        }
        return sb.toString();
    }
}
