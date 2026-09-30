"""Validates the final sprite sheet in sprites/ (likka.png, likka.json, likka_poses.json).

Same criteria as the unit test of likkapet_design_system.md §1.7 (the 10 sheet tags exist,
every pose points to an existing tag, every frame is 96x96) plus art-format checks: binary
alpha, 12-16 colours, feet on y = 92, max height 90 with 2 px free on top, frame count and
per-frame duration per tag. Prints a per-tag table; exits with code 1 on any failure.

Run from the project root (needs Pillow and numpy):
    python likka_sprites/tools/validate.py
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

SPR = Path(__file__).resolve().parents[2] / 'sprites'
SHEET_TAGS = ['idle', 'walk_down', 'walk_up', 'walk_diag_down_right', 'annoyed', 'fury',
              'peek', 'sit', 'sleep', 'look_around']
EXPECTED_FRAMES = {'idle': 8, 'walk_down': 8, 'walk_up': 8, 'walk_diag_down_right': 5,
                   'annoyed': 4, 'fury': 6, 'peek': 4, 'sit': 4, 'sleep': 4, 'look_around': 4}
EXPECTED_FPS = {'idle': 8, 'walk_down': 10, 'walk_up': 10, 'walk_diag_down_right': 10,
                'annoyed': 8, 'fury': 8, 'peek': 8, 'sit': 6, 'sleep': 4, 'look_around': 6}
VALID_MODES = {'loop', 'once', 'once_then_idle', 'hold'}
VALID_EXTRAS = {'z', 'sweat_drop', 'aura', 'sparkles'}
FEET_Y, MAX_H, TOP_FREE = 92, 90, 2

failures = []


def check(ok, msg):
    if not ok:
        failures.append(msg)
    return ok


data = json.load(open(SPR / 'likka.json', encoding='utf-8'))
poses = json.load(open(SPR / 'likka_poses.json', encoding='utf-8'))
sheet = np.array(Image.open(SPR / 'likka.png').convert('RGBA'))
frames, meta = data['frames'], data['meta']
tags = {t['name']: t for t in meta['frameTags']}

# --- same criteria as the unit test ---
check(sorted(tags) == sorted(SHEET_TAGS), f'tags {sorted(tags)} != the 10 sheet tags')
for name, p in poses.items():
    check(p.get('tag') in tags, f'pose {name} -> missing tag {p.get("tag")}')
    check(p.get('mode') in VALID_MODES, f'pose {name}: bad mode {p.get("mode")}')
    check(set(p.get('extras', [])) <= VALID_EXTRAS, f'pose {name}: bad extras')
for i, f in enumerate(frames):
    fr = f['frame']
    check(fr['w'] == 96 and fr['h'] == 96, f'frame {i} is {fr["w"]}x{fr["h"]}')
    check(f['sourceSize'] == {'w': 96, 'h': 96} and not f['trimmed'], f'frame {i} trimmed')

# --- Aseprite format ---
check(meta['size'] == {'w': sheet.shape[1], 'h': sheet.shape[0]}, 'meta.size != png size')
for t in meta['frameTags']:
    check(set(t) >= {'name', 'from', 'to', 'direction'}, f'tag {t["name"]} missing keys')

# --- art format ---
alpha = np.unique(sheet[..., 3])
check(set(alpha.tolist()) <= {0, 255}, f'alpha not binary: {alpha.tolist()}')
colours = np.unique(sheet[sheet[..., 3] == 255][:, :3], axis=0)
check(12 <= len(colours) <= 16, f'{len(colours)} colours (need 12-16)')

rows = []
for name in SHEET_TAGS:
    t = tags[name]
    n = t['to'] - t['from'] + 1
    check(n == EXPECTED_FRAMES[name], f'{name}: {n} frames, expected {EXPECTED_FRAMES[name]}')
    heights, bottoms, tops, centres, durs = [], [], [], [], set()
    for i in range(t['from'], t['to'] + 1):
        fr = frames[i]['frame']
        durs.add(frames[i]['duration'])
        cell = sheet[fr['y']:fr['y'] + 96, fr['x']:fr['x'] + 96]
        ys, xs = np.where(cell[..., 3] > 0)
        heights.append(ys.max() - ys.min() + 1)
        bottoms.append(ys.max())
        tops.append(ys.min())
        centres.append((xs.min() + xs.max()) / 2)
    check(durs == {round(1000 / EXPECTED_FPS[name])}, f'{name}: durations {durs}')
    check(set(bottoms) == {FEET_Y}, f'{name}: feet rows {sorted(set(bottoms))}')
    check(max(heights) <= MAX_H, f'{name}: max height {max(heights)} > {MAX_H}')
    check(min(tops) >= TOP_FREE, f'{name}: top row {min(tops)} < {TOP_FREE}')
    rows.append((name, n, sorted(durs)[0], min(heights), max(heights), min(tops),
                 min(centres), max(centres)))

print(f'{"tag":22} frames  ms   h(min-max)  top  x-centre')
for r in rows:
    print(f'{r[0]:22} {r[1]:>4} {r[2]:>6}   {r[3]:>3}-{r[4]:<3}    {r[5]:>3}  {r[6]:.1f}-{r[7]:.1f}')
print('colours:', len(colours), ' alpha values:', alpha.tolist(), ' poses:', len(poses))
if failures:
    print('\nFAIL:\n  ' + '\n  '.join(failures))
    sys.exit(1)
print('\nOK: all checks passed')
