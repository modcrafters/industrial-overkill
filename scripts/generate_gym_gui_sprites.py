"""Small code-drawn UI glyphs matching MMDLib's flat controls; no dependencies."""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/industrial_overkill/textures/gui/sprites'


def write(name, pixels):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    size = len(pixels)
    raw = b''.join(b'\0' + bytes(c for pixel in row for c in pixel) for row in pixels)
    ROOT.mkdir(parents=True, exist_ok=True)
    (ROOT / (name + '.png')).write_bytes(
        b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
        + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


def generate():
    for intensity in range(1, 4):
        pixels = [[(0, 0, 0, 0) for _ in range(12)] for _ in range(12)]
        for dot in range(3):
            color = (220, 223, 225, 255) if dot < intensity else (91, 94, 97, 255)
            for y in range(5, 7):
                for x in range(dot * 4 + 1, dot * 4 + 3):
                    pixels[y][x] = color
        write('intensity_' + str(intensity), pixels)

    pixels = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    # A neutral cage/crate outline. The slot renderer supplies the ghost opacity.
    for y in range(2, 14):
        for x in range(2, 14):
            if y in (2, 3, 12, 13) or x in (2, 6, 9, 13):
                pixels[y][x] = (216, 220, 224, 255)
    write('courier_slot', pixels)


if __name__ == '__main__':
    generate()
