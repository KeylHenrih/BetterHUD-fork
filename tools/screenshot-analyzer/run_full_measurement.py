#!/usr/bin/env python3
"""
tools/screenshot-analyzer/run_full_measurement.py
Comprehensive measurement across real user screenshots:
- Vanilla 26.1.2 tellraw reference
- BetterHud Client and Server mode across multiple layout heights
- Regular vs Italic, Bold vs Bold+Italic
"""
import argparse
import os
import sys
from PIL import Image
import numpy as np

parser = argparse.ArgumentParser(description="Comprehensive measurement across real user screenshots")
parser.add_argument("--vanilla", default=os.environ.get("VANILLA_SCREENSHOT", "screenshots/2026-10-26_23.32.40.png"), help="Path to vanilla screenshot")
parser.add_argument("--betterhud", default=os.environ.get("BETTERHUD_SCREENSHOT", "screenshots/2026-10-26_23.36.57.png"), help="Path to BetterHud screenshot")
parser.add_argument("--scale", type=float, default=3.0, help="GUI scale factor (default: 3.0)")
args = parser.parse_args()

path_vanilla = args.vanilla
path_bh = args.betterhud
scale = args.scale

if not os.path.exists(path_vanilla):
    print(f"Error: Vanilla screenshot not found at '{path_vanilla}'. Provide via --vanilla or $VANILLA_SCREENSHOT.")
    sys.exit(1)
if not os.path.exists(path_bh):
    print(f"Error: BetterHud screenshot not found at '{path_bh}'. Provide via --betterhud or $BETTERHUD_SCREENSHOT.")
    sys.exit(1)
blue = np.array([52, 152, 219])
red = np.array([231, 76, 60])
white = np.array([255, 255, 255])

mb_v = np.sqrt(np.sum((img_v - blue)**2, axis=-1)) < 35
mr_v = np.sqrt(np.sum((img_v - red)**2, axis=-1)) < 35

# Row 1 (regular)
yb1, xb1 = np.where(mb_v & (np.arange(img_v.shape[0])[:, None] <= 998))
yr1, xr1 = np.where(mr_v & (np.arange(img_v.shape[0])[:, None] <= 998))
v_reg_top = (np.mean(xr1[yr1 == yr1.min()]) - np.mean(xb1[yb1 == yb1.min()])) / scale
v_reg_bot = (np.mean(xr1[yr1 == yr1.max()]) - np.mean(xb1[yb1 == yb1.max()])) / scale
v_gap = round((v_reg_top + v_reg_bot) / 2.0)
v_ref_top = v_reg_top - v_gap
v_ref_bot = v_reg_bot - v_gap

# Row 2 (bold)
yb2, xb2 = np.where(mb_v & (np.arange(img_v.shape[0])[:, None] >= 1002))
yr2, xr2 = np.where(mr_v & (np.arange(img_v.shape[0])[:, None] >= 1002))
v_bold_top = (np.mean(xr2[yr2 == yr2.min()]) - np.mean(xb2[yb2 == yb2.min()])) / scale
v_bold_bot = (np.mean(xr2[yr2 == yr2.max()]) - np.mean(xb2[yb2 == yb2.max()])) / scale
v_bold_gap = round((v_bold_top + v_bold_bot) / 2.0)
v_ref_bold_top = v_bold_top - v_bold_gap
v_ref_bold_bot = v_bold_bot - v_bold_gap

print("==========================================================================================")
print("  SCREENSHOT-BASED VANILLA CLIENT 26.1.2 ETALON (USER CLIENT)")
print("==========================================================================================")
print(f"Screenshot: {path_vanilla}")
print(f"Detected GUI scale: {int(scale)} (glyph height 24 physical px = 8.0 GUI px)")
print(f"Regular vs Italic:     Delta_vanilla_top = {v_ref_top:+.3f} px | Delta_vanilla_bottom = {v_ref_bot:+.3f} px | Slant = {v_ref_top - v_ref_bot:.3f} px")
print(f"Bold vs Bold+Italic:   Delta_vanilla_top = {v_ref_bold_top:+.3f} px | Delta_vanilla_bottom = {v_ref_bold_bot:+.3f} px | Slant = {v_ref_bold_top - v_ref_bold_bot:.3f} px")
print("==========================================================================================\n")

# BetterHUD screenshot
img = np.array(Image.open(path_bh).convert("RGB"))

