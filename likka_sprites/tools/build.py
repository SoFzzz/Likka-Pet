"""Builds Likka's final sprite sheet from the AI drafts.

Reads the source sheets in sprites/drafts/2d-sprites-sheets/ and writes sprites/likka.png,
sprites/likka.json (Aseprite JSON "Array" + frameTags format) and sprites/likka_poses.json,
following likkapet_design_system.md §1.7: integer nearest-neighbour downscale per sheet,
binary alpha, one 16-colour palette for the whole sheet, 1 px outline, feet on y = 92,
x-centred per tag, and peek cut on the right side. Output is deterministic (seeded k-means).

Run from the project root (needs Pillow, numpy and scipy):
    python likka_sprites/tools/build.py
"""
import json
import warnings
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

warnings.filterwarnings('ignore')  # palette PNGs with byte transparency

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'sprites' / 'drafts' / '2d-sprites-sheets'
OUT = ROOT / 'sprites'
CELL = 96
FEET_Y = 92            # last opaque row
PALETTE_SIZE = 16      # includes the outline colour
MIN_ISLAND = 6         # opaque specks smaller than this are removed
PEEK_VISIBLE = 0.60    # share of Likka's width left inside the canvas in peek

# key: file, columns, rows, frame count, downscale factor, sampling phase (x, y)
SHEETS = {
    'idle': ('left-diagonal-down-idle.png', 8, 1, 8, 1, (0, 0)),
    'walk_down': ('front_front_walk_walk_down.png', 8, 1, 8, 1, (0, 0)),
    'walk_up': ('walk_up.png', 5, 3, 12, 1, (0, 0)),
    'diag': ('likka-walk-rigth-diagonal.png', 5, 1, 5, 3, (2, 2)),
    'angry': ('Likka-a-angry.png', 5, 5, 25, 2, (0, 0)),
    'sit': ('Likka-sit-sit-down.png', 5, 5, 25, 2, (0, 0)),
    'sleep': ('Likka-sleepy.png', 5, 5, 25, 2, (0, 0)),
    'curiosity': ('Likka-c-curiosity.png', 5, 5, 25, 2, (0, 0)),
}

# tag, source sheet, chosen source frame indices (0-based, row-major), timing: either one fps for
# every frame of the tag, or a list with each frame's duration in ms (holds, uneven pacing)
TAGS = [
    # idle: only the rest frames; source frames 2-5 lift the elytron and every rest <-> lift step
    # changes >= 1456 px, so they read as a jump. Frame 0 is held so a breath lasts 1.5 s.
    ('idle', 'idle', [0, 1, 6, 7], [750, 250, 250, 250]),
    ('walk_down', 'walk_down', [0, 1, 2, 3, 4, 5, 6, 7], 10),
    ('walk_up', 'walk_up', [0, 1, 2, 5, 6, 7, 8, 11], 10),
    ('walk_diag_down_right', 'diag', [0, 1, 2, 3, 4], 10),
    ('annoyed', 'angry', [0, 6, 17, 16], 8),
    ('fury', 'angry', [7, 8, 9, 10, 11, 12], 8),
    ('peek', 'idle', [0, 1, 6, 7], [750, 250, 250, 250]),  # same breath as idle
    ('sit', 'sit', [0, 4, 16, 23], 6),
    ('sleep', 'sleep', [15, 16, 23, 17], 4),
    ('look_around', 'curiosity', [0, 6, 11, 19], 6),
]

# Frames the common palette is computed from. Frozen to the first build's selection so that
# changing a tag's frames or timing never recolours the sheet.
PALETTE_FRAMES = {
    'idle': [0, 1, 2, 3, 4, 5, 6, 7],
    'walk_down': [0, 1, 2, 3, 4, 5, 6, 7],
    'walk_up': [0, 1, 2, 5, 6, 7, 8, 11],
    'diag': [0, 1, 2, 3, 4],
    'annoyed': ('angry', [0, 6, 17, 16]),
    'fury': ('angry', [7, 8, 9, 10, 11, 12]),
    'sit': [0, 4, 16, 23],
    'sleep': [15, 16, 23, 17],
    'curiosity': [0, 6, 11, 19],
}

POSES = {
    "idle": {"tag": "idle", "mode": "loop"},
    "talk": {"tag": "idle", "mode": "loop"},
    "happy": {"tag": "idle", "mode": "loop", "extras": ["sparkles"]},
    "worried": {"tag": "look_around", "mode": "loop", "extras": ["sweat_drop"]},
    "sleeping": {"tag": "sleep", "mode": "loop", "extras": ["z"]},
    "sit": {"tag": "sit", "mode": "loop"},
    "perch": {"tag": "sit", "mode": "loop"},
    "peek": {"tag": "peek", "mode": "loop"},
    "annoyed": {"tag": "annoyed", "mode": "loop"},
    "dragged": {"tag": "annoyed", "mode": "loop"},
    "fury": {"tag": "fury", "mode": "loop", "extras": ["aura"]},
    "walk_down": {"tag": "walk_down", "mode": "loop"},
    "walk_up": {"tag": "walk_up", "mode": "loop"},
    "walk_right": {"tag": "walk_diag_down_right", "mode": "loop"},
    "goodbye": {"tag": "walk_diag_down_right", "mode": "loop"},
    "walk_diag_down_right": {"tag": "walk_diag_down_right", "mode": "loop"},
    "walk_left": {"tag": "walk_diag_down_right", "mode": "loop", "mirror": True},
    "walk_diag_down_left": {"tag": "walk_diag_down_right", "mode": "loop", "mirror": True},
}



