#!/usr/bin/env python3
"""Compare legacy Eufy 6.1.20 UI resource names against 6.1.30.

Download only official matching original APKMirror bundles in an ephemeral
runner and report resource-name-to-ID changes. No APKs committed or uploaded.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import zipfile

from fetch_upstream_32823 import CACHE, TARGET, apkmirror_link, download
from inspect_32823 import BASE, IDS, find_aapt2

OLD_SHA256 = "9e31dffe605d05b07452a8d53bf926c57513b442fe5e713d468a97e3d6131ca5"
OLD_URL = TARGET.replace("6-1-30_32823", "6-1-20_31181")
JAVA_SOURCE = Path(__file__).resolve().parents[2] / "extensions/eufy/src/main/java/ca/mlkem/eufy/CameraOnly.java"

def source_id(source: str, name: str) -> int:
    hits = re.findall(r"\b" + re.escape(name) + r"\s*=\s*(0x[0-9a-fA-F]+)\b", source)
    assert len(hits) == 1, f"Expected exactly one ID declaration for {name}; found {hits}"
    return int(hits[0], 16)


def build_maps(aapt2: Path, apk: Path) -> tuple[dict[int, tuple[str, str]], dict[tuple[str,str],int]]:
    result = subprocess.run([str(aapt2), "dump", "resources", str(apk)],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            text=True, timeout=120, check=True)
    by_id, by_name = {}, {}
    rx = re.compile(r"^\s*resource\s+(0x[0-9a-fA-F]{8})\s+(id|string)/([^\s(:]+)")
    for line in result.stdout.splitlines():
        m = rx.match(line)
        if not m:
            continue
        rid = int(m.group(1), 16)
        key = (m.group(2), m.group(3))
        by_id[rid] = key
        by_name[key] = rid
    return by_id, by_name


def main(patched: Path | None = None) -> None:
    source = JAVA_SOURCE.read_text()
    aapt2 = find_aapt2()
    assert aapt2 is not None, "Android build-tools aapt2 required"
    old_path = CACHE / "original-31181.apkm"
    if not old_path.exists():
        url = apkmirror_link(OLD_URL)
        sha, n, host = download(url, old_path)
        assert sha == OLD_SHA256, f"Old APKMirror SHA256 mismatch: {sha}"
        print("Verified original 6.1.20 APKM", n, sha, flush=True)
    with zipfile.ZipFile(old_path) as z:
        assert "base.apk" in z.namelist()
        old_base = CACHE / "base-31181.apk"
        old_base.write_bytes(z.read("base.apk"))
    old_ids, old_names = build_maps(aapt2, old_base)
    new_ids, new_names = build_maps(aapt2, BASE)
    patched_names = build_maps(aapt2, patched)[1] if patched else None
    print("=== Eufy 6.1.20 -> 6.1.30 UI resource remapping ===", flush=True)
    missing = []
    remaps = {}
    for old_id, label in IDS.items():
        old_key = old_ids.get(old_id)
        new_key_at_old_id = new_ids.get(old_id)
        new_id = new_names.get(old_key) if old_key else None
        print(f"{label}: legacy=0x{old_id:08x} old_name={old_key} "
              f"new_name_at_legacy_id={new_key_at_old_id} "
              f"new_id_for_old_name={hex(new_id) if new_id else '<missing>'}", flush=True)
        if not old_key or new_id is None:
            missing.append(label)
        else:
            assert source_id(source, label) == old_id, f"Legacy Java constant mismatches {label}"
            assert source_id(source, label + "_32823") == new_id, f"32823 Java constant mismatches {label}"
            if patched_names is not None:
                assert patched_names.get(old_key) == new_id, (
                    f"Resource ID changed while patching {label}: "
                    f"original 32823 {new_id:#x}, patched {patched_names.get(old_key)}"
                )
            remaps[label] = (old_id, new_id, old_key)
    assert not missing, f"Critical UI resource names missing: {missing}"
    print("ALL legacy UI resource names map to current IDs:", flush=True)
    for label, (old_id, new_id, key) in remaps.items():
        print(f"{label} 0x{old_id:08x} => 0x{new_id:08x} {key}", flush=True)
    print("PASS: both Eufy source ID tables match original APKMs", flush=True)
    if patched_names is not None:
        print("PASS: all 12 Eufy 32823 resource IDs retained in the merged patched APK", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--patched", type=Path, help="Verify the new APK preserves target ID assignments")
    main(parser.parse_args().patched)