mb = np.sqrt(np.sum((img - blue)**2, axis=-1)) < 35
mr = np.sqrt(np.sum((img - red)**2, axis=-1)) < 35
mw = np.sqrt(np.sum((img - white)**2, axis=-1)) < 35

def get_edge_coords(mask, y0, y1, x0, x1):
    top_row = mask[y0, x0:x1]
    bot_row = mask[y1, x0:x1]
    xt = np.where(top_row)[0]
    xb = np.where(bot_row)[0]
    if len(xt) == 0 or len(xb) == 0:
        return None
    top_min_x = np.min(xt) + x0
    bot_min_x = np.min(xb) + x0
    return float(top_min_x), float(bot_min_x)

tests = [
    # Mode, Category, Y-label, y0, y1, rx0, rx1, ix0, ix1, mask_type, is_bold
    ("Client Mode", "Regular vs Italic", "Y = -100", 252, 275, 1520, 1550, 1665, 1700, "color", False),
    ("Client Mode", "Bold vs Bold+Italic", "Y = -100", 282, 305, 1415, 1450, 1585, 1625, "color", True),
    ("Client Mode", "Regular vs Italic", "Y = 0",    552, 575, 1230, 1265, 1375, 1415, "color", False),
    ("Client Mode", "Bold vs Bold+Italic", "Y = 0",    582, 605, 1135, 1170, 1305, 1345, "color", True),
    ("Client Mode", "Regular vs Italic", "Y = 50",   702, 725, 960, 995,   1105, 1145, "color", False),
    ("Client Mode", "Bold vs Bold+Italic", "Y = 50",   732, 755, 860, 895,   1035, 1075, "color", True),
    # Server Mode (white blocks)
    ("Server Mode", "Regular vs Italic", "Y = -100", 252, 275, 1120, 1160, 1260, 1310, "white", False),
    ("Server Mode", "Regular vs Italic", "Y = 0",    552, 575, 830,  870,  970,  1020,  "white", False),
    ("Server Mode", "Regular vs Italic", "Y = 50",   702, 725, 560,  600,  700,  750,   "white", False),
]

print("==========================================================================================================================================")
print("  BETTERHUD SCREEN MEASUREMENTS VS VANILLA ETALON (TOLERANCE: +/-0.25 GUI-PX)")
print(f"  Screenshot: {path_bh}")
print("==========================================================================================================================================")
col_fmt = "| {:<12} | {:<20} | {:<10} | {:<17} | {:<17} | {:^11} | {:^11} | {:^9} | {:^9} | {:^7} |"
sep = f"|{'-'*14}|{'-'*22}|{'-'*12}|{'-'*19}|{'-'*19}|{'-'*13}|{'-'*13}|{'-'*11}|{'-'*11}|{'-'*9}|"
print(col_fmt.format("Mode", "Category", "Layout Y", "Reg (top/bot)", "Ita (top/bot)", "Delta_top", "Delta_bot", "Err_top", "Err_bot", "Verdict"))
print(sep)

for mode, cat, y_lbl, y0, y1, rx0, rx1, ix0, ix1, mtype, is_bold in tests:
    ref_t = v_ref_bold_top if is_bold else v_ref_top
    ref_b = v_ref_bold_bot if is_bold else v_ref_bot
    
    m = (mb, mr) if mtype == "color" else (mw, mw)
    reg = get_edge_coords(m[0], y0, y1, rx0, rx1)
    ita = get_edge_coords(m[1], y0, y1, ix0, ix1)
    
    if reg is None or ita is None:
        print(col_fmt.format(mode, cat, y_lbl, "NOT FOUND", "NOT FOUND", "N/A", "N/A", "N/A", "N/A", "FAIL"))
        continue
    
    rt, rb = reg
    it, ib = ita
    
    dx_t = (it - rt) / scale
    dx_b = (ib - rb) / scale
    nom = round((dx_t + dx_b) / 2.0)
    d_top = dx_t - nom
    d_bot = dx_b - nom
    
    err_t = abs(d_top - ref_t)
    err_b = abs(d_bot - ref_b)
    verdict = "PASS" if max(err_t, err_b) <= 0.25 else "FAIL"
    
    reg_s = f"{rt:.1f} / {rb:.1f}"
    ita_s = f"{it:.1f} / {ib:.1f}"
    print(col_fmt.format(mode, cat, y_lbl, reg_s, ita_s, f"{d_top:+.3f}", f"{d_bot:+.3f}", f"{err_t:.3f}", f"{err_b:.3f}", verdict))

print("==========================================================================================================================================")
