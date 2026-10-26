#!/usr/bin/env python3
"""
Test suite for analyze_italic.py using synthetic images.
"""

import os
import unittest
import tempfile
from PIL import Image, ImageDraw
from analyze_italic import analyze_screenshot

class TestAnalyzeItalic(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()

    def create_synthetic_image(self, shift_server: int = 48, shift_client: int = 48, shear_bug_at_200: int = 0) -> str:
        # 1920x1080 canvas, dark background (RGB 20, 20, 20)
        img = Image.new('RGB', (1920, 1080), color=(20, 20, 20))
        draw = ImageDraw.Draw(img)

        gui_scale = 3
        marker_size = 8 * gui_scale # 24x24 px
        server_x = 400
        client_x = 1200

        # 4 heights: -100, 0, 50, 200 (mapped to canvas Y coordinates)
        heights_y = [150, 350, 550, 800]

        for h_idx, base_y in enumerate(heights_y):
            for s_idx in range(4): # 4 styles: normal, italic, bold, bold+italic
                row_y = base_y + s_idx * 35

                # Server Column
                draw.rectangle([server_x, row_y, server_x + marker_size - 1, row_y + marker_size - 1], fill=(255, 255, 0))
                # Text block (white) at shift_server
                text_x_server = server_x + shift_server
                draw.rectangle([text_x_server, row_y + 4, text_x_server + 60, row_y + marker_size - 4], fill=(255, 255, 255))

                # Client Column
                cur_client_shift = shift_client
                if h_idx == 3 and s_idx == 1: # italic at y=200
                    cur_client_shift += shear_bug_at_200

                draw.rectangle([client_x, row_y, client_x + marker_size - 1, row_y + marker_size - 1], fill=(255, 255, 0))
                text_x_client = client_x + cur_client_shift
                draw.rectangle([text_x_client, row_y + 4, text_x_client + 60, row_y + marker_size - 4], fill=(255, 255, 255))

        out_path = os.path.join(self.temp_dir, f"synth_{shift_server}_{shift_client}_{shear_bug_at_200}.png")
        img.save(out_path)
        return out_path

    def test_exact_match_passes(self):
        path = self.create_synthetic_image(shift_server=48, shift_client=48, shear_bug_at_200=0)
        passed, results = analyze_screenshot(path, gui_scale=3, tolerance=0.5)
        self.assertTrue(passed, "Exact match across all rows must PASS")
        self.assertEqual(len(results), 16, "Must analyze all 16 rows")
        for r in results:
            self.assertEqual(r["status"], "PASS")
            self.assertEqual(r["error_gui"], 0.0)

    def test_subpixel_tolerance_passes(self):
        # 1 physical pixel discrepancy at scale 3 is 0.333 GUI px <= 0.5 tolerance
        path = self.create_synthetic_image(shift_server=48, shift_client=49, shear_bug_at_200=0)
        passed, results = analyze_screenshot(path, gui_scale=3, tolerance=0.5)
        self.assertTrue(passed, "Discrepancy of 0.333 GUI px must PASS within 0.5 tolerance")
        for r in results:
            self.assertEqual(r["status"], "PASS")

    def test_shear_bug_fails(self):
        # 12 physical pixels error = 4.0 GUI pixels >> 0.5 tolerance
        path = self.create_synthetic_image(shift_server=48, shift_client=48, shear_bug_at_200=12)
        passed, results = analyze_screenshot(path, gui_scale=3, tolerance=0.5)
        self.assertFalse(passed, "Simulated shear bug must FAIL")
        failing_rows = [r for r in results if r["status"] == "FAIL"]
        self.assertEqual(len(failing_rows), 1)
        self.assertEqual(failing_rows[0]["y"], 200)
        self.assertEqual(failing_rows[0]["style"], "italic")
        self.assertAlmostEqual(failing_rows[0]["error_gui"], 4.0, places=2)

if __name__ == "__main__":
    unittest.main()
