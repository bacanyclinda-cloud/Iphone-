#!/usr/bin/env python3
"""Create 120 ORIGINAL, usable offline abstract wallpapers for Velocity Launcher.

Deterministic procedural artwork made with NumPy + Pillow. These are *actual*
wallpapers shown by the Android gallery, not dummy bytes or artificial padding.
The generator runs in GitHub Actions so the repo stays small; JPEG files are
packed in the final Android APK and are usable without a network connection.
"""
from pathlib import Path
import argparse
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

FAMILIES = [
    ('Atlantic', (7, 34, 76), (19, 121, 175), (104, 221, 235)),
    ('Amethyst', (19, 10, 50), (99, 49, 187), (197, 118, 246)),
    ('Aurora', (4, 38, 57), (35, 142, 137), (139, 106, 230)),
    ('Midnight', (4, 11, 32), (18, 50, 120), (93, 120, 240)),
    ('Neon Drive', (4, 11, 26), (22, 70, 150), (72, 189, 239)),
    ('Pearl', (57, 69, 119), (153, 171, 214), (228, 224, 241)),
    ('Copper', (26, 13, 37), (116, 51, 99), (241, 143, 108)),
    ('Lagoon', (4, 48, 53), (35, 145, 159), (102, 216, 171)),
    ('Iris', (32, 18, 72), (97, 74, 204), (237, 124, 189)),
    ('Glacier', (23, 64, 107), (96, 163, 220), (190, 227, 247)),
]

COUNT = 120
WIDTH, HEIGHT = 1200, 2600

def render(index, dest, width=WIDTH, height=HEIGHT):
    family = index // 12
    variant = index % 12
    rng = np.random.default_rng(4321 + index * 937)
    name, c0, c1, c2 = FAMILIES[family]
    w, h = 360, 780
    yy, xx = np.mgrid[0:h, 0:w].astype('float32')
    x, y = xx / w, yy / h
    angle = .6 + variant * .38
    drift = .12 * np.sin((y * 1.8 + x * .8) * np.pi + angle)
    t = np.clip(.14 + .83*y + .2*np.sin((x * 1.3 - y *.8)*np.pi + angle) + drift,0,1)
    bg = (1-t[...,None])*np.array(c0) + t[...,None]*np.array(c1)
    cx = .24 + .58*((variant * 7) % 13)/12
    cy = .22 + .48*((variant * 11) % 17)/16
    r = np.sqrt(((x-cx)*.85)**2 + ((y-cy)*1.05)**2)
    halo = (np.exp(-(r/.27)**2) *.7)[...,None]
    bg = bg*(1-halo) + np.array(c2)*halo
    # Floating glass ribbons, real image geometry rather than repeated flat gradients.
    cx2 = .3 + .22*np.sin(index * 2.34)
    wave = x - (.5 + .31*np.sin(y*3.7 + angle) + .07*np.cos(y*12.8 + angle))
    gloss = np.exp(-(wave/.05)**2)*.36 + np.exp(-((wave-.12)/.11)**2)*.15
    bg = bg*(1-gloss[...,None]) + np.array(c2)*gloss[...,None]
    shard = np.exp(-((x-(cx2+.18*np.sin(2*np.pi*y+angle)))/.19)**2)*np.maximum(0,.58-y)*.2
    bg += shard[...,None]*np.array([26,44,51])
    bg = np.clip(bg,0,255).astype(np.uint8)
    image = Image.fromarray(bg,'RGB').resize((width,height), Image.Resampling.BICUBIC)
    # Atmospheric arcs and optical reflections; every design is unique.
    d = ImageDraw.Draw(image,'RGBA')
    for j in range(3):
        xcen = int(width*(.18 + .67*rng.random()))
        ycen = int(height*(.21 + .52*rng.random()))
        rad = int(width*(.2 + .8*rng.random()))
        box=(xcen-rad,ycen-rad*2,xcen+rad,ycen+rad*2)
        d.arc(box,start=int(rng.integers(170,260)),end=int(rng.integers(305,390)),fill=(*c2,int(28+rng.integers(0,35))),width=int(2+5*rng.random()))
    if family==4:  # Night-drive light trails
        for line in range(4):
            sy = height*(.59 + line*.04)
            points=[]
            for j in range(31):
                t1=j/30
                points.append((int(-width*.08+width*1.18*t1),int(sy+height*.09*np.sin(t1*3.9 + variant*.3))))
            d.line(points, fill=(*c2,65-line*10),width=2+line)
    # Real subtle fine photographic film grain. It also improves the appearance of
    # continuous-tone gradients on displays without color banding.
    arr=np.asarray(image,dtype=np.int16).copy()
    noise=rng.normal(0,4.8,(height,width,1))
    arr=np.clip(arr+noise,0,255).astype('uint8')
    image=Image.fromarray(arr,'RGB')
    dest.parent.mkdir(parents=True,exist_ok=True)
    image.save(dest,format='JPEG',quality=93,optimize=True,subsampling=0)
    return name, dest.stat().st_size


def main():
    p=argparse.ArgumentParser(); p.add_argument('--output', default='app/src/main/assets/wallpapers');p.add_argument('--count',type=int,default=COUNT);p.add_argument('--width',type=int,default=WIDTH);p.add_argument('--height',type=int,default=HEIGHT)
    args=p.parse_args()
    base=Path(args.output);total=0
    for i in range(args.count):
        name,size=render(i, base/f'wall_{i:03d}.jpg',args.width,args.height)
        total+=size
        if (i+1)%12==0: print(f'{i+1}/{args.count}: {name}  {total/1024/1024:.1f} MiB',flush=True)
    print(f'{args.count} REAL offline wallpapers: {total/1024/1024:.1f} MiB')

if __name__=='__main__': main()
