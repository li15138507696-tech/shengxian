from PIL import Image
import math
from pathlib import Path
size=128
im=Image.new('RGBA',(size,size))
for y in range(size):
 for x in range(size):
  u=x/size*math.tau;v=y/size*math.tau
  warp=math.sin(u*2+math.sin(v*3))*.8+math.sin(v*2)*.5
  grain=math.sin(u*11+warp*3+v*4)*.5+.5
  strata=abs(math.sin(v*4+warp))
  value=int(170+54*strata+18*grain)
  im.putpixel((x,y),(value,min(255,value+6),min(255,value+9),255))
p=Path('src/main/resources/assets/shengxian/textures/effect/qi_marble.png')
p.parent.mkdir(parents=True,exist_ok=True)
im.save(p)
print('Wrote seamless mineral-flow material',p)
