"""Builds Likka's launcher icon and notification icon from `sprites/likka.png` (design system §1.5).

    python likka_sprites/tools/icons.py preview            # writes likka_sprites/review/icon_preview.png
    python likka_sprites/tools/icons.py apply [VARIANT]    # writes the Android resources (default: fit, the one in the app)

Nothing is drawn by hand: the face is a 48x48 px crop of frame 0 of `idle` (head and antlers only),
enlarged by an integer factor with nearest neighbour, over a flat cocoa background. The notification
icon is the antlers' silhouette traced from the same frame as a 24dp monochrome vector.

Variants (the scale is per density; xxhdpi and xxxhdpi keep the 3:4 ratio of their pixel sizes):
  spec  4x xxhdpi / 5x xxxhdpi, as written in §1.5.
  fit   3x xxhdpi / 4x xxxhdpi, so the opaque face stays inside the 66dp safe circle (see `report`).
"""

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / "sprites" / "likka.png"
SHEET_JSON = ROOT / "sprites" / "likka.json"
RES = ROOT / "app" / "src" / "main" / "res"
REVIEW = ROOT / "likka_sprites" / "review"

COCOA = (0x3D, 0x1B, 0x1C)  # brand.cocoa
FRAME = 96
# Head and antlers of frame 0 of `idle`: a 48x48 crop whose opaque part is the closest to a circle
# (the position that minimises its radius), with everything from HEAD_BOTTOM_ROW down cleared: that
# is the cloak, wing and scarf, not the face.
FACE_BOX = (18, 7, 66, 55)
HEAD_BOTTOM_ROW = 48
# The antlers end where the hood starts, 23 rows below the top of the sprite.
ANTLER_ROWS = (7, 31)
LAYER_DP = 108
SAFE_DP = 66
DENSITY_PX_PER_DP = {"xxhdpi": 3, "xxxhdpi": 4}
VARIANTS = {
    "spec": {"xxhdpi": 4, "xxxhdpi": 5},
    "fit": {"xxhdpi": 3, "xxxhdpi": 4},
}


def idle_frame0() -> Image.Image:
    sheet = Image.open(SHEET).convert("RGBA")
    frames = json.loads(SHEET_JSON.read_text())["frames"]
    box = frames[0]["frame"]
    return sheet.crop((box["x"], box["y"], box["x"] + box["w"], box["y"] + box["h"]))


def face(frame: Image.Image) -> Image.Image:
    cropped = frame.crop(FACE_BOX)
    cleared = np.array(cropped)
    cleared[HEAD_BOTTOM_ROW - FACE_BOX[1] :] = 0
    return Image.fromarray(cleared, "RGBA")


