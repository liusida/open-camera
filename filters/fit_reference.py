"""Fit a colour filter (LUT) that reproduces an edit, from a before/after photo pair.

Fits one smooth, monotonic tone curve per channel (after vs before), plus a 3x3 colour
mixing matrix for cross-channel shifts, then bakes it into out/luts/<name>.cube and
renders previews.

    uv run --no-project --with numpy --with pillow --with tqdm filters/fit_reference.py \
        filters/reference/before.jpg filters/reference/after.jpg my_ideal
"""
import sys
from pathlib import Path
import numpy as np
from PIL import Image
from tqdm import tqdm
sys.path.insert(0, str(Path(__file__).parent))
from make_filters import bake_lut, write_cube, apply_lut, OUT

def load(p, w=None):
    im = Image.open(p).convert("RGB")
    if w: im = im.resize((w, int(w * im.height / im.width)), Image.BOX)
    return np.asarray(im, dtype=np.float32) / 255

def fit_curve(x, y, bins=64):
    """Monotonic smooth curve y=f(x) on [0,1], from binned medians."""
    edges = np.linspace(0, 1, bins + 1); cx, cy = [0.0], [0.0]
    for i in range(bins):
        m = (x >= edges[i]) & (x < edges[i + 1])
        if m.sum() > 20:
            cx.append((edges[i] + edges[i + 1]) / 2); cy.append(float(np.median(y[m])))
    cx.append(1.0); cy.append(max(1.0, cy[-1]) if cy[-1] > 0.98 else cy[-1] + (1 - cy[-1]) * 0.9 if False else max(cy[-1], 1.0))
    cy = np.maximum.accumulate(np.array(cy))                 # monotonic
    grid = np.linspace(0, 1, 256)
    f = np.interp(grid, cx, cy)
    k = np.ones(15) / 15                                        # smooth
    f = np.convolve(np.pad(f, 7, mode="edge"), k, mode="valid")
    f[0] = cy[0]
    return grid, np.clip(f, 0, 1)

def main(before, after, name):
    a = load(before, 400).reshape(-1, 3); b = load(after, 400).reshape(-1, 3)
    # 1) colour mixing matrix (with offset), fitted in least squares
    X = np.c_[a, np.ones(len(a))]
    M, *_ = np.linalg.lstsq(X, b, rcond=None)
    mixed = np.clip(X @ M, 0, 1)
    # 2) per-channel tone curves on top of the mix, to capture non-linear tone changes
    curves = [fit_curve(mixed[:, c], b[:, c]) for c in range(3)]
    def fn(c):
        sh = c.shape; flat = c.reshape(-1, 3)
        m = np.clip(np.c_[flat, np.ones(len(flat))] @ M, 0, 1)
        out = np.stack([np.interp(m[:, i], *curves[i]) for i in range(3)], -1)
        return out.reshape(sh)
    lut = bake_lut(fn)
    (OUT / "luts").mkdir(parents=True, exist_ok=True)
    write_cube(lut, OUT / "luts" / f"{name}.cube", name)
    pred = apply_lut(load(before, 400), lut).reshape(-1, 3)
    err = np.abs(pred - b).mean() * 255; base = np.abs(a - b).mean() * 255
    print(f"mean abs error per channel: {base:.1f} (no filter) -> {err:.1f} (fitted LUT), in 0-255 units")
    print("colour matrix (rows: in R,G,B,offset):\n", M.round(3))
    for src, tag in tqdm([(before, "reference"), (Path(__file__).parent.parent / "prototype/assets/camera-preview.jpg", "sample")], desc="render"):
        im = Image.open(src).convert("RGB"); im.thumbnail((1600, 1600), Image.LANCZOS)
        res = apply_lut(np.asarray(im, np.float32) / 255, lut)
        Image.fromarray((res * 255 + 0.5).astype(np.uint8)).save(OUT / f"{name}_{tag}.jpg", quality=90)

if __name__ == "__main__":
    main(*sys.argv[1:4])
