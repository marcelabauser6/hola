#!/usr/bin/env python3
"""Generate deterministic, mipmap-safe Minecraft coin sprites from the 1024px masters.

The originals contain useful coin colour below partially transparent edge pixels, but also a large
RGB backdrop below alpha=0. Resizing straight RGBA channels can mix that backdrop into the rim.
This generator instead performs an exact integer area reduction in premultiplied-alpha space,
then uses a 50% coverage threshold. The result has:

* no negative-lobe filter (no LANCZOS/BICUBIC ringing or overshoot);
* binary alpha and black RGB below alpha=0 (no fringe or hidden matte);
* every visible RGB channel inside the contributing source block's range; and
* a 64px final size, retaining the TF lettering while reducing atlas/mipmap shimmer.

Use --comparison to rebuild tools/artifacts/coin_texture_comparison.png with real 16/32/64/128
alternatives. Nothing except the selected 64px result is written into the resource pack.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ORIGINALS = ROOT / "originals" / "coin-textures-1024"
TARGET = ROOT / "src" / "main" / "resources" / "assets" / "athens_coins" / "textures" / "item"
ARTIFACT = ROOT / "tools" / "artifacts" / "coin_texture_comparison.png"

COINS = ("bronze", "silver", "gold")
SOURCE_SIZE = 1024
FINAL_SIZE = 64
ALTERNATIVE_SIZES = (16, 32, 64, 128)
ALPHA_COVERAGE = 0.5
HIGHLIGHT_LUMA = 220
HIGHLIGHT_GAP = 24


@dataclass(frozen=True)
class Metrics:
    size: int
    visible: int
    colors: int
    partial_alpha: int
    hidden_rgb: int
    isolated_highlights: int
    local_palette_violations: int
    edge_halos: int


def source_path(coin: str) -> Path:
    return ORIGINALS / f"{coin}_coin.png"


def target_path(coin: str) -> Path:
    return TARGET / f"{coin}_coin.png"


def load_source(coin: str) -> Image.Image:
    path = source_path(coin)
    with Image.open(path) as opened:
        if opened.size != (SOURCE_SIZE, SOURCE_SIZE):
            raise ValueError(f"{path}: expected {SOURCE_SIZE}x{SOURCE_SIZE}, got {opened.size}")
        return opened.convert("RGBA")


def area_reduce(source: Image.Image, size: int) -> Image.Image:
    """Reduce by exact source blocks using alpha-weighted colour and binary coverage.

    All supported sizes divide 1024 exactly. This is deliberately implemented without a Pillow
    resampling filter: each destination pixel only sees its own source block, so it cannot ring or
    borrow a colour from an adjacent region.
    """
    if source.size != (SOURCE_SIZE, SOURCE_SIZE):
        raise ValueError(f"expected a {SOURCE_SIZE}x{SOURCE_SIZE} source, got {source.size}")
    if size not in ALTERNATIVE_SIZES:
        raise ValueError(f"size must be one of {ALTERNATIVE_SIZES}, got {size}")

    factor = SOURCE_SIZE // size
    block_area = factor * factor
    threshold = round(255 * block_area * ALPHA_COVERAGE)
    src = source.load()
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dst = result.load()

    for out_y in range(size):
        y0 = out_y * factor
        for out_x in range(size):
            x0 = out_x * factor
            alpha_sum = red_sum = green_sum = blue_sum = 0
            for y in range(y0, y0 + factor):
                for x in range(x0, x0 + factor):
                    red, green, blue, alpha = src[x, y]
                    alpha_sum += alpha
                    red_sum += red * alpha
                    green_sum += green * alpha
                    blue_sum += blue * alpha
            if alpha_sum >= threshold:
                # Python's round is deterministic; clamping only guards against malformed input.
                dst[out_x, out_y] = (
                    min(255, max(0, round(red_sum / alpha_sum))),
                    min(255, max(0, round(green_sum / alpha_sum))),
                    min(255, max(0, round(blue_sum / alpha_sum))),
                    255,
                )
    return result


def _luminance(pixel: tuple[int, int, int, int]) -> int:
    return (54 * pixel[0] + 183 * pixel[1] + 19 * pixel[2]) // 256


def _is_edge(pixels, x: int, y: int, width: int, height: int) -> bool:
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        nx, ny = x + dx, y + dy
        if nx < 0 or ny < 0 or nx >= width or ny >= height or pixels[nx, ny][3] == 0:
            return True
    return False


def measure(image: Image.Image, source: Image.Image | None = None) -> Metrics:
    """Measure artifact classes used by the generator and static verifier.

    A highlight is only called isolated when it is brighter than *every* visible neighbour, not
    merely brighter than their median. This avoids rejecting intentional connected shine bands.
    A halo is an edge pixel whose colour falls outside its alpha-contributing source block.
    """
    rgba = image.convert("RGBA")
    width, height = rgba.size
    pixels = rgba.load()
    visible_colors: set[tuple[int, int, int]] = set()
    visible = partial = hidden = isolated = local_violations = edge_halos = 0

    source_pixels = source.load() if source is not None else None
    factor = SOURCE_SIZE // width if source is not None and width == height and SOURCE_SIZE % width == 0 else None

    for y in range(height):
        for x in range(width):
            pixel = pixels[x, y]
            alpha = pixel[3]
            if alpha == 0:
                if pixel[:3] != (0, 0, 0):
                    hidden += 1
                continue
            visible += 1
            visible_colors.add(pixel[:3])
            if alpha != 255:
                partial += 1

            neighbours = []
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    if (dx or dy) and 0 <= x + dx < width and 0 <= y + dy < height:
                        neighbour = pixels[x + dx, y + dy]
                        if neighbour[3] != 0:
                            neighbours.append(_luminance(neighbour))
            luminance = _luminance(pixel)
            if (len(neighbours) >= 3 and luminance >= HIGHLIGHT_LUMA
                    and luminance >= max(neighbours) + HIGHLIGHT_GAP):
                isolated += 1

            if source_pixels is not None and factor is not None:
                mins = [255, 255, 255]
                maxs = [0, 0, 0]
                contributors = 0
                for sy in range(y * factor, (y + 1) * factor):
                    for sx in range(x * factor, (x + 1) * factor):
                        source_pixel = source_pixels[sx, sy]
                        if source_pixel[3] == 0:
                            continue
                        contributors += 1
                        for channel in range(3):
                            mins[channel] = min(mins[channel], source_pixel[channel])
                            maxs[channel] = max(maxs[channel], source_pixel[channel])
                violation = contributors == 0 or any(
                    pixel[channel] < mins[channel] or pixel[channel] > maxs[channel]
                    for channel in range(3)
                )
                if violation:
                    local_violations += 1
                    if _is_edge(pixels, x, y, width, height):
                        edge_halos += 1

    return Metrics(
        size=width,
        visible=visible,
        colors=len(visible_colors),
        partial_alpha=partial,
        hidden_rgb=hidden,
        isolated_highlights=isolated,
        local_palette_violations=local_violations,
        edge_halos=edge_halos,
    )


def validation_errors(coin: str, image: Image.Image, source: Image.Image) -> list[str]:
    expected = area_reduce(source, FINAL_SIZE)
    metrics = measure(image, source)
    errors = []
    if image.size != (FINAL_SIZE, FINAL_SIZE):
        errors.append(f"expected {FINAL_SIZE}x{FINAL_SIZE}, got {image.size[0]}x{image.size[1]}")
    if image.mode != "RGBA":
        errors.append(f"expected RGBA, got {image.mode}")
    if image.size == expected.size and image.convert("RGBA").tobytes() != expected.tobytes():
        errors.append("pixels differ from deterministic premultiplied-area generation")
    if metrics.partial_alpha:
        errors.append(f"{metrics.partial_alpha} alpha-fringe pixels")
    if metrics.hidden_rgb:
        errors.append(f"{metrics.hidden_rgb} transparent pixels carry hidden RGB")
    if metrics.isolated_highlights:
        errors.append(f"{metrics.isolated_highlights} isolated high-luminance pixels")
    if metrics.local_palette_violations:
        errors.append(f"{metrics.local_palette_violations} pixels outside their local source palette")
    if metrics.edge_halos:
        errors.append(f"{metrics.edge_halos} edge halo pixels")
    return [f"{coin}_coin.png: {error}" for error in errors]


def _checkerboard(width: int, height: int, cell: int = 8) -> Image.Image:
    image = Image.new("RGB", (width, height), (56, 58, 62))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            if (x // cell + y // cell) % 2:
                pixels[x, y] = (76, 78, 82)
    return image


def _preview(image: Image.Image, size: int) -> Image.Image:
    background = _checkerboard(size, size)
    scaled = image.resize((size, size), Image.Resampling.NEAREST)
    background.paste(scaled, mask=scaled.getchannel("A"))
    return background


def create_comparison(current: dict[str, Image.Image], candidates: dict[str, dict[int, Image.Image]]) -> None:
    tile = 160
    label_height = 54
    margin = 16
    columns = (("Current BOX", 128),) + tuple((f"Clean {size}", size) for size in ALTERNATIVE_SIZES)
    width = margin * 2 + tile * len(columns)
    height = margin * 2 + label_height + len(COINS) * (tile + label_height)
    sheet = Image.new("RGB", (width, height), (31, 33, 37))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    draw.text((margin, 9), "Fantastic Coins - current vs premultiplied area + binary alpha", fill=(240, 240, 240), font=font)
    draw.text((margin, 25), "Final selection: Clean 64 (no ringing, fringe, hidden RGB, local overshoot)", fill=(174, 213, 255), font=font)

    for column, (title, size) in enumerate(columns):
        x = margin + column * tile
        draw.text((x + 5, label_height), f"{title} ({size}px)", fill=(230, 230, 230), font=font)

    for row, coin in enumerate(COINS):
        y = margin + label_height + row * (tile + label_height)
        images: Iterable[Image.Image] = (current[coin],) + tuple(candidates[coin][size] for size in ALTERNATIVE_SIZES)
        source = load_source(coin)
        for column, image in enumerate(images):
            x = margin + column * tile
            sheet.paste(_preview(image, tile), (x, y))
            metrics = measure(image, source)
            line1 = f"{coin}: vis {metrics.visible}, colors {metrics.colors}"
            line2 = (f"partial {metrics.partial_alpha} hidden {metrics.hidden_rgb} "
                     f"hi {metrics.isolated_highlights}")
            line3 = f"local {metrics.local_palette_violations} halo {metrics.edge_halos}"
            draw.text((x + 3, y + tile + 5), line1, fill=(228, 228, 228), font=font)
            draw.text((x + 3, y + tile + 19), line2, fill=(180, 183, 188), font=font)
            draw.text((x + 3, y + tile + 33), line3, fill=(180, 183, 188), font=font)
    ARTIFACT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(ARTIFACT, optimize=True)


def print_metrics(label: str, coin: str, metrics: Metrics) -> None:
    print(
        f"  {label:<11} {coin:<6} {metrics.size:>3}px | visible {metrics.visible:>4} | "
        f"colors {metrics.colors:>4} | partial {metrics.partial_alpha:>3} | "
        f"hidden {metrics.hidden_rgb:>3} | isolated-hi {metrics.isolated_highlights:>2} | "
        f"local/halo {metrics.local_palette_violations}/{metrics.edge_halos}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--comparison", action="store_true", help="write the static 16/32/64/128 comparison sheet")
    parser.add_argument("--verify-only", action="store_true", help="validate existing resource-pack sprites without writing them")
    args = parser.parse_args()

    TARGET.mkdir(parents=True, exist_ok=True)
    baseline: dict[str, Image.Image] = {}
    candidates: dict[str, dict[int, Image.Image]] = {}
    errors: list[str] = []

    for coin in COINS:
        source = load_source(coin)
        # Reproduce the former shipped texture in memory so reports and the comparison artifact
        # retain a stable before/after baseline even after the final assets have been regenerated.
        baseline[coin] = source.resize((128, 128), Image.Resampling.BOX)
        print_metrics("legacy-box", coin, measure(baseline[coin], source))
        existing_path = target_path(coin)
        if not existing_path.exists() and args.verify_only:
            errors.append(f"{coin}_coin.png: missing")
            continue

        candidates[coin] = {size: area_reduce(source, size) for size in ALTERNATIVE_SIZES}
        for size in ALTERNATIVE_SIZES:
            print_metrics(f"alt-{size}", coin, measure(candidates[coin][size], source))

        if not args.verify_only:
            candidates[coin][FINAL_SIZE].save(existing_path, optimize=True)
        with Image.open(existing_path) as opened:
            shipped = opened.copy()
        print_metrics("final", coin, measure(shipped, source))
        errors.extend(validation_errors(coin, shipped, source))

    if args.comparison and not errors:
        create_comparison(baseline, candidates)
        print(f"  comparison: {ARTIFACT.relative_to(ROOT)}")

    if errors:
        print("Coin texture verification failed:")
        for error in errors:
            print(f"  - {error}")
        return 1
    print(f"Coin textures verified: {len(COINS)} deterministic {FINAL_SIZE}x{FINAL_SIZE} RGBA sprites")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
