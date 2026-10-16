#!/usr/bin/env python3
"""Builds the resource pack from tools/art/*.jpeg (element icon sheet + orb concept art). Needs Pillow, numpy, scipy."""
import json, os, colorsys
import numpy as np
from PIL import Image, ImageDraw, ImageOps
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "resourcepack")
TEX = os.path.join(OUT, "assets/marbles/textures")
for d in ["textures/font", "textures/item", "font", "items", "models/item"]:
    os.makedirs(os.path.join(OUT, "assets/marbles", d), exist_ok=True)
CELL = 64
ORDER = ["fire","water","lightning","earth","ice","wind","void","nature","light","magma"]

def runs(mask1d, min_len=20):
    out, start = [], None
    for i, v in enumerate(mask1d):
        if v and start is None: start = i
        if not v and start is not None:
            if i - start >= min_len: out.append((start, i))
            start = None
    if start is not None and len(mask1d) - start >= min_len: out.append((start, len(mask1d)))
    return out

# ---------- element icons from the sheet ----------
sheet = Image.open(os.path.join(HERE, "art/elements.jpeg")).convert("RGB")
arr = np.asarray(sheet).astype(int)
bg = arr[5, 5]
H = arr.shape[0]
while H > 0 and arr[H-1].max() < 12: H -= 1       # trim black bar
diff = np.abs(arr[:H] - bg).sum(axis=2) > 40
tiles = []
for (y0, y1) in runs(diff.sum(axis=1) > 3):
    for (x0, x1) in runs(diff[y0:y1].sum(axis=0) > 3):
        tiles.append((x0, y0, x1, y1))
assert len(tiles) == 10, f"expected 10 tiles, found {len(tiles)}"

