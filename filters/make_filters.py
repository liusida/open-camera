"""Generate candidate colour filters as 3D LUTs (.cube) and preview them on a sample photo.

Each filter is defined as a function on RGB in [0, 1]. It is baked into a 33^3 LUT
(the same format the app would load), and the preview images are produced by applying
that LUT with trilinear interpolation, so what you see here is what the app would get.

Usage:
    uv run --with numpy --with pillow --with tqdm filters/make_filters.py [photo.jpg]
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from tqdm import tqdm

HERE = Path(__file__).parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parent / "prototype/assets/camera-preview.jpg"
OUT = HERE / "out"
LUT_SIZE = 33
PREVIEW_LONG_SIDE = 1600


# ---------- building blocks ----------
def luma(c):
    return c[..., 0:1] * 0.2126 + c[..., 1:2] * 0.7152 + c[..., 2:3] * 0.0722

def saturation(c, s):
    y = luma(c)
    return y + (c - y) * s

def s_curve(c, k):
    """Contrast around mid-grey; k>0 adds contrast."""
    return c + k * (c - 0.5) * (1 - np.abs(2 * c - 1))

def levels(c, black=0.0, white=1.0):
    """Lift blacks / compress whites (output range)."""
    return black + c * (white - black)

def gain(c, r, g, b):
    return c * np.array([r, g, b])

def split_tone(c, shadow_rgb, highlight_rgb, amount=1.0):
    y = luma(c)
    sh = np.array(shadow_rgb) * (1 - y) ** 2
    hi = np.array(highlight_rgb) * y ** 2
    return c + amount * (sh + hi)

def mono(c, tint=(1.0, 1.0, 1.0)):
    return luma(c) * np.array(tint)


# ---------- the candidate filters (original looks, not copies of any brand's presets) ----------
FILTERS = {
    "original":     lambda c: c,
    "muted_chrome": lambda c: split_tone(s_curve(saturation(c, 0.72), 0.25), (-0.02, 0.0, 0.03), (0.03, 0.01, -0.02)),
    "warm_film":    lambda c: levels(s_curve(saturation(gain(c, 1.06, 1.0, 0.9), 1.05), 0.2), 0.04, 0.98),
    "faded_matte":  lambda c: levels(saturation(s_curve(c, -0.1), 0.8), 0.09, 0.93),
    "teal_orange":  lambda c: s_curve(split_tone(saturation(c, 1.08), (-0.06, 0.02, 0.07), (0.07, 0.02, -0.06)), 0.15),
    "cool_clean":   lambda c: s_curve(saturation(gain(c, 0.95, 1.0, 1.07), 0.95), 0.12),
    "vivid":        lambda c: s_curve(saturation(c, 1.35), 0.22),
    "soft_pastel":  lambda c: split_tone(levels(saturation(s_curve(c, -0.15), 0.85), 0.07, 1.0), (0.0, 0.0, 0.02), (0.04, 0.0, 0.02)),
    "vintage":      lambda c: levels(split_tone(saturation(c, 0.6), (0.03, 0.01, -0.03), (0.06, 0.03, -0.04)), 0.06, 0.94),
    "mono":         lambda c: s_curve(mono(c), 0.15),
    "mono_contrast": lambda c: s_curve(s_curve(mono(c), 0.45), 0.25),
    "mono_warm":    lambda c: levels(s_curve(mono(c, (1.05, 1.0, 0.88)), 0.2), 0.03, 1.0),
}


def bake_lut(fn, n=LUT_SIZE):
    g = np.linspace(0, 1, n)
    # .cube order: red changes fastest, then green, then blue
    b, gg, r = np.meshgrid(g, g, g, indexing="ij")
    grid = np.stack([r, gg, b], axis=-1)            # [b, g, r, 3]
    return np.clip(fn(grid), 0, 1)

def write_cube(lut, path, title):
    n = lut.shape[0]
    with open(path, "w") as f:
        f.write(f'TITLE "{title}"\nLUT_3D_SIZE {n}\n')
        for row in lut.reshape(-1, 3):
            f.write(f"{row[0]:.6f} {row[1]:.6f} {row[2]:.6f}\n")

def apply_lut(img, lut):
    """Trilinear interpolation, img float [H,W,3] in [0,1], lut indexed [b,g,r]."""
    n = lut.shape[0]
    x = img * (n - 1)
    i0 = np.clip(np.floor(x).astype(int), 0, n - 2)
    f = x - i0
    r0, g0, b0 = i0[..., 0], i0[..., 1], i0[..., 2]
    fr, fg, fb = f[..., 0:1], f[..., 1:2], f[..., 2:3]
    out = 0
    for db in (0, 1):
        for dg in (0, 1):
            for dr in (0, 1):
                w = (fr if dr else 1 - fr) * (fg if dg else 1 - fg) * (fb if db else 1 - fb)
                out = out + w * lut[b0 + db, g0 + dg, r0 + dr]
    return out


def main():
    OUT.mkdir(exist_ok=True)
    (OUT / "luts").mkdir(exist_ok=True)
    im = Image.open(SRC).convert("RGB")
    im.thumbnail((PREVIEW_LONG_SIDE, PREVIEW_LONG_SIDE), Image.LANCZOS)
    img = np.asarray(im, dtype=np.float32) / 255.0
    for name, fn in tqdm(FILTERS.items(), desc="filters"):
        lut = bake_lut(fn)
        write_cube(lut, OUT / "luts" / f"{name}.cube", name)
        res = apply_lut(img, lut)
        Image.fromarray((res * 255 + 0.5).astype(np.uint8)).save(OUT / f"{name}.jpg", quality=90)
    print(f"wrote {len(FILTERS)} previews and LUTs to {OUT}")


if __name__ == "__main__":
    main()
