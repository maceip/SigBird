#!/usr/bin/env python3
"""Extract VINTS armor from a JPEG that embeds it in a COM segment."""
from __future__ import annotations

import struct
import sys
from pathlib import Path

MAGIC = b"VINTS-ARMOR\x00"


def extract(jpeg: bytes) -> bytes:
    if jpeg[:2] != b"\xff\xd8":
        raise SystemExit("not a JPEG")
    i = 2
    n = len(jpeg)
    while i + 4 <= n:
        while i < n and jpeg[i] == 0xFF:
            i += 1
        if i >= n:
            break
        marker = jpeg[i]
        i += 1
        if marker in (0xD8, 0xD9):
            continue
        if marker == 0xDA:
            raise SystemExit("no VINTS armor COM found before SOS")
        seglen = struct.unpack(">H", jpeg[i : i + 2])[0]
        if seglen < 2 or i + seglen > n:
            raise SystemExit("truncated JPEG segment")
        seg = jpeg[i + 2 : i + seglen]
        i += seglen
        if marker == 0xFE and seg.startswith(MAGIC):
            return seg[len(MAGIC) :]
    raise SystemExit("no VINTS armor COM found")


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(f"usage: {sys.argv[0]} <jpeg> [out.txt]")
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(str(src) + ".armor.txt")
    data = extract(src.read_bytes())
    dst.write_bytes(data)
    print(f"wrote {dst} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
