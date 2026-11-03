from PIL import Image,ImageDraw,ImageFilter
import math,random
W=256
img=Image.new('RGBA',(W,W),(0,0,0,0))
d=ImageDraw.Draw(img)
rng=random.Random(42)
# 三层同心底纹
for y in range(W):
 for x in range(W):
  nx,ny=x/W,y/W
  vein=0.0
  vein+=math.sin(nx*3.1*math.pi+math.sin(ny*2.3*math.pi+0.7)*1.6+0.3)
  vein+=0.7*math.sin(ny*2.8*math.pi+math.sin(nx*2.1*math.pi+1.9)*1.4+1.1)
  vein+=0.45*math.sin((nx+ny)*2.2*math.pi+math.sin((nx-ny)*1.9*math.pi+2.7)*1.2)
  vein=(vein+2.15)/4.3
  if vein>0.58:
   t=min(1.0,(vein-0.58)/0.30)
   r=0.80+t*0.20; g=0.62+t*0.28; b=0.22+t*0.10
   a=int(210+45*t)
   d.point((x,y),(int(r*255),int(g*255),int(b*255),a))
  elif vein>0.42:
   t=(vein-0.42)/0.16
   r=0.30+t*0.40; g=0.45+t*0.25; b=0.40+t*0.05
   d.point((x,y),(int(r*255),int(g*255),int(b*255),215))
  else:
   t=vein/0.42
   r=0.08+t*0.12; g=0.22+t*0.15; b=0.26+t*0.10
   d.point((x,y),(int(r*255),int(g*255),int(b*255),220))
# 三层同心圆弧金线
for k,rr in enumerate([38,72,108]):
 for seg in range(6):
  sa=seg*1.047+k*0.5+rng.uniform(-0.2,0.2)
  ea=sa+rng.uniform(0.5,1.0)
  lw=rng.choice([1,1,2])
  for step in range(80):
   a=sa+(ea-sa)*step/80
   px=int((W/2+math.cos(a)*rr)%W); py=int((W/2+math.sin(a)*rr)%W)
   d.point((px,py),(255,230,145,240))
   if lw>1:
    d.point(((px+1)%W,py),(250,215,130,225))
    d.point((px,(py+1)%W),(250,215,130,225))
# 星辉点缀
for i in range(18):
 x,y=rng.randint(4,W-5),rng.randint(4,W-5)
 d.point((x,y),(255,240,180,250))
 d.point((x-1,y),(230,200,120,200))
 d.point((x+1,y),(230,200,120,200))
img=img.filter(ImageFilter.GaussianBlur(0.7))
img.save('src/main/resources/assets/shengxian/textures/effect/ornate_gilt.png')
print('saved')