# Compatibility matrix

`legacy` is a behavioural replay of the commercial 3.2.1 entry point. `verify`
is the repaired entry point used by the open-source build.

| input | original 3.2.1 | `verify` | `legacy` |
| --- | --- | --- | --- |
| `S0BLEODH7OQDU9d0-99` | exit 3, expired | exit 0, licensed until 2099-12-31 | exit 3, expired |
| `S0BLEODH7OQDU9d0-00` | exit 3, expired | exit 3, expired | exit 3, expired |
| `S0BLEODH7OQDU900-99` | exit 2, invalid | exit 2, invalid | exit 2, invalid |
| `S0BLEODH7OQDU9zz-99` | uncaught `NumberFormatException`, exit 1 | exit 2, invalid | exit 2, invalid |
| `S0BLEODH7OQDU9d0_99` | exit 3 (separator ignored) | exit 2, invalid | exit 3 (separator ignored) |
| `nope` | exit 2, invalid | exit 2, invalid | exit 2, invalid |

Notes:

- The original banner string is `FastSnap Pro 3.2.1)` in every branch. `legacy`
  reproduces it exactly; `verify` prints `FastSnap Pro 3.2.1`.
- The original `Lic.parse()` did not catch `NumberFormatException`. A 19-char
  serial with non-hex checksum digits crashed the JVM. The rebuild converts that
  into a normal `invalid serial` result; this is an intentional robustness fix.
- The original ignored `serial[16]` entirely and used `Long.parseLong` on
  `serial[17..18]`. `legacy` preserves both quirks; `verify` requires `-` and two
  ASCII digits.
