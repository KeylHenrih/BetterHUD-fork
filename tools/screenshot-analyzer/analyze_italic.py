#!/usr/bin/env python3
"""
BetterHUD Italic Shear & Alignment Screenshot Analyzer
Analyzes Minecraft screenshots of /hud debug-popup debug_italic_popup hold.
Measures the distance from the reference solid glyph '█' to the text in Server
and Client columns across heights Y in {-100, 0, 50, 200} for styles:
normal, italic, bold, and bold+italic.
"""

import sys
import os
import argparse
from typing import List, Tuple, Dict, Optional
from PIL import Image, ImageDraw

def hex_to_rgb(hex_str: str) -> Tuple[int, int, int]:
    clean = hex_str.lstrip('#')
    if len(clean) == 3:
        clean = ''.join(c * 2 for c in clean)
    return tuple(int(clean[i:i+2], 16) for i in (0, 2, 4))

def color_dist(c1: Tuple[int, int, int], c2: Tuple[int, int, int]) -> float:
    return max(abs(c1[0] - c2[0]), abs(c1[1] - c2[1]), abs(c1[2] - c2[2]))

def is_color_match(pixel: Tuple[int, int, int], target: Tuple[int, int, int], tol: int = 35) -> bool:
    return color_dist(pixel[:3], target) <= tol

def find_connected_components(mask: List[List[bool]], w: int, h: int) -> List[Tuple[int, int, int, int]]:
    """Finds bounding boxes (min_x, min_y, max_x, max_y) of connected components."""
    visited = [[False] * w for _ in range(h)]
    boxes = []

    for y in range(h):
        for x in range(w):
            if mask[y][x] and not visited[y][x]:
                min_x, max_x = x, x
                min_y, max_y = y, y
                queue = [(x, y)]
                visited[y][x] = True
                
                while queue:
                    cx, cy = queue.pop()
                    if cx < min_x: min_x = cx
                    if cx > max_x: max_x = cx
                    if cy < min_y: min_y = cy
                    if cy > max_y: max_y = cy

                    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < w and 0 <= ny < h and mask[ny][nx] and not visited[ny][nx]:
                            visited[ny][nx] = True
                            queue.append((nx, ny))
                
                boxes.append((min_x, min_y, max_x, max_y))
    return boxes

