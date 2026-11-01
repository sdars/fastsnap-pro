package com.fastsnap.core;

import java.io.PrintStream;
import java.util.Locale;

/**
 * FastSnap Pro command-line entry point.
 *
 * <p>The commercial 3.2.1 jar accepted one positional serial, printed a banner
 * and exited with 0/2/3. This rebuild preserves that invocation shape and adds
 * {@code verify}, {@code inspect}, {@code generate} and {@code legacy}
 * subcommands. A bare first argument is still treated as a serial.
 *
 * <ul>
 *   <li>0 - licensed</li>
 *   <li>2 - serial structurally invalid</li>
 *   <li>3 - serial valid but expired</li>
 *   <li>64 - usage error</li>
 * </ul>
 */
public final class App {

    public static final String VERSION = "3.2.1";
    public static final String PRODUCT = "FastSnap Pro";
    public static final int EXIT_OK = 0;
    public static final int EXIT_INVALID = 2;
    public static final int EXIT_EXPIRED = 3;
    public static final int EXIT_USAGE = 64;

    private App() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != EXIT_OK) {
            System.exit(code);
        }
    }

    /** Testable entry point; never calls {@link System#exit}. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            return verify("", out);
        }
        return switch (args[0]) {
            case "verify" -> args.length < 2 ? usage(out) : verify(args[1], out);
            case "inspect" -> args.length < 2 ? usage(out) : inspect(args[1], out);
            case "generate", "keygen" -> generate(args, out, err);
            case "legacy" -> args.length < 2 ? usage(out) : legacy(args[1], out);
            case "version", "--version", "-V" -> {
                out.println(PRODUCT + " " + VERSION);
                yield EXIT_OK;
            }
            case "help", "--help", "-h" -> usage(out);
            default -> verify(args[0], out);
        };
    }

    private static int usage(PrintStream out) {
        out.println(PRODUCT + " " + VERSION);
        out.println();
        out.println("Usage:");
        out.println("  fastsnap-pro verify <serial>              validate a serial key");
        out.println("  fastsnap-pro inspect <serial>             decode a serial key");
        out.println("  fastsnap-pro generate --owner <name> [--expires <YY>] [--json]");
        out.println("                                            mint a serial key");
        out.println("  fastsnap-pro legacy <serial>              replay the 3.2.1 expiry bug");
        out.println("  fastsnap-pro version                      print version");
        out.println();
        out.println("A bare argument is treated as 'verify <serial>'.");
        return EXIT_USAGE;
    }

    private static int verify(String serial, PrintStream out) {
        Lic lic = Lic.parse(serial);
        if (lic == null) {
            out.println(PRODUCT + " " + VERSION);
            out.println("invalid serial");
            return EXIT_INVALID;
        }
        if (!lic.valid(System.currentTimeMillis())) {
            out.println(PRODUCT + " " + VERSION);
            out.println("license expired for " + lic.owner());
            return EXIT_EXPIRED;
        }
        out.println(PRODUCT + " " + VERSION);
        out.println("licensed to " + lic.owner() + " until " + lic.expiresDate());
        return EXIT_OK;
    }

    /**
     * Byte-for-byte behavioural replay of the commercial 3.2.1 entry point,
     * including its banner typo and the two-digit-year-vs-epoch comparison.
     */
    private static int legacy(String serial, PrintStream out) {
        Lic lic = Lic.parseLegacy(serial);
        if (lic == null) {
            out.println(PRODUCT + " " + VERSION + ")");
            out.println("invalid serial");
            return EXIT_INVALID;
        }
        if (!lic.validLegacy(System.currentTimeMillis())) {
            out.println(PRODUCT + " " + VERSION + ")");
            out.println("license expired for " + lic.owner());
            return EXIT_EXPIRED;
        }
        out.println(PRODUCT + " " + VERSION + ")");
        out.println("licensed to " + lic.owner() + " until " + lic.expires());
        return EXIT_OK;
    }

    private static int inspect(String serial, PrintStream out) {
        Lic lic = Lic.parse(serial);
        if (lic == null) {
            out.println("invalid serial");
            return EXIT_INVALID;
        }
        out.println("owner      : " + lic.owner());
        out.println("body       : " + lic.owner() + " (" + Lic.BODY_LENGTH + " chars)");
        out.println("checksum   : " + Lic.checksumHex(lic.owner()));
        out.println("expiry YY  : " + lic.expiryYearShort());
        out.println("expiry date: " + lic.expiresDate());
        out.println("valid now  : " + lic.valid(System.currentTimeMillis()));
        return EXIT_OK;
    }

    private static int generate(String[] args, PrintStream out, PrintStream err) {
        String owner = null;
        int expires = 99;
        boolean json = false;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--owner", "-o" -> {
                    if (i + 1 >= args.length) {
                        return usage(out);
                    }
                    owner = args[++i];
                }
                case "--expires", "-e" -> {
                    if (i + 1 >= args.length) {
                        return usage(out);
                    }
                    try {
                        expires = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException ex) {
                        err.println("expires must be a two digit year, e.g. 29");
                        return EXIT_USAGE;
                    }
                }
                case "--json" -> json = true;
                default -> {
                    err.println("unknown option: " + args[i]);
                    return EXIT_USAGE;
                }
            }
        }
        if (owner == null || owner.isBlank()) {
            err.println("--owner is required");
            return EXIT_USAGE;
        }
        if (expires < Lic.MIN_EXPIRY || expires > Lic.MAX_EXPIRY) {
            err.println("expires must be between 0 and 99");
            return EXIT_USAGE;
        }
        String serial = SerialGenerator.generate(owner, expires);
        Lic lic = Lic.parse(serial);
        if (lic == null) {
            err.println("internal error: generated serial did not parse");
            return EXIT_USAGE;
        }
        if (json) {
            out.println("{");
            out.println("  \"product\": \"" + PRODUCT + "\",");
            out.println("  \"version\": \"" + VERSION + "\",");
            out.println("  \"serial\": \"" + serial + "\",");
            out.println("  \"owner\": \"" + lic.owner() + "\",");
            out.println("  \"expires\": " + expires);
            out.println("}");
        } else {
            out.println(serial);
        }
        return EXIT_OK;
    }

    static String formatHex(int value) {
        return String.format(Locale.ROOT, "%04x", value);
    }
}
