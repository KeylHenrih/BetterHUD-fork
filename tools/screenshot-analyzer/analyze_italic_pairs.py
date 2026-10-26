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

  3. Synthetic test validation:
     python3 analyze_italic_pairs.py --test-synthetic
     Validates the analyzer algorithms against BakedSheetGlyph mathematical geometry (scales 2 and 3).
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
    Finds bounding boxes and pixel coordinates of connected components in binary mask
    using pure Python / NumPy BFS (zero external scipy dependency).
    """
    visited = np.zeros_like(mask, dtype=bool)
    components = []
    
    ys, xs = np.where(mask)
    for y, x in zip(ys, xs):
        if visited[y, x]:
            continue
        # BFS traversal
        q = [(y, x)]
        visited[y, x] = True
        comp_y = []
        comp_x = []
        while q:
            cy, cx = q.pop()
            comp_y.append(cy)
            comp_x.append(cx)
            for dy, dx in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                ny, nx = cy + dy, cx + dx
                if 0 <= ny < mask.shape[0] and 0 <= nx < mask.shape[1]:
                    if mask[ny, nx] and not visited[ny, nx]:
                        visited[ny, nx] = True
                        q.append((ny, nx))
                        
        if len(comp_y) < min_pixels:
            continue
            
        comp_y = np.array(comp_y)
        comp_x = np.array(comp_x)
        y_min, y_max = int(comp_y.min()), int(comp_y.max())
        x_min, x_max = int(comp_x.min()), int(comp_x.max())
        
        # Exact edge coordinates (top row and bottom row)
        top_row_x = comp_x[comp_y == y_min]
        bot_row_x = comp_x[comp_y == y_max]
        top_x = float(np.min(top_row_x))
        bottom_x = float(np.min(bot_row_x))
        
        components.append({
            'y_min': y_min,
            'y_max': y_max,
            'x_min': x_min,
            'x_max': x_max,
            'top_x': top_x,
            'bottom_x': bottom_x,
            'height': int(y_max - y_min + 1),
            'width': int(x_max - x_min + 1),
            'pixel_count': len(comp_y)
        })
        
    return components

def find_glyph_bounds(mask):
    """
    Finds bounding box and top/bottom edge X coordinates from binary mask (single glyph).
    """
    y_indices, x_indices = np.where(mask)
    if len(y_indices) == 0:
        return None
    y_min, y_max = int(y_indices.min()), int(y_indices.max())
    x_min, x_max = int(x_indices.min()), int(x_indices.max())
    
    top_row_x = x_indices[y_indices == y_min]
    top_x = float(np.min(top_row_x))
    
    bottom_row_x = x_indices[y_indices == y_max]
    bottom_x = float(np.min(bottom_row_x))
    
    return {
        'y_min': y_min,
        'y_max': y_max,
        'x_min': x_min,
        'x_max': x_max,
        'top_x': top_x,
        'bottom_x': bottom_x,
        'height': int(y_max - y_min + 1),
        'width': int(x_max - x_min + 1),
        'pixel_count': len(y_indices)
    }

def analyze_pair(reg_bounds, italic_bounds, nominal_spacing_gui=20.0, gui_scale=3, ref_top=1.0, ref_bottom=-1.0):
    """
    Measures Delta_top and Delta_bottom for a single pair of Regular and Italic glyphs.
    """
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
        'dx_top_px': dx_top_px,
        'dx_bottom_px': dx_bottom_px,
        'ref_top': ref_top,
        'ref_bottom': ref_bottom,
        'err_top': err_top,
        'err_bottom': err_bottom,
        'max_err': max_err,
        'verdict': verdict,
        'reg_bounds': reg_bounds,
        'italic_bounds': italic_bounds
    }

def run_synthetic_tests():
    """
    Runs self-test with synthetic BakedSheetGlyph bitmaps at scale 2 and 3.
    """
    print("=== RUNNING SYNTHETIC BAKEDSHEETGLYPH TESTS ===")
    for scale in [2, 3]:
        h_px = 8 * scale
        w_px = 8 * scale
        nominal_gap_gui = 15.0
        gap_px = int(nominal_gap_gui * scale)
        
        # Regular block at (x=10, y=10)
        reg_x0 = 10
        reg_y0 = 10
        reg_bounds = {
            'y_min': reg_y0,
            'y_max': reg_y0 + h_px - 1,
            'x_min': reg_x0,
            'x_max': reg_x0 + w_px - 1,
            'top_x': float(reg_x0),
            'bottom_x': float(reg_x0),
            'height': h_px,
            'width': w_px,
            'pixel_count': h_px * w_px
        }
        
        # Italic block: top shifted right by +1.0 GUI px (+scale px), bottom shifted left by -1.0 GUI px (-scale px)
        ita_base_x = reg_x0 + gap_px
        ita_top_x = ita_base_x + scale
        ita_bot_x = ita_base_x - scale
        ita_bounds = {
            'y_min': reg_y0,
            'y_max': reg_y0 + h_px - 1,
            'x_min': ita_bot_x,
            'x_max': ita_top_x + w_px - 1,
            'top_x': float(ita_top_x),
            'bottom_x': float(ita_bot_x),
            'height': h_px,
            'width': int((ita_top_x + w_px - 1) - ita_bot_x + 1),
            'pixel_count': h_px * w_px
        }
        
        res = analyze_pair(reg_bounds, ita_bounds, nominal_spacing_gui=nominal_gap_gui, gui_scale=scale)
        assert res is not None, f"Failed at scale {scale}"
        assert abs(res['delta_top'] - 1.0) < 1e-4, f"Scale {scale} delta_top mismatch: {res['delta_top']}"
        assert abs(res['delta_bottom'] - (-1.0)) < 1e-4, f"Scale {scale} delta_bottom mismatch: {res['delta_bottom']}"
        assert res['verdict'] == "PASS", f"Scale {scale} verdict not PASS"
        print(f"  [+] GUI Scale {scale}: Delta_top={res['delta_top']:+.3f}, Delta_bottom={res['delta_bottom']:+.3f} -> PASS")
        
    print("[+] All synthetic BakedSheetGlyph geometry tests passed successfully!\n")

def main():
    parser = argparse.ArgumentParser(description="Analyze Italic Shear Delta in Minecraft screenshots.")
    parser.add_argument("screenshot", nargs="?", default=None, help="Path to screenshot image")
    parser.add_argument("--scale", type=int, default=3, choices=[2, 3, 4], help="Minecraft GUI Scale (default: 3)")
    parser.add_argument("--vanilla", action="store_true", help="Vanilla mode: measures reference Delta_top and Delta_bottom from vanilla tellraw")
    parser.add_argument("--save-ref", type=str, default="vanilla_reference.json", help="File to save vanilla reference measurements")
    parser.add_argument("--ref-json", type=str, default=None, help="File to load vanilla reference from")
    parser.add_argument("--ref-top", type=float, default=1.0, help="Explicit reference Delta_top (default: 1.0)")
    parser.add_argument("--ref-bottom", type=float, default=-1.0, help="Explicit reference Delta_bottom (default: -1.0)")
    parser.add_argument("--test-synthetic", action="store_true", help="Run self-tests on synthetic BakedSheetGlyph bitmaps")
    args = parser.parse_args()

    if args.test_synthetic:
        run_synthetic_tests()
        if not args.screenshot:
            return

    if not args.screenshot:
        parser.print_help()
        sys.exit(1)

    if not os.path.exists(args.screenshot):
        print(f"Error: Screenshot '{args.screenshot}' not found.")
        sys.exit(1)

    img = Image.open(args.screenshot).convert("RGB")
    img_np = np.array(img)
    print(f"[+] Loaded screenshot: {args.screenshot} ({img.size[0]}x{img.size[1]}), GUI Scale: {args.scale}")

    # Extract Blue (#3498DB) and Red (#E74C3C) glyph masks
    blue_mask = extract_color_mask(img_np, "#3498DB", tolerance=45.0)
    red_mask = extract_color_mask(img_np, "#E74C3C", tolerance=45.0)

    blue_comps = find_connected_components(blue_mask, min_pixels=6 * args.scale)
    red_comps = find_connected_components(red_mask, min_pixels=6 * args.scale)

    # Filter candidates for solid probe glyphs (height around 8 * scale)
    solid_blue = [c for c in blue_comps if abs(c["height"] - 8 * args.scale) <= 2 and c["width"] >= 6 * args.scale]
    solid_red = [c for c in red_comps if abs(c["height"] - 8 * args.scale) <= 2 and c["width"] >= 6 * args.scale]
    
    # Fallback to all components if no solid glyphs found
    b_pool = solid_blue if len(solid_blue) > 0 else blue_comps
    r_pool = solid_red if len(solid_red) > 0 else red_comps

    print(f"[+] Detected glyph candidates: {len(b_pool)} regular (Blue), {len(r_pool)} italic (Red)")

    if len(b_pool) == 0 or len(r_pool) == 0:
        print("[-] No matching glyph pairs found. Check that colors #3498DB (Blue) and #E74C3C (Red) are visible.")
        sys.exit(1)

    # If --ref-json is provided, load references and enforce GUI scale matching
    ref_top = args.ref_top
    ref_bottom = args.ref_bottom
    if args.ref_json and os.path.exists(args.ref_json):
        with open(args.ref_json, 'r') as f:
            ref_data = json.load(f)
            ref_scale = ref_data.get("gui_scale")
            if ref_scale is not None and ref_scale != args.scale:
                print(f"[-] ERROR: GUI Scale mismatch! Reference was measured at scale {ref_scale}, but analyzer requested scale {args.scale}.")
                print(f"[-] Please run with --scale {ref_scale} to match reference.")
                sys.exit(1)
            ref_top = float(ref_data.get("delta_vanilla_top", ref_top))
            ref_bottom = float(ref_data.get("delta_vanilla_bottom", ref_bottom))
            print(f"[+] Loaded vanilla reference from {args.ref_json}: top={ref_top:+.3f}, bottom={ref_bottom:+.3f} (GUI Scale: {ref_scale})")

    # Pair each blue glyph with the nearest red glyph on the same row (within 4 GUI pixels vertically)
    max_y_diff = 4 * args.scale
    pairs = []
    used_red = set()

    # Sort top to bottom
    b_pool.sort(key=lambda c: c['y_min'])
    for b in b_pool:
        best_r = None
        best_dist = float('inf')
        for r_idx, r in enumerate(r_pool):
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
        vanilla_results = []
        for idx, (b, r) in enumerate(pairs):
            dx_top = (r['top_x'] - b['top_x']) / float(args.scale)
            dx_bottom = (r['bottom_x'] - b['bottom_x']) / float(args.scale)
            slant = dx_top - dx_bottom
            midline = (dx_top + dx_bottom) / 2.0
            vanilla_results.append({
                'row': idx + 1,
                'raw_reg': f"{b['top_x']:.1f} / {b['bottom_x']:.1f}",
                'raw_ita': f"{r['top_x']:.1f} / {r['bottom_x']:.1f}",
                'dx_top': dx_top,
                'dx_bottom': dx_bottom,
                'slant': slant,
                'midline': midline
            })
        
        mean_slant = float(np.mean([res['slant'] for res in vanilla_results]))
        measured_top = mean_slant / 2.0
        measured_bottom = -mean_slant / 2.0

        print("\n=== VANILLA SCREEN REFERENCE MEASUREMENTS ===")
        print(f"Detected {len(pairs)} reference row(s):")
        for res in vanilla_results:
            print(f"  Row {res['row']}: Reg[{res['raw_reg']}] Ita[{res['raw_ita']}] | dx_top={res['dx_top']:.3f} px, dx_bottom={res['dx_bottom']:.3f} px, slant={res['slant']:.3f} px")
        print(f"\nMeasured Vanilla Reference:")
        print(f"  Delta_vanilla_top    = {measured_top:+.3f} GUI px")
        print(f"  Delta_vanilla_bottom = {measured_bottom:+.3f} GUI px")
        print(f"  Total Slant          = {mean_slant:.3f} GUI px")

        ref_output = {
            "gui_scale": args.scale,
            "delta_vanilla_top": measured_top,
            "delta_vanilla_bottom": measured_bottom,
            "total_slant": mean_slant,
            "rows_measured": len(vanilla_results),
            "screenshot": os.path.abspath(args.screenshot)
        }
        with open(args.save_ref, 'w') as f:
            json.dump(ref_output, f, indent=2)
        print(f"[+] Saved vanilla reference to '{args.save_ref}'")
        return

    # BetterHud verification mode
    print("\n" + "=" * 120)
    print(f"  BETTERHUD CLIENT ITALIC VERIFICATION TABLE (Tolerance: +/-0.25 GUI-px)")
    print(f"  Reference: Delta_vanilla_top = {ref_top:+.2f} px, Delta_vanilla_bottom = {ref_bottom:+.2f} px")
    print("=" * 120)
    col_fmt = "| {:^5} | {:^10} | {:<17} | {:<17} | {:^11} | {:^12} | {:^9} | {:^9} | {:^7} |"
    sep = f"|{'-'*7}|{'-'*12}|{'-'*19}|{'-'*19}|{'-'*13}|{'-'*14}|{'-'*11}|{'-'*11}|{'-'*9}|"
    print(col_fmt.format("Row", "Y (approx)", "Raw Reg (top/bot)", "Raw Ita (top/bot)", "Delta_top", "Delta_bottom", "Err_top", "Err_bot", "Verdict"))
    print(sep)

    all_passed = True
    for idx, (b, r) in enumerate(pairs):
        dx_top = (r['top_x'] - b['top_x']) / float(args.scale)
        dx_bot = (r['bottom_x'] - b['bottom_x']) / float(args.scale)
        nominal_spacing = round((dx_top + dx_bot) / 2.0)
        
        res = analyze_pair(b, r, nominal_spacing_gui=nominal_spacing, gui_scale=args.scale, ref_top=ref_top, ref_bottom=ref_bottom)
        if res is None:
            continue
        if res['verdict'] != "PASS":
            all_passed = False
            
        reg_s = f"{b['top_x']:.1f} / {b['bottom_x']:.1f}"
        ita_s = f"{r['top_x']:.1f} / {r['bottom_x']:.1f}"
        row_str = col_fmt.format(idx+1, b['y_min'], reg_s, ita_s, f"{res['delta_top']:+.3f}", f"{res['delta_bottom']:+.3f}", f"{res['err_top']:.3f}", f"{res['err_bottom']:.3f}", res['verdict'])
        print(row_str)

    print("=" * 120)
    if all_passed:
        print("[+] VERDICT: ALL PAIRS MATCH VANILLA REFERENCE WITHIN 0.25 GUI-PX (PASS)")
    else:
        print("[-] VERDICT: DISCREPANCY EXCEEDS 0.25 GUI-PX TOLERANCE ON ONE OR MORE ROWS (FAIL)")

if __name__ == "__main__":
    main()
