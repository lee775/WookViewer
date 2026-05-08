#!/usr/bin/env python3
"""
앱 아이콘 생성기.

소스 PNG 1장으로부터 안드로이드 launcher 아이콘 풀세트 생성:
  - mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png        (legacy / round mask)
  - mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png  (모든 launcher 폴백)
  - mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png (adaptive icon foreground)
  - mipmap-anydpi-v26/ic_launcher.xml         (adaptive)
  - mipmap-anydpi-v26/ic_launcher_round.xml
  - values/ic_launcher_background.xml         (배경색)

Adaptive icon foreground는 108dp 컨테이너 안 66dp 안전영역 안에 들어가도록 스케일.
"""

import os
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "icon" / "KakaoTalk_20260508_164014715.png"
RES = ROOT / "app" / "src" / "main" / "res"

LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

ADAPTIVE_TOTAL = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}
SAFE_RATIO = 66 / 108  # 안전영역 비율

BACKGROUND_COLOR = "#B8E5DA"


def main() -> int:
    if not SRC.exists():
        print(f"Source not found: {SRC}", file=sys.stderr)
        return 1

    src = Image.open(SRC).convert("RGBA")
    print(f"Source: {SRC.name}, size={src.size}")

    # 1) Legacy launcher 아이콘 (square)
    for density, size in LEGACY_SIZES.items():
        out_dir = RES / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        img = src.resize((size, size), Image.LANCZOS)
        img.save(out_dir / "ic_launcher.png", "PNG", optimize=True)
        # 일부 launcher는 ic_launcher_round.png를 별도 요청 — adaptive XML이 우선이지만 폴백용
        img.save(out_dir / "ic_launcher_round.png", "PNG", optimize=True)
        print(f"  legacy {density}: {size}px")

    # 2) Adaptive foreground (108dp 컨테이너의 66dp 안전영역에 fit)
    for density, total in ADAPTIVE_TOTAL.items():
        out_dir = RES / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        safe = int(total * SAFE_RATIO)
        scaled = src.resize((safe, safe), Image.LANCZOS)
        canvas = Image.new("RGBA", (total, total), (0, 0, 0, 0))
        offset = (total - safe) // 2
        canvas.paste(scaled, (offset, offset), scaled)
        canvas.save(out_dir / "ic_launcher_foreground.png", "PNG", optimize=True)
        print(f"  adaptive fg {density}: {total}px (safe={safe}px)")

    # 3) Adaptive icon XML
    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)

    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""
    (anydpi / "ic_launcher.xml").write_text(adaptive_xml, encoding="utf-8")
    (anydpi / "ic_launcher_round.xml").write_text(adaptive_xml, encoding="utf-8")
    print(f"  adaptive XML: {anydpi}/ic_launcher{{,_round}}.xml")

    # 4) 배경색 리소스
    values_dir = RES / "values"
    values_dir.mkdir(parents=True, exist_ok=True)
    bg_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">{BACKGROUND_COLOR}</color>
</resources>
"""
    (values_dir / "ic_launcher_background.xml").write_text(bg_xml, encoding="utf-8")
    print(f"  background color: {BACKGROUND_COLOR}")

    print("\nDone.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