def load_frames(key):
    """Source frames of one sheet, downscaled by nearest neighbour, alpha made binary."""
    name, cols, rows, count, k, (px, py) = SHEETS[key]
    a = np.array(Image.open(SRC / name).convert('RGBA'))
    ch, cw = a.shape[0] // rows, a.shape[1] // cols
    frames = []
    for i in range(count):
        r, c = divmod(i, cols)
        cell = a[r * ch:(r + 1) * ch, c * cw:(c + 1) * cw]
        small = cell[py::k, px::k].copy()
        small[..., 3] = np.where(small[..., 3] >= 128, 255, 0)
        small[small[..., 3] == 0] = 0
        frames.append(small)
    return frames

def remove_islands(frame):
    mask = frame[..., 3] > 0
    labels, n = ndimage.label(mask, structure=np.ones((3, 3)))
    sizes = ndimage.sum(mask, labels, range(1, n + 1))
    keep = np.isin(labels, [i + 1 for i, s in enumerate(sizes) if s >= MIN_ISLAND])
    out = frame.copy()
    out[~keep] = 0
    return out


def to_lab(rgb):
    c = np.asarray(rgb, float) / 255
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    xyz = c @ np.array([[0.4124, 0.3576, 0.1805],
                        [0.2126, 0.7152, 0.0722],
                        [0.0193, 0.1192, 0.9505]]).T
    xyz /= np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]),
                     200 * (f[..., 1] - f[..., 2])], -1)


def kmeans_palette(cols, weights, k, fixed, iters=60, seed=7):
    """Weighted k-means in Lab; `fixed` RGB centres never move (outline, amber eyes)."""
    rng = np.random.default_rng(seed)
    lab = to_lab(cols)
    centers = list(to_lab(np.array(fixed, float)))
    while len(centers) < k:  # k-means++ init around the fixed centres
        d = np.min([((lab - c) ** 2).sum(1) for c in centers], axis=0) * weights
        centers.append(lab[rng.choice(len(lab), p=d / d.sum())])
    centers = np.array(centers)
    nf = len(fixed)
    for _ in range(iters):
        idx = ((lab[:, None, :] - centers[None]) ** 2).sum(-1).argmin(1)
        for j in range(nf, k):
            m = idx == j
            if m.any():
                centers[j] = np.average(lab[m], axis=0, weights=weights[m])
    idx = ((lab[:, None, :] - centers[None]) ** 2).sum(-1).argmin(1)
    rgb = [np.array(f, float) for f in fixed]
    for j in range(nf, k):  # representative RGB = weighted mean of members
        m = idx == j
        rgb.append(np.average(cols[m], axis=0, weights=weights[m]))
    return np.round(np.array(rgb)).astype(np.uint8)


# Anchors taken from the clean, real-pixel-size idle sheet.
OUTLINE_RGB = (0x14, 0x0A, 0x0B)
AMBER_EYES_RGB = (0xEC, 0xA1, 0x42)


def build_palette(frames_by_tag):
    """Common palette: weighted k-means over unique colours, each tag weighted equally,
    counts damped (sqrt) so small accents survive; outline and amber eyes are pinned."""
    acc = {}
    for frames in frames_by_tag.values():
        px = np.concatenate([f[f[..., 3] > 0][:, :3] for f in frames])
        cols, counts = np.unique(px, axis=0, return_counts=True)
        w = np.sqrt(counts) / np.sqrt(counts).sum()
        for c, wi in zip(map(tuple, cols), w):
            acc[c] = acc.get(c, 0) + wi
    cols = np.array(list(acc.keys()), float)
    w = np.array(list(acc.values()))
    pal = kmeans_palette(cols, w, PALETTE_SIZE, [OUTLINE_RGB, AMBER_EYES_RGB])
    rest = pal[2:][np.argsort(to_lab(pal[2:])[:, 0])]
    return np.vstack([pal[:2], rest])


def to_palette(frame, pal):
    out = frame.copy()
    m = out[..., 3] > 0
    px = to_lab(out[m][:, :3])
    idx = ((px[:, None, :] - to_lab(pal)[None]) ** 2).sum(-1).argmin(1)
    out[m, :3] = pal[idx]
    return out


def apply_outline(frame, outline):
    """Every opaque pixel touching transparency (4-neighbourhood) becomes the outline colour."""
    mask = frame[..., 3] > 0
    padded = np.pad(mask, 1)
    inner = padded[:-2, 1:-1] & padded[2:, 1:-1] & padded[1:-1, :-2] & padded[1:-1, 2:]
    out = frame.copy()
    out[mask & ~inner, :3] = outline
    return out


