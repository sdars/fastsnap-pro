# Reverse-engineering notes

## Artifact

- `analysis/2026-11-01/original/FastSnapPro-3.2.1.jar`
- SHA-256 `fe49098e804420bf4d963bae81b2e32852613edfd0c2220eaa6b3c8a9baf1e7b`
- 2,420 bytes, 7 zip entries, classes compiled with major version 65 (Java 21)

## Tools used

| Tool | Purpose |
| --- | --- |
| `unzip -l/-p` | inventory, manifest, class extraction |
| `javap -p -c -constants` | bytecode and constant-pool ground truth |
| JADX 1.5.1 (`/root/Knight/jadx/bin/jadx`) | independent Java decompilation |
| CFR 0.152 | second decompiler for cross-checking |
| `javac --release 21` | rebuild and byte-identical class comparison |

## Verification

Compiling the two recovered source files with `javac --release 21` produced
classes byte-identical to the classes inside the original jar:

| class | SHA-256 |
| --- | --- |
| `com/fastsnap/core/App.class` | `fd211d325c0089ea53f2da4fd0c68ec2cb5507d28b2b5ee9654eedc9694c040f` |
| `com/fastsnap/core/Lic.class` | `fcc16018c88399dd2c34061ace1ec31139eed89d9c4961283944dcee4750c30e` |

`cmp` against the extracted classes reports identical files. This proves the
decompiled control flow, string constants and field layout are complete for the
original two-class program.

## What changed in the rebuild

- `App` gained `verify`, `inspect`, `generate`/`keygen`, `legacy`, `version`
  and `help`. A bare serial still takes the original path.
- `Lic` gained strict parsing, range-checked checksum parsing, end-of-year
  expiry semantics, and explicit legacy methods.
- `SerialGenerator` is new; it implements the fixed-point search described in
  [ALGORITHM.md](ALGORITHM.md).
- The self-test suite is new and has no third-party dependencies.

## Original bytecode facts

- `Lic.parse` requires exactly 19 characters, takes `substring(0, 16)` as the
  body, and `Long.parseLong(substring(17))` as the expiry.
- The checksum loop is `n = n * 31 + body.charAt(i) & 0xFFFF`.
- The comparison is against `Integer.parseInt(body.substring(14, 16), 16)`.
- `valid(long)` is `expires >= now`.
- `App.main` prints the literal `FastSnap Pro 3.2.1)` in all three branches.
