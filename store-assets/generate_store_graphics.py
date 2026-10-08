"""Generate Play Store feature graphic and app infographic from Store icon.png."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
ICON_PATH = ROOT / "Store icon.png"
OUT_FEATURE = ROOT / "feature_graphic_1024x500.png"
OUT_INFOGRAPHIC = ROOT / "app_infographic.png"

# Archerypal brand palette
FOREST = (27, 67, 50)       # #1B4332
MOSS = (45, 106, 79)        # #2D6A4F
FIELD = (64, 145, 108)      # #40916C
DEEP = (8, 28, 21)          # #081C15
GOLD = (255, 183, 3)        # #FFB703
SAND = (216, 243, 220)      # #D8F3DC
TEXT = (241, 250, 238)      # #F1FAEE
MUTED = (183, 201, 192)     # #B7C9C0
CARD = (27, 46, 36)         # #1B2E24

FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_REG = r"C:\Windows\Fonts\segoeui.ttf"


def load_font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def vertical_gradient(size: tuple[int, int], top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    w, h = size
    img = Image.new("RGB", size)
    draw = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(h - 1, 1)
        color = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        draw.line([(0, y), (w, y)], fill=color)
    return img


def draw_rounded_rect(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    radius: int,
    fill: tuple[int, int, int],
    outline: tuple[int, int, int] | None = None,
    width: int = 2,
) -> None:
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def paste_icon(canvas: Image.Image, icon: Image.Image, box: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = box
    target_w, target_h = x1 - x0, y1 - y0
    fitted = icon.copy().convert("RGBA")
    fitted.thumbnail((target_w, target_h), Image.Resampling.LANCZOS)
    px = x0 + (target_w - fitted.width) // 2
    py = y0 + (target_h - fitted.height) // 2
    canvas.paste(fitted, (px, py), fitted)


def draw_gold_accent(draw: ImageDraw.ImageDraw, x: int, y: int, w: int) -> None:
    draw.rounded_rectangle((x, y, x + w, y + 6), radius=3, fill=GOLD)


def create_feature_graphic(icon: Image.Image) -> Image.Image:
    w, h = 1024, 500
    img = vertical_gradient((w, h), (34, 84, 62), DEEP)
    draw = ImageDraw.Draw(img)

    # Subtle field arcs
    for i, alpha in enumerate((28, 18, 10)):
        ring = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ring_draw = ImageDraw.Draw(ring)
        cx, cy = 210, h // 2
        r = 170 + i * 42
        ring_draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(*FIELD, alpha), width=3)
        img = Image.alpha_composite(img.convert("RGBA"), ring).convert("RGB")
        draw = ImageDraw.Draw(img)

    paste_icon(img, icon, (36, 36, 430, 464))

    title_font = load_font(FONT_BOLD, 72)
    sub_font = load_font(FONT_REG, 30)
    tag_font = load_font(FONT_REG, 24)
    bullet_font = load_font(FONT_REG, 22)

    draw_gold_accent(draw, 470, 118, 120)

    draw.text((470, 132), "Archerypal", font=title_font, fill=TEXT)
    draw.text((470, 220), "Score tracking on the field", font=sub_font, fill=SAND)

    bullets = [
        "Host or join matches nearby — no internet needed",
        "Log scores with a large outdoor-readable pad",
        "Live leaderboard syncs across every archer",
    ]
    y = 290
    for line in bullets:
        draw.ellipse((476, y + 8, 488, y + 20), fill=GOLD)
        draw.text((502, y), line, font=bullet_font, fill=MUTED)
        y += 42

    draw.rounded_rectangle((470, 430, 760, 468), radius=18, fill=MOSS)
    draw.text((498, 440), "Offline  •  Peer-to-peer  •  Free", font=tag_font, fill=TEXT)

    return img


def draw_step_card(
    canvas: Image.Image,
    box: tuple[int, int, int, int],
    number: str,
    title: str,
    body: str,
) -> None:
    draw = ImageDraw.Draw(canvas)
    x0, y0, x1, y1 = box
    draw_rounded_rect(draw, box, 22, CARD, outline=FIELD, width=2)

    num_font = load_font(FONT_BOLD, 34)
    title_font = load_font(FONT_BOLD, 30)
    body_font = load_font(FONT_REG, 22)

    badge_size = 52
    badge_box = (x0 + 24, y0 + 24, x0 + 24 + badge_size, y0 + 24 + badge_size)
    draw.ellipse(badge_box, fill=GOLD)
    num_w = draw.textlength(number, font=num_font)
    draw.text(
        (badge_box[0] + (badge_size - num_w) / 2, badge_box[1] + 6),
        number,
        font=num_font,
        fill=DEEP,
    )

    draw.text((x0 + 92, y0 + 28), title, font=title_font, fill=TEXT)
    draw.multiline_text((x0 + 24, y0 + 92), body, font=body_font, fill=MUTED, spacing=6)


def create_infographic(icon: Image.Image) -> Image.Image:
    w, h = 1080, 1620
    img = vertical_gradient((w, h), FOREST, DEEP)
    draw = ImageDraw.Draw(img)

    # Header band
    draw.rounded_rectangle((40, 40, w - 40, 300), radius=28, fill=CARD, outline=FIELD, width=2)
    paste_icon(img, icon, (56, 56, 250, 250))

    title_font = load_font(FONT_BOLD, 58)
    sub_font = load_font(FONT_REG, 26)
    section_font = load_font(FONT_BOLD, 34)
    footer_font = load_font(FONT_REG, 22)

    draw.text((270, 88), "Archerypal", font=title_font, fill=TEXT)
    draw.text((270, 162), "What is it?", font=section_font, fill=GOLD)
    draw.multiline_text(
        (270, 210),
        "Offline archery score tracking for groups\non the range. One phone hosts, everyone\njoins and logs scores in real time.",
        font=sub_font,
        fill=MUTED,
        spacing=4,
    )

    draw.text((40, 340), "How it works", font=section_font, fill=TEXT)
    draw_gold_accent(draw, 40, 388, 180)

    steps = [
        (
            "1",
            "Host a match",
            "Create a session on the field. Set how many\ntargets you'll shoot before scoring begins.",
        ),
        (
            "2",
            "Join via QR or nearby",
            "Other archers scan a QR code or pick the\nhost from nearby devices — no accounts.",
        ),
        (
            "3",
            "Log every arrow",
            "Large 0–10 number pad built for outdoor\nlight. Submit scores turn by turn.",
        ),
        (
            "4",
            "See the leaderboard",
            "Totals update live as the host merges\nscores. Everyone stays in sync.",
        ),
    ]

    y = 420
    card_h = 188
    gap = 22
    for number, title, body in steps:
        draw_step_card(img, (40, y, w - 40, y + card_h), number, title, body)
        y += card_h + gap

    # Highlights
    draw.text((40, y + 18), "Built for the field", font=section_font, fill=TEXT)
    draw_gold_accent(draw, 40, y + 66, 200)
    y += 88

    highlights = [
        ("No server", "Scores stay on your phones.\nNothing uploaded to the cloud."),
        ("Works offline", "Peer-to-peer sync over\nBluetooth and Wi‑Fi Direct."),
        ("Group friendly", "Host sets the round, every\narcher logs their own scores."),
    ]

    col_w = (w - 100) // 3
    for i, (title, body) in enumerate(highlights):
        x0 = 40 + i * (col_w + 10)
        box = (x0, y, x0 + col_w, y + 190)
        draw_rounded_rect(draw, box, 20, CARD, outline=MOSS, width=2)
        draw.text((x0 + 20, y + 20), title, font=load_font(FONT_BOLD, 24), fill=GOLD)
        draw.multiline_text((x0 + 20, y + 62), body, font=load_font(FONT_REG, 20), fill=MUTED, spacing=5)

    draw.rounded_rectangle((40, h - 96, w - 40, h - 40), radius=20, fill=MOSS)
    draw.text(
        (72, h - 78),
        "Archerypal  —  Pure Kotlin Android  —  Google Play",
        font=footer_font,
        fill=TEXT,
    )

    return img


def main() -> None:
    icon = Image.open(ICON_PATH).convert("RGBA")
    feature = create_feature_graphic(icon)
    feature.save(OUT_FEATURE, "PNG", optimize=True)
    print(f"Wrote {OUT_FEATURE} ({feature.size[0]}x{feature.size[1]})")

    infographic = create_infographic(icon)
    infographic.save(OUT_INFOGRAPHIC, "PNG", optimize=True)
    print(f"Wrote {OUT_INFOGRAPHIC} ({infographic.size[0]}x{infographic.size[1]})")


if __name__ == "__main__":
    main()