def bbox(frame):
    ys, xs = np.where(frame[..., 3] > 0)
    return ys.min(), ys.max(), xs.min(), xs.max()


def place(frame, dx, feet_y=FEET_Y):
    """Crop to content and paste on a 96x96 canvas: x = dx + bbox left, bottom row at feet_y.
    Content falling outside the canvas is cut (used by peek)."""
    y0, y1, x0, x1 = bbox(frame)
    content = frame[y0:y1 + 1, x0:x1 + 1]
    canvas = np.zeros((CELL, CELL, 4), np.uint8)
    top = feet_y - (y1 - y0)
    left = x0 + dx
    h, w = content.shape[:2]
    cx0, cx1 = max(0, left), min(CELL, left + w)
    cy0, cy1 = max(0, top), min(CELL, top + h)
    canvas[cy0:cy1, cx0:cx1] = content[cy0 - top:cy1 - top, cx0 - left:cx1 - left]
    return canvas


def x_offset(frames, peek=False):
    """One horizontal offset per tag (keeps in-animation motion): union bbox centred,
    or for peek pushed right so only PEEK_VISIBLE of the width stays in the canvas."""
    x0 = min(bbox(f)[2] for f in frames)
    x1 = max(bbox(f)[3] for f in frames)
    width = x1 - x0 + 1
    if peek:
        visible = round(width * PEEK_VISIBLE)
        return (CELL - visible) - x0
    return (CELL - width) // 2 - x0


def palette_source(group, spec):
    """(sheet, frame indices) of a PALETTE_FRAMES entry; the key is the sheet unless given."""
    return spec if isinstance(spec, tuple) else (group, spec)


def frame_durations(timing, count):
    """Per-frame durations in ms (likka.json `duration`) from a tag's fps or explicit list."""
    if isinstance(timing, int):
        return [round(1000 / timing)] * count
    if len(timing) != count:
        raise ValueError(f'{len(timing)} durations for {count} frames')
    return list(timing)


def main():
    cache = {}

    def source_frames(sheet, idx):
        if sheet not in cache:
            cache[sheet] = [remove_islands(f) for f in load_frames(sheet)]
        return [cache[sheet][i] for i in idx]

    raw = {tag: source_frames(sheet, idx) for tag, sheet, idx, _ in TAGS}
    pal = build_palette({group: source_frames(*palette_source(group, spec))
                         for group, spec in PALETTE_FRAMES.items()})
    outline = pal[0]

    cols = max(len(i) for _, _, i, _ in TAGS)
    sheet_img = np.zeros((CELL * len(TAGS), CELL * cols, 4), np.uint8)
    frames_json, tags_json = [], []
    n = 0
    for row, (tag, sheet, idx, timing) in enumerate(TAGS):
        clean = [apply_outline(to_palette(f, pal), outline) for f in raw[tag]]
        dx = x_offset(clean, peek=(tag == 'peek'))
        start = n
        durations = frame_durations(timing, len(clean))
        for col, f in enumerate(clean):
            cell = place(f, dx)
            sheet_img[row * CELL:(row + 1) * CELL, col * CELL:(col + 1) * CELL] = cell
            frames_json.append({
                "filename": f"likka {n}.aseprite",
                "frame": {"x": col * CELL, "y": row * CELL, "w": CELL, "h": CELL},
                "rotated": False,
                "trimmed": False,
                "spriteSourceSize": {"x": 0, "y": 0, "w": CELL, "h": CELL},
                "sourceSize": {"w": CELL, "h": CELL},
                "duration": durations[col],
            })
            n += 1
        tags_json.append({"name": tag, "from": start, "to": n - 1,
                          "direction": "forward", "color": "#000000ff"})

    Image.fromarray(sheet_img, 'RGBA').save(OUT / 'likka.png', optimize=True)
    meta = {
        "app": "likka build script (Aseprite JSON Array export format)",
        "version": "1.3",
        "image": "likka.png",
        "format": "RGBA8888",
        "size": {"w": sheet_img.shape[1], "h": sheet_img.shape[0]},
        "scale": "1",
        "frameTags": tags_json,
        "layers": [{"name": "Likka", "opacity": 255, "blendMode": "normal"}],
        "slices": [],
    }
    with open(OUT / 'likka.json', 'w', encoding='utf-8') as fh:
        json.dump({"frames": frames_json, "meta": meta}, fh, indent=1)
        fh.write('\n')
    with open(OUT / 'likka_poses.json', 'w', encoding='utf-8') as fh:
        body = ',\n'.join(f'  "{k}": ' + json.dumps(v, separators=(', ', ': '))
                          .replace('{"', '{ "').replace('}', ' }') for k, v in POSES.items())
        fh.write('{\n' + body + '\n}\n')
    print('wrote', OUT / 'likka.png', 'likka.json', 'likka_poses.json')
    print('palette:', ['#%02X%02X%02X' % tuple(c) for c in pal])


if __name__ == '__main__':
    main()
