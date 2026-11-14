#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 logo.jpg 生成 Android 自适应图标（Adaptive Icon）。

用法：
    pkg install python-pillow -y        # Termux 首次需要装依赖
    python3 gen_icon.py ~/watch/logo.jpg --full

产物：
    app/src/main/res/drawable/ic_launcher_foreground.png   (432x432)
    app/src/main/res/drawable/ic_launcher_monochrome.png   (432x432，单色)
    app/src/main/res/values/ic_launcher_background.xml     (从 logo 提取主色)

自适应图标规范：
    画布 108dp，可视安全区中心 66dp，系统将按厂商蒙版裁切。
    因此内容缩放到约 61% 居中，避免被裁掉边角。
"""

import os
import sys

try:
    from PIL import Image, ImageOps
except ImportError:
    sys.exit("缺少 Pillow，请先执行: pkg install python-pillow -y")

# 108dp 画布 @ xxxhdpi(4x) = 432px
CANVAS = 432
# 安全区 66dp / 108dp ≈ 0.611
SAFE_RATIO = 0.611
CONTENT = int(CANVAS * SAFE_RATIO)

# 是否全出血：图片铺满整个画布，避免自适应图标底色在四周露出成「蓝边」。
# 用法：python3 gen_icon.py logo.jpg --full
FULL_BLEED = "--full" in sys.argv
if FULL_BLEED:
    CONTENT = CANVAS


def dominant_color(img: Image.Image) -> str:
    """提取图片主色：缩到 1x1 取平均，避开纯黑纯白。"""
    small = img.convert("RGB").resize((1, 1), Image.LANCZOS)
    r, g, b = small.getpixel((0, 0))
    # 过暗或过亮时拉回中间调，保证图标不糊在背景上
    if r + g + b < 90:
        r, g, b = max(r, 40), max(g, 40), max(b, 40)
    if r + g + b > 660:
        r, g, b = min(r, 200), min(g, 200), min(b, 200)
    return "#%02X%02X%02X" % (r, g, b)


def build_foreground(src: Image.Image) -> Image.Image:
    """透明画布上居中放置 logo。"""
    logo = src.convert("RGBA")
    # 裁成正方形，避免拉伸变形
    w, h = logo.size
    side = min(w, h)
    left, top = (w - side) // 2, (h - side) // 2
    logo = logo.crop((left, top, left + side, top + side))
    logo = logo.resize((CONTENT, CONTENT), Image.LANCZOS)

    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    offset = (CANVAS - CONTENT) // 2
    canvas.paste(logo, (offset, offset), logo)
    return canvas


def build_monochrome(src: Image.Image) -> Image.Image:
    """Android 13+ 主题图标：白色单色剪影 + alpha 保留。"""
    logo = src.convert("RGBA")
    w, h = logo.size
    side = min(w, h)
    left, top = (w - side) // 2, (h - side) // 2
    logo = logo.crop((left, top, left + side, top + side))
    logo = logo.resize((CONTENT, CONTENT), Image.LANCZOS)

    # 颜色统一白色，透明度沿用原图 alpha
    white = Image.new("RGBA", logo.size, (255, 255, 255, 255))
    white.putalpha(logo.split()[-1])

    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    offset = (CANVAS - CONTENT) // 2
    canvas.paste(white, (offset, offset), white)
    return canvas


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("用法: python3 gen_icon.py <logo.jpg>")

    src_path = sys.argv[1]
    if not os.path.isfile(src_path):
        sys.exit("找不到文件: %s" % src_path)

    src = Image.open(src_path)
    print("源图: %s  %dx%d" % (src_path, src.size[0], src.size[1]))

    res = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "app", "src", "main", "res")
    drawable = os.path.join(res, "drawable")
    values = os.path.join(res, "values")
    os.makedirs(drawable, exist_ok=True)
    os.makedirs(values, exist_ok=True)

    fg_path = os.path.join(drawable, "ic_launcher_foreground.png")
    build_foreground(src).save(fg_path, "PNG")
    print("生成 foreground: %s (%dx%d)" % (fg_path, CANVAS, CANVAS))

    mono_path = os.path.join(drawable, "ic_launcher_monochrome.png")
    build_monochrome(src).save(mono_path, "PNG")
    print("生成 monochrome: %s" % mono_path)

    color = dominant_color(src)
    bg_path = os.path.join(values, "ic_launcher_background.xml")
    with open(bg_path, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write("<resources>\n")
        f.write('    <color name="ic_launcher_background">%s</color>\n' % color)
        f.write("</resources>\n")
    print("生成 background 主色: %s -> %s" % (color, bg_path))

    print("\n完成。重新编译: ~/watch/build.sh")


if __name__ == "__main__":
    main()
