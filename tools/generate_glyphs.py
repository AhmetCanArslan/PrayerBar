"""Generates the status bar drawables and their Kotlin lookup table.

SystemUI draws a status bar icon from a resource in this package, scaled to the icon height with
its aspect ratio intact. There is no way to hand it a bitmap built at runtime, so everything the
bar can show has to exist as a drawable before the build.

Two kinds come out of here. CHARSET gives one glyph per character, which can spell anything but
costs a slot — and roughly fifteen pixels of icon spacing — for every letter. CHUNKS gives whole
words their own drawable: the countdown has only a few dozen possible readings, so each one is
drawn once, properly kerned, and takes a single slot instead of four.

Run from the repo root after changing CHARSET or CHUNKS:

    python3 tools/generate_glyphs.py

Source font: Roboto-Regular (Apache 2.0), the Android system font.
"""

import os
import unicodedata

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

FONT = (
    "/Applications/Android Studio.app/Contents/plugins/design-tools/"
    "resources/layoutlib/data/fonts/Roboto-Regular.ttf"
)

CHARSET = (
    "0123456789"
    " :.,-+/·"
    "ABCDEFGHIJKLMNOPQRSTUVWXYZÇĞİÖŞÜ"
    "abcdefghijklmnopqrstuvwxyzçğıöşü"
)

def countdown_chunks():
    """Every reading CountdownFormat.remainingCompact can produce.

    It spells the countdown the way the status bar clock spells the time, so the label sits beside
    the clock without looking like a different thing.

    Under an hour there is no hour worth showing, so the reading is the bare minute count.

    Only the first twelve hours get a reading of their own. The gap between two tracked prayers is
    almost always shorter than that, and a whole day of them costs twice the drawables for a case
    most users never see. The bare ":mm" tails cover what is left: past the twelfth hour the
    segmenter matches the hour count and the tail separately, so "13:05" costs two slots instead
    of falling apart into four.

    Single-digit minutes are left out: the segmenter only looks for chunks of two characters or
    more, and a one-character reading already costs the single slot a glyph would.
    """
    hours = ["%d:%02d" % (h, m) for h in range(1, 12) for m in range(0, 60)]
    minutes = ["%d" % m for m in range(10, 60)]
    tails = [":%02d" % m for m in range(0, 60)]
    return hours + minutes + tails


CHUNKS = countdown_chunks()

# The box maps to whatever height SystemUI gives a status bar icon. Measured against the clock on
# a 1080px bar, that box is about 36.7px tall and the clock rests 24px digits on a baseline 6.7px
# above its bottom. The label shares that baseline so the two read as one line, but is drawn
# smaller than the clock — a countdown is secondary to the time, and it keeps the label narrow.
#
# The box is 100 units rather than 24 only so every coordinate lands on a whole number: at 1440
# drawables the two decimal places the finer box needed were costing more than a megabyte.
VIEWPORT_HEIGHT = 100.0
INTRINSIC_HEIGHT = 24
CAP_TARGET = 54.3      # 20px of the 36.7px box
BASELINE = 81.9        # the clock's baseline

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAWABLE_DIR = os.path.join(ROOT, "app/src/main/res/drawable")
KOTLIN_FILE = os.path.join(
    ROOT, "app/src/main/java/com/arslan/prayerbar/statusbar/Glyphs.kt"
)

VECTOR = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{width}dp"
    android:height="{height}dp"
    android:viewportWidth="{viewport}"
    android:viewportHeight="{box}">
{body}</vector>
'''

PATH = '''    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{data}" />
'''


def glyph_name(char):
    return "pb_glyph_u%04x" % ord(char)


def chunk_name(text):
    """Resource names take letters, digits and underscores, so a colon becomes one."""
    return "pb_word_" + "".join(c if c.isalnum() else "_" for c in text.lower())


def draw(font, glyph_set, cmap, hmtx, scale, text, name):
    """Lays the string out on one baseline and writes it as a single vector drawable."""
    pen_x = 0.0
    body = ""
    for char in text:
        glyph = cmap.get(ord(char))
        if glyph is None:
            raise SystemExit("font has no glyph for %r" % char)
        pen = SVGPathPen(glyph_set, ntos=lambda value: "%.0f" % value)
        glyph_set[glyph].draw(
            TransformPen(pen, (scale, 0, 0, -scale, pen_x, BASELINE))
        )
        data = pen.getCommands()
        if data:
            body += PATH.format(data=data)
        pen_x += hmtx[glyph][0] * scale

    width = round(pen_x)
    if width <= 0:
        raise SystemExit("zero advance for %r" % text)
    intrinsic = max(1, round(width * INTRINSIC_HEIGHT / VIEWPORT_HEIGHT))
    with open(os.path.join(DRAWABLE_DIR, name + ".xml"), "w") as handle:
        handle.write(VECTOR.format(
            width=intrinsic,
            height=INTRINSIC_HEIGHT,
            viewport=width,
            box=round(VIEWPORT_HEIGHT),
            body=body,
        ))


def main():
    font = TTFont(FONT)
    glyph_set = font.getGlyphSet()
    cmap = font.getBestCmap()
    upem = font["head"].unitsPerEm
    cap_height = getattr(font["OS/2"], "sCapHeight", 0) or int(upem * 0.71)
    scale = CAP_TARGET / cap_height
    hmtx = font["hmtx"]

    entries = []
    for char in CHARSET:
        name = glyph_name(char)
        draw(font, glyph_set, cmap, hmtx, scale, char, name)
        entries.append((char, name))

    chunks = []
    for text in CHUNKS:
        name = chunk_name(text)
        draw(font, glyph_set, cmap, hmtx, scale, text, name)
        chunks.append((text, name))

    with open(KOTLIN_FILE, "w") as handle:
        handle.write(kotlin(entries, chunks))

    print("wrote %d glyphs and %d chunks" % (len(entries), len(chunks)))


def kotlin(entries, chunks):
    lines = [
        "package com.arslan.prayerbar.statusbar",
        "",
        "import com.arslan.prayerbar.R",
        "",
        "/**",
        " * Generated by tools/generate_glyphs.py — do not edit by hand.",
        " *",
        " * [byChar] can spell anything but spends a slot per character; [byChunk] holds the whole",
        " * words worth drawing in one piece. [StatusBarText.segments] prefers the longest chunk it",
        " * can match and falls back to single characters, so a label costs as few slots as possible.",
        " */",
        "object Glyphs {",
        "",
        "    val byChar: Map<Char, Int> = mapOf(",
    ]
    for char, name in entries:
        label = unicodedata.name(char, "").title() or "unnamed"
        lines.append("        '%s' to R.drawable.%s, // %s" % (escape(char), name, label))
    lines += ["    )", "", "    val byChunk: Map<String, Int> = mapOf("]
    for text, name in chunks:
        lines.append('        "%s" to R.drawable.%s,' % (text, name))
    lines += [
        "    )",
        "",
        "    val longestChunk: Int = byChunk.keys.maxOf { it.length }",
        "",
        "    fun has(char: Char): Boolean = byChar.containsKey(char)",
        "}",
        "",
    ]
    return "\n".join(lines)


def escape(char):
    if char == "'":
        return "\\'"
    if char == "\\":
        return "\\\\"
    return char


if __name__ == "__main__":
    main()
