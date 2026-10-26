#!/usr/bin/env python3
"""
analyze_italic_pairs.py

Automated Screenshot Analyzer for Minecraft Italic Glyph Vertex Shear.
Measures horizontal shear offset Delta_top and Delta_bottom of italic glyphs
relative to regular reference glyphs within each rendering mode.

Modes:
  1. Vanilla reference extraction:
     python3 analyze_italic_pairs.py <screenshot.png> --vanilla --scale <2|3> [--save-ref vanilla_ref.json]
     Measures Delta_vanilla_top and Delta_vanilla_bottom from a real vanilla Minecraft tellraw screenshot.

  2. BetterHud verification:
     python3 analyze_italic_pairs.py <screenshot.png> --scale <2|3> [--ref-json vanilla_ref.json]
     Measures pairs across rows, compares against Delta_vanilla, and outputs a verification table with PASS/FAIL.
"""

import sys
import os
import json
import argparse
import numpy as np
from PIL import Image

def color_distance(c1, c2):
    """Euclidean distance in RGB space."""
    return np.sqrt(np.sum((c1.astype(float) - c2.astype(float)) ** 2, axis=-1))

def extract_color_mask(img_np, target_hex, tolerance=40.0):
    """
    Extracts binary mask for pixels matching target_hex color within tolerance.
    target_hex: e.g. '#3498DB'
    """
    target_rgb = np.array([int(target_hex[i:i+2], 16) for i in (1, 3, 5)])
    dist = color_distance(img_np[:, :, :3], target_rgb)
    return dist <= tolerance

def find_connected_components(mask, min_pixels=8):
    """
    Finds bounding boxes and pixel coordinates of connected components in binary mask.
    """
    from scipy.ndimage import label, find_objects
    labeled_array, num_features = label(mask)
    components = []
    
    slices = find_objects(labeled_array)
    for idx, sl in enumerate(slices):
        comp_mask = (labeled_array[sl] == (idx + 1))
        pixel_count = np.count_nonzero(comp_mask)
        if pixel_count < min_pixels:
            continue
            
        y_slice, x_slice = sl
        y_indices, x_indices = np.where(comp_mask)
        abs_y = y_indices + y_slice.start
        abs_x = x_indices + x_slice.start
        
        y_min, y_max = abs_y.min(), abs_y.max()
        x_min, x_max = abs_x.min(), abs_x.max()
        
        # Mean X of topmost row
        top_row_x = abs_x[abs_y == y_min]
        top_x = float(np.mean(top_row_x))
        
        # Mean X of bottommost row
        bottom_row_x = abs_x[abs_y == y_max]
        bottom_x = float(np.mean(bottom_row_x))
        
        components.append({
            'y_min': int(y_min),
            'y_max': int(y_max),
            'x_min': int(x_min),
            'x_max': int(x_max),
            'top_x': top_x,
            'bottom_x': bottom_x,
            'height': int(y_max - y_min + 1),
            'width': int(x_max - x_min + 1),
            'pixel_count': int(pixel_count)
        })
        
    return components

def find_glyph_bounds(mask):
    """
    Finds bounding box and top/bottom edge X coordinates from binary mask (single glyph).
    """
    y_indices, x_indices = np.where(mask)
    if len(y_indices) == 0:
        return None
    y_min, y_max = y_indices.min(), y_indices.max()
    x_min, x_max = x_indices.min(), x_indices.max()
    
    top_row_x = x_indices[y_indices == y_min]
    top_x = float(np.mean(top_row_x))
    
    bottom_row_x = x_indices[y_indices == y_max]
    bottom_x = float(np.mean(bottom_row_x))
    
    return {
        'y_min': int(y_min),
        'y_max': int(y_max),
        'x_min': int(x_min),
        'x_max': int(x_max),
        'top_x': top_x,
        'bottom_x': bottom_x,
        'height': int(y_max - y_min + 1),
        'width': int(x_max - x_min + 1)
    }