def analyze_screenshot(
    image_path: str,
    gui_scale: Optional[int] = None,
    tolerance: float = 0.5,
    marker_color_hex: str = "#FFFF00",
    text_color_hex: str = "#FFFFFF",
    debug_out_path: Optional[str] = None
) -> Tuple[bool, List[Dict]]:
    img = Image.open(image_path).convert('RGB')
    w, h = img.size
    pixels = img.load()

    marker_rgb = hex_to_rgb(marker_color_hex)
    text_rgb = hex_to_rgb(text_color_hex)

    # 1. Create binary mask of marker pixels
    marker_mask = [[is_color_match(pixels[x, y], marker_rgb) for x in range(w)] for y in range(h)]
    marker_boxes = find_connected_components(marker_mask, w, h)

    # Filter marker boxes by aspect ratio (should be approximately square glyph █)
    valid_markers = []
    for b in marker_boxes:
        bw = b[2] - b[0] + 1
        bh = b[3] - b[1] + 1
        # In Minecraft font, █ is 8x8 GUI px
        if bw >= 6 and bh >= 6 and 0.6 <= (bw / float(bh)) <= 1.6:
            valid_markers.append(b)

    if not valid_markers:
        print(f"Error: No valid reference markers ('█', color {marker_color_hex}) found in image.", file=sys.stderr)
        return False, []

    # Estimate GUI scale if not specified
    if gui_scale is None:
        widths = sorted([b[2] - b[0] + 1 for b in valid_markers])
        median_width = widths[len(widths) // 2]
        gui_scale = max(1, round(median_width / 8.0))
        print(f"Auto-detected GUI Scale: {gui_scale} (from marker width {median_width}px / 8)")
    else:
        print(f"Using specified GUI Scale: {gui_scale}")

    # 2. Split markers into Server (left half) and Client (right half) columns
    valid_markers.sort(key=lambda b: (b[0], b[1]))
    mid_x = w / 2.0
    server_markers = [b for b in valid_markers if (b[0] + b[2]) / 2.0 < mid_x]
    client_markers = [b for b in valid_markers if (b[0] + b[2]) / 2.0 >= mid_x]

    # Sort each column vertically by Y
    server_markers.sort(key=lambda b: (b[1] + b[3]) / 2.0)
    client_markers.sort(key=lambda b: (b[1] + b[3]) / 2.0)

    # 3. For each marker, detect the leading edge of the text to its right
    def measure_text_offset(marker_box: Tuple[int, int, int, int]) -> Optional[float]:
        mx1, my1, mx2, my2 = marker_box
        row_height = my2 - my1 + 1
        search_min_y = max(0, my1 - int(row_height * 0.5))
        search_max_y = min(h - 1, my2 + int(row_height * 0.5))
        # Search rightwards from marker right edge
        search_start_x = mx2 + 1
        search_end_x = min(w - 1, search_start_x + int(row_height * 20))

        min_text_x = None
        for x in range(search_start_x, search_end_x):
            for y in range(search_min_y, search_max_y + 1):
                if is_color_match(pixels[x, y], text_rgb):
                    min_text_x = x
                    break
            if min_text_x is not None:
                break
        
        if min_text_x is None:
            return None
        # Distance from marker left edge (min_x) to text left edge
        return float(min_text_x - mx1)

    # We expect 16 rows: 4 heights (-100, 0, 50, 200) x 4 styles
    height_labels = [-100, 0, 50, 200]
    style_labels = ["normal", "italic", "bold", "bold+italic"]

    results = []
    all_passed = True

    # Cluster server and client markers into corresponding rows
    matched_rows = min(len(server_markers), len(client_markers))
    if matched_rows < 4:
        print(f"Warning: Expected at least 4 rows, found server={len(server_markers)}, client={len(client_markers)}")

    draw = None
    if debug_out_path:
        out_img = img.copy()
        draw = ImageDraw.Draw(out_img)

    for i in range(matched_rows):
        sm = server_markers[i]
        cm = client_markers[i]

        s_dist = measure_text_offset(sm)
        c_dist = measure_text_offset(cm)

        y_idx = min(i // 4, len(height_labels) - 1)
        s_idx = i % 4
        y_label = height_labels[y_idx] if i < 16 else f"row_{i}"
        style_label = style_labels[s_idx] if i < 16 else f"style_{s_idx}"

        if s_dist is None or c_dist is None:
            discrepancy_gui = float('nan')
            status = "FAIL (No text)"
            all_passed = False
        else:
            diff_px = abs(c_dist - s_dist)
            discrepancy_gui = diff_px / float(gui_scale)
            if discrepancy_gui <= tolerance:
                status = "PASS"
            else:
                status = "FAIL"
                all_passed = False

        row_info = {
            "index": i,
            "y": y_label,
            "style": style_label,
            "server_dist": s_dist,
            "client_dist": c_dist,
            "error_gui": discrepancy_gui,
            "status": status
        }
        results.append(row_info)

        if draw:
            # Draw marker boxes and text pointers
            draw.rectangle(sm, outline=(255, 255, 0), width=2)
            draw.rectangle(cm, outline=(255, 255, 0), width=2)
            if s_dist is not None:
                draw.line([(sm[0] + s_dist, sm[1]), (sm[0] + s_dist, sm[3])], fill=(0, 255, 0), width=2)
            if c_dist is not None:
                draw.line([(cm[0] + c_dist, cm[1]), (cm[0] + c_dist, cm[3])], fill=(0, 255, 0), width=2)

    if debug_out_path and draw:
        out_img.save(debug_out_path)
        print(f"Annotated debug image saved to: {debug_out_path}")

    return all_passed, results

def print_report(image_path: str, tolerance: float, all_passed: bool, results: List[Dict]):
    print("================================================================================")
    print("                    BETTERHUD ITALIC SHEAR ANALYZER")
    print("================================================================================")
    print(f"Target Screenshot : {image_path}")
    print(f"Allowed Tolerance : {tolerance:.2f} GUI pixels")
    print(f"Analyzed Rows     : {len(results)}")
    print("--------------------------------------------------------------------------------")
    print(f"{'Height Y':<10} | {'Style':<14} | {'Server Dist (px)':<18} | {'Client Dist (px)':<18} | {'Error (GUI px)':<15} | {'Status'}")
    print("-" * 92)

    for r in results:
        s_str = f"{r['server_dist']:.1f}" if r['server_dist'] is not None else "N/A"
        c_str = f"{r['client_dist']:.1f}" if r['client_dist'] is not None else "N/A"
        e_str = f"{r['error_gui']:.3f}" if not (r['error_gui'] != r['error_gui']) else "N/A"
        print(f"{str(r['y']):<10} | {r['style']:<14} | {s_str:<18} | {c_str:<18} | {e_str:<15} | {r['status']}")

    print("================================================================================")
    if all_passed and results:
        print(f"Final Result: PASS (All {len(results)} rows match within {tolerance:.2f} GUI px tolerance)")
    else:
        print("Final Result: FAIL (Discrepancy exceeds tolerance or elements not detected)")
    print("================================================================================")

def main():
    parser = argparse.ArgumentParser(description="Analyze BetterHUD italic shear alignment in screenshots.")
    parser.add_argument("--image", "-i", required=True, help="Path to screenshot image (PNG)")
    parser.add_argument("--gui-scale", "-s", type=int, default=None, help="Minecraft GUI Scale (auto-detected if omitted)")
    parser.add_argument("--tolerance", "-t", type=float, default=0.5, help="Discrepancy tolerance in GUI pixels (default: 0.5)")
    parser.add_argument("--marker-color", default="#FFFF00", help="Hex color of reference glyph █ (default: #FFFF00)")
    parser.add_argument("--text-color", default="#FFFFFF", help="Hex color of text (default: #FFFFFF)")
    parser.add_argument("--debug-out", default=None, help="Optional output path to save annotated verification image")

    args = parser.parse_args()

    if not os.path.isfile(args.image):
        print(f"Error: Screenshot file '{args.image}' not found.", file=sys.stderr)
        sys.exit(2)

    passed, results = analyze_screenshot(
        image_path=args.image,
        gui_scale=args.gui_scale,
        tolerance=args.tolerance,
        marker_color_hex=args.marker_color,
        text_color_hex=args.text_color,
        debug_out_path=args.debug_out
    )

    print_report(args.image, args.tolerance, passed, results)
    sys.exit(0 if (passed and results) else 1)

if __name__ == "__main__":
    main()
