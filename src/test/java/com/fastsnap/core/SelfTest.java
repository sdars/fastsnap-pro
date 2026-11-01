package com.fastsnap.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

/** Zero-dependency regression suite: {@code ./gradlew selftest}. */
public final class SelfTest {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        testChecksum();
        testParsing();
        testFixedPointMath();
        testGenerator();
        testExpiry();
        testCli();
        testLegacyReplay();
        System.out.println(checks + " checks, " + failures + " failure(s)");
        if (failures != 0) {
            System.exit(1);
        }
        System.out.println("OK: all self tests passed");
    }

    private static void testChecksum() {
        check("checksum(empty)", Lic.checksum("") == 0);
        check("checksum reference", Lic.checksum("S0BLEODH7OQDU9d0") == 0xD0);
        check("checksum hex lowercase", Lic.checksumHex("S0BLEODH7OQDU9d0").equals("00d0"));
        check("checksum suffix", Lic.checksumSuffix("S0BLEODH7OQDU9d0").equals("d0"));
    }

    private static void testParsing() {
        check("null rejected", Lic.parse(null) == null);
        check("short rejected", Lic.parse("ABC") == null);
        check("long rejected", Lic.parse("S0BLEODH7OQDU9d0-999") == null);
        check("bad checksum rejected", Lic.parse("S0BLEODH7OQDU900-99") == null);
        check("non-hex suffix rejected", Lic.parse("S0BLEODH7OQDU9zz-99") == null);
        check("bad separator rejected", Lic.parse("S0BLEODH7OQDU9d0_99") == null);
        check("non-digit expiry rejected", Lic.parse("S0BLEODH7OQDU9d0-9x") == null);

        Lic lic = Lic.parse("S0BLEODH7OQDU9d0-27");
        check("reference parses", lic != null);
        check("reference owner", lic != null && lic.owner().equals("S0BLEODH7OQDU9d0"));
        check("reference expiry", lic != null && lic.expiryYearShort() == 27);
        check("reference year", lic != null && lic.expiryYear() == 2027);
        check("reference date", lic != null && lic.expiresDate().equals("2027-12-31"));

        // The commercial build accepted any 19th-character layout because it
        // used parseLong on the last two characters and never read index 16.
        check("legacy ignores separator",
                Lic.parseLegacy("S0BLEODH7OQDU9d0X99") != null);
        check("strict checks separator",
                Lic.parse("S0BLEODH7OQDU9d0X99") == null);
    }

    private static void testFixedPointMath() {
        Set<String> bodies = new HashSet<>();
        String[] prefixes = {"S0BLEODH7OQDU9", "ALICEXXXXXXANO", "FASTSNAP000000"};
        for (String prefix : prefixes) {
            String body = SerialGenerator.fixedPoint(prefix);
            if (body == null) {
                check("no fixed point for " + prefix, prefix.equals("FASTSNAP000000"));
                continue;
            }
            check("fixed point length " + prefix, body.length() == 16);
            check("fixed point starts with prefix " + prefix, body.startsWith(prefix));
            check("fixed point checksum " + prefix,
                    Lic.checksum(body) == Integer.parseInt(body.substring(14), 16));
            bodies.add(body);
        }
        check("fixed points are distinct", bodies.size() == 2);

        // 15*c1 == -961*P has 16 admissible residues out of 65536.
        int distinctResidues = 0;
        Set<Integer> residues = new HashSet<>();
        for (char c : "0123456789abcdef".toCharArray()) {
            residues.add((15 * c) & 0xFFFF);
        }
        distinctResidues = residues.size();
        check("16 admissible checksum residues", distinctResidues == 16);
    }

    private static void testGenerator() {
        String[] owners = {
                "alice", "Bob Smith", "ACME", "x", "ABCDEFGHIJKLMN",
                "A".repeat(30), "S0BLEODH7OQDU9d0"
        };
        Set<String> serials = new HashSet<>();
        for (String owner : owners) {
            String serial = SerialGenerator.generate(owner, 99);
            check("length for " + quote(owner), serial.length() == Lic.SERIAL_LENGTH);
            check("separator for " + quote(owner), serial.charAt(16) == '-');
            check("expiry for " + quote(owner), serial.endsWith("-99"));
            Lic lic = Lic.parse(serial);
            check("generated parses for " + quote(owner), lic != null);
            check("generated checksum for " + quote(owner),
                    lic != null && Lic.checksum(lic.owner())
                            == Integer.parseInt(lic.owner().substring(14), 16));
            serials.add(serial);
        }
        check("normalized owners map to distinct serials", serials.size() == owners.length);

        // Empty and all-non-ASCII owners both normalize to the same X padding.
        String emptyOwner = SerialGenerator.generate("", 99);
        String unicodeOwner = SerialGenerator.generate("张三", 99);
        check("empty owner is padded", emptyOwner.startsWith("XXXXXXXXXXX"));
        check("non-ASCII owner is padded", unicodeOwner.equals(emptyOwner));

        String prefixed = SerialGenerator.generate("Bob Smith", 30);
        check("owner prefix preserved", prefixed.startsWith("BOBSMITHXXX"));
        check("expiry 30", prefixed.endsWith("-30"));

        try {
            SerialGenerator.generate("alice", 100);
            check("expiry 100 rejected", false);
        } catch (IllegalArgumentException expected) {
            check("expiry 100 rejected", true);
        }
        try {
            SerialGenerator.generate("alice", -1);
            check("expiry -1 rejected", false);
        } catch (IllegalArgumentException expected) {
            check("expiry -1 rejected", true);
        }
    }

    private static void testExpiry() {
        Lic lic = Lic.parse("S0BLEODH7OQDU9d0-27");
        long end = LocalDate.of(2027, 12, 31)
                .atTime(23, 59, 59, 999_000_000)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        check("valid before expiry", lic.valid(end - 1));
        check("valid at expiry", lic.valid(end));
        check("expired after expiry", !lic.valid(end + 1));
        check("legacy bug always expires", !lic.validLegacy(System.currentTimeMillis()));
        check("expiry instant", lic.expiryEpochMillis() == end);
    }

    private static void testCli() {
        Captured ok = run("verify", "S0BLEODH7OQDU9d0-99");
        check("verify exit 0", ok.code == 0);
        check("verify text", ok.out.contains("licensed to S0BLEODH7OQDU9d0 until 2099-12-31"));

        Captured expired = run("verify", "S0BLEODH7OQDU9d0-01");
        check("expired exit 3", expired.code == 3);
        check("expired text", expired.out.contains("license expired for"));

        Captured invalid = run("nope");
        check("invalid exit 2", invalid.code == 2);
        check("invalid text", invalid.out.contains("invalid serial"));

        Captured usage = run("help");
        check("help exit 64", usage.code == 64);
        check("help lists generate", usage.out.contains("generate --owner"));

        Captured version = run("version");
        check("version exit 0", version.code == 0);
        check("version text", version.out.trim().equals("FastSnap Pro 3.2.1"));

        Captured generated = run("generate", "--owner", "alice", "--expires", "99");
        check("generate exit 0", generated.code == 0);
        String serial = generated.out.trim();
        check("generate verifies", Lic.parse(serial) != null);
        check("generate owner prefix", serial.startsWith("ALICEXXXXXX"));

        Captured json = run("generate", "--owner", "alice", "--expires", "42", "--json");
        check("json exit 0", json.code == 0);
        check("json serial", json.out.contains("\"serial\": \"" + serial.substring(0, 16) + "-42\""));
        check("json expiry", json.out.contains("\"expires\": 42"));

        Captured inspect = run("inspect", serial);
        check("inspect exit 0", inspect.code == 0);
        check("inspect valid", inspect.out.contains("valid now  : true"));

        Captured missingOwner = run("generate");
        check("generate requires owner", missingOwner.code == 64);
        Captured badExpiry = run("generate", "--owner", "alice", "--expires", "x");
        check("generate rejects bad expiry", badExpiry.code == 64);
        Captured unknown = run("generate", "--wat");
        check("generate rejects unknown flag", unknown.code == 64);
    }

    private static void testLegacyReplay() {
        Captured replay = run("legacy", "S0BLEODH7OQDU9d0-99");
        check("legacy exit 3", replay.code == 3);
        check("legacy banner typo", replay.out.contains("FastSnap Pro 3.2.1)"));
        check("legacy expiry", replay.out.contains("license expired for S0BLEODH7OQDU9d0"));

        Captured bad = run("legacy", "nope");
        check("legacy invalid exit 2", bad.code == 2);
        check("legacy invalid text", bad.out.contains("invalid serial"));
    }

    private static Captured run(String... args) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8);
        int code = App.run(args, ps, ps);
        return new Captured(code, buf.toString(StandardCharsets.UTF_8));
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL  " + name);
        }
    }

    private static String quote(String value) {
        return value.isEmpty() ? "\"\"" : value;
    }

    private record Captured(int code, String out) {
    }
}
