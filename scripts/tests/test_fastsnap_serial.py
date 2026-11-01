#!/usr/bin/env python3
"""Tests for the standalone Python serial codec."""

from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import fastsnap_serial as fs  # noqa: E402


class ChecksumTests(unittest.TestCase):
    def test_empty(self) -> None:
        self.assertEqual(fs.checksum(""), 0)

    def test_reference(self) -> None:
        self.assertEqual(fs.checksum("S0BLEODH7OQDU9d0"), 0xD0)

    def test_original_prefix(self) -> None:
        self.assertEqual(fs.checksum("FASTSNAP000000"), 0x7A6)


class ParseTests(unittest.TestCase):
    def test_rejects(self) -> None:
        for value in (None, "", "ABC", "S0BLEODH7OQDU9d0-999",
                      "S0BLEODH7OQDU900-99", "S0BLEODH7OQDU9zz-99",
                      "S0BLEODH7OQDU9d0_99", "S0BLEODH7OQDU9d0-9x"):
            with self.subTest(value=value):
                self.assertIsNone(fs.parse(value))

    def test_accepts_reference(self) -> None:
        license_ = fs.parse("S0BLEODH7OQDU9d0-27")
        self.assertIsNotNone(license_)
        assert license_ is not None
        self.assertEqual(license_.owner, "S0BLEODH7OQDU9d0")
        self.assertEqual(license_.expires, 27)
        self.assertEqual(license_.expiry_year, 2027)
        self.assertEqual(license_.expiry_date, "2027-12-31")

    def test_legacy_separator_quirk(self) -> None:
        self.assertIsNotNone(fs.parse("S0BLEODH7OQDU9d0X99", strict=False))
        self.assertIsNone(fs.parse("S0BLEODH7OQDU9d0X99"))

    def test_legacy_expiry_bug(self) -> None:
        license_ = fs.parse("S0BLEODH7OQDU9d0-99")
        assert license_ is not None
        self.assertFalse(license_.valid_legacy(fs._now_millis()))


class GeneratorTests(unittest.TestCase):
    def test_known_owner(self) -> None:
        self.assertEqual(fs.generate("alice", 99), "ALICEXXXXXXANO20-99")
        self.assertEqual(fs.generate("Bob Smith", 30), "BOBSMITHXXXA4C60-30")

    def test_round_trip(self) -> None:
        for owner in ("alice", "Bob Smith", "ACME", "x", "", "张三", "A" * 40):
            serial = fs.generate(owner, 99)
            license_ = fs.parse(serial)
            self.assertIsNotNone(license_, serial)
            assert license_ is not None
            self.assertEqual(len(serial), 19)
            self.assertEqual(serial[16], "-")
            self.assertEqual(serial[17:], "99")
            self.assertEqual(fs.checksum(license_.owner),
                             int(license_.owner[14:], 16))

    def test_prefix_preserved(self) -> None:
        self.assertTrue(fs.generate("Bob Smith").startswith("BOBSMITHXXX"))
        self.assertTrue(fs.generate("").startswith("XXXXXXXXXXX"))

    def test_rejects_bad_expiry(self) -> None:
        for value in (-1, 100):
            with self.assertRaises(ValueError):
                fs.generate("alice", value)

    def test_fixed_point(self) -> None:
        body = fs.fixed_point("S0BLEODH7OQDU9")
        self.assertIsNotNone(body)
        assert body is not None
        self.assertEqual(fs.checksum(body), int(body[14:], 16))
        self.assertIsNone(fs.fixed_point("FASTSNAP000000"))


class CliTests(unittest.TestCase):
    def run_cli(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(Path(fs.__file__)), *args],
            capture_output=True, text=True, check=False,
        )

    def test_generate_and_verify(self) -> None:
        generated = self.run_cli("generate", "--owner", "alice", "--expires", "99")
        self.assertEqual(generated.returncode, 0, generated.stderr)
        serial = generated.stdout.strip()
        self.assertEqual(serial, "ALICEXXXXXXANO20-99")

        verified = self.run_cli("verify", serial)
        self.assertEqual(verified.returncode, 0, verified.stdout)
        self.assertIn("licensed to ALICEXXXXXXANO20 until 2099-12-31", verified.stdout)

        legacy = self.run_cli("legacy", serial)
        self.assertEqual(legacy.returncode, 3)
        self.assertIn("FastSnap Pro 3.2.1)", legacy.stdout)

        inspected = self.run_cli("inspect", serial)
        self.assertEqual(inspected.returncode, 0)
        self.assertIn("valid now  : true", inspected.stdout)

    def test_json(self) -> None:
        result = self.run_cli("generate", "-o", "alice", "-e", "42", "--json")
        self.assertEqual(result.returncode, 0, result.stderr)
        payload = json.loads(result.stdout)
        self.assertEqual(payload["expires"], 42)
        self.assertTrue(payload["serial"].endswith("-42"))

    def test_invalid(self) -> None:
        result = self.run_cli("verify", "nope")
        self.assertEqual(result.returncode, 2)
        self.assertIn("invalid serial", result.stdout)


if __name__ == "__main__":
    unittest.main()