def cut(box):
    x0, y0, x1, y1 = box
    crop = sheet.crop((x0-2, y0-2, x1+2, y1+2))
    a = np.asarray(crop).astype(int)
    near = np.abs(a - bg).sum(axis=2) < 45
    lab, _ = ndi.label(near)
    border = set(lab[0].tolist()+lab[-1].tolist()+lab[:,0].tolist()+lab[:,-1].tolist()) - {0}
    outside = np.isin(lab, list(border))
    alpha = np.where(outside, 0, 255).astype(np.uint8)
    im = crop.convert("RGBA"); im.putalpha(Image.fromarray(alpha))
    side = max(im.size); sq = Image.new("RGBA", (side, side), (0,0,0,0))
    sq.paste(im, ((side-im.width)//2, (side-im.height)//2))
    return sq.resize((CELL, CELL), Image.LANCZOS)

def mark(tile, n):
    t = tile.copy(); d = ImageDraw.Draw(t)
    for i in range(n):
        x = CELL-22 + i*8
        d.rectangle((x-1, CELL-24, x+4, CELL-8), fill=(0,0,0,255))
        d.rectangle((x, CELL-23, x+3, CELL-9), fill=(255,255,255,255))
    return t

def lock_tile(base):
    g = ImageOps.grayscale(base.convert("RGB")).point(lambda v: int(v*0.5)).convert("RGBA")
    g.putalpha(base.getchannel("A")); d = ImageDraw.Draw(g)
    d.arc((20,10,44,38), 180, 360, fill=(235,235,235,255), width=5)
    d.rectangle((16,28,48,52), fill=(235,235,235,255), outline=(0,0,0,255), width=2)
    d.rectangle((29,35,35,46), fill=(30,30,30,255))
    return g

icons = Image.new("RGBA", (CELL*10, CELL*3), (0,0,0,0))
cut_tiles = [cut(b) for b in tiles]          # row-major: matches ORDER
for i, t in enumerate(cut_tiles):
    icons.paste(mark(t,1), (i*CELL, 0)); icons.paste(mark(t,2), (i*CELL, CELL))
icons.paste(lock_tile(cut_tiles[3]), (0, CELL*2))
icons.save(os.path.join(TEX, "font/icons.png"))

# ---------- orbs (trader / energy / reroller) ----------
orbs = Image.open(os.path.join(HERE, "art/orbs.jpeg")).convert("RGB")
oa = np.asarray(orbs).astype(int)
OH = oa.shape[0]
while OH > 0 and oa[OH-1].max() < 12: OH -= 1

def orb(x0, x1):
    sub = oa[:OH, x0:x1]; mask = sub.max(axis=2) > 48
    mask = ndi.binary_dilation(mask, iterations=4)
    lab, n = ndi.label(mask)
    big = np.argmax(ndi.sum(mask, lab, range(1, n+1))) + 1
    comp = ndi.binary_fill_holes(lab == big)
    comp = ndi.binary_erosion(comp, iterations=3)
    ys, xs = np.where(comp); y0, y1, x0b, x1b = ys.min(), ys.max()+1, xs.min(), xs.max()+1
    rgb = Image.fromarray(sub.astype(np.uint8)).convert("RGBA")
    alpha = Image.fromarray((ndi.gaussian_filter(comp.astype(float), 1.2)*255).astype(np.uint8))
    rgb.putalpha(alpha)
    im = rgb.crop((x0b, y0, x1b, y1)); side = max(im.size)
    sq = Image.new("RGBA", (side, side), (0,0,0,0)); sq.paste(im, ((side-im.width)//2, (side-im.height)//2))
    return sq.resize((128,128), Image.LANCZOS)

mid = oa.shape[1]//2
trader, energy = orb(0, mid), orb(mid, oa.shape[1])
hsv = energy.convert("RGB").convert("HSV"); h, s, v = hsv.split()
h = h.point(lambda x: (x+95) % 256)
reroll = Image.merge("HSV", (h, s, v)).convert("RGB").convert("RGBA"); reroll.putalpha(energy.getchannel("A"))
for name, im in [("energy_marble", energy), ("marble_trader", trader), ("element_reroller", reroll)]:
    im.save(os.path.join(TEX, f"item/{name}.png"))
    json.dump({"model":{"type":"minecraft:model","model":f"marbles:item/{name}"}},
              open(os.path.join(OUT, f"assets/marbles/items/{name}.json"),"w"))
    json.dump({"parent":"minecraft:item/generated","textures":{"layer0":f"marbles:item/{name}"}},
              open(os.path.join(OUT, f"assets/marbles/models/item/{name}.json"),"w"))

# ---------- digits ----------
D = {"0":[" ### ","#   #","#  ##","# # #","##  #","#   #"," ### "],"1":["  #  "," ##  ","  #  ","  #  ","  #  ","  #  "," ### "],
"2":[" ### ","#   #","    #","   # ","  #  "," #   ","#####"],"3":[" ### ","#   #","    #","  ## ","    #","#   #"," ### "],
"4":["   # ","  ## "," # # ","#  # ","#####","   # ","   # "],"5":["#####","#    ","#### ","    #","    #","#   #"," ### "],
"6":[" ### ","#    ","#### ","#   #","#   #","#   #"," ### "],"7":["#####","    #","   # ","  #  "," #   "," #   "," #   "],
"8":[" ### ","#   #","#   #"," ### ","#   #","#   #"," ### "],"9":[" ### ","#   #","#   #"," ####","    #","    #"," ### "],
"s":["     "," ####","#    "," ### ","    #","#### ","     "],"v":["     ","    #","   # ","#  # "," ##  ","  #  ","     "],
"E":["#####","#    ","#    ","#### ","#    ","#    ","#####"]}
keys = list("0123456789svE")   # E100..E109 digits, E10A 's', E10B check, E10C 'E'
dg = Image.new("RGBA", (8*len(keys), 8), (0,0,0,0)); dp = dg.load()
for i, k in enumerate(keys):
    for r, row in enumerate(D[k]):
        for c, ch in enumerate(row):
            if ch == "#": dp[i*8+c, r] = (255,255,255,255)
    dp[i*8+4, 7] = (255,255,255,1)      # invisible pixel -> fixed 5px glyph width
dg.save(os.path.join(TEX, "font/digits.png"))

u = chr
json.dump({"providers":[
 {"type":"bitmap","file":"marbles:font/icons.png","height":16,"ascent":16,
  "chars":["".join(u(0xE000+i) for i in range(10)),"".join(u(0xE010+i) for i in range(10)),u(0xE020)+"\u0000"*9]},
 {"type":"bitmap","file":"marbles:font/digits.png","height":8,"ascent":-1,
  "chars":["".join(u(0xE100+i) for i in range(len(keys)))]},
 {"type":"space","advances":{u(0xE0F0):-17.0,u(0xE0F1):6.0,u(0xE0F2):10.0}}]},
 open(os.path.join(OUT,"assets/marbles/font/hud.json"),"w"))
json.dump({"pack":{"pack_format":75,"description":"Marbles OP AH Abilities",
 "supported_formats":{"min_inclusive":46,"max_inclusive":75}}}, open(os.path.join(OUT,"pack.mcmeta"),"w"), indent=1)
print("pack generated; tiles:", len(tiles))