def foreground_layer(face_img: Image.Image, scale: int, density: str) -> Image.Image:
    size = LAYER_DP * DENSITY_PX_PER_DP[density]
    big = face_img.resize((face_img.width * scale, face_img.height * scale), Image.NEAREST)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer.alpha_composite(big, ((size - big.width) // 2, (size - big.height) // 2))
    return layer


def composed_icon(layer: Image.Image, density: str) -> Image.Image:
    base = Image.new("RGBA", layer.size, COCOA + (255,))
    base.alpha_composite(layer)
    return base


def mask_shape(kind: str, px: int) -> Image.Image:
    """The 72dp visible window of an adaptive icon, as a mask of px x px."""
    mask = Image.new("L", (px * 4, px * 4), 0)
    d = ImageDraw.Draw(mask)
    big = px * 4
    if kind == "circle":
        d.ellipse((0, 0, big - 1, big - 1), fill=255)
    elif kind == "square":
        d.rectangle((0, 0, big - 1, big - 1), fill=255)
    elif kind == "squircle":
        d.rounded_rectangle((0, 0, big - 1, big - 1), radius=big * 0.32, fill=255)
    elif kind == "teardrop":
        d.ellipse((0, 0, big - 1, big - 1), fill=255)
        d.rectangle((big // 2, big // 2, big - 1, big - 1), fill=255)  # one square corner
    return mask.resize((px, px), Image.LANCZOS)


def launcher_render(layer: Image.Image, density: str, kind: str, icon_dp: int = 48) -> Image.Image:
    """The icon as a launcher draws it: the 72dp window of the 108dp layers, shown at icon_dp."""
    scale = DENSITY_PX_PER_DP[density]
    window = 72 * scale
    start = (layer.width - window) // 2
    cropped = composed_icon(layer, density).crop((start, start, start + window, start + window))
    shown = icon_dp * scale
    cropped = cropped.resize((shown, shown), Image.LANCZOS)
    out = Image.new("RGBA", (shown, shown), (0, 0, 0, 0))
    out.paste(cropped, (0, 0), mask_shape(kind, shown))
    return out


def report(face_img: Image.Image) -> list[str]:
    alpha = np.array(face_img)[:, :, 3]
    ys, xs = np.nonzero(alpha)
    cx = cy = face_img.width / 2
    radius = float(np.max(np.hypot(xs + 0.5 - cx, ys + 0.5 - cy)))
    lines = [f"face opaque radius from the crop centre: {radius:.1f} px"]
    for name, scales in VARIANTS.items():
        for density, scale in scales.items():
            safe_px = SAFE_DP / 2 * DENSITY_PX_PER_DP[density]
            lines.append(
                f"  {name:5s} {density:8s} {scale}x -> {radius * scale:6.1f} px vs safe circle {safe_px:.0f} px "
                f"({'inside' if radius * scale <= safe_px else 'OUTSIDE'})"
            )
    return lines


def relative_luminance(rgb) -> float:
    def lin(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = rgb
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def contrast(a, b) -> float:
    la, lb = sorted((relative_luminance(a), relative_luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def colour_contrasts(face_img: Image.Image) -> list[str]:
    px = np.array(face_img).reshape(-1, 4)
    px = px[px[:, 3] > 0][:, :3]
    colours, counts = np.unique(px, axis=0, return_counts=True)
    order = np.argsort(-counts)
    lines = ["main face colours against cocoa (share of opaque pixels, contrast ratio):"]
    for i in order[:8]:
        c = tuple(int(v) for v in colours[i])
        lines.append(f"  #{c[0]:02X}{c[1]:02X}{c[2]:02X} {counts[i] / len(px):5.1%}  {contrast(c, COCOA):.2f}:1")
    return lines


def write_preview() -> Path:
    frame = idle_frame0()
    face_img = face(frame)
    kinds = ["circle", "teardrop", "squircle", "square"]
    rows = list(VARIANTS)
    zoom = 3
    cell = 144 * zoom + 24
    sheet = Image.new("RGBA", (cell * len(kinds) + 24 + 160, (cell + 160) * len(rows) + 24), (200, 200, 200, 255))
    d = ImageDraw.Draw(sheet)
    for r, name in enumerate(rows):
        layer = foreground_layer(face_img, VARIANTS[name]["xxhdpi"], "xxhdpi")
        y0 = 24 + r * (cell + 160)
        d.text((8, y0), name, fill=(0, 0, 0, 255))
        for c, kind in enumerate(kinds):
            icon = launcher_render(layer, "xxhdpi", kind)
            x0 = 24 + 160 + c * cell
            sheet.alpha_composite(icon.resize((icon.width * zoom, icon.height * zoom), Image.NEAREST), (x0, y0))
            # real size (1x, as the phone draws it) under the enlarged one
            sheet.alpha_composite(icon, (x0, y0 + 144 * zoom + 8))
            d.text((x0 + 160, y0 + 144 * zoom + 8), f"{name} / {kind}", fill=(0, 0, 0, 255))
    out = REVIEW / "icon_preview.png"
    sheet.convert("RGB").save(out)
    return out


def trace_antlers(frame: Image.Image) -> tuple[str, int, int]:
    """Path data (one rectangle per horizontal run) of the antlers' silhouette, and its viewport."""
    alpha = np.array(frame)[:, :, 3] > 0
    top, bottom = ANTLER_ROWS
    ys, xs = np.nonzero(alpha[top : bottom + 1])
    x0, x1 = xs.min(), xs.max() + 1
    width = x1 - x0
    side = max(width, bottom + 1 - top)
    y_offset = (side - (bottom + 1 - top)) // 2
    parts = []
    for row in range(top, bottom + 1):
        x = x0
        while x < x1:
            if alpha[row, x]:
                start = x
                while x < x1 and alpha[row, x]:
                    x += 1
                parts.append(f"M{start - x0},{row - top + y_offset}h{x - start}v1h-{x - start}z")
            else:
                x += 1
    return "".join(parts), side, side


def antlers_vector(frame: Image.Image) -> str:
    path, w, h = trace_antlers(frame)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by likka_sprites/tools/icons.py from frame 0 of `idle`: do not edit by hand. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        f'    android:viewportWidth="{w}"\n'
        f'    android:viewportHeight="{h}">\n'
        '    <path\n'
        '        android:fillColor="#FFFFFFFF"\n'
        f'        android:pathData="{path}" />\n'
        "</vector>\n"
    )


ADAPTIVE_XML = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@color/ic_launcher_background" />\n'
    '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
    "</adaptive-icon>\n"
)


def apply(variant: str) -> None:
    frame = idle_frame0()
    face_img = face(frame)
    for density, scale in VARIANTS[variant].items():
        target = RES / f"mipmap-{density}"
        target.mkdir(parents=True, exist_ok=True)
        foreground_layer(face_img, scale, density).save(target / "ic_launcher_foreground.png")
    (RES / "mipmap-anydpi").mkdir(parents=True, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (RES / "mipmap-anydpi" / name).write_text(ADAPTIVE_XML, encoding="utf-8", newline="\n")
    (RES / "values" / "ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">#{COCOA[0]:02X}{COCOA[1]:02X}{COCOA[2]:02X}</color>\n'
        "</resources>\n",
        encoding="utf-8",
        newline="\n",
    )
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "drawable" / "ic_notification_antlers.xml").write_text(antlers_vector(frame), encoding="utf-8", newline="\n")


def main() -> None:
    command = sys.argv[1] if len(sys.argv) > 1 else "preview"
    if command == "preview":
        print(write_preview())
        print("\n".join(report(face(idle_frame0()))))
        print("\n".join(colour_contrasts(face(idle_frame0()))))
    elif command == "apply":
        variant = sys.argv[2] if len(sys.argv) > 2 else "fit"
        apply(variant)
        print(f"applied variant {variant}")
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
