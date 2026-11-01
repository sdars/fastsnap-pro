package com.fastsnap.core;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * FastSnap Pro 3.2.1 serial-key model.
 *
 * <p>A serial is exactly 19 characters:
 *
 * <pre>
 *   [0..13]  14-character body prefix
 *   [14..15] two lowercase hex digits encoding the low byte of the checksum
 *   [16]     separator; the commercial build ignored this character
 *   [17..18] two-digit expiry year (00..99, meaning 2000..2099)
 * </pre>
 *
 * <p>The checksum is the original rolling hash
 * {@code h = (h * 31 + c) &amp; 0xFFFF} over all 16 body characters. Because the
 * last two body characters are parsed as a hexadecimal byte, a valid body must
 * satisfy {@code checksum(body) == Integer.parseInt(body.substring(14), 16)}.
 * Only bodies whose checksum is in {@code 0..255} can satisfy that equation.
 *
 * <p>The commercial 3.2.1 build compared the two-digit expiry value directly
 * against {@link System#currentTimeMillis()}, so every serial was immediately
 * reported as expired. {@link #valid(long)} implements the intended
 * end-of-year semantics; {@link #validLegacy(long)} preserves the original bug
 * for forensic comparison through the {@code legacy} CLI command.
 */
public final class Lic {

    public static final int SERIAL_LENGTH = 19;
    public static final int BODY_LENGTH = 16;
    public static final int CHECKSUM_OFFSET = 14;
    public static final int SEPARATOR_INDEX = 16;
    public static final int EXPIRY_OFFSET = 17;
    public static final char SEPARATOR = '-';
    public static final int MIN_EXPIRY = 0;
    public static final int MAX_EXPIRY = 99;

    private final String owner;
    private final long expires;
    private final char separator;

    private Lic(String owner, long expires, char separator) {
        this.owner = owner;
        this.expires = expires;
        this.separator = separator;
    }

    /**
     * Parse a serial using the strict, documented 3.2.1-open rules.
     *
     * @return the parsed license, or {@code null} when the serial is invalid
     */
    public static Lic parse(String serial) {
        return parseInternal(serial, true);
    }

    /**
     * Parse a serial using the commercial 3.2.1 quirks: the separator is not
     * checked and {@link Long#parseLong(String)} is used for the expiry field.
     * This exists so the original binary's behaviour can be reproduced and
     * documented; new code should use {@link #parse(String)}.
     */
    public static Lic parseLegacy(String serial) {
        return parseInternal(serial, false);
    }

    private static Lic parseInternal(String serial, boolean strict) {
        if (serial == null || serial.length() != SERIAL_LENGTH) {
            return null;
        }

        String body = serial.substring(0, BODY_LENGTH);
        char separator = serial.charAt(SEPARATOR_INDEX);
        if (strict && separator != SEPARATOR) {
            return null;
        }

        String expiryText = serial.substring(EXPIRY_OFFSET);
        long expiry;
        try {
            if (strict) {
                if (!isTwoAsciiDigits(expiryText)) {
                    return null;
                }
                expiry = (expiryText.charAt(0) - '0') * 10L
                        + (expiryText.charAt(1) - '0');
            } else {
                expiry = Long.parseLong(expiryText);
            }
        } catch (NumberFormatException ex) {
            return null;
        }

        int expected;
        try {
            expected = Integer.parseInt(body.substring(CHECKSUM_OFFSET, BODY_LENGTH), 16);
        } catch (NumberFormatException ex) {
            return null;
        }

        if (expected < 0 || expected > 0xFF || checksum(body) != expected) {
            return null;
        }
        return new Lic(body, expiry, separator);
    }

    private static boolean isTwoAsciiDigits(String value) {
        return value.length() == 2
                && value.charAt(0) >= '0' && value.charAt(0) <= '9'
                && value.charAt(1) >= '0' && value.charAt(1) <= '9';
    }

    /** Rolling {@code *31 + char} hash truncated to 16 bits. */
    public static int checksum(String body) {
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            sum = (sum * 31 + body.charAt(i)) & 0xFFFF;
        }
        return sum;
    }

    /** Full 16-bit checksum rendered as four lowercase hex digits. */
    public static String checksumHex(String body) {
        return String.format(Locale.ROOT, "%04x", checksum(body));
    }

    /** The two-character checksum suffix embedded in a serial body. */
    public static String checksumSuffix(String body) {
        if (body == null || body.length() != BODY_LENGTH) {
            throw new IllegalArgumentException("body must be 16 characters");
        }
        return body.substring(CHECKSUM_OFFSET, BODY_LENGTH);
    }

    /** True when the expiry field is a two-digit year. */
    public boolean hasTwoDigitExpiry() {
        return expires >= MIN_EXPIRY && expires <= MAX_EXPIRY;
    }

    /**
     * Reconstructed validity check: the license is valid through
     * 23:59:59.999 UTC on 31 December of {@link #expiryYear()}.
     */
    public boolean valid(long now) {
        return hasTwoDigitExpiry() && now <= expiryEpochMillis();
    }

    /**
     * Exact commercial 3.2.1 comparison: {@code expires >= now}. Since the
     * expiry is a two-digit year and {@code now} is epoch milliseconds, this is
     * false for every normal serial. Kept for forensic compatibility.
     */
    public boolean validLegacy(long now) {
        return expires >= now;
    }

    public String owner() {
        return owner;
    }

    public long expires() {
        return expires;
    }

    public char separator() {
        return separator;
    }

    /** Two-digit expiry year as stored in the key (0..99). */
    public int expiryYearShort() {
        return hasTwoDigitExpiry() ? (int) expires : -1;
    }

    /** Full expiry year: the stored value is a year in 2000..2099. */
    public int expiryYear() {
        if (!hasTwoDigitExpiry()) {
            throw new IllegalStateException("expiry is not a two-digit year: " + expires);
        }
        return 2000 + (int) expires;
    }

    /** Expiry instant: 23:59:59.999 UTC on 31 December of {@link #expiryYear()}. */
    public long expiryEpochMillis() {
        return LocalDate.of(expiryYear(), 12, 31)
                .atTime(23, 59, 59, 999_000_000)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
    }

    /** Expiry date rendered as {@code yyyy-MM-dd} (UTC). */
    public String expiresDate() {
        if (!hasTwoDigitExpiry()) {
            return "n/a";
        }
        return LocalDate.of(expiryYear(), 12, 31).toString();
    }

    @Override
    public String toString() {
        if (hasTwoDigitExpiry()) {
            return owner + separator + String.format(Locale.ROOT, "%02d", expires);
        }
        return owner + separator + expires;
    }
}
