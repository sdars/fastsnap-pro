# FastSnap Pro 3.2.1 — open-source reconstruction

[![build](https://github.com/sdars/fastsnap-pro/actions/workflows/build.yml/badge.svg)](https://github.com/sdars/fastsnap-pro/actions/workflows/build.yml)

This repository is a clean, buildable, dependency-free Java reconstruction of the
FastSnap Pro 3.2.1 command-line license tool. It preserves the observable
behaviour of the shipped 3.2.1 jar, repairs the two defects that made the
commercial build unusable, and adds a deterministic serial generator so the
project can be built and exercised without a vendor server.

- Java 21, Gradle 8.9 wrapper, zero runtime dependencies
- 100-assertion offline self-test suite
- Reproducible jar, sources jar and Javadoc jar
- Documented key format and checksum algorithm
- `legacy` subcommand that replays the original 3.2.1 behaviour byte-for-byte

## Quick start

```bash
./gradlew build selftest
java -jar build/libs/fastsnap-pro-3.2.1.jar version
java -jar build/libs/fastsnap-pro-3.2.1.jar generate --owner alice --expires 99
java -jar build/libs/fastsnap-pro-3.2.1.jar verify ALICEXXXXXXANO20-99
java -jar build/libs/fastsnap-pro-3.2.1.jar inspect ALICEXXXXXXANO20-99
```

`verify` exits `0` for a valid license, `2` for a malformed serial, `3` for an
expired license and `64` for a usage error. A bare first argument is treated as
a serial, so the original `java -jar ... <serial>` invocation still works.

## Key format

A serial is exactly 19 characters:

```text
[0..13]  body prefix, 14 characters
[14..15] two lowercase hex digits: checksum(body) & 0xFF
[16]     '-' separator
[17..18] two-digit expiry year, 00..99 meaning 2000..2099
```

The body is the first 16 characters. Its rolling checksum is
`h = (h * 31 + c) & 0xFFFF` over all 16 characters. A body is valid only when
`checksum(body) == Integer.parseInt(body.substring(14, 16), 16)`. Because the
stored value is a byte, only bodies whose checksum lands in `0..255` are
representable. See [docs/ALGORITHM.md](docs/ALGORITHM.md) for the derivation and
the generator's fixed-point search.

## Compatibility with the commercial 3.2.1 jar

The shipped jar had two defects, both preserved in `legacy` mode:

1. the banner was printed as `FastSnap Pro 3.2.1)` (stray parenthesis);
2. `Lic.valid()` compared the two-digit expiry year directly with
   `System.currentTimeMillis()`, so every key was reported as expired.

The default mode fixes those defects: the banner is clean and the expiry is
interpreted as 23:59:59.999 UTC on 31 December of `2000 + YY`. `legacy` keeps
the original text, the original separator handling and the original expiry
comparison. See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## Build requirements

JDK 21 is required (the Gradle build targets Java 21). No network access is
needed after the Gradle distribution and wrapper are present. The wrapper is
checked in.

```bash
./gradlew clean build selftest
```

Artifacts:

- `build/libs/fastsnap-pro-3.2.1.jar`
- `build/libs/fastsnap-pro-3.2.1-sources.jar`
- `build/libs/fastsnap-pro-3.2.1-javadoc.jar`
- `build/distributions/fastsnap-pro-3.2.1.zip` / `.tar`
- `build/release/fastsnap-pro-3.2.1-open.zip` (self-contained bundle with launcher)

## Repository layout

```text
src/main/java/com/fastsnap/core/   App, Lic, SerialGenerator
src/test/java/com/fastsnap/core/   SelfTest (100 assertions, no JUnit)
docs/                               protocol, algorithm and lessons
analysis/2026-11-01/               original jar, hashes, javap and jadx output
scripts/                            release and verification helpers
```

## License

MIT. The reconstructed source is an independent implementation; the original
3.2.1 artifact is kept under `analysis/` for forensic comparison only.
