#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从“球迹直播”4K 品牌源图生成 Android TV 资源。

源图固定为 tools/assets/QiuJi-Live-app-icon-4K.png，并通过 SHA-256 校验，
避免资源在无意间被替换后仍生成出不可追溯的图标。

输出（Android TV 工程）：
  mipmap-xxxhdpi/ic_launcher_fg.png  432x432 自适应图标前景
  mipmap-xhdpi/ic_launcher_fg.png    216x216 自适应图标前景
  drawable/qiuji_live_logo.png       透明底白色“球迹直播”启动字标
  drawable/ic_banner.png             640x360 TV banner（用户提供的 16:9 品牌图）
"""
from __future__ import annotations

import hashlib
import os
import sys
from functools import lru_cache
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent.parent
SOURCE_ICON = ROOT / "tools" / "assets" / "QiuJi-Live-app-icon-4K.png"
SOURCE_SHA256 = "28910d1d8a76951f984c466a073e03dae50e89e702ce4508e9db2eb414fc0e15"
SOURCE_SIZE = (3840, 2160)
BRAND_NAME = "球迹直播"

RES_ROOTS = (
    ROOT / "android" / "app" / "src" / "main" / "res",
)

WORDMARK = (255, 255, 255, 255)

# 从 4K 横幅左侧裁出足球/播放标记；避开分隔斜线和右侧英文品牌字。
SOURCE_MARK_CROP = (400, 560, 1330, 1490)
ICON_SAFE_RATIO = 0.64

OUTPUT_SIZES = {
    "mipmap-xxxhdpi/ic_launcher_fg.png": (432, 432),
    "mipmap-xhdpi/ic_launcher_fg.png": (216, 216),
    "drawable/qiuji_live_logo.png": None,  # 尺寸由字体排版决定
    "drawable/ic_banner.png": (640, 360),
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


@lru_cache(maxsize=1)
def load_source() -> Image.Image:
    if not SOURCE_ICON.is_file():
        raise FileNotFoundError(f"缺少品牌源图：{SOURCE_ICON}")
    actual = sha256_file(SOURCE_ICON)
    if actual != SOURCE_SHA256:
        raise RuntimeError(
            "品牌源图校验失败：\n"
            f"  路径：{SOURCE_ICON}\n"
            f"  期望：{SOURCE_SHA256}\n"
            f"  实际：{actual}"
        )
    image = Image.open(SOURCE_ICON).convert("RGBA")
    if image.size != SOURCE_SIZE:
        raise ValueError(
            f"品牌源图必须为 {SOURCE_SIZE[0]}x{SOURCE_SIZE[1]}，"
            f"实际为 {image.size[0]}x{image.size[1]}"
        )
    return image


@lru_cache(maxsize=8)
def load_cjk_bold_font(size: int) -> ImageFont.FreeTypeFont:
    windir = Path(os.environ.get("WINDIR", r"C:\Windows"))
    candidates = (
        windir / "Fonts" / "msyhbd.ttc",
        windir / "Fonts" / "msyh.ttc",
        windir / "Fonts" / "simhei.ttf",
        Path("/System/Library/Fonts/PingFang.ttc"),
        Path("/System/Library/Fonts/STHeiti Medium.ttc"),
        Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"),
        Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
        Path("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),
    )
    for font_path in candidates:
        if font_path.is_file():
            try:
                return ImageFont.truetype(str(font_path), size=size, index=0)
            except OSError:
                continue
    raise FileNotFoundError(
        "未找到可用的中文字体。请安装微软雅黑、苹方或 Noto Sans CJK 后重试。"
    )


def make_white_wordmark(font_size: int = 128) -> Image.Image:
    """生成透明底、纯白色的“球迹直播”字标。"""
    font = load_cjk_bold_font(font_size)
    probe = ImageDraw.Draw(Image.new("RGBA", (2, 2)))
    bbox = probe.textbbox((0, 0), BRAND_NAME, font=font)
    pad_x = round(font_size * 0.12)
    pad_y = round(font_size * 0.14)
    width = bbox[2] - bbox[0] + pad_x * 2
    height = bbox[3] - bbox[1] + pad_y * 2
    canvas = Image.new("RGBA", (width, height), (255, 255, 255, 0))
    ImageDraw.Draw(canvas).text(
        (pad_x - bbox[0], pad_y - bbox[1]),
        BRAND_NAME,
        font=font,
        fill=WORDMARK,
    )
    return canvas


def make_icon_mark(size: int) -> Image.Image:
    """裁出横幅左侧足球/播放标记，保持原图的黑色背景与红白配色。"""
    crop = load_source().crop(SOURCE_MARK_CROP).convert("RGBA")
    if crop.width != crop.height:
        raise AssertionError(f"图标裁切区必须为正方形，实际为 {crop.size}")
    return crop.resize((size, size), Image.Resampling.LANCZOS)


def make_icon_foreground(size: int) -> Image.Image:
    """生成自适应图标前景；标记整体保持在自适应图标安全区。"""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    badge_size = round(size * ICON_SAFE_RATIO)
    badge = make_icon_mark(badge_size)
    offset = (size - badge_size) // 2
    canvas.alpha_composite(badge, (offset, offset))
    return canvas


def make_banner(width: int = 640, height: int = 360) -> Image.Image:
    """使用用户提供的 16:9 4K 品牌图作为 TV 桌面横幅。"""
    return load_source().convert("RGB").resize(
        (width, height),
        Image.Resampling.LANCZOS,
    ).convert("RGBA")


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def open_png(path: Path) -> Image.Image:
    image = Image.open(path)
    image.load()
    return image


def validate_outputs() -> None:
    for path in (SOURCE_ICON,):
        if not path.is_file():
            raise AssertionError(f"缺少文件：{path}")

    relative_files = tuple(OUTPUT_SIZES)
    expected_hashes: dict[str, str] = {}
    for relative in relative_files:
        project_hashes = []
        for res_root in RES_ROOTS:
            path = res_root / relative
            if not path.is_file():
                raise AssertionError(f"缺少生成文件：{path}")
            image = open_png(path)
            expected_size = OUTPUT_SIZES[relative]
            if expected_size is not None and image.size != expected_size:
                raise AssertionError(
                    f"尺寸错误：{path} 期望 {expected_size}，实际 {image.size}"
                )
            if image.mode != "RGBA":
                raise AssertionError(f"PNG 必须为 RGBA：{path} 实际 {image.mode}")
            project_hashes.append(sha256_file(path))

        expected_hashes[relative] = project_hashes[0]

    for res_root in RES_ROOTS:
        for relative in (
            "mipmap-xxxhdpi/ic_launcher_fg.png",
            "mipmap-xhdpi/ic_launcher_fg.png",
        ):
            image = open_png(res_root / relative)
            bbox = image.getchannel("A").getbbox()
            if bbox is None:
                raise AssertionError(f"图标前景完全透明：{res_root / relative}")
            left, top, right, bottom = bbox
            width, height = image.size
            if not (
                left >= round(width * 0.16)
                and top >= round(height * 0.16)
                and right <= round(width * 0.84)
                and bottom <= round(height * 0.84)
            ):
                raise AssertionError(
                    f"图标主体超出自适应安全区：{res_root / relative} bbox={bbox}"
                )

        wordmark = open_png(res_root / "drawable" / "qiuji_live_logo.png")
        alpha = wordmark.getchannel("A")
        alpha_min, alpha_max = alpha.getextrema()
        if alpha_min != 0 or alpha_max != 255:
            raise AssertionError("启动字标必须同时包含透明像素和不透明像素")
        rgba = wordmark.tobytes()
        solid_pixels = 0
        for offset in range(0, len(rgba), 4):
            if rgba[offset + 3] == 0:
                continue
            solid_pixels += 1
            if rgba[offset : offset + 3] != b"\xff\xff\xff":
                raise AssertionError("启动字标必须为透明底白色字")
        if solid_pixels == 0:
            raise AssertionError("启动字标必须包含可见文字")

    print(f"源图 SHA-256: {SOURCE_SHA256}")
    print(f"品牌名: {BRAND_NAME}")
    print("生成文件尺寸与哈希验证通过：")
    for relative, digest in expected_hashes.items():
        size = OUTPUT_SIZES[relative]
        size_text = f"{size[0]}x{size[1]}" if size else "透明字标"
        print(f"  {relative} | {size_text} | sha256={digest}")


def generate() -> None:
    source = load_source()
    if source.size != SOURCE_SIZE:
        raise AssertionError(
            f"品牌源图尺寸必须为 {SOURCE_SIZE[0]}x{SOURCE_SIZE[1]}"
        )

    icon_432 = make_icon_foreground(432)
    icon_216 = icon_432.resize((216, 216), Image.Resampling.LANCZOS)
    wordmark = make_white_wordmark()
    banner = make_banner()

    for res_root in RES_ROOTS:
        save_png(icon_432, res_root / "mipmap-xxxhdpi" / "ic_launcher_fg.png")
        save_png(icon_216, res_root / "mipmap-xhdpi" / "ic_launcher_fg.png")
        save_png(wordmark, res_root / "drawable" / "qiuji_live_logo.png")
        save_png(banner, res_root / "drawable" / "ic_banner.png")

    validate_outputs()


if __name__ == "__main__":
    try:
        generate()
    except Exception as exc:
        print(f"图标生成失败：{exc}", file=sys.stderr)
        raise
