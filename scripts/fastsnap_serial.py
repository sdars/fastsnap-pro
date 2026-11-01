#!/usr/bin/env python3
"""Standalone FastSnap Pro 3.2.1 serial codec.

Pure protocol implementation: no adb, Frida, device or RPC dependency.

The serial is 19 characters:

    14-char prefix | 2 hex checksum digits | separator | 2-digit expiry year

The checksum is the 16-bit rolling hash h = (h * 31 + ord(c)) & 0xFFFF
over the 16-character body. The final two body characters are read back as a
hexadecimal byte, so a valid body satisfies the fixed point:

    checksum(body) == int(body[14:16], 16)

Only bodies whose checksum is in 0..255 can be valid. For a fixed
14-character prefix, at most one checksum suffix works, so the generator
varies up to three trailing characters until it finds a solvable prefix.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone

SERIAL_LENGTH = 19
BODY_LENGTH = 16
CHECKSUM_OFFSET = 14
SEPARATOR_INDEX = 16
EXPIRY_OFFSET = 17
SEPARATOR = "-"
ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
HEX = "0123456789abcdef"
MAX_TAIL = 3


def checksum(body: str) -> int:
    """16-bit rolling hash used by the original 3.2.1 build."""
    value = 0
    for char in body:
        value = (value * 31 + ord(char)) & 0xFFFF
    return value


@dataclass(frozen=True)
class License:
    owner: str
    expires: int
    separator: str = SEPARATOR

    @property
    def expiry_year(self) -> int:
        return 2000 + self.expires

    @property
    def expiry_epoch_millis(self) -> int:
        end = datetime(self.expiry_year, 12, 31, 23, 59, 59, 999000, tzinfo=timezone.utc)
        return int(end.timestamp() * 1000)

    @property
    def expiry_date(self) -> str:
        return f"{self.expiry_year}-12-31"

    def valid(self, now_millis: int) -> bool:
        return 0 <= self.expires <= 99 and now_millis <= self.expiry_epoch_millis

    def valid_legacy(self, now_millis: int) -> bool:
        """The commercial bug: a two-digit year compared to epoch millis."""
        return self.expires >= now_millis

    def __str__(self) -> str:
        return f"{self.owner}{self.separator}{self.expires:02d}"


def parse(serial: str | None, *, strict: bool = True) -> License | None:
    """Parse a serial; return None instead of raising on hostile input."""
    if serial is None or len(serial) != SERIAL_LENGTH:
        return None
    body = serial[:BODY_LENGTH]
    separator = serial[SEPARATOR_INDEX]
    if strict and separator != SEPARATOR:
        return None
    expiry_text = serial[EXPIRY_OFFSET:]
    try:
        if strict:
            if not re.fullmatch(r"[0-9]{2}", expiry_text):
                return None
            expires = int(expiry_text)
        else:
            expires = int(expiry_text)
    except ValueError:
        return None
    try:
        expected = int(body[CHECKSUM_OFFSET:], 16)
    except ValueError:
        return None
    if expected > 0xFF or checksum(body) != expected:
        return None
    return License(body, expires, separator)


def fixed_point(prefix: str) -> str | None:
    """Return a valid 16-character body for a 14-character prefix."""
    if len(prefix) != 14:
        raise ValueError("prefix must be 14 characters")
    for value in range(256):
        body = prefix + HEX[value >> 4] + HEX[value & 0x0F]
        if checksum(body) == value:
            return body
    return None


def normalise(owner: str | None, length: int = 14) -> str:
    text = "".join(c for c in (owner or "").upper() if c in ALPHABET)[:length]
    return text.ljust(length, "X")


def generate(owner: str, expires: int = 99) -> str:
    """Mint a serial that passes parse() and the fixed expiry check."""
    if not 0 <= expires <= 99:
        raise ValueError("expires must be 0..99")
    normalized = normalise(owner)
    for tail in range(MAX_TAIL + 1):
        base = normalized[: 14 - tail]
        for number in range(len(ALPHABET) ** tail):
            value = number
            suffix = []
            for _ in range(tail):
                suffix.append(ALPHABET[value % len(ALPHABET)])
                value //= len(ALPHABET)
            prefix = base + "".join(reversed(suffix))
            body = fixed_point(prefix)
            if body is not None:
                return f"{body}{SEPARATOR}{expires:02d}"
    raise RuntimeError(f"no checksum fixed point for owner {owner!r}")


def _print_license(license_: License, now: int, legacy: bool) -> int:
    banner = "FastSnap Pro 3.2.1" + (")" if legacy else "")
    if legacy:
        if not license_.valid_legacy(now):
            print(banner)
            print(f"license expired for {license_.owner}")
            return 3
        print(banner)
        print(f"licensed to {license_.owner} until {license_.expires}")
        return 0
    if not license_.valid(now):
        print(banner)
        print(f"license expired for {license_.owner}")
        return 3
    print(banner)
    print(f"licensed to {license_.owner} until {license_.expiry_date}")
    return 0


def _now_millis() -> int:
    return int(datetime.now(tz=timezone.utc).timestamp() * 1000)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="fastsnap_serial.py")
    sub = parser.add_subparsers(dest="command", required=True)

    verify = sub.add_parser("verify", help="validate a serial")
    verify.add_argument("serial")

    inspect = sub.add_parser("inspect", help="decode a serial")
    inspect.add_argument("serial")

    legacy = sub.add_parser("legacy", help="replay the 3.2.1 expiry bug")
    legacy.add_argument("serial")

    generate_parser = sub.add_parser("generate", help="mint a serial")
    generate_parser.add_argument("--owner", "-o", required=True)
    generate_parser.add_argument("--expires", "-e", type=int, default=99)
    generate_parser.add_argument("--json", action="store_true")

    args = parser.parse_args(argv)
    now = _now_millis()

    if args.command == "generate":
        try:
            serial = generate(args.owner, args.expires)
        except ValueError as exc:
            parser.error(str(exc))
        license_ = parse(serial)
        assert license_ is not None
        if args.json:
            print(json.dumps({
                "product": "FastSnap Pro",
                "version": "3.2.1",
                "serial": serial,
                "owner": license_.owner,
                "expires": args.expires,
            }, indent=2))
        else:
            print(serial)
        return 0

    license_ = parse(args.serial, strict=args.command != "legacy")
    if license_ is None:
        print("FastSnap Pro 3.2.1" + (")" if args.command == "legacy" else ""))
        print("invalid serial")
        return 2
    if args.command == "verify":
        return _print_license(license_, now, legacy=False)
    if args.command == "legacy":
        return _print_license(license_, now, legacy=True)
    print(f"owner      : {license_.owner}")
    print(f"body       : {license_.owner} ({BODY_LENGTH} chars)")
    print(f"checksum   : {checksum(license_.owner):04x}")
    print(f"expiry YY  : {license_.expires}")
    print(f"expiry date: {license_.expiry_date}")
    print("valid now  : " + ("true" if license_.valid(now) else "false"))
    return 0


if __name__ == "__main__":
    sys.exit(main())

