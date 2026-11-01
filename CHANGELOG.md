# Changelog

## 3.2.1 — 2026-11-01

First open-source reconstruction of FastSnap Pro 3.2.1.

- Recovered `App` and `Lic` from the shipped jar; both rebuild byte-identically.
- Added strict serial parsing, end-of-year expiry semantics and a deterministic
  serial generator.
- Added `verify`, `inspect`, `generate`/`keygen`, `legacy`, `version` and `help`.
- Added a 100-assertion, dependency-free self-test suite.
- Added a Python reference implementation and cross-check script.
- Documented the key format, checksum fixed-point construction and original
  compatibility quirks.
