"""Render editable companion-style Lowfin vector geometry into Android launcher assets."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]; ASSETS=ROOT/'design'; RES=ROOT/'app/src/main/res'
FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'; ASSETS.mkdir(exist_ok=True)
BG='#151619'; WHITE='#f5f5f6'; ACCENT='#b5a3d3'
VERTICAL='M0 280V78C0 36 37 15 92 0V157C92 205 63 229 0 280Z'
HORIZONTAL='M16 282L108 194C130 172 166 164 195 164H282C268 235 230 282 179 282Z'
def cubic(a,b,c,d):
    return [tuple((1-t)**3*a[k]+3*(1-t)**2*t*b[k]+3*(1-t)*t*t*c[k]+t**3*d[k] for k in range(2)) for t in [i/32 for i in range(33)]]
VERT=[(0,280),(0,78)]+cubic((0,78),(0,36),(37,15),(92,0))+[(92,157)]+cubic((92,157),(92,205),(63,229),(0,280))
BASE=[(16,282),(108,194)]+cubic((108,194),(130,172),(166,164),(195,164))+[(282,164)]+cubic((282,164),(268,235),(230,282),(179,282))
def mark(img,x,y,size):
    d=ImageDraw.Draw(img); scale=size/282
    for points,color in [(VERT,'#55545f'),(BASE,'#79688f')]:d.polygon([(x+a*scale,y+b*scale) for a,b in points],fill=color)
def draw(img,kind):
    w,h=img.size; d=ImageDraw.Draw(img)
    size=(w*.43 if kind=='icon' else h*.65)
    mark(img,(w-size)/2,(h-size)/2,size)
    d.text((w/2,h*.52),'Lowfin',font=ImageFont.truetype(FONT,round(w*.13 if kind=='icon' else h*.27)),anchor='mm',fill=WHITE)
    return img

def svg(kind,w,h,transparent=False):
    size=w*.43 if kind=='icon' else h*.65
    x=(w-size)/2;y=(h-size)/2;tx=w/2;ty=h*.52;font=w*.13 if kind=='icon' else h*.27;anchor='middle'
    bg='' if transparent else f'<rect width="{w}" height="{h}" fill="{BG}"/>'
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{bg}
<g transform="translate({x} {y}) scale({size/282})"><path d="{VERTICAL}" fill="#55545f"/><path d="{HORIZONTAL}" fill="#79688f"/></g>
<text x="{tx}" y="{ty}" text-anchor="{anchor}" dominant-baseline="central" font-family="DejaVu Sans,sans-serif" font-weight="bold" font-size="{font}" fill="{WHITE}">Lowfin</text></svg>'''
icon=draw(Image.new('RGB',(2048,2048),BG),'icon')
for size in (1024,512):icon.resize((size,size),Image.Resampling.LANCZOS).save(ASSETS/f'lowfin-icon-{size}.png')
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    p=RES/f'mipmap-{density}';p.mkdir(exist_ok=True);icon.resize((size,size),Image.Resampling.LANCZOS).save(p/'ic_launcher.png')
fg=draw(Image.new('RGBA',(1728,1728),(0,0,0,0)),'icon').resize((432,432),Image.Resampling.LANCZOS)
fg.save(RES/'drawable-nodpi/launcher_foreground.png');fg.save(ASSETS/'lowfin-adaptive-foreground.png')
banner=draw(Image.new('RGB',(2560,1440),BG),'banner')
for w,h in [(1280,720),(320,180)]:banner.resize((w,h),Image.Resampling.LANCZOS).save(ASSETS/f'lowfin-google-tv-{w}x{h}.png')
banner.resize((320,180),Image.Resampling.LANCZOS).save(RES/'drawable-xhdpi/tv_banner.png')
(ASSETS/'lowfin-icon.svg').write_text(svg('icon',1024,1024));(ASSETS/'lowfin-google-tv.svg').write_text(svg('banner',1280,720))
preview=Image.new('RGB',(1440,580),'#080808');preview.paste(icon.resize((500,500),Image.Resampling.LANCZOS),(40,40));preview.paste(banner.resize((800,450),Image.Resampling.LANCZOS),(600,65));preview.save(ASSETS/'lowfin-brand-preview.png')
print('Updated companion-style launcher icons, adaptive foreground, TV banners and editable SVG sources')
