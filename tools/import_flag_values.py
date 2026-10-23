#!/usr/bin/env python3
"""Holt die aconfig-Flagwerte eines AOSP-Release-Configs aus platform/build/release.

Die Werte stehen pro Release in aconfig/<release>/<paket>/*_flag_values.textproto; ein Release
erbt von anderen (release_configs/<release>.textproto, "inherits"). Das Skript lädt das
Verzeichnis als tar.gz von Gitiles, löst die Vererbung auf und schreibt die Werte der Pakete,
die der Launcher braucht, nach flags/release-values.properties.

Aufruf: tools/import_flag_values.py <aosp-tag> <release>
Beispiel: tools/import_flag_values.py android-17.0.0_r1 cp1a
"""
import io
import pathlib
import re
import sys
import tarfile
import urllib.request

REPO = "https://android.googlesource.com/platform/build/release/+archive/refs/tags"

# aconfig-Paket in AOSP -> Paket der generierten Flags-Klasse (siehe flags/aconfig/fw_*.aconfig)
PACKAGES = {
    "com.android.launcher3": "com.android.launcher3",
    "com.android.systemui.shared": "com.android.systemui.shared",
    "com.android.wm.shell": "com.android.wm.shell",
    "com.android.providers.media.flags": "com.android.providers.media.flags",
    "android.appwidget.flags": "de.letzgo.launcher.fwflags.appwidget",
    "android.security": "de.letzgo.launcher.fwflags.security",
    "android.multiuser": "de.letzgo.launcher.fwflags.multiuser",
    "com.android.window.flags": "de.letzgo.launcher.fwflags.window",
}


def fetch(tag, path):
    with urllib.request.urlopen(f"{REPO}/{tag}/{path}.tar.gz") as resp:
        return tarfile.open(fileobj=io.BytesIO(resp.read()), mode="r:gz")


def read_all(tar):
    return {m.name: tar.extractfile(m).read().decode() for m in tar.getmembers() if m.isfile()}


def main(tag, release):
    configs = read_all(fetch(tag, "release_configs"))
    aconfig = read_all(fetch(tag, "aconfig"))

    def chain(name):
        text = configs.get(f"{name}.textproto", "")
        result = []
        for parent in re.findall(r'inherits:\s*"([^"]+)"', text):
            result += chain(parent)
        return result + [name]

    order = list(dict.fromkeys(chain(release)))
    values = {}
    for name in order:
        for path, text in sorted(aconfig.items()):
            if not path.startswith(f"{name}/") or not path.endswith("_flag_values.textproto"):
                continue
            for block in re.findall(r"flag_value\s*\{(.*?)\}", text, re.S):
                pkg = re.search(r'package:\s*"([^"]+)"', block)
                flag = re.search(r'name:\s*"([^"]+)"', block)
                state = re.search(r"state:\s*(\w+)", block)
                if pkg and flag and state and pkg.group(1) in PACKAGES:
                    key = f"{PACKAGES[pkg.group(1)]}.{flag.group(1)}"
                    values[key] = state.group(1) == "ENABLED"

    out = pathlib.Path(__file__).resolve().parent.parent / "flags" / "release-values.properties"
    lines = [
        f"# Generiert von tools/import_flag_values.py {tag} {release}, nicht bearbeiten.",
        f"# Release-Kette: {' -> '.join(order)}",
    ]
    lines += [f"{k}={'true' if v else 'false'}" for k, v in sorted(values.items())]
    out.write_text("\n".join(lines) + "\n")
    print(f"{len(values)} Werte nach {out}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
