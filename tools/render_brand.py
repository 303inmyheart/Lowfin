"""Render LowFin's editable SVG design as launcher icons and TV banners.

A quiet, rounded L sits behind a bright wordmark. All artwork is local geometry
and typography; Pillow exports density-specific bitmap assets.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'design'
RES=ROOT/'app/src/main/res'
FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
ASSETS.mkdir(exist_ok=True)
L_PATH='M376 220H462V686Q462 708 484 708H712V794H418Q376 794 376 752Z'
L_POINTS=[(376,220),(462,220),(462,686),(464,696),(473,705),(484,708),(712,708),(712,794),(418,794),(402,791),(389,782),(379,768),(376,752)]

def background(w,h):
    img=Image.new('RGB',(w,h)); d=ImageDraw.Draw(img)
    for y in range(h):
        g=round(25-10*y/max(1,h-1)); d.line((0,y,w,y),fill=(g,g,g))
    return img

def emblem(image,cx,cy,scale):
    d=ImageDraw.Draw(image)
    origin=(cx-512*scale,cy-512*scale)
    d.polygon([(origin[0]+x*scale,origin[1]+y*scale) for x,y in L_POINTS],fill='#55555c')
    # A subtle inner highlight gives the monogram definition at TV distance.
    d.line([(origin[0]+390*scale,origin[1]+236*scale),(origin[0]+390*scale,origin[1]+746*scale)],fill='#73737a',width=max(1,round(2*scale)))
    font=ImageFont.truetype(FONT,round(154*scale))
    d.text((cx,cy+8*scale),'LowFin',font=font,anchor='mm',fill='#f7f7f8',stroke_width=max(1,round(5*scale)),stroke_fill='#131315')

def svg(w,h,cx,cy,scale):
    # SVG retains editable type. The exported PNGs embed the font pixels.
    x=cx-512*scale; y=cy-512*scale
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">
<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#191919"/><stop offset="1" stop-color="#0f0f0f"/></linearGradient></defs>
<rect width="{w}" height="{h}" fill="url(#bg)"/>
<g transform="translate({x} {y}) scale({scale})"><path d="{L_PATH}" fill="#55555c"/><path d="M390 236V746" stroke="#73737a" stroke-width="2"/>
<text x="512" y="530" text-anchor="middle" dominant-baseline="central" font-family="DejaVu Sans,sans-serif" font-weight="bold" font-size="154" fill="#f7f7f8" stroke="#131315" stroke-width="10" paint-order="stroke">LowFin</text></g></svg>'''

icon=background(2048,2048); emblem(icon,1024,1024,2)
for size in (1024,512): icon.resize((size,size),Image.Resampling.LANCZOS).save(ASSETS/f'lowfin-icon-{size}.png')
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    path=RES/f'mipmap-{density}'; path.mkdir(exist_ok=True); icon.resize((size,size),Image.Resampling.LANCZOS).save(path/'ic_launcher.png')
fg=Image.new('RGBA',(1728,1728),(0,0,0,0)); emblem(fg,864,864,1.68)
fg=fg.resize((432,432),Image.Resampling.LANCZOS)
fg.save(RES/'drawable-nodpi/launcher_foreground.png'); fg.save(ASSETS/'lowfin-adaptive-foreground.png')
banner=background(2560,1440); emblem(banner,1280,720,2)
for w,h in [(1280,720),(320,180)]: banner.resize((w,h),Image.Resampling.LANCZOS).save(ASSETS/f'lowfin-google-tv-{w}x{h}.png')
banner.resize((320,180),Image.Resampling.LANCZOS).save(RES/'drawable-xhdpi/tv_banner.png')
(ASSETS/'lowfin-icon.svg').write_text(svg(1024,1024,512,512,1))
(ASSETS/'lowfin-google-tv.svg').write_text(svg(1280,720,640,360,1))
preview=Image.new('RGB',(1440,580),'#080808')
preview.paste(icon.resize((500,500),Image.Resampling.LANCZOS),(40,40)); preview.paste(banner.resize((800,450),Image.Resampling.LANCZOS),(600,65))
preview.save(ASSETS/'lowfin-brand-preview.png')
print('Updated LowFin launcher icons, adaptive foreground, TV banners and SVG sources')
