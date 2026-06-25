"""Render a faithful PNG preview of the ActionCut clapperboard adaptive icon (background +
foreground) so the design can be eyeballed without building the app. Mirrors the vector
geometry in app/src/main/res/drawable/ic_launcher_{bg,foreground}.xml."""
import math
from PIL import Image, ImageDraw

S = 16  # supersample scale (108 -> 1728) for crisp edges
W = 108 * S
img = Image.new("RGB", (W, W), (11, 11, 20))
d = ImageDraw.Draw(img)


def sc(v):
    return v * S


# --- Background diagonal gradient (#0B0B14 -> #171229) ---
c0, c1 = (11, 11, 20), (23, 18, 41)
for y in range(W):
    for_x_t = y / (W - 1)
    # approximate diagonal by blending on y (close enough for preview)
    r = int(c0[0] + (c1[0] - c0[0]) * for_x_t)
    g = int(c0[1] + (c1[1] - c0[1]) * for_x_t)
    b = int(c0[2] + (c1[2] - c0[2]) * for_x_t)
    d.line([(0, y), (W, y)], fill=(r, g, b))

# --- Slate board (rounded rect, light) ---
d.rounded_rectangle([sc(17), sc(50), sc(87), sc(85)], radius=sc(5), fill=(244, 244, 248))

# --- Brand play triangle (violet->mint, approx solid mint-teal) ---
d.polygon([(sc(47), sc(59)), (sc(66), sc(68)), (sc(47), sc(77))], fill=(74, 170, 200))

# --- Clapper bar + stripes on a separate layer, then rotate -9deg about (22,47) ---
bar = Image.new("RGBA", (W, W), (0, 0, 0, 0))
bd = ImageDraw.Draw(bar)
bd.rounded_rectangle([sc(17), sc(33), sc(85), sc(47)], radius=sc(3), fill=(21, 21, 31, 255))
for x0 in (21, 37, 53, 69):
    bd.polygon(
        [(sc(x0), sc(47)), (sc(x0 + 7), sc(47)), (sc(x0 + 13), sc(33)), (sc(x0 + 6), sc(33))],
        fill=(244, 244, 248, 255),
    )
bar = bar.rotate(9, center=(sc(22), sc(47)), resample=Image.BICUBIC)  # PIL positive = CCW; screen looks like Android -9
img.paste(bar, (0, 0), bar)

# Downsample for anti-aliasing and add a rounded mask (launcher squircle approximation).
out = img.resize((432, 432), Image.LANCZOS)
out.save("tools/icon_preview.png")
print("wrote tools/icon_preview.png")
