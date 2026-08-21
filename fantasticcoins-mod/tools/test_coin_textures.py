#!/usr/bin/env python3
"""Regression tests for the coin texture artifact detectors."""
from __future__ import annotations

import unittest

from PIL import Image

from gen_coin_textures import (
    COINS,
    FINAL_SIZE,
    area_reduce,
    load_source,
    measure,
    validation_errors,
)


class CoinTextureTests(unittest.TestCase):
    def test_generated_sprites_pass_every_guard(self) -> None:
        for coin in COINS:
            with self.subTest(coin=coin):
                source = load_source(coin)
                image = area_reduce(source, FINAL_SIZE)
                self.assertEqual([], validation_errors(coin, image, source))

    def test_wrong_dimensions_are_rejected(self) -> None:
        source = load_source("bronze")
        wrong = area_reduce(source, 32)
        errors = validation_errors("bronze", wrong, source)
        self.assertTrue(any("expected 64x64" in error for error in errors), errors)

    def test_hidden_rgb_and_alpha_fringe_are_detected(self) -> None:
        source = load_source("bronze")
        image = area_reduce(source, FINAL_SIZE)
        pixels = image.load()
        pixels[0, 0] = (12, 34, 56, 0)
        pixels[1, 0] = (12, 34, 56, 96)
        metrics = measure(image, source)
        self.assertEqual(1, metrics.hidden_rgb)
        self.assertEqual(1, metrics.partial_alpha)
        errors = validation_errors("bronze", image, source)
        self.assertTrue(any("hidden RGB" in error for error in errors), errors)
        self.assertTrue(any("alpha-fringe" in error for error in errors), errors)

    def test_isolated_highlight_is_detected(self) -> None:
        synthetic = Image.new("RGBA", (5, 5), (80, 80, 80, 255))
        synthetic.putpixel((2, 2), (255, 255, 255, 255))
        self.assertEqual(1, measure(synthetic).isolated_highlights)

        source = load_source("bronze")
        image = area_reduce(source, FINAL_SIZE)
        for y in range(31, 34):
            for x in range(31, 34):
                image.putpixel((x, y), (80, 80, 80, 255))
        image.putpixel((32, 32), (255, 255, 255, 255))
        errors = validation_errors("bronze", image, source)
        self.assertTrue(any("isolated high-luminance" in error for error in errors), errors)

    def test_edge_colour_outside_local_source_palette_is_a_halo(self) -> None:
        source = load_source("gold")
        image = area_reduce(source, FINAL_SIZE)
        pixels = image.load()
        changed = False
        for y in range(FINAL_SIZE):
            for x in range(FINAL_SIZE):
                if pixels[x, y][3] == 0:
                    continue
                neighbours = (
                    (x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1),
                )
                if any(nx < 0 or ny < 0 or nx >= FINAL_SIZE or ny >= FINAL_SIZE
                       or pixels[nx, ny][3] == 0 for nx, ny in neighbours):
                    pixels[x, y] = (0, 255, 255, 255)
                    changed = True
                    break
            if changed:
                break
        self.assertTrue(changed)
        metrics = measure(image, source)
        self.assertGreater(metrics.local_palette_violations, 0)
        self.assertGreater(metrics.edge_halos, 0)
        errors = validation_errors("gold", image, source)
        self.assertTrue(any("outside their local source palette" in error for error in errors), errors)
        self.assertTrue(any("edge halo" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
