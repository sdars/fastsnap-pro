#!/usr/bin/env python3
"""Cross-check the Python reference against fixed Java vectors."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from fastsnap_serial import checksum, generate, parse  # noqa: E402

VECTORS = [
    ("alice", "ALICEXXXXXXANO20-99"),
    ("Bob Smith", "BOBSMITHXXXA4C60-99"),
    ("ACME", "ACMEXXXXXXXA0H2a-99"),
]


def main() -> int:
    failures = 0
    if checksum("S0BLEODH7OQDU9d0") != 0xD0:
        print("FAIL checksum")
        failures += 1
    if parse("S0BLEODH7OQDU9d0-99") is None:
        print("FAIL parse")
        failures += 1
    if parse("S0BLEODH7OQDU900-99") is not None:
        print("FAIL tamper")
        failures += 1
    for owner, expected in VECTORS:
        actual = generate(owner, 99)
        if actual != expected:
            print(f"FAIL generate {owner}: {actual} != {expected}")
            failures += 1
        if parse(expected) is None:
            print(f"FAIL valid {expected}")
            failures += 1
    if failures:
        print(f"{failures} failure(s)")
        return 1
    print("OK: python reference matches Java vectors")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
