import math, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT = "/usr/share/fonts/truetype/google-fonts/ofl/opensans/OpenSans[wdth,wght].ttf"
def font(size, wght=800, wdth=100):
    f = ImageFont.truetype(FONT, size)
    try: f.set_variation_by_axes([wght, wdth])
    except Exception: pass
    return f

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))
def grad_stops(stops, t):
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]; t1, c1 = stops[i + 1]
        if t <= t1: return lerp(c0, c1, (t - t0) / max(1e-6, t1 - t0))
    return stops[-1][1]

SKY = [(0.0, (34, 10, 66)), (0.45, (92, 22, 120)), (0.75, (214, 50, 132)), (0.92, (255, 120, 110)), (1.0, (255, 170, 100))]
WIN = [(84, 232, 255), (255, 140, 200)]

def scene(W, H, horizon, sun_c, sun_r, skyline=True, seed=3, cols=18):
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(horizon):
        d.line([(0, y), (W, y)], fill=grad_stops(SKY, y / horizon))
    # halo behind the sun
    halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    cx, cy = sun_c
    hd.ellipse([cx - sun_r * 1.45, cy - sun_r * 1.45, cx + sun_r * 1.45, cy + sun_r * 1.45], fill=(255, 150, 200, 40))
    hd.ellipse([cx - sun_r * 1.2, cy - sun_r * 1.2, cx + sun_r * 1.2, cy + sun_r * 1.2], fill=(255, 150, 200, 35))
    img.paste(halo, (0, 0), halo)
    # dartboard sun
    d.ellipse([cx - sun_r, cy - sun_r, cx + sun_r, cy + sun_r], fill=(40, 14, 60))
    r = sun_r * 0.96
    rings = [(1.0, 0), (0.93, 1), (0.62, 0), (0.55, 1), (0.16, 2)]
    pal_a = [(255, 82, 140), (255, 178, 60)]
    for k in range(20):
        a0 = math.radians(k * 18 - 99); a1 = math.radians((k + 1) * 18 - 99)
        for rr, kind in [(1.0, 'dbl'), (0.92, 'outer'), (0.62, 'tre'), (0.54, 'inner')]:
            pass
    def wedge(rad, col, k):
        a0 = k * 18 - 99; a1 = a0 + 18
        d.pieslice([cx - rad, cy - rad, cx + rad, cy + rad], a0, a1, fill=col)
    for k in range(20):
        even = k % 2 == 0
        wedge(r, (232, 36, 70) if even else (255, 205, 90), k)          # double ring
        wedge(r * 0.92, (255, 82, 140) if even else (255, 178, 60), k)   # outer single
        wedge(r * 0.62, (232, 36, 70) if even else (255, 205, 90), k)    # treble
        wedge(r * 0.55, (255, 82, 140) if even else (255, 178, 60), k)   # inner single
    d.ellipse([cx - r * 0.16, cy - r * 0.16, cx + r * 0.16, cy + r * 0.16], fill=(255, 215, 110))
    d.ellipse([cx - r * 0.065, cy - r * 0.065, cx + r * 0.065, cy + r * 0.065], fill=(232, 36, 70))
    # skyline
    rnd = random.Random(seed)
    if skyline:
        x = -rnd.randint(0, 40)
        while x < W:
            bw = rnd.randint(int(W * 0.035), int(W * 0.075))
            bh = rnd.randint(int(horizon * 0.12), int(horizon * 0.38))
            col = (46, 16, 72) if rnd.random() < 0.6 else (60, 20, 88)
            d.rectangle([x, horizon - bh, x + bw, horizon], fill=col)
            ws = max(4, bw // 9)
            for wy in range(horizon - bh + ws * 2, horizon - ws * 2, ws * 3):
                for wx in range(x + ws * 2, x + bw - ws * 2, ws * 3):
                    if rnd.random() < 0.38:
                        d.rectangle([wx, wy, wx + ws, wy + ws], fill=WIN[rnd.random() < 0.3])
            x += bw + rnd.randint(int(W * 0.01), int(W * 0.06))
    # floor + neon grid
    d.rectangle([0, horizon, W, H], fill=(24, 6, 40))
    lw = max(2, H // 300)
    d.line([(0, horizon), (W, horizon)], fill=(255, 120, 220), width=lw * 2)
    vx = W / 2
    for i in range(-cols * 2, cols * 2 + 1):
        x_bottom = vx + i * W / cols
        d.line([(vx + i * W / (cols * 11), horizon), (x_bottom, H)], fill=(236, 48, 170), width=lw)
    yy = 0.0; step = 0.02
    while True:
        y = horizon + (H - horizon) * (yy ** 1.9)
        if y > H: break
        d.line([(0, y), (W, y)], fill=(236, 48, 170), width=lw)
        yy += step; step *= 1.22
    return img

def chrome_text(img, text, cx, cy, size, spacing, outline):
    W, H = img.size
    f = font(size, 800)
    # measure with spacing
    widths = [f.getbbox(ch)[2] - f.getbbox(ch)[0] if ch != ' ' else size * 0.35 for ch in text]
    adv = [f.getlength(ch) for ch in text]
    total = sum(adv) + spacing * (len(text) - 1)
    asc, desc = f.getmetrics()
    x0 = cx - total / 2
    y0 = cy - (asc + desc) / 2
    mask = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask)
    x = x0
    for ch, a in zip(text, adv):
        md.text((x, y0), ch, font=f, fill=255)
        x += a + spacing
    # outline: dilate
    out = mask.filter(ImageFilter.MaxFilter(outline * 2 + 1)) if outline < 30 else mask
    if outline >= 30:
        o = mask
        for _ in range(outline // 14):
            o = o.filter(ImageFilter.MaxFilter(29))
        out = o
    # shadow
    shadow = out.filter(ImageFilter.GaussianBlur(outline))
    img.paste((10, 2, 20), (int(outline * 0.4), int(outline * 0.8)), shadow.point(lambda v: v * 0.6))
    img.paste((30, 10, 52), (0, 0), out)
    # gradient fill
    bb = mask.getbbox()
    top, bot = bb[1], bb[3]
    fill = Image.new("RGB", (W, H))
    fd = ImageDraw.Draw(fill)
    stops = [(0.0, (120, 70, 235)), (0.45, (190, 90, 230)), (0.62, (255, 120, 190)), (0.85, (255, 225, 205)), (1.0, (255, 245, 230))]
    for y in range(top, bot + 1):
        fd.line([(0, y), (W, y)], fill=grad_stops(stops, (y - top) / max(1, bot - top)))
    img.paste(fill, (0, 0), mask)
    # a thin highlight line across the middle, like the chrome titles
    hl = Image.new("L", (W, H), 0)
    hd = ImageDraw.Draw(hl)
    my = top + (bot - top) * 0.52
    hd.rectangle([0, my, W, my + max(2, size // 60)], fill=150)
    hl = Image.composite(hl, Image.new("L", (W, H), 0), mask)
    img.paste((255, 255, 255), (0, 0), hl)
    return img

# ---- developer header: 4096 x 2304 ----
SS = 1
W, H = 4096, 2304
hz = int(H * 0.62)
img = scene(W, H, hz, (int(W * 0.5), int(hz * 0.62)), int(H * 0.36), seed=7)
img = chrome_text(img, "AM GAMES", W // 2, int(H * 0.5), 420, 50, 22)
img.save("developer_header_4096x2304.png", optimize=True)

# ---- developer icon: 512 x 512 (rendered at 2x) ----
S = 1024
ic = scene(S, S, int(S * 0.72), (S // 2, int(S * 0.44)), int(S * 0.36), seed=11, cols=9)
ic = chrome_text(ic, "AM", S // 2, int(S * 0.47), 440, 10, 20)
ic = ic.resize((512, 512), Image.LANCZOS)
ic.save("developer_icon_512.png", optimize=True)
print("ok")
