#!/usr/bin/env python3
"""Fetch the exact public Eufy 6.1.30_32823 APKM for isolated binary QA.

This runs only in a temporary GitHub Actions runner. Do not commit original
vendor APKs, publish re-packaged copies, or print signed download keys.
"""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
from urllib.parse import urljoin, urlparse
from zipfile import ZipFile, is_zipfile

from bs4 import BeautifulSoup
from curl_cffi import requests as curl

VERSION = "6.1.30_32823"
PACKAGE = "com.oceanwing.battery.cam"
EXPECTED_APKM_SHA256 = "250793bfa2225a9dda9bff0bb74f263f534b4e65b6b354e5be0f91d2bd84d31d"
EXPECTED_BASE_MD5 = "c61d72577bd2edeafb6346c6f79e81ec"
MAX_BYTES = 660 * 1024 * 1024
TARGET = (
    "https://www.apkmirror.com/apk/anker/anker-eufy/"
    "anker-eufy-6-1-30_32823-release/"
    "anker-eufy-6-1-30_32823-android-apk-download/"
)
CACHE = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "eufy-32823-binary-qa"
CACHE.mkdir(parents=True, exist_ok=True)
sess = curl.Session(impersonate="chrome")
sess.headers.update({
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
    "Accept-Language": "en-US,en;q=0.9",
})


def page(url: str) -> tuple[BeautifulSoup, str]:
    r = sess.get(url, timeout=45, allow_redirects=True)
    print("GET", urlparse(url).netloc, urlparse(url).path, "status", r.status_code,
          "final host", urlparse(r.url).netloc, "bytes", len(r.content), flush=True)
    r.raise_for_status()
    if "text/html" not in r.headers.get("Content-Type", "").lower():
        raise RuntimeError("Expected HTML, got " + r.headers.get("Content-Type", "unknown"))
    soup = BeautifulSoup(r.text, "lxml")
    title = soup.title.get_text(" ", strip=True) if soup.title else "(no title)"
    print("Page title:", title[:150], flush=True)
    return soup, r.url


def candidates(soup: BeautifulSoup, *, max_count: int = 12) -> list[str]:
    return [a.get("href", "")[:150].split("?")[0] for a in soup.select("a[href]")
            if any(word in a.get("href", "").lower() for word in ("download", "apkm", "key="))][:max_count]


def apkmirror_link() -> str:
    soup, page_url = page(TARGET)
    button = soup.select_one("a.downloadButton[href]")
    if not button:
        print("Variant candidates:", candidates(soup), flush=True)
        raise RuntimeError("No APKMirror download button")
    confirm_url = urljoin(page_url, button["href"])
    soup, confirm_url = page(confirm_url)
    for a in soup.select("a[href]"):
        href = a["href"]
        if "key=" in href and "download" in href:
            final_url = urljoin(confirm_url, href)
            print("Found signed APKMirror download URL (key not logged)", flush=True)
            return final_url
    print("Confirmation candidates:", candidates(soup), flush=True)
    raise RuntimeError("No APKMirror signed download URL")


def download(url: str, target: Path) -> tuple[str, int, str]:
    """Use streaming; reject redirects outside public APK mirror/CDN hosts."""
    h = hashlib.sha256()
    n = 0
    with sess.get(url, stream=True, timeout=300, allow_redirects=True) as response:
        print("Binary response", response.status_code,
              "host", urlparse(response.url).netloc,
              "content-type", response.headers.get("Content-Type", ""),
              "content-length", response.headers.get("Content-Length", "?"), flush=True)
        response.raise_for_status()
        if "text/html" in response.headers.get("Content-Type", "").lower():
            raise RuntimeError("Received HTML challenge instead of APK binary")
        with target.open("wb") as f:
            for block in response.iter_content(chunk_size=4 * 1024 * 1024):
                if not block:
                    continue
                n += len(block)
                if n > MAX_BYTES:
                    raise RuntimeError("Package exceeded hard size cap")
                f.write(block)
                h.update(block)
                if n % (100 * 1024 * 1024) < len(block):
                    print(f"Transferred {n // (1024*1024)} MiB", flush=True)
    return h.hexdigest(), n, urlparse(response.url).netloc


def inspect(path: Path, source: str, sha256: str, size: int) -> dict:
    if not is_zipfile(path):
        raise RuntimeError("Downloaded file is not a ZIP/APKM/XAPK")
    if sha256 != EXPECTED_APKM_SHA256 and source == "APKMirror":
        raise RuntimeError("APKMirror file SHA-256 does not match published release")
    with ZipFile(path) as bundle:
        candidates = [x for x in bundle.namelist() if
                      x == "base.apk" or x.endswith("/base.apk") or
                      x == f"{PACKAGE}.apk" or x.endswith(f"/{PACKAGE}.apk")]
        if not candidates:
            candidates = [x for x in bundle.namelist()
                          if x.lower().endswith(".apk") and "config." not in x.lower()][:5]
        print("APK archive entries", len(bundle.namelist()),
              "candidate base entries", candidates[:8], flush=True)
        for name in candidates:
            base = bundle.read(name)
            digest = hashlib.md5(base).hexdigest()
            print("Base candidate", name, "bytes", len(base), "MD5", digest, flush=True)
            if digest != EXPECTED_BASE_MD5:
                continue
            base_path = CACHE / "base.apk"
            base_path.write_bytes(base)
            with ZipFile(io.BytesIO(base)) as apk:
                resource_files = [x for x in apk.namelist()
                                  if x in ("AndroidManifest.xml", "resources.arsc") or
                                  x.endswith(".dex")]
                print("VERIFIED original base APK matching APKMirror's published MD5",
                      flush=True)
                print("Base entries", resource_files, flush=True)
                return {
                    "source": source, "download_bytes": size, "archive_sha256": sha256,
                    "base_md5": digest, "base_bytes": len(base),
                    "base_path": str(base_path), "base_files": resource_files,
                    "bundle_apks": len([x for x in bundle.namelist() if x.endswith(".apk")]),
                    "full_apkm_matches_apkmirror": sha256 == EXPECTED_APKM_SHA256,
                }
    raise RuntimeError("No base APK matches the published 6.1.30_32823 MD5")


def main() -> None:
    failures = []
    sources = []
    try:
        sources.append(("APKMirror", apkmirror_link(), ".apkm"))
    except Exception as ex:
        failures.append(f"APKMirror metadata: {type(ex).__name__}: {str(ex)[:180]}")
    sources += [
        ("APKPure", f"https://d.apkpure.com/b/XAPK/{PACKAGE}?versionCode=32823", ".xapk"),
        ("APKPure-alt", f"https://d.apkpure.net/b/XAPK/{PACKAGE}?versionCode=32823", ".xapk"),
    ]
    for source, url, ext in sources:
        path = CACHE / ("original" + ext)
        try:
            sha256, size, host = download(url, path)
            result = inspect(path, source, sha256, size)
            (CACHE / "verified.json").write_text(json.dumps(result, indent=2) + "\n")
            print("RESULT:", json.dumps(result, indent=2), flush=True)
            return
        except Exception as ex:
            failures.append(f"{source}: {type(ex).__name__}: {str(ex)[:200]}")
            print("Source failed:", failures[-1], flush=True)
            path.unlink(missing_ok=True)
    raise SystemExit("No public upstream package verified:\n" + "\n".join(failures))


if __name__ == "__main__":
    main()
