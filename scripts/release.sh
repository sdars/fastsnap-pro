#!/usr/bin/env bash
# Build the release payload, verify it, and print checksums.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

./gradlew --no-daemon clean build selftest releaseBundle
if [[ -x /root/Knight/.venv/bin/python ]]; then
  /root/Knight/.venv/bin/python scripts/test_reference.py
else
  python3 scripts/test_reference.py
fi

mkdir -p release
cp build/libs/fastsnap-pro-3.2.1.jar release/
cp build/libs/fastsnap-pro-3.2.1-sources.jar release/
cp build/libs/fastsnap-pro-3.2.1-javadoc.jar release/
cp build/distributions/fastsnap-pro-3.2.1.zip release/
cp build/distributions/fastsnap-pro-3.2.1.tar release/
cp build/release/fastsnap-pro-3.2.1-open.zip release/
(
  cd release
  sha256sum fastsnap-pro-3.2.1* > SHA256SUMS
  sha256sum -c SHA256SUMS
)
cat release/SHA256SUMS
