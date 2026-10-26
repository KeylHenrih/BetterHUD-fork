#!/usr/bin/env python3
"""
Unit tests for analyze_italic_pairs.py using synthetic raster images.
"""

import unittest
import numpy as np
from PIL import Image, ImageDraw
import analyze_italic_pairs as analyzer

def generate_synthetic_pair_image(gui_scale=3, spacing_gui=20.0, top_shear_gui=1.0, bottom_shear_gui=-1.0, width_gui=8.0, height_gui=8.0):
    """
    Renders a synthetic image containing a regular glyph (blue) and an italic glyph (red).
    """
    s = float(gui_scale)
    h_px = int(height_gui * s)
    w_px = int(width_gui * s)
    spacing_px = int(spacing_gui * s)
    
    img_w = int(spacing_px + w_px * 3)
    img_h = int(h_px + 20 * s)
    
    # 0 = background, 1 = regular, 2 = italic
    reg_mask = np.zeros((img_h, img_w), dtype=bool)
    italic_mask = np.zeros((img_h, img_w), dtype=bool)
    
    y0 = int(5 * s)
    x0_reg = int(5 * s)
    
    # Regular glyph: rectangular vertical quad
    for dy in range(h_px):
        y = y0 + dy
        for dx in range(w_px):
            reg_mask[y, x0_reg + dx] = True
            
    # Italic glyph: slanted quad
    x0_italic_base = x0_reg + spacing_px
    top_offset_px = int(round(top_shear_gui * s))
    bottom_offset_px = int(round(bottom_shear_gui * s))
    
    for dy in range(h_px):
        y = y0 + dy
        # Linear interpolation of shear from top to bottom
        t = dy / float(h_px - 1) if h_px > 1 else 0.0
        offset_px = int(round((1.0 - t) * top_offset_px + t * bottom_offset_px))
        for dx in range(w_px):
            italic_mask[y, x0_italic_base + offset_px + dx] = True
            
    return reg_mask, italic_mask

class TestAnalyzeItalicPairs(unittest.TestCase):

    def test_ideal_vanilla_scale_3(self):
        """Vanilla 26.1.2 at GUI scale 3: top=+1.0, bottom=-1.0 -> PASS (0.00 error)"""
        reg_mask, italic_mask = generate_synthetic_pair_image(gui_scale=3, top_shear_gui=1.0, bottom_shear_gui=-1.0)
        res = analyzer.analyze_pair(reg_mask, italic_mask, nominal_spacing_gui=20.0, gui_scale=3)
        self.assertIsNotNone(res)
        self.assertEqual(res['verdict'], "PASS")
        self.assertAlmostEqual(res['delta_top'], 1.0, delta=0.05)
        self.assertAlmostEqual(res['delta_bottom'], -1.0, delta=0.05)
        self.assertLessEqual(res['max_err'], 0.05)

    def test_ideal_vanilla_scale_2(self):
        """Vanilla 26.1.2 at GUI scale 2: top=+1.0, bottom=-1.0 -> PASS (0.00 error)"""
        reg_mask, italic_mask = generate_synthetic_pair_image(gui_scale=2, top_shear_gui=1.0, bottom_shear_gui=-1.0)
        res = analyzer.analyze_pair(reg_mask, italic_mask, nominal_spacing_gui=20.0, gui_scale=2)
        self.assertIsNotNone(res)
        self.assertEqual(res['verdict'], "PASS")
        self.assertAlmostEqual(res['delta_top'], 1.0, delta=0.05)
        self.assertAlmostEqual(res['delta_bottom'], -1.0, delta=0.05)
        self.assertLessEqual(res['max_err'], 0.05)

    def test_within_tolerance_subpixel_shift(self):
        """Subpixel variation (+0.15 GUI px) within 0.25 tolerance -> PASS"""
        reg_mask, italic_mask = generate_synthetic_pair_image(gui_scale=3, top_shear_gui=1.15, bottom_shear_gui=-0.85)
        res = analyzer.analyze_pair(reg_mask, italic_mask, nominal_spacing_gui=20.0, gui_scale=3)
        self.assertIsNotNone(res)
        self.assertEqual(res['verdict'], "PASS")
        self.assertLessEqual(res['max_err'], 0.25)

    def test_reproduce_uncompensated_bug_shift(self):
        """Reproduces bug without +1.75 compensation (shifted left by 1.75 px) -> FAIL"""
        # Bug gave top = -0.75, bottom = -2.75
        reg_mask, italic_mask = generate_synthetic_pair_image(gui_scale=3, top_shear_gui=-0.75, bottom_shear_gui=-2.75)
        res = analyzer.analyze_pair(reg_mask, italic_mask, nominal_spacing_gui=20.0, gui_scale=3)
        self.assertIsNotNone(res)
        self.assertEqual(res['verdict'], "FAIL")
        self.assertAlmostEqual(res['max_err'], 1.75, delta=0.1)

    def test_out_of_tolerance_shift_plus_one_px(self):
        """Shift of +1.0 GUI px -> FAIL"""
        reg_mask, italic_mask = generate_synthetic_pair_image(gui_scale=3, top_shear_gui=2.0, bottom_shear_gui=0.0)
        res = analyzer.analyze_pair(reg_mask, italic_mask, nominal_spacing_gui=20.0, gui_scale=3)
        self.assertIsNotNone(res)
        self.assertEqual(res['verdict'], "FAIL")
        self.assertAlmostEqual(res['max_err'], 1.0, delta=0.1)

    def test_out_of_tolerance_shift_0_30_px(self):
        """Shift of +0.30 GUI px (just above 0.25 threshold) -> FAIL"""
        reg_mask, italic_mask = generate_synthetic_pair_image(gui_scale=3, top_shear_gui=1.30, bottom_shear_gui=-0.70)
        res = analyzer.analyze_pair(reg_mask, italic_mask, nominal_spacing_gui=20.0, gui_scale=3)
        self.assertIsNotNone(res)
        self.assertEqual(res['verdict'], "FAIL")
        self.assertGreater(res['max_err'], 0.25)

if __name__ == '__main__':
    unittest.main()
