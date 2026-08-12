#!/usr/bin/env python3
"""Sklada ikone projektu i baner z tego, co w tym modzie jest naprawde wlasne:
znikajacego swiata.

Kategoria "hardcore" na Modrincie to same serca - zlote, czerwone, przekreslone. Serce
wygladaloby jak kazdy inny mod obok. Tresc Boss Runa jest inna: to nie gracz umiera,
tylko SWIAT idzie do kosza. Ikona pokazuje wiec kawalek terenu pekajacy na pol, z rozzarzona
szczelina i blokami odpadajacymi w ciemnosc.

Rysujemy na grubej siatce (16 kratek na bok), bo ikona ma byc czytelna w 48 px na liscie -
drobne detale i tak by tam zniknely, a kanciasty ksztalt czyta sie od razu jako Minecraft.

Uzycie:  python3 tools/make_art.py
         FONT_DIR=/sciezka/do/fontow python3 tools/make_art.py   # dla banera
Wynik:   docs/media/icon.png (512x512), docs/media/banner.png (1280x640)

Baner potrzebuje Antonio i Poppins (oba SIL OFL, Google Fonts). Nie trzymamy ich w repo -
bez nich powstaje sama ikona.
"""
import os
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

WYNIK = Path(__file__).resolve().parent.parent / "docs" / "media"
FONTY = Path(os.environ.get("FONT_DIR", "/usr/share/fonts"))

TLO = (16, 10, 12)          # prawie czern z czerwona nuta - niebezpieczenstwo bez krzyku
TRAWA = (86, 130, 62)
TRAWA_CIEN = (62, 96, 45)
ZIEMIA = (110, 78, 54)
KAMIEN = (108, 108, 116)
KAMIEN_CIEN = (74, 74, 82)
SZCZELINA = (255, 116, 58)   # rozgrzana krawedz pekniecia
SZCZELINA_JASNA = (255, 214, 150)

# Szerokosc kolejnych rzedow terenu w kratkach, liczona od szczeliny w kazda strone.
# Wyspa zwezajaca sie ku dolowi - kanciasty klin, nie prostokat.
RZEDY = [5, 5, 5, 4, 4, 3, 2, 1]
ZIARNO = 5


def _kolor(r, losowy):
    if r == 0:
        return TRAWA
    if r == 1:
        return TRAWA_CIEN if losowy < .3 else ZIEMIA
    if r == 2:
        return ZIEMIA
    return KAMIEN_CIEN if losowy < .35 else KAMIEN


def wyspa(rys, srodek_x, gora_y, kratka, spadek=2):
    """Teren rozdarty wzdluz srodka. Prawa polowa osuwa sie o kilka kratek w dol -
    to samo pekniecie, co w opisie moda: swiat rozlatuje sie po jednej smierci.
    Krawedzie zwrocone do szczeliny sa rozgrzane, wiec oko idzie prosto do rozdarcia."""
    random.seed(ZIARNO)
    for r, szer in enumerate(RZEDY):
        for polowa, znak in ((0, -1), (1, 1)):
            y = gora_y + (r + (spadek if polowa else 0)) * kratka
            for i in range(szer):
                kolor = _kolor(r, random.random())
                if i == 0 and r < 5:                       # kratka przy samej szczelinie
                    kolor = SZCZELINA_JASNA if r == 0 else SZCZELINA
                x = srodek_x + (i if znak > 0 else -i - 1) * kratka
                rys.rectangle([x, y, x + kratka - 2, y + kratka - 2], fill=kolor)


def odpryski(rys, srodek_x, gora_y, kratka, spadek=2):
    """Kilka blokow juz wypadlo ze szczeliny - to one robia z pekniecia ruch.
    Lecą w dol wzdluz rozdarcia, nie po calym kafelku, zeby nie wygladaly na szum."""
    for dx, dy, ubytek in ((0.2, 8.6, 6), (1.1, 9.8, 10), (-0.9, 10.6, 12), (0.6, 11.6, 16)):
        bok = kratka - ubytek
        x = srodek_x + dx * kratka
        y = gora_y + (dy + spadek) * kratka
        rys.rectangle([x, y, x + bok, y + bok], fill=KAMIEN_CIEN)


def ikona(bok=512):
    im = Image.new("RGB", (bok, bok), TLO)
    rys = ImageDraw.Draw(im)
    kratka = bok // 16
    wyspa(rys, bok // 2, kratka * 2, kratka)
    odpryski(rys, bok // 2, kratka * 2, kratka)
    return im


def baner(szer=1280, wys=640):
    im = Image.new("RGB", (szer, wys), TLO)
    rys = ImageDraw.Draw(im)
    kratka = 38
    # Wyzej niz w ikonie, zeby spadajace bloki zmiescily sie nad dolna krawedzia.
    wyspa(rys, 300, 66, kratka)
    odpryski(rys, 300, 66, kratka)

    rys.text((640, 232), "BOSS RUN",
             font=ImageFont.truetype(str(FONTY / "Antonio-VariableFont_wght.ttf"), 104),
             fill=(240, 236, 236))
    rys.text((642, 330), "HARDCORE",
             font=ImageFont.truetype(str(FONTY / "Antonio-VariableFont_wght.ttf"), 104),
             fill=SZCZELINA)
    rys.text((646, 452), "One death and the world is gone.",
             font=ImageFont.truetype(str(FONTY / "Poppins-Medium.ttf"), 29),
             fill=(198, 168, 164))
    rys.text((646, 506), "Fabric · Minecraft 26.2",
             font=ImageFont.truetype(str(FONTY / "Poppins-Regular.ttf"), 23),
             fill=(126, 104, 102))
    return im


def main() -> None:
    WYNIK.mkdir(parents=True, exist_ok=True)
    ikona().save(WYNIK / "icon.png")
    print(WYNIK / "icon.png")
    try:
        baner().save(WYNIK / "banner.png")
        print(WYNIK / "banner.png")
    except OSError as e:
        print(f"baner pominiety (brak fontow w {FONTY}): {e}")


if __name__ == "__main__":
    main()
