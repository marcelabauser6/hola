#!/usr/bin/env python3
"""
Renders a block model the way Minecraft's inventory does, so a model can be checked without
launching the game.

There is no client here, and "does the ATM look like an ATM" is not a question a static check can
answer - so this rasterises the model itself. It reproduces the pieces of vanilla's item rendering
that decide the silhouette: the `block/block` GUI transform (30 degrees about X, 225 about Y), an
orthographic camera, nearest-neighbour texture sampling, and vanilla's per-face shading constants.
It is not a pixel-exact copy of the game's renderer, but it is close enough to judge shape, contrast
and whether a texture landed on the face it was meant to.

Usage:
    python3 tools/render_block_model.py atm            # -> tools/artifacts/atm_preview.png
    python3 tools/render_block_model.py atm --size 512
"""
import argparse
import json
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir)
ASSETS = os.path.join(ROOT, "src/main/resources/assets/athens_coins")
ARTIFACTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "artifacts")

# Vanilla's directional shading. Faces are not lit, they are multiplied by a constant per direction,
# which is why a flat-coloured cube still reads as three-dimensional.
SHADE = {"up": 1.00, "down": 0.50, "north": 0.80, "south": 0.80, "east": 0.60, "west": 0.60}

# Face -> (corner order in block space, axis) for a box from `f` to `t`.
def face_quad(f, t, face):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face == "north":     # -Z, seen from the front
        return [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)]
    if face == "south":     # +Z
        return [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)]
    if face == "west":      # -X
        return [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)]
    if face == "east":      # +X
        return [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)]
    if face == "up":        # +Y
        return [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
    if face == "down":      # -Y
        return [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]
    raise ValueError(face)


def rotate(p, rx_deg, ry_deg):
    """Rotates about Y then X, matching the order vanilla builds its display quaternion in."""
    x, y, z = p[0] - 8.0, p[1] - 8.0, p[2] - 8.0
    ry = math.radians(ry_deg)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    rx = math.radians(rx_deg)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return x, y, z


def resolve(textures, ref):
    """Follows #references through the model's texture map to a resource path."""
    seen = 0
    while ref.startswith("#") and seen < 8:
        ref = textures.get(ref[1:], ref)
        seen += 1
    return ref


def load_texture(ref, cache):
    if ref in cache:
        return cache[ref]
    path = ref.split(":", 1)[-1]
    full = os.path.join(ASSETS, "textures", path + ".png")
    img = Image.open(full).convert("RGBA") if os.path.isfile(full) else None
    cache[ref] = img
    return img


def render(model_name, size, scale_factor):
    with open(os.path.join(ASSETS, "models/block", model_name + ".json"), encoding="utf-8") as fh:
        model = json.load(fh)
    textures = model.get("textures", {})
    cache = {}

    # Collect every face as a screen-space quad with its depth, then paint back to front.
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = out.load()
    zbuf = [[-1e9] * size for _ in range(size)]

    # Block units -> pixels. 16 units fills `size` before the GUI scale is applied.
    unit = size / 16.0 * scale_factor
    cx = cy = size / 2.0

    quads = []
    for element in model.get("elements", []):
        f = element["from"]
        t = element["to"]
        for face, spec in element.get("faces", {}).items():
            tex = load_texture(resolve(textures, spec.get("texture", "")), cache)
            if tex is None:
                continue
            uv = spec.get("uv", [0, 0, 16, 16])
            corners = [rotate(p, 30, 225) for p in face_quad(f, t, face)]
            depth = sum(c[2] for c in corners) / 4.0
            quads.append((depth, corners, tex, uv, SHADE.get(face, 0.8)))

    # Painter's order plus a depth test: the depth test is what stops the tray from being swallowed
    # by the cabinet face it protrudes from.
    quads.sort(key=lambda q: q[0])
    for depth, corners, tex, uv, shade in quads:
        screen = [(cx + c[0] * unit, cy - c[1] * unit) for c in corners]
        u0, v0, u1, v1 = uv
        tw, th = tex.size
        tpx = tex.load()
        xs = [s[0] for s in screen]
        ys = [s[1] for s in screen]
        x_min, x_max = int(math.floor(min(xs))), int(math.ceil(max(xs)))
        y_min, y_max = int(math.floor(min(ys))), int(math.ceil(max(ys)))
        # Bilinear inverse map over the quad, sampled per pixel.
        for sy in range(max(0, y_min), min(size, y_max + 1)):
            for sx in range(max(0, x_min), min(size, x_max + 1)):
                st = quad_uv(screen, sx + 0.5, sy + 0.5)
                if st is None:
                    continue
                s, tt = st
                z = (corners[0][2] * (1 - s) * (1 - tt) + corners[1][2] * s * (1 - tt)
                     + corners[2][2] * s * tt + corners[3][2] * (1 - s) * tt)
                if z < zbuf[sy][sx]:
                    continue
                tu = u0 + (u1 - u0) * s
                tv = v0 + (v1 - v0) * tt
                ix = min(tw - 1, max(0, int(tu / 16.0 * tw)))
                iy = min(th - 1, max(0, int(tv / 16.0 * th)))
                r, g, b, a = tpx[ix, iy]
                if a < 16:
                    continue
                zbuf[sy][sx] = z
                px[sx, sy] = (int(r * shade), int(g * shade), int(b * shade), 255)
    return out


def quad_uv(quad, x, y):
    """Inverse-maps a point into a quad's (s,t), or None when outside. Two triangles, barycentric."""
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = quad
    for (ax, ay, bx, by, cx_, cy_, sa, ta, sb, tb, sc, tc) in (
            (x0, y0, x1, y1, x2, y2, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0),
            (x0, y0, x2, y2, x3, y3, 0.0, 0.0, 1.0, 1.0, 0.0, 1.0)):
        den = (by - cy_) * (ax - cx_) + (cx_ - bx) * (ay - cy_)
        if abs(den) < 1e-9:
            continue
        wa = ((by - cy_) * (x - cx_) + (cx_ - bx) * (y - cy_)) / den
        wb = ((cy_ - ay) * (x - cx_) + (ax - cx_) * (y - cy_)) / den
        wc = 1.0 - wa - wb
        if wa < -1e-6 or wb < -1e-6 or wc < -1e-6:
            continue
        return wa * sa + wb * sb + wc * sc, wa * ta + wb * tb + wc * tc
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model")
    parser.add_argument("--size", type=int, default=384)
    # 0.625 is the GUI scale from block/block; 1.0 renders the block filling the frame.
    parser.add_argument("--scale", type=float, default=0.625)
    parser.add_argument("--out", default=None)
    args = parser.parse_args()

    os.makedirs(ARTIFACTS, exist_ok=True)
    img = render(args.model, args.size, args.scale)
    # Checkerboard behind it, so transparent gaps in the silhouette are obvious.
    bg = Image.new("RGBA", img.size, (32, 32, 38, 255))
    tile = 16
    bgp = bg.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            if ((x // tile) + (y // tile)) % 2 == 0:
                bgp[x, y] = (44, 44, 52, 255)
    bg.alpha_composite(img)
    out = args.out or os.path.join(ARTIFACTS, args.model + "_preview.png")
    bg.save(out)
    print("wrote", os.path.relpath(out, ROOT))


if __name__ == "__main__":
    main()