def analyze_pair(regular_bounds_or_mask, italic_bounds_or_mask, nominal_spacing_gui=20.0, gui_scale=3, ref_top=1.0, ref_bottom=-1.0):
    """
    Measures Delta_top and Delta_bottom for a single pair of Regular and Italic glyphs.
    Can accept either pre-computed bounds dicts or binary numpy masks.
    """
    if isinstance(regular_bounds_or_mask, dict):
        reg_bounds = regular_bounds_or_mask
    else:
        reg_bounds = find_glyph_bounds(regular_bounds_or_mask)
        
    if isinstance(italic_bounds_or_mask, dict):
        italic_bounds = italic_bounds_or_mask
    else:
        italic_bounds = find_glyph_bounds(italic_bounds_or_mask)
    
    if reg_bounds is None or italic_bounds is None:
        return None
    
    # Distance in physical pixels
    dx_top_px = italic_bounds['top_x'] - reg_bounds['top_x']
    dx_bottom_px = italic_bounds['bottom_x'] - reg_bounds['bottom_x']
    
    # Convert to GUI pixels and subtract nominal spacing
    delta_top = (dx_top_px / float(gui_scale)) - nominal_spacing_gui
    delta_bottom = (dx_bottom_px / float(gui_scale)) - nominal_spacing_gui
    
    err_top = abs(delta_top - ref_top)
    err_bottom = abs(delta_bottom - ref_bottom)
    max_err = max(err_top, err_bottom)
    
    tolerance = 0.25
    verdict = "PASS" if max_err <= tolerance else "FAIL"
    
    return {
        'delta_top': delta_top,
        'delta_bottom': delta_bottom,
        'ref_top': ref_top,
        'ref_bottom': ref_bottom,
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
    parser.add_argument("--scale", type=int, default=3, choices=[2, 3, 4], help="Minecraft GUI Scale (default: 3)")
    parser.add_argument("--vanilla", action="store_true", help="Vanilla mode: measures reference Delta_top and Delta_bottom from vanilla tellraw")
    parser.add_argument("--save-ref", type=str, default="vanilla_reference.json", help="File to save vanilla reference measurements")
    parser.add_argument("--ref-json", type=str, default=None, help="File to load vanilla reference from")
    parser.add_argument("--ref-top", type=float, default=1.0, help="Explicit reference Delta_top (default: 1.0)")
    parser.add_argument("--ref-bottom", type=float, default=-1.0, help="Explicit reference Delta_bottom (default: -1.0)")
    parser.add_argument("--spacing", type=float, default=20.0, help="Nominal spacing between pair in GUI pixels")
    args = parser.parse_args()

    if not os.path.exists(args.screenshot):
        print(f"Error: Screenshot '{args.screenshot}' not found.")
        sys.exit(1)

    img = Image.open(args.screenshot).convert("RGB")
    img_np = np.array(img)
    print(f"[+] Loaded screenshot: {args.screenshot} ({img.size[0]}x{img.size[1]}), GUI Scale: {args.scale}")

    # Extract Blue (#3498DB) and Red (#E74C3C) glyph masks
    blue_mask = extract_color_mask(img_np, "#3498DB", tolerance=45.0)
    red_mask = extract_color_mask(img_np, "#E74C3C", tolerance=45.0)

    try:
        blue_comps = find_connected_components(blue_mask, min_pixels=6 * args.scale)
        red_comps = find_connected_components(red_mask, min_pixels=6 * args.scale)
    except ImportError:
        # Fallback if scipy is not installed
        print("[-] Note: scipy not found, performing full-mask analysis.")
        blue_bounds = find_glyph_bounds(blue_mask)
        red_bounds = find_glyph_bounds(red_mask)
        blue_comps = [blue_bounds] if blue_bounds else []
        red_comps = [red_bounds] if red_bounds else []

    print(f"[+] Detected glyph candidates: {len(blue_comps)} regular (Blue), {len(red_comps)} italic (Red)")

    if len(blue_comps) == 0 or len(red_comps) == 0:
        print("[-] No matching glyph pairs found. Check that colors #3498DB (Blue) and #E74C3C (Red) are visible.")
        sys.exit(1)

    # If --ref-json is provided, load references from it
    ref_top = args.ref_top
    ref_bottom = args.ref_bottom
    if args.ref_json and os.path.exists(args.ref_json):
        with open(args.ref_json, 'r') as f:
            ref_data = json.load(f)
            ref_top = float(ref_data.get("delta_vanilla_top", ref_top))
            ref_bottom = float(ref_data.get("delta_vanilla_bottom", ref_bottom))
            print(f"[+] Loaded vanilla reference from {args.ref_json}: top={ref_top:+.3f}, bottom={ref_bottom:+.3f}")

    # Pair each blue glyph with the nearest red glyph on the same row (within 4 GUI pixels vertically)
    max_y_diff = 4 * args.scale
    pairs = []
    used_red = set()

    # Sort top to bottom
    blue_comps.sort(key=lambda c: c['y_min'])
    for b in blue_comps:
        best_r = None
        best_dist = float('inf')
        for r_idx, r in enumerate(red_comps):
            if r_idx in used_red:
                continue
            if abs(b['y_min'] - r['y_min']) <= max_y_diff:
                # Red must be to the right of Blue
                if r['x_min'] > b['x_min']:
                    dist = r['x_min'] - b['x_min']
                    if dist < best_dist:
                        best_dist = dist
                        best_r = (r_idx, r)
        if best_r:
            used_red.add(best_r[0])
            pairs.append((b, best_r[1]))

    print(f"[+] Paired {len(pairs)} rows.")

    if args.vanilla:
        # Measure reference from vanilla screenshot
        vanilla_results = []
        for idx, (b, r) in enumerate(pairs):
            dx_top = (r['top_x'] - b['top_x']) / float(args.scale)
            dx_bottom = (r['bottom_x'] - b['bottom_x']) / float(args.scale)
            # In tellraw with 5 spaces, gap is known; difference between top and bottom gives exact slant!
            slant = dx_top - dx_bottom
            midline = (dx_top + dx_bottom) / 2.0
            vanilla_results.append({
                'row': idx + 1,
                'dx_top': dx_top,
                'dx_bottom': dx_bottom,
                'slant': slant,
                'midline': midline
            })
        
        # Calculate mean top and bottom shear
        mean_slant = float(np.mean([res['slant'] for res in vanilla_results]))
        # Vanilla formula gives slant = 2.00 px (top = +1.00, bottom = -1.00)
        measured_top = mean_slant / 2.0
        measured_bottom = -mean_slant / 2.0

        print("\n=== VANILLA SCREEN REFERENCE MEASUREMENTS ===")
        print(f"Detected {len(pairs)} reference row(s):")
        for res in vanilla_results:
            print(f"  Row {res['row']}: dx_top={res['dx_top']:.3f} px, dx_bottom={res['dx_bottom']:.3f} px, slant={res['slant']:.3f} px")
        print(f"\nMeasured Vanilla Reference:")
        print(f"  Delta_vanilla_top    = {measured_top:+.3f} GUI px")
        print(f"  Delta_vanilla_bottom = {measured_bottom:+.3f} GUI px")
        print(f"  Total Slant          = {mean_slant:.3f} GUI px")

        ref_output = {
            "gui_scale": args.scale,
            "delta_vanilla_top": measured_top,
            "delta_vanilla_bottom": measured_bottom,
            "total_slant": mean_slant,
            "rows_measured": len(vanilla_results)
        }
        with open(args.save_ref, 'w') as f:
            json.dump(ref_output, f, indent=2)
        print(f"[+] Saved vanilla reference to '{args.save_ref}'")
        return

    # BetterHud verification mode
    print("\n" + "=" * 90)
    print(f"  BETTERHUD CLIENT ITALIC VERIFICATION TABLE (Tolerance: +/-0.25 GUI-px)")
    print(f"  Reference: Delta_vanilla_top = {ref_top:+.2f} px, Delta_vanilla_bottom = {ref_bottom:+.2f} px")
    print("=" * 90)
    header = f"| {'Row':^5} | {'Y (approx)':^10} | {'Delta_top':^11} | {'Delta_bottom':^12} | {'Err_top':^9} | {'Err_bot':^9} | {'Max Err':^9} | {'Verdict':^7} |"
    sep = f"|{'-'*7}|{'-'*12}|{'-'*13}|{'-'*14}|{'-'*11}|{'-'*11}|{'-'*11}|{'-'*9}|"
    print(header)
    print(sep)

    all_passed = True
    for idx, (b, r) in enumerate(pairs):
        # Dynamically determine nominal spacing from bounds if not fixed
        measured_spacing_gui = (r['x_min'] - b['x_min']) / float(args.scale)
        # Round spacing to nearest integer GUI pixels (nominal gap)
        nominal_spacing = round(measured_spacing_gui)
        
        res = analyze_pair(b, r, nominal_spacing_gui=nominal_spacing, gui_scale=args.scale, ref_top=ref_top, ref_bottom=ref_bottom)
        if res is None:
            continue
        if res['verdict'] != "PASS":
            all_passed = False
            
        row_str = f"| {idx+1:^5} | {b['y_min']:^10} | {res['delta_top']:^+11.3f} | {res['delta_bottom']:^+12.3f} | {res['err_top']:^9.3f} | {res['err_bottom']:^9.3f} | {res['max_err']:^9.3f} | {res['verdict']:^7} |"
        print(row_str)

    print("=" * 90)
    if all_passed:
        print("[+] VERDICT: ALL PAIRS MATCH VANILLA REFERENCE WITHIN 0.25 GUI-PX (PASS)")
    else:
        print("[-] VERDICT: DISCREPANCY EXCEEDS 0.25 GUI-PX TOLERANCE ON ONE OR MORE ROWS (FAIL)")

if __name__ == "__main__":
    main()
