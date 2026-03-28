# -*- coding: utf-8 -*-
"""将圆角白底上的 app logo 转为 1:1 方形、直角、深蓝铺满边缘。"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw


def sample_navy_rgb(rgb: Image.Image) -> tuple[int, int, int]:
    import numpy as np

    arr = np.array(rgb)
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]
    mask = (r < 55) & (g < 70) & (b < 100) & (r + g + b < 200)
    if not np.any(mask):
        return 20, 28, 48
    sub = arr[mask]
    return tuple(int(x) for x in np.median(sub, axis=0))


def rgba_to_rgb_white_bg(im: Image.Image) -> Image.Image:
    if im.mode != "RGBA":
        return im.convert("RGB")
    bg = Image.new("RGB", im.size, (255, 255, 255))
    bg.paste(im, mask=im.split()[3])
    return bg


def flood_outer_to_navy(im: Image.Image, navy: tuple[int, int, int], thresh: int = 35) -> Image.Image:
    """从四角洪水填充：仅与外边白底连通区域变为 navy，内层相框白边不与外连通。"""
    out = im.copy()
    w, h = out.size
    corners = ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1))
    for xy in corners:
        try:
            ImageDraw.floodfill(out, xy=xy, value=navy, thresh=thresh)
        except ValueError:
            pass
    return out


def center_square_crop(im: Image.Image) -> Image.Image:
    w, h = im.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    return im.crop((left, top, left + side, top + side))


def main() -> int:
    root = Path(__file__).resolve().parent
    src = root / "source_logo.png"
    if len(sys.argv) >= 2:
        src = Path(sys.argv[1])
    if not src.is_file():
        print("缺少源图：请将原图另存为", src, "或传入路径参数", file=sys.stderr)
        return 1

    im = Image.open(src)
    im = rgba_to_rgb_white_bg(im)
    navy = sample_navy_rgb(im)
    im = flood_outer_to_navy(im, navy)
    im = center_square_crop(im)
    out512 = root / "logo_square_512.png"
    out1024 = root / "logo_square_1024.png"
    im.resize((512, 512), Image.Resampling.LANCZOS).save(out512, "PNG", optimize=True)
    im.resize((1024, 1024), Image.Resampling.LANCZOS).save(out1024, "PNG", optimize=True)
    print("OK navy=", navy, "->", out512.name, out1024.name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
