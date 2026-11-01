# FastSnap Pro 3.2.1 — open-source reconstruction

This release is the first clean, buildable reconstruction of the FastSnap Pro
3.2.1 command-line tool.

## Highlights

- Java 21, Gradle wrapper, zero runtime dependencies.
- Recovered `App` and `Lic` classes compile byte-identically to the originals.
- Fixed the 3.2.1 expiry bug (two-digit year compared to epoch milliseconds).
- `legacy` mode replays the original banner typo, separator handling and expiry
  comparison exactly.
- Deterministic serial generator with a documented fixed-point construction.
- 100 Java self-test assertions and 15 Python reference tests.
- Reproducible jar; `SHA256SUMS` attached.

## Artifacts

| File | Contents |
| --- | --- |
| `fastsnap-pro-3.2.1.jar` | executable jar |
| `fastsnap-pro-3.2.1-sources.jar` | sources |
| `fastsnap-pro-3.2.1-javadoc.jar` | API docs |
| `fastsnap-pro-3.2.1-open.zip` | self-contained bundle with `bin/` launcher |
| `fastsnap-pro-3.2.1.zip` / `.tar` | Gradle distributions |
| `SHA256SUMS` | checksums |

## Quick start

```bash
java -jar fastsnap-pro-3.2.1.jar generate --owner alice --expires 99
java -jar fastsnap-pro-3.2.1.jar verify ALICEXXXXXXANO20-99
java -jar fastsnap-pro-3.2.1.jar legacy S0BLEODH7OQDU9d0-99
```
