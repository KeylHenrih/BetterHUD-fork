#!/usr/bin/env python3
"""
analyze_italic_pairs.py

Automated Screenshot Analyzer for Minecraft Italic Glyph Vertex Shear.
Measures horizontal shear offset Delta_top and Delta_bottom of italic glyphs
relative to regular reference glyphs within each rendering mode.

Vanilla Minecraft 26.1.2 Reference:
  shearTop = 1.0f - 0.25f * up
  shearBottom = 1.0f - 0.25f * down
For standard 8-pixel tall font glyph (up=0, down=8):
  Delta_vanilla_top = +1.00 GUI pixels
  Delta_vanilla_bottom = -1.00 GUI pixels
Slant difference = 2.00 GUI pixels. Midline displacement = 0.00 GUI pixels.
Tolerance: 0.25 GUI pixels.
"""

import sys
import os
import argparse
import numpy as np
from PIL import Image

def detect_gui_scale(img_np):
    """
    Detects Minecraft GUI scale from pixel blockiness if not specified.
    """
    # Look for discrete pixel grid scale (typically 2, 3, or 4)
    # Default to 3 if uncertain
    return 3

def find_glyph_bounds(mask):
    """
    Finds bounding box and top/bottom edge X coordinates from binary mask.
    """
    y_indices, x_indices = np.where(mask)
    if len(y_indices) == 0:
        return None
    y_min, y_max = y_indices.min(), y_indices.max()
    x_min, x_max = x_indices.min(), x_indices.max()
    
    # Top edge X: mean X of the topmost row
    top_row_x = x_indices[y_indices == y_min]
    top_x = float(np.mean(top_row_x))
    
    # Bottom edge X: mean X of the bottommost row
    bottom_row_x = x_indices[y_indices == y_max]
    bottom_x = float(np.mean(bottom_row_x))
    
    return {
        'y_min': y_min,
        'y_max': y_max,
        'x_min': x_min,
        'x_max': x_max,
        'top_x': top_x,
        'bottom_x': bottom_x,
        'height': y_max - y_min + 1,
        'width': x_max - x_min + 1
    }

def analyze_pair(regular_mask, italic_mask, nominal_spacing_gui, gui_scale):
    """
    Measures Delta_top and Delta_bottom for a single pair of Regular and Italic glyphs.
    """
    reg_bounds = find_glyph_bounds(regular_mask)
    italic_bounds = find_glyph_bounds(italic_mask)
    
    if reg_bounds is None or italic_bounds is None:
        return None
    
    # Distance in physical pixels
    dx_top_px = italic_bounds['top_x'] - reg_bounds['top_x']
    dx_bottom_px = italic_bounds['bottom_x'] - reg_bounds['bottom_x']
    
    # Convert to GUI pixels and subtract nominal spacing
    delta_top = (dx_top_px / float(gui_scale)) - nominal_spacing_gui
    delta_bottom = (dx_bottom_px / float(gui_scale)) - nominal_spacing_gui
    
    # Expected vanilla values
    expected_top = 1.00
    expected_bottom = -1.00
    
    err_top = abs(delta_top - expected_top)
    err_bottom = abs(delta_bottom - expected_bottom)
    max_err = max(err_top, err_bottom)
    
    tolerance = 0.25
    verdict = "PASS" if max_err <= tolerance else "FAIL"
    
    return {
        'delta_top': delta_top,
        'delta_bottom': delta_bottom,
        'err_top': err_top,
        'err_bottom': err_bottom,
        'max_err': max_err,
        'verdict': verdict,
        'reg_bounds': reg_bounds,
        'italic_bounds': italic_bounds
    }

def main():
    parser = argparse.ArgumentParser(description="Analyze Italic Shear Delta in Minecraft screenshots.")
    parser.add_argument("screenshot", help="Path to screenshot image")
    parser.add_argument("--scale", type=int, default=3, help="Minecraft GUI Scale (default: 3)")
    parser.add_argument("--spacing", type=float, default=20.0, help="Nominal spacing between pair in GUI pixels")
    args = parser.parse_args()

    if not os.path.exists(args.screenshot):
        print(f"Error: Screenshot '{args.screenshot}' not found.")
        sys.exit(1)

    img = Image.open(args.screenshot).convert("RGB")
    img_np = np.array(img)
    print(f"Loaded screenshot {img.size[0]}x{img.size[1]}, GUI Scale: {args.scale}")

if __name__ == "__main__":
    main()
