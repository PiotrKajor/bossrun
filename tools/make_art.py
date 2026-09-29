#!/usr/bin/env python3
"""Sklada ikone projektu i baner: pikselowa czaszka z zarzacymi sie oczodolami.

Rysujemy na grubej siatce (16 kratek na bok), bo ikona ma byc czytelna w 48 px na liscie -
drobne detale i tak by tam zniknely, a kanciasty ksztalt czyta sie od razu jako Minecraft.
Zar w oczach ma ten sam pomaranczowy, co napis HARDCORE na banerze.

Uzycie:  python3 tools/make_art.py
         FONT_DIR=/sciezka/do/fontow python3 tools/make_art.py   # dla banera
Wynik:   docs/media/icon.png (512x512), icon.gif (256x256, animowana), banner.png (1280x640)

Baner potrzebuje Antonio i Poppins (oba SIL OFL, Google Fonts). Nie trzymamy ich w repo -
bez nich powstaje sama ikona.
"""
import math
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

WYNIK = Path(__file__).resolve().parent.parent / "docs" / "media"
FONTY = Path(os.environ.get("FONT_DIR", "/usr/share/fonts"))

TLO = (10, 6, 8)
TLO_POSWIATA = (40, 14, 16)  # czerwonawy srodek - niebezpieczenstwo bez krzyku
ZAR = (255, 116, 58)
ZAR_JASNY = (255, 196, 120)  # oko w szczycie oddechu

CZASZKA = [
    "....KKKKKKKK....",
    "...KWWWWWWWWK...",
    "..KWWWWWWWWWSK..",
    "..KWWWWWWWWWSK..",
    "..KWWWWWWWWWSK..",
    "..KWEEEWWEEESK..",
    "..KWEOEWWEOESK..",
    "..KWEEEWWEEESK..",
    "..KWWWWEEWWWSK..",
    "...KWWWEEWWSK...",
    "....KWWWWWSK....",
    "....KWEWEWEK....",
    "....KWWWWWSK....",
    ".....KKKKKK.....",
]
KOLORY = {"K": (24, 16, 18), "W": (236, 228, 206), "S": (186, 176, 152), "E": (40, 20, 22), "O": ZAR}


def _poswiata(im, box, promien, kolor):
    maska = Image.new("L", im.size, 0)
    ImageDraw.Draw(maska).ellipse(box, fill=255)
    maska = maska.filter(ImageFilter.GaussianBlur(promien))
    return Image.composite(Image.new("RGB", im.size, kolor), im, maska)


def czaszka(im, ox, oy, kratka, zar=1.0):
    """Twardy cien, piksele, a zar na samym wierzchu - pod spodem zaslanialyby go oczodoly.
    `zar` 0..1 skaluje poswiate i jasnosc oczu - z tego zyje animacja."""
    kolory = dict(KOLORY)
    kolory["O"] = tuple(int(e + (o - e) * (0.3 + 0.7 * zar)) for o, e in zip(ZAR_JASNY, KOLORY["E"]))
    rys = ImageDraw.Draw(im)
    for przes, cien in ((kratka // 3, True), (0, False)):
        for y, rzad in enumerate(CZASZKA):
            for x, c in enumerate(rzad):
                if c in kolory:
                    x0, y0 = ox + x * kratka + przes, oy + y * kratka + przes
                    rys.rectangle([x0, y0, x0 + kratka - 1, y0 + kratka - 1],
                                  fill=(0, 0, 0) if cien else kolory[c])
    if zar > 0.05:
        maska = Image.new("L", im.size, 0)
        md = ImageDraw.Draw(maska)
        for y, rzad in enumerate(CZASZKA):
            for x, c in enumerate(rzad):
                if c == "O":
                    sx, sy = ox + x * kratka + kratka // 2, oy + y * kratka + kratka // 2
                    r = kratka * (0.8 + 0.9 * zar)
                    md.ellipse((sx - r, sy - r, sx + r, sy + r), fill=int(200 * zar))
        maska = maska.filter(ImageFilter.GaussianBlur(kratka * 0.9))
        im = Image.composite(Image.new("RGB", im.size, (255, 90, 30)), im, maska)
        # sam piksel oka zostaje ostry nad poswiata
        rys = ImageDraw.Draw(im)
        for y, rzad in enumerate(CZASZKA):
            for x, c in enumerate(rzad):
                if c == "O":
                    x0, y0 = ox + x * kratka, oy + y * kratka
                    rys.rectangle([x0, y0, x0 + kratka - 1, y0 + kratka - 1], fill=kolory["O"])
    return im


def ikona(bok=512, zar=1.0, unies=0):
    im = Image.new("RGB", (bok, bok), TLO)
    im = _poswiata(im, (60, 60, bok - 60, bok - 60), 90, TLO_POSWIATA)
    kratka = bok * 28 // 512
    return czaszka(im, (bok - 16 * kratka) // 2,
                   (bok - len(CZASZKA) * kratka) // 2 - unies * kratka // 4, kratka, zar)


def animacja(bok=256, klatki=24):
    """Oddech zaru + czaszka unoszaca sie o cwierc kratki; w polowie petli oczy gasna
    na dwie klatki, jakby cos w nich mrugnelo. 256 px, bo Modrinth tnie ikone do 256 KiB."""
    kadry = []
    for i in range(klatki):
        faza = math.sin(2 * math.pi * i / klatki)
        zar = 0.0 if i in (14, 15) else 0.55 + 0.45 * faza
        kadry.append(ikona(bok, zar, unies=round(1 + faza)))
    kadry = [k.quantize(64, dither=Image.Dither.NONE) for k in kadry]
    kadry[0].save(WYNIK / "icon.gif", save_all=True, append_images=kadry[1:],
                  duration=[80] * klatki, loop=0, optimize=True, disposal=1)


def baner(szer=1280, wys=640):
    im = Image.new("RGB", (szer, wys), TLO)
    im = _poswiata(im, (80, 80, 560, 560), 110, TLO_POSWIATA)
    kratka = 28
    im = czaszka(im, 320 - 8 * kratka, (wys - len(CZASZKA) * kratka) // 2, kratka)
    rys = ImageDraw.Draw(im)
    rys.text((640, 150), "BOSS RUN",
             font=ImageFont.truetype(str(FONTY / "Antonio-VariableFont_wght.ttf"), 104),
             fill=(240, 236, 236))
    rys.text((642, 248), "HARDCORE",
             font=ImageFont.truetype(str(FONTY / "Antonio-VariableFont_wght.ttf"), 104),
             fill=ZAR)
    rys.text((646, 390), "One death and the world is gone.",
             font=ImageFont.truetype(str(FONTY / "Poppins-Medium.ttf"), 29),
             fill=(198, 168, 164))
    rys.text((646, 444), "Fabric · Minecraft 1.20.4 – 26.2",
             font=ImageFont.truetype(str(FONTY / "Poppins-Regular.ttf"), 23),
             fill=(126, 104, 102))
    return im


def main() -> None:
    WYNIK.mkdir(parents=True, exist_ok=True)
    ikona().save(WYNIK / "icon.png")
    print(WYNIK / "icon.png")
    animacja()
    print(WYNIK / "icon.gif")
    try:
        baner().save(WYNIK / "banner.png")
        print(WYNIK / "banner.png")
    except OSError as e:
        print(f"baner pominiety (brak fontow w {FONTY}): {e}")


if __name__ == "__main__":
    main()
