#!/usr/bin/env python3
import io, os, struct, zlib
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")
os.makedirs(OUT, exist_ok=True)
MB = 1024 * 1024


def w(name, data):
    with open(os.path.join(OUT, name), "wb") as f:
        f.write(data if isinstance(data, bytes) else data.encode())
    print(f"  {name:55s} {len(data):>12,d} bytes")


def img_bytes(fmt, size=(64, 64), color=(200, 30, 30), mode="RGB", **kw):
    buf = io.BytesIO()
    Image.new(mode, size, color).save(buf, fmt, **kw)
    return buf.getvalue()


def pad_to(data, size, filler=b"\x00"):
    """Rellena DESPUÉS del fin de imagen (EOI/IEND) hasta el tamaño exacto: sigue siendo imagen decodificable."""
    assert len(data) <= size
    return data + filler * (size - len(data))


jpg = img_bytes("JPEG", (120, 90), (30, 120, 220), quality=85)
png = img_bytes("PNG", (120, 90), (30, 200, 120))

# ---------- válidos ----------
w("valid_small.jpg", jpg)
w("valid_small.png", png)
w("valid_UPPER.JPG", jpg)
w("valid_jpeg_ext.jpeg", jpg)
w("valid_noext", jpg)
w("foto ñandú café.jpg", jpg)
w("photo.php.jpg", jpg)
w("name_" + "x" * 200 + ".jpg", jpg)
w("transparent.png", img_bytes("PNG", (64, 64), (255, 0, 0, 80), mode="RGBA"))
w("dimension_1x1.png", img_bytes("PNG", (1, 1), (0, 0, 0)))
w("progressive.jpg", img_bytes("JPEG", (200, 200), (90, 90, 200), progressive=True, quality=80))
w("cmyk.jpg", img_bytes("JPEG", (64, 64), (10, 20, 30, 40), mode="CMYK"))

# JPG con EXIF/GPS (privacidad: ¿se limpian los metadatos?)
exif = Image.Exif()
exif[0x010F] = "QA-CAMERA"
exif[0x8825] = {1: "N", 2: (19.0, 18.0, 30.0), 3: "W", 4: (98.0, 12.0, 0.0)}
buf = io.BytesIO(); Image.new("RGB", (64, 64), (5, 5, 5)).save(buf, "JPEG", exif=exif); w("exif_gps.jpg", buf.getvalue())

# ---------- límites de tamaño (5 MB = 5*1024*1024) ----------
w("valid_4_9MB.jpg", pad_to(jpg, 5 * MB - 100 * 1024))
w("exact_5MB.jpg", pad_to(jpg, 5 * MB))
w("over_5MB_plus1.jpg", pad_to(jpg, 5 * MB + 1))
w("over_6MB.jpg", pad_to(jpg, 6 * MB))
w("huge_25MB.jpg", pad_to(jpg, 25 * MB))

# ---------- vacíos / falsos / corruptos ----------
w("empty.jpg", b"")
w("one_byte.jpg", b"\xff")
w("fake_text.jpg", "Esto no es una imagen, es texto plano.\n")
w("fake_exe.jpg", b"MZ\x90\x00\x03\x00\x00\x00" + b"\x00" * 200)
w("truncated.jpg", jpg[: len(jpg) // 3])
w("png_as.jpg", png)          # PNG real con extensión .jpg
w("jpg_as.png", jpg)          # JPG real con extensión .png
w("polyglot.jpg", jpg + b"\n<?php system($_GET['c']); ?><script>alert(1)</script>")

# ---------- formatos NO permitidos ----------
w("svg_xss.svg", '<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"><script>alert(document.cookie)</script></svg>')
w("animated.gif", img_bytes("GIF", (32, 32), (0, 0, 0), mode="P"))
w("image.webp", img_bytes("WEBP", (32, 32), (0, 200, 0)))
w("image.bmp", img_bytes("BMP", (32, 32), (0, 0, 200)))
w("image.tiff", img_bytes("TIFF", (32, 32), (200, 200, 0)))
w("document.pdf", b"%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")
w("script.exe", b"MZ\x90\x00" + b"\x00" * 500)
w("shell.php", "<?php echo shell_exec($_GET['cmd']); ?>")
w("photo.jpg.exe", jpg)

# ---------- bomba de descompresión: PNG gris 12000x12000 (~144M px) que pesa muy poco ----------
def png_bomb(path, side):
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    comp = zlib.compressobj(9)
    row = comp.compress(b"\x00" + b"\x00" * side)  # filtro 0 + fila de ceros
    z = zlib.compressobj(1)
    idat = b""
    rowraw = b"\x00" + b"\x00" * side
    for _ in range(side):
        idat += z.compress(rowraw)
    idat += z.flush()
    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", side, side, 8, 0, 0, 0, 0))
            + chunk(b"IDAT", idat) + chunk(b"IEND", b""))
    w(path, data)

png_bomb("dimension_12000x12000.png", 12000)
print("OK ->", OUT)
