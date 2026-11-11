#!/usr/bin/env python3
"""Fail-closed audit of Eufy 6.1.30 original (verified APKMirror) base APK.

Only inspect original vendor files. No package rewriting, no certificate exposure,
no vendor binary committed or stored as Actions artifact.
"""
from __future__ import annotations

from pathlib import Path
import os
import re
import shutil
import subprocess
import zipfile

from loguru import logger
logger.remove()
from androguard.core.dex import DEX

BASE = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "eufy-32823-binary-qa" / "base.apk"
PACKAGE = "com.oceanwing.battery.cam"
VERSION_NAME = "6.1.30_32823"
VERSION_CODE = "32823"
FACTORY_CLASS = "Ls/h/e/l/l/A;"
LEGACY_CLASS = "Ls/h/e/l/l/S;"
FACTORY_METHOD = "instantiateActivity"
EXPECTED_FACTORY = [
    "sget-boolean", "if-eqz", "invoke-virtual", "move-result-object",
    "iput-object", "if-eqz", "iget-object", "invoke-virtual",
    "move-result-object", "return-object", "invoke-super",
    "move-result-object", "goto",
]
EXPECTED_FACTORY_CALL = "Landroid/app/AppComponentFactory;->instantiateActivity"
# These IDs are bound into the legacy CameraOnly Java extension. Only retain
# them after positively verifying their resource name/usage in the new version.
IDS = {
    0x7f0a1719: "MAIN_TABS",
    0x7f0a0c04: "HOME_BANNER",
    0x7f0a0c03: "AI_BANNER",
    0x7f0a1a01: "SHOP_IMAGE",
    0x7f0a19ff: "SHOP_CLOSE",
    0x7f0a1284: "SOLAR_IMAGE",
    0x7f0a1285: "SOLAR_CLOSE",
    0x7f0a1e2b: "SOLAR_MORE",
    0x7f14082b: "DISCOVER",
    0x7f1439fa: "SMART",
    0x7f1437dd: "SHOP",
    0x7f1439f9: "HOME",
}


def find_aapt2() -> Path | None:
    from shutil import which
    x = which("aapt2")
    if x:
        return Path(x)
    root = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "/opt/android-sdk")))
    candidates = sorted(root.glob("build-tools/*/aapt2"), reverse=True)
    return candidates[0] if candidates else None


def main() -> None:
    if not BASE.is_file():
        raise SystemExit(f"Verified original base not present: {BASE}")
    aapt2 = find_aapt2()
    if not aapt2:
        raise RuntimeError("Cannot check Android manifest/resources: aapt2 unavailable")
    manifest_dump = subprocess.run([str(aapt2), "dump", "badging", str(BASE)],
                                   text=True, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, timeout=120, check=True).stdout
    package_record = next((line for line in manifest_dump.splitlines()
                           if line.startswith("package:")), "")
    print("Android build-tools manifest:", package_record, flush=True)
    assert f"name='{PACKAGE}'" in package_record, "Wrong app package"
    assert f"versionCode='{VERSION_CODE}'" in package_record, "Wrong versionCode"
    assert f"versionName='{VERSION_NAME}'" in package_record, "Wrong versionName"
    print("PASS: original APK manifest package and versionCode/versionName", flush=True)
    # Fail closed if the new bundle has a different signing identity. APKMirror
    # archives retain Anker's original signature on the base APK.
    apksigner = shutil.which("apksigner") or str(aapt2.parent / "apksigner")
    assert Path(apksigner).is_file(), "Android apksigner required"
    cert_output = subprocess.run([apksigner, "verify", "--print-certs", str(BASE)],
                                 text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                 timeout=120, check=True).stdout
    certs = re.findall(r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", cert_output)
    assert len(certs) == 1, f"Expected one original signer, found {len(certs)}"
    expected_cert = "166c234557b776cad8ac94c979379e48df387d4d8f96a343df40fcd905bff686"
    assert certs[0].lower() == expected_cert, "Eufy 32823 signer differs from reviewed 31181"
    print("PASS: Eufy 32823 signer matches reviewed 31181", flush=True)

    with zipfile.ZipFile(BASE) as z:
        dex_names = [n for n in z.namelist() if re.fullmatch(r"classes[0-9]*\.dex", n)]
        print("DEX files:", dex_names, flush=True)
        methods = {}
        for dex_name in dex_names:
            dex = DEX(z.read(dex_name))
            for cls in dex.get_classes():
                if cls.get_name() not in (FACTORY_CLASS, LEGACY_CLASS):
                    continue
                methods[cls.get_name()] = list(cls.get_methods())
                print("Matched class", cls.get_name(), "in", dex_name,
                      "methods", len(methods[cls.get_name()]), flush=True)
        assert FACTORY_CLASS in methods, "Eufy protected Activity factory class changed"
        assert LEGACY_CLASS in methods, "Eufy legacy startup class changed"
        factory = [m for m in methods[FACTORY_CLASS] if m.get_name() == FACTORY_METHOD
                   and m.get_descriptor() == "(Ljava/lang/ClassLoader; Ljava/lang/String; Landroid/content/Intent;)Landroid/app/Activity;"]
        # Descriptors from androguard may include spaces after parameter separators.
        if not factory:
            factory = [m for m in methods[FACTORY_CLASS] if m.get_name() == FACTORY_METHOD
                       and "Landroid/app/Activity;" in m.get_descriptor()]
        assert len(factory) == 1, "Expected exactly one Activity factory method"
        method = factory[0]
        ins = list(method.get_instructions())
        opcodes = [i.get_name() for i in ins]
        print("Factory register count", method.get_code().get_registers_size(),
              "opcodes", opcodes, flush=True)
        assert method.get_code().get_registers_size() == 5, "Factory register shape differs"
        assert opcodes == EXPECTED_FACTORY, "Factory instruction signature differs"
        factory_calls = [(idx, i.get_name(), i.get_output())
                         for idx, i in enumerate(ins)
                         if i.get_name() in ("invoke-virtual", "invoke-super")
                         and EXPECTED_FACTORY_CALL in i.get_output()]
        print("Factory call positions:", [(idx, op) for idx, op, _ in factory_calls], flush=True)
        assert [idx for idx, _, _ in factory_calls] == [7, 10], \
            "Expected delegated and framework Activity factory paths at 7 and 10"
        assert any(m.get_name()=="onCreate" and m.get_descriptor().endswith("V")
                   for m in methods[LEGACY_CLASS]), "Legacy startup onCreate changed"
        print("PASS: protected factory instruction shape, both delegated/framework paths, startup", flush=True)

    # Old numeric IDs still exist, but now identify unrelated resources.
    # The name-accurate old/new ID table is checked, along with the compiled
    # extension constants, by compare_resource_maps.py before patching.
    print("Factory preflight complete; version-specific resource map checked separately.", flush=True)


if __name__ == "__main__":
    main()
