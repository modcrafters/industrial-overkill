"""Reproducible 32px machine-family textures; Python standard library only."""
from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/industrial_overkill'


class Canvas:
    def __init__(self, background='555966'):
        self.pixels = [[bytes.fromhex(background) for _ in range(32)] for _ in range(32)]

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.pixels[y][x] = bytes.fromhex(color)

    def save(self, name):
        def chunk(kind, data):
            return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
        raw = b''.join(b'\0' + b''.join(row) for row in self.pixels)
        path = ROOT / 'textures/block' / (name + '.png')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 32, 32, 8, 2, 0, 0, 0))
                         + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


def frame():
    c = Canvas()
    c.rect(0, 0, 31, 31, '20232c')
    c.rect(1, 1, 30, 30, 'a7a9b6')
    c.rect(2, 2, 29, 29, '757986')
    c.rect(2, 29, 29, 30, '424652')
    c.rect(3, 3, 28, 28, '555b68')
    for x in (2, 27):
        for y in (2, 27):
            c.rect(x, y, x + 2, y + 2, '353946')
            c.rect(x, y, x + 1, y, 'd1d2dc')
            c.rect(x + 1, y + 1, x + 1, y + 1, '838a98')
    return c


def front(active=False):
    c = frame()
    c.rect(6, 2, 25, 5, '204d59')
    c.rect(7, 2, 24, 3, 'b0eff6' if active else '39717a')
    c.rect(7, 4, 24, 4, '43c8df' if active else '285761')
    c.rect(5, 7, 26, 25, 'aaaeba')
    c.rect(6, 8, 25, 24, '383d49')
    c.rect(7, 9, 24, 23, '1b222a')
    # Cobblestone silhouette, individually shaded irregular stones.
    c.rect(10, 11, 21, 21, '444b57')
    for x, y, w, h, color in [(10, 11, 4, 3, '919bab'), (16, 11, 4, 2, 'bbc1cd'),
                              (15, 15, 3, 4, '778493'), (19, 14, 2, 3, 'a6afbd'),
                              (10, 16, 3, 3, 'b2b7c5'), (11, 20, 4, 1, '798695'),
                              (17, 20, 4, 1, '9fa9b9')]:
        c.rect(x, y, x + w - 1, y + h - 1, color)
        c.rect(x, y, x + w - 1, y, 'c4cad4')
    c.rect(9, 26, 22, 29, '202e39')
    for x in (10, 15, 20):
        c.rect(x, 27, x + 1, 28, '48c7df' if active else '285761')
        c.rect(x, 27, x + 1, 27, 'b2f2fa' if active else '39717a')
    return c


def side():
    c = frame()
    c.rect(6, 5, 25, 26, '878c98')
    for y in range(7, 25, 4):
        c.rect(7, y, 24, y + 1, '303642')
        c.rect(7, y + 2, 24, y + 2, 'acafba')
    return c


def top(active=False):
    c = frame()
    c.rect(5, 5, 26, 26, '2c6d7a' if active else '204d59')
    c.rect(6, 6, 25, 25, '63d3e2' if active else '39717a')
    c.rect(8, 8, 23, 23, '303844')
    c.rect(9, 9, 22, 22, '747d8b')
    c.rect(10, 10, 21, 10, '9da8b5')
    return c


def bottom():
    c = frame()
    c.rect(7, 7, 24, 24, '303642')
    c.rect(9, 9, 22, 22, '626977')
    c.rect(10, 10, 21, 10, '89909e')
    return c


def front_mask():
    c = Canvas('000000')
    c.rect(7, 2, 24, 4, 'ffffff')
    for x in (10, 15, 20):
        c.rect(x, 27, x + 1, 28, 'ffffff')
    return c


def top_mask():
    c = Canvas('000000')
    c.rect(6, 6, 25, 25, 'ffffff')
    c.rect(8, 8, 23, 23, '000000')
    return c


if __name__ == '__main__':
    for name, draw in [('front', front), ('side', side), ('top', top), ('bottom', bottom)]:
        draw().save('rock_stew_' + name)
    front(active=True).save('rock_stew_front_active')
    top(active=True).save('rock_stew_top_active')
    front_mask().save('rock_stew_front_emissive')
    top_mask().save('rock_stew_top_emissive')
    # Conditional appearances wrap this base; XML supplies the active textures and masks.
    model = {'parent': 'minecraft:block/orientable', 'textures': {
        name: 'industrial_overkill:block/rock_stew_' + texture for name, texture in
        [('front', 'front'), ('side', 'side'), ('top', 'top'), ('bottom', 'bottom')]}}
    path = ROOT / 'models/block/rock_stew_factory_base.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
