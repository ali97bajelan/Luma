#!/usr/bin/env python3
"""Audit source color contrast and bundled font coverage without building Android.

Run from any directory with Python 3 and fontconfig (fc-query). This measures
opaque role pairs, not rendered pixels, alpha/disabled states or Android shaping.
"""

from pathlib import Path
import hashlib
import re
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[2]
THEME = ROOT / "app/src/main/java/com/luma/lifeos/ui/theme/Theme.kt"
failures = []


def luminance(rgb):
    channels = [int(rgb[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in channels]
    return sum(v * weight for v, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(a, b):
    low, high = sorted((luminance(a), luminance(b)))
    return (high + 0.05) / (low + 0.05)


print("# T30 source audit\n")
print("Opaque sRGB WCAG contrast; text threshold 4.5:1, essential boundaries 3:1.\n")
print("| Theme | Foreground / background | Hex pair | Ratio | Result |")
print("| --- | --- | --- | --- | --- |")
palettes = {"Light": {}, "Dark": {}}
for theme, block in re.findall(r"private val (Light|Dark)(?:Fixed)?Colors = \w+\((.*?)\n\)", THEME.read_text(), re.S):
    palettes[theme].update(re.findall(r"(\w+) = Color\(0xFF([0-9A-F]{6})\)", block))
assert all(palettes.values()), "Theme palettes not found"
for theme, colors in palettes.items():
    pairs = [("on" + role[0].upper() + role[1:], role, 4.5) for role in
             ("primary", "primaryContainer", "secondary", "secondaryContainer", "tertiary",
              "tertiaryContainer", "primaryFixed", "error", "errorContainer", "background")]
    pairs += [(fg, bg, 4.5) for bg in ("primaryFixed", "primaryFixedDim")
              for fg in ("onPrimaryFixed", "onPrimaryFixedVariant")]
    pairs += [("inverseOnSurface", "inverseSurface", 4.5), ("inversePrimary", "inverseSurface", 4.5)]
    surfaces = [role for role in colors if role.startswith("surface") and role != "surfaceTint"]
    pairs += [(fg, bg, threshold) for bg in surfaces for fg, threshold in
              (("onSurface", 4.5), ("onSurfaceVariant", 4.5), ("primary", 4.5),
               ("secondary", 4.5), ("tertiary", 4.5), ("error", 4.5), ("outline", 3.0))]
    for fg, bg, threshold in pairs:
        ratio = contrast(colors[fg], colors[bg])
        result = "PASS" if ratio >= threshold else "FAIL"
        if result == "FAIL":
            failures.append(f"{theme} {fg}/{bg}")
        print(f"| {theme} | {fg} / {bg} | #{colors[fg]} / #{colors[bg]} | {ratio:.3f} | {result} |")

print("\n## Bundled fonts\n")
latin = "Luma ÀÂÆÇÉÈÊËÎÏÔŒÙÛÜŸ àâæçéèêëîïôœùûüÿ 0123456789"
for filename, sample in (("newsreader.ttf", latin), ("plus_jakarta_sans.ttf", latin),
                         ("vazirmatn.ttf", "Luma 12 kg امروز می‌روم ۱۲۳ فارسی")):
    path = ROOT / "app/src/main/res/font" / filename
    data = path.read_bytes()
    tables = {data[i:i + 4]: struct.unpack_from(">II", data, i + 8)
              for i in range(12, 12 + struct.unpack_from(">H", data, 4)[0] * 16, 16)}
    offset, _ = tables[b"fvar"]
    _, _, axes_start, _, axes_count, axis_size, _, _ = struct.unpack_from(">8H", data, offset)
    axes = {}
    for index in range(axes_count):
        tag, minimum, default, maximum = struct.unpack_from(">4siii", data, offset + axes_start + index * axis_size)
        axes[tag.decode()] = tuple(value / 65536 for value in (minimum, default, maximum))
    assert axes["wght"][0] <= 400 <= 700 <= axes["wght"][2]
    if filename == "newsreader.ttf":
        assert axes["opsz"][0] <= 26 <= axes["opsz"][2]
    charset = subprocess.check_output(["fc-query", "--format=%{charset}\n", str(path)], text=True).splitlines()[0]
    ranges = [tuple(int(part, 16) for part in value.split("-")) for value in charset.split()]
    missing = sorted({ch for ch in sample if not any(r[0] <= ord(ch) <= r[-1] for r in ranges)})
    if missing:
        failures.append(f"{filename}: missing {missing}")
    print(f"- {filename}: {'FAIL' if missing else 'PASS'} sample glyph coverage; axes {axes}; "
          f"SHA-256 `{hashlib.sha256(data).hexdigest()}`.")

print(f"\nResult: {'FAIL' if failures else 'PASS'} ({len(failures)} failures). "
      "Android rendering, accessibility and layout validation: NOT RUN.")
raise SystemExit(bool(failures))
