"""Generate fictional, localized static picker previews for pre-Android-12 launchers.

No user records are accessed. Newer launchers use localized previewLayout resources.
Requires Pillow with RAQM for Persian shaping (development only).
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
COPY = {
    "": ("Habits", "Tasks", "Oct 20 · 1 of 3 targets reached", "Oct 20 · 3 due today or overdue", "Updated 13:07", "View all habits", "View all tasks", "Add task", ["Mood", "Memory", "Journal"]),
    "-fr": ("Habitudes", "Tâches", "20 oct. · 1 objectif sur 3 atteint", "20 oct. · 3 tâches dues ou en retard", "Mis à jour à 13:07", "Voir les habitudes", "Voir les tâches", "Ajouter", ["Humeur", "Souvenir", "Journal"]),
    "-fa": ("عادت‌ها", "کارها", "۲۰ اکتبر · ۱ هدف از ۳ هدف", "۲۰ اکتبر · ۳ کار امروز یا عقب‌افتاده", "به‌روزرسانی ۱۳:۰۷", "همهٔ عادت‌ها", "همهٔ کارها", "افزودن کار", ["حال‌وهوا", "خاطره", "روزنوشت"]),
}
for suffix, copy in COPY.items():
    output = ROOT / ("drawable" + suffix + "-nodpi")
    output.mkdir(exist_ok=True)
    rtl = suffix == "-fa"
    for kind in ("habits", "tasks", "capture"):
        image = Image.new("RGBA", (500, 320), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle((0, 0, 499, 319), radius=28, fill="#F8F8F2")
        def text(value, y, size=26, bold=False, center=False):
            font = ImageFont.truetype(BOLD if bold else FONT, size)
            draw.text((250 if center else 472 if rtl else 28, y), value, font=font,
                      fill="#0A2F27", anchor="mt" if center else "rt" if rtl else "lt",
                      direction="rtl" if rtl else "ltr")
        if kind == "capture":
            for i, label in enumerate(copy[8]):
                y = 20 + i * 96
                draw.rounded_rectangle((24, y, 476, y + 80), radius=18, fill="#CDE8DC")
                text(label, y + 24, center=True)
        else:
            task = kind == "tasks"
            text(copy[1 if task else 0], 28, 34, True)
            draw.text((28 if rtl else 472, 28), "↻", font=ImageFont.truetype(FONT, 34), fill="#0A2F27", anchor="lt" if rtl else "rt")
            text(copy[3 if task else 2], 98, 24)
            text(copy[4], 175, 20)
            text(copy[6 if task else 5], 250, 24)
            if task:
                # Both navigation and Add are shown, without fictional personal titles in summary.
                draw.rounded_rectangle((300 if not rtl else 24, 226, 476 if not rtl else 200, 302), radius=16, fill="#CDE8DC")
                draw.text((388 if not rtl else 112, 250), copy[7], font=ImageFont.truetype(FONT, 22), fill="#0A2F27", anchor="mt", direction="rtl" if rtl else "ltr")
        image.save(output / f"widget_preview_{kind}.png")
