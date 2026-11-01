# FastSnap Pro 3.2.1 serial algorithm

## 1. Layout

A serial is 19 ASCII characters:

| Offset | Length | Meaning |
| --- | --- | --- |
| 0 | 14 | free-form body prefix (printed as the owner) |
| 14 | 2 | lowercase hexadecimal checksum byte |
| 16 | 1 | separator; `-` in well-formed keys, ignored by 3.2.1 |
| 17 | 2 | expiry year, `00`..`99` meaning 2000..2099 |

The body is `serial[0..15]`. The original jar returned the entire body as the
owner string, so the checksum digits are visible in the printed owner.

## 2. Checksum

```text
h = 0
for c in body:
    h = (h * 31 + ord(c)) & 0xFFFF
```

Validation compares `h` with `Integer.parseInt(body[14..15], 16)`. Because the
stored value is only two hex digits, the comparison succeeds only when `h` is
in `0..255` and equals those two digits. This is a 16-bit rolling hash truncated
at the comparison site, not a cryptographic MAC.

Reference vectors (verified against the original jar and CFR/JADX output):

| body | h | stored | valid |
| --- | --- | --- | --- |
| `S0BLEODH7OQDU9d0` | `0x00d0` | `d0` | yes |
| `S0BLEODH7OQDU900` | `0x????` | `00` | no |
| `FastSnapaaaakm80` | `0x0080` | `80` | yes |

## 3. Generating a key

For a fixed 14-character prefix `p`, let `P = checksum(p)`. The body is
`p + c1 + c2`, where `c1` and `c2` are ASCII hex digits with values `d1` and
`d2`. Expanding the hash:

```text
checksum(body) = (961 * P + 31 * ascii(c1) + ascii(c2)) & 0xFFFF
```

With `ascii(c) = 48 + d`, the requirement `checksum(body) == 16*d1 + d2`
reduces to:

```text
15 * d1 == -961 * P - 1536   (mod 65536)
```

`15` is invertible modulo `65536` (`15 * 61167 == 1`), so `d1` is uniquely
determined. If it falls in `0..15`, the prefix admits a fixed point; `d2` then
cancels out and can be `0`. About 1 prefix in 256 has a solution.

The generator normalises the owner to `[A-Z0-9]`, pads with `X` to 14
characters, then varies the last 0..3 characters over `[A-Z0-9]` until it finds
a solvable prefix. This is deterministic and bounded. Examples produced by the
implementation:

| requested owner | generated body | serial |
| --- | --- | --- |
| `alice` | `ALICEXXXXXXANO20` | `ALICEXXXXXXANO20-99` |
| `Bob Smith` | `BOBSMITHXXXA4C60` | `BOBSMITHXXXA4C60-99` |
| `ACME` | `ACMEXXXXXXXA0H2a` | `ACMEXXXXXXXA0H2a-99` |

## 4. Expiry

The stored `YY` is a year in 2000..2099. The reconstructed validity check treats
the license as valid through `23:59:59.999 UTC` on 31 December of that year.

The commercial jar instead executed `expires >= System.currentTimeMillis()`,
comparing `0..99` with epoch milliseconds. Every key therefore printed
`license expired`. `Lic.validLegacy()` and `fastsnap-pro legacy <serial>` keep
that behaviour for forensic replay.

## 5. Python reference

A dependency-free reference implementation lives in
`scripts/fastsnap_serial.py`. It implements parse, checksum, fixed-point search and
verification, and is exercised by `scripts/test_reference.py` against the same
vectors as the Java suite.
