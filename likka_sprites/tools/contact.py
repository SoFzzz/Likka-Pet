"""Review images for the final sprite sheet, written to likka_sprites/review/.

Reads sprites/likka.png + sprites/likka.json and writes, per tag, a contact sheet
(contact_<tag>.png: every frame x4 on cocoa #3D1B1C and on cream #FFF4E6, with the y = 2 top
limit and the canvas border drawn) and an animated GIF (anim_<tag>.gif, x4 on cocoa, using the
per-frame durations). Also writes overview.png (whole sheet x2 on cocoa) and
peek_options.png (peek at 55 / 60 / 65 % visible, rebuilt with build.py's functions).

Run from the project root (needs Pillow, numpy and scipy), optionally limited to some tags:
    python likka_sprites/tools/contact.py [tag ...]
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import build

ROOT = Path(__file__).resolve().parents[2]
SPR = ROOT / 'sprites'
OUT = ROOT / 'likka_sprites' / 'review'
COCOA = (0x3D, 0x1B, 0x1C, 255)
CREAM = (0xFF, 0xF4, 0xE6, 255)
GREY = (40, 40, 40, 255)
WHITE = (255, 255, 255, 255)
Z = 4
PEEK_OPTIONS = [0.55, 0.60, 0.65]


def cell(sheet, frame):
    f = frame['frame']
    return sheet.crop((f['x'], f['y'], f['x'] + f['w'], f['y'] + f['h']))


def on(bg, img, z, guides=False):
    base = Image.new('RGBA', img.size, bg)
    base.alpha_composite(img)
    base = base.resize((img.width * z, img.height * z), Image.NEAREST)
    if guides:  # top limit y = 2 and canvas border
        d = ImageDraw.Draw(base)
        d.line([(0, 2 * z), (base.width, 2 * z)], fill=(255, 80, 80, 255))
        d.rectangle([0, 0, base.width - 1, base.height - 1], outline=(128, 128, 128, 255))
    return base


def write_tag(sheet, frames, tag):
    idx = list(range(tag['from'], tag['to'] + 1))
    size, pad = 96 * Z, 8
    contact = Image.new('RGBA', (len(idx) * (size + pad) + pad, 2 * (size + pad) + pad + 14), GREY)
    ImageDraw.Draw(contact).text(
        (pad, 2), f"{tag['name']}  frames {tag['from']}-{tag['to']}  "
                  f"{frames[idx[0]]['duration']} ms", fill=WHITE)
    for k, i in enumerate(idx):
        x = pad + k * (size + pad)
        contact.paste(on(COCOA, cell(sheet, frames[i]), Z, True), (x, 14 + pad))
        contact.paste(on(CREAM, cell(sheet, frames[i]), Z, True), (x, 14 + 2 * pad + size))
    contact.convert('RGB').save(OUT / f"contact_{tag['name']}.png")
    gif = [on(COCOA, cell(sheet, frames[i]), Z).convert('RGB') for i in idx]
    gif[0].save(OUT / f"anim_{tag['name']}.gif", save_all=True, append_images=gif[1:],
                duration=[frames[i]['duration'] for i in idx], loop=0, disposal=2)


def write_overview(sheet):
    base = Image.new('RGBA', sheet.size, COCOA)
    base.alpha_composite(sheet)
    base.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST).convert('RGB') \
        .save(OUT / 'overview.png')


def write_peek_options(sheet):
    """First peek frame at several visible widths, using the sheet's own palette."""
    px = np.array(sheet)
    pal = np.unique(px[px[..., 3] == 255][:, :3], axis=0)
    idle_frames = [build.remove_islands(f) for f in build.load_frames('idle')]
    peek_idx = next(idx for tag, _, idx, _ in build.TAGS if tag == 'peek')
    clean = [build.apply_outline(build.to_palette(idle_frames[i], pal),
                                 np.array(build.OUTLINE_RGB, np.uint8)) for i in peek_idx]
    size, pad = 96 * Z, 10
    out = Image.new('RGB', (len(PEEK_OPTIONS) * (size + pad) + pad, 2 * (size + pad) + 30), GREY[:3])
    d = ImageDraw.Draw(out)
    default = build.PEEK_VISIBLE
    for col, visible in enumerate(PEEK_OPTIONS):
        build.PEEK_VISIBLE = visible
        frame = Image.fromarray(build.place(clean[0], build.x_offset(clean, peek=True)))
        x = pad + col * (size + pad)
        d.text((x, 6), f'peek visible {round(visible * 100)}%', fill=WHITE[:3])
        out.paste(on(COCOA, frame, Z).convert('RGB'), (x, 25))
        out.paste(on(CREAM, frame, Z).convert('RGB'), (x, 25 + size + pad))
    build.PEEK_VISIBLE = default
    out.save(OUT / 'peek_options.png')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.open(SPR / 'likka.png').convert('RGBA')
    data = json.load(open(SPR / 'likka.json', encoding='utf-8'))
    only = sys.argv[1:]
    for tag in data['meta']['frameTags']:
        if not only or tag['name'] in only:
            write_tag(sheet, data['frames'], tag)
    if not only:
        write_overview(sheet)
        write_peek_options(sheet)
    print('written to', OUT)


if __name__ == '__main__':
    main()
