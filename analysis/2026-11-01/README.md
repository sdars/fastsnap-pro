# FastSnap Pro 3.2.1 analysis

This directory holds the evidence for the reconstruction. It is excluded from
the public source tree by `.gitignore`; the jar is the original shipped sample.

- `original/FastSnapPro-3.2.1.jar` — SHA-256 `fe49098e...af1e7b`
- `original/store.txt` — vendor store description supplied with the sample
- `javap/` — `javap -p -c -constants` output for both classes
- `jadx/` — JADX 1.5.1 decompilation
- `decompiled/` — CFR 0.152 decompilation
- `jadx.log` — JADX run log

Rebuild check: `javac --release 21` on the recovered sources yields classes
byte-identical to those inside the jar (see `docs/REVERSE_ENGINEERING.md`).
