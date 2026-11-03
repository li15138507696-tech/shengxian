"""Original tribulation art. Rebuild with bundled Python + Pillow; no game build needed.
Each cube owns a non-overlapping Bedrock box-UV net, painted face by face.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import math, random, json

BASE = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/shengxian'
TEX = 512
PALETTES = {
 'stone': ((29,35,37),(72,83,81),(110,124,115)),
 'bronze': ((64,43,29),(126,89,47),(203,160,85)),
 'jade': ((15,61,57),(37,122,101),(111,207,158)),
 'leaf': ((13,49,43),(27,101,70),(97,176,105)),
 'blood': ((48,9,20),(112,20,39),(188,46,64)),
 'heart': ((105,12,35),(201,38,68),(255,128,120)),
 'glow': ((27,100,92),(82,190,153),(183,246,193)),
}

def rounded(v): return [round(x,4) for x in v]

class Model:
 def __init__(self, name):
  self.name=name; self.bones=[]; self.materials=[]
  self.bone('root',None,[0,0,0])
 def bone(self,name,parent='root',pivot=(0,0,0),rotation=None):
  b={'name':name,'pivot':rounded(pivot),'cubes':[]}
  if parent: b['parent']=parent
  if rotation: b['rotation']=rounded(rotation)
  self.bones.append(b); return name
 def cube(self,bone,origin,size,material):
  b=next(b for b in self.bones if b['name']==bone)
  c={'origin':rounded(origin),'size':list(size),'uv':[0,0]}
  b['cubes'].append(c); self.materials.append((c,material))
 def finish(self,animations):
  image=Image.new('RGB',(TEX,TEX),(12,17,20))
  # Stable largest-first shelf layout gives each cube a unique integer UV net.
  order=sorted(enumerate(self.materials),key=lambda p:(-(p[1][0]['size'][1]+p[1][0]['size'][2]),p[0]))
  x=y=row=0; occupied=[]
  for index,(cube,mat) in order:
   w,h,d=map(int,cube['size']); nw,nh=2*(w+d),h+d
   if x+nw+2>TEX: x=0; y+=row+2; row=0
   assert y+nh+2<=TEX,(self.name,'atlas overflow',y,nh)
   cube['uv']=[x+1,y+1]; occupied.append((x,y,nw+2,nh+2))
   u,v=cube['uv']
   # Bedrock UV: top/bottom strip then west/north/east/south.
   faces=[(u+d,v,w,d),(u+d+w,v,w,d),(u,v+d,d,h),(u+d,v+d,w,h),(u+d+w,v+d,d,h),(u+2*d+w,v+d,w,h)]
   for fi,rect in enumerate(faces): paint_face(image,rect,mat,index*97+fi,fi)
   x+=nw+2; row=max(row,nh)
  desc={'identifier':'geometry.shengxian.'+self.name,'texture_width':TEX,'texture_height':TEX,'visible_bounds_width':16,'visible_bounds_height':16,'visible_bounds_offset':[0,3,0]}
  geo={'format_version':'1.12.0','minecraft:geometry':[{'description':desc,'bones':self.bones}]}
  anim={'format_version':'1.8.0','animations':animations}
  for directory in ['geo','animations','textures/entity']: (BASE/directory).mkdir(parents=True,exist_ok=True)
  (BASE/'geo'/f'{self.name}.geo.json').write_text(json.dumps(geo,ensure_ascii=False,indent=2)+chr(10),encoding='utf-8')
  (BASE/'animations'/f'{self.name}.animation.json').write_text(json.dumps(anim,indent=2)+chr(10),encoding='utf-8')
  image.save(BASE/'textures/entity'/f'{self.name}.png',optimize=False)
  return validate(self,geo,anim,image,occupied)

def paint_face(atlas,rect,mat,seed,face):
 x,y,w,h=rect; rng=random.Random(seed+13073); low,mid,hi=PALETTES[mat]
 tile=Image.new('RGB',(w,h)); px=tile.load()
 for yy in range(h):
  for xx in range(w):
   grain=rng.randint(-8,8); wave=math.sin(xx*.7+yy*.19)*3
   base=mid if (xx+yy*3)%11<8 else low
   shade=(1,.83,.7,.95,.78,.88)[face]
   px[xx,yy]=tuple(max(0,min(255,int(c*shade+grain+wave))) for c in base)
 p=ImageDraw.Draw(tile)
 if w>2 and h>2:
  p.line([(0,h-1),(0,0),(w-1,0)],fill=hi)
  p.line([(w-1,1),(w-1,h-1),(1,h-1)],fill=low)
 if mat in ('jade','blood','heart','glow'):
  for k in range(max(1,w*h//100)):
   sx=rng.randrange(w); sy=rng.randrange(h)
   points=[(sx,sy),(min(w-1,sx+2),min(h-1,sy+3)),(max(0,sx-1),min(h-1,sy+6))]
   p.line(points,fill=hi,width=1)
  if mat in ('blood','heart') and w>6:
   p.line([(w//2,1),(w//2-2,h//3),(w//2+1,h//2),(w//2-1,h-2)],fill=low)
 elif mat=='leaf':
  p.line([(w//2,0),(w//2,h-1)],fill=hi)
  for yy in range(3,h,4): p.line([(0,yy-2),(w//2,yy),(w-1,yy-2)],fill=mid)
 else:
  # Stepped ritual seal carving: bronze-filled etchings on stone, patina on bronze.
  color=(70,132,110) if mat=='bronze' else (148,115,64)
  for yy in range(4,h-3,9):
   for xx in range(3,w-3,9):
    p.line([(xx,yy+3),(xx,yy),(xx+4,yy),(xx+4,yy+4),(xx+2,yy+4),(xx+2,yy+2)],fill=color)
  for k in range(max(1,w*h//90)):
   xx=rng.randrange(w); yy=rng.randrange(h); p.point((xx,yy),fill=hi)
 atlas.paste(tile,(x,y))

def channel(kind,frames): return {kind:{str(t):rounded(v) for t,v in frames}}
def clip(model,label,length,loop,bones):
 return ('animation.'+model+'.'+label,{'loop':loop,'animation_length':length,'bones':bones})
def rotation(a,b,c=0): return channel('rotation',[(0,a),(.5,b),(1,c if isinstance(c,list) else a)])
def animations(m,moving,center):
 n=m.name; idle={center:channel('scale',[(0,[1,1,1]),(1.5,[1.035,1.055,1.035]),(3,[1,1,1])])}
 for i,b in enumerate(moving): idle[b]=channel('rotation',[(0,[0,0,-3]),(1.5,[4 if i%2 else -4,0,4]),(3,[0,0,-3])])
 charge={center:channel('scale',[(0,[1,1,1]),(.8,[.88,1.12,.88]),(1.2,[1.09,1.09,1.09])])}
 attack={center:channel('rotation',[(0,[0,0,0]),(.2,[0,-16,-8]),(.4,[0,24,12]),(.9,[0,0,0])])}
 death={'root':channel('position',[(0,[0,0,0]),(1,[0,-7,0]),(2.2,[0,-22,0])])}
 death['root'].update(channel('scale',[(0,[1,1,1]),(1.5,[.8,.8,.8]),(2.2,[.01,.01,.01])]))
 for i,b in enumerate(moving):
  s=-1 if i%2 else 1
  charge[b]=channel('rotation',[(0,[0,0,0]),(.8,[s*12,0,s*18]),(1.2,[0,s*10,0])])
  attack[b]=channel('rotation',[(0,[0,0,0]),(.18,[s*-9,0,s*-12]),(.4,[s*24,s*14,s*27]),(.9,[0,0,0])])
  death[b]=channel('rotation',[(0,[0,0,0]),(1,[s*20,s*10,s*25]),(2.2,[s*65,s*35,s*70])])
 hurt={'root':channel('rotation',[(0,[0,0,0]),(.1,[6,0,-7]),(.22,[-4,0,5]),(.5,[0,0,0])])}
 return dict([clip(n,'idle',3,True,idle),clip(n,'charge',1.2,False,charge),clip(n,'attack',.9,False,attack),clip(n,'hurt',.5,False,hurt),clip(n,'death',2.2,False,death)])

def willow():
 m=Model('jade_willow'); m.bone('wind_eye',pivot=[0,42,0])
 # Open 12-sided jade torus, with bronze inner rails and offset dark backing.
 for i in range(12):
  a=i*30; b=m.bone(f'eye_arc_{i}','wind_eye',[0,42,0],[0,0,a])
  m.cube(b,[-4,59,-4],[8,5,8],'jade')
  m.cube(b,[-3,59,-5],[6,2,1],'bronze')
  if i%2==0: m.cube(b,[-1,61,-6],[2,2,1],'glow')
 m.bone('crown','wind_eye',[0,62,0]);m.cube('crown',[-6,64,-3],[12,3,6],'bronze');m.cube('crown',[-2,67,-2],[4,5,4],'jade')
 # Distinct sweeping forked boughs with parented drooping tendrils.
 moving=[]
 for i in range(8):
  a=i*45; rad=math.radians(a); x=round(math.cos(rad)*20); z=round(math.sin(rad)*14)
  top=55+(i%3)*3
  b=m.bone(f'bough_{i}','wind_eye',[x,top,z],[0,-a,(-1 if x<0 else 1)*10]); moving.append(b)
  m.cube(b,[x-2,top-5,z-2],[4,10,4],'bronze')
  for j in range(3):
   name=m.bone(f'willow_{i}_{j}',b if j==0 else f'willow_{i}_{j-1}',[x,top-5-j*13,z],[(-1)**i*4,0,(-1)**i*6])
   moving.append(name)
   m.cube(name,[x-1,top-18-j*13,z-1],[2,13,2],'jade')
   m.cube(name,[x-5 if j%2==0 else x+1,top-17-j*13,z-1],[4,9,2],'leaf')
   if j==1: m.cube(name,[x+1,top-11-j*13,z-1],[4,7,2],'leaf')
 return m,animations(m,moving,'wind_eye')

def chimes():
 m=Model('eight_chimes');m.bone('suspension',pivot=[0,90,0]);m.bone('qing_body','suspension',[0,76,0],[0,0,-13])
 # An asymmetric bent L lithophone. The open right-hand quadrant is intentional.
 m.cube('qing_body',[-22,29,-4],[13,49,8],'stone')
 m.cube('qing_body',[-20,22,-4],[43,13,8],'stone')
 m.cube('qing_body',[-24,31,-3],[2,43,6],'bronze')
 m.cube('qing_body',[-19,20,-3],[40,2,6],'bronze')
 m.cube('qing_body',[-20,72,-5],[10,3,10],'bronze')
 m.cube('qing_body',[18,24,-5],[5,9,10],'bronze')
 # Parallel incised inlays, rivets and protruding jade resonance plugs.
 for side in [-1,1]:
  z=side*5
  for j in range(6):
   m.cube('qing_body',[-20,34+j*6,z],[2,3,1],'jade')
   m.cube('qing_body',[-12,34+j*6,z],[2,2,1],'bronze')
  for j in range(6):
   m.cube('qing_body',[-13+j*6,25,z],[3,2,1],'jade')
   m.cube('qing_body',[-13+j*6,31,z],[2,2,1],'bronze')
 # Upper bronze mythical beast, two horns and an open-jawed chain socket.
 m.bone('beast_head','suspension',[0,86,0])
 for origin,size,mat in [([-9,83,-5],[18,8,10],'bronze'),([-6,80,-6],[12,3,12],'stone'),([-4,85,-8],[8,4,3],'bronze'),([-7,87,-9],[3,2,2],'glow'),([4,87,-9],[3,2,2],'glow'),([-8,91,-3],[3,5,4],'jade'),([5,91,-3],[3,5,4],'jade')]: m.cube('beast_head',origin,size,mat)
 for side in [-1,1]:
  b=m.bone('horn_'+str(side),'beast_head',[side*7,92,0],[0,0,side*-25]);m.cube(b,[side*7-1,91,-2],[2,17,4],'bronze')
 for j in range(3):
  b=m.bone(f'chain_{j}','suspension',[-15,77+j*4,0])
  m.cube(b,[-17,76+j*4,-2],[1,4,4],'bronze');m.cube(b,[-14,76+j*4,-2],[1,4,4],'bronze')
 # Eight independently rigged, differently placed overtone keys. Reuse at eight yaw angles.
 moving=['qing_body','beast_head']
 for i in range(8):
  x=-24 if i<4 else -6+(i-4)*8; y=40+i*8 if i<4 else 19
  b=m.bone(f'overtone_{i}','qing_body',[x,y,0],[0,0,(-1)**i*(8+i*2)]);moving.append(b)
  m.cube(b,[x-2,y-5,-3],[4,7+(i%3),6],'bronze');m.cube(b,[x-1,y-4,-4],[2,4,1],'glow')
 return m,animations(m,moving,'qing_body')

def blood():
 m=Model('blood_core');m.bone('pulse_heart',pivot=[0,26,0])
 # Exposed cruciform pulse chamber, deliberately not a solid sphere.
 for o,s,mat in [([-5,18,-5],[10,16,10],'heart'),([-8,22,-4],[16,8,8],'blood'),([-3,16,-3],[6,20,6],'heart'),([-2,21,-6],[4,10,1],'heart')]: m.cube('pulse_heart',o,s,mat)
 moving=[]
 for ring in range(2):
  for i in range(6):
   a=i*60+ring*30; b=m.bone(f'shell_{ring}_{i}',pivot=[0,26,0],rotation=[0,a,(-1)**i*(12+ring*7)]); moving.append(b)
   # Long crystal blade, cap, basal facet, dark bevel and bright vein.
   y=8+ring*8; radius=12+ring*5
   m.cube(b,[-4,y,radius],[8,23-ring*4,5],'blood')
   m.cube(b,[-3,y+23-ring*4,radius+1],[6,5,3],'blood')
   m.cube(b,[-2,y-4,radius+1],[4,4,3],'stone')
   m.cube(b,[-5,y+4,radius+1],[1,14,3],'stone')
   m.cube(b,[-1,y+5,radius-1],[2,12,1],'heart')
 for i in range(10):
  a=i*36+12; y=8+(i%4)*8
  b=m.bone(f'shard_{i}',pivot=[0,26,0],rotation=[(-1)**i*9,a,(-1)**i*17]);moving.append(b)
  m.cube(b,[-2,y,22],[4,8,4],'blood');m.cube(b,[-1,y+8,23],[2,3,2],'heart')
 return m,animations(m,moving,'pulse_heart')

# Exact rest-pose world bounds, composing bone transforms about absolute pivots.
def matrix(rotation):
 x,y,z=[math.radians(v) for v in rotation];cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
 return [[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]]
def transformed(point,bone,byname):
 p=bone['pivot'];r=matrix(bone.get('rotation',[0,0,0]));v=[point[i]-p[i] for i in range(3)]
 q=[sum(r[i][j]*v[j] for j in range(3))+p[i] for i in range(3)]
 return transformed(q,byname[bone['parent']],byname) if 'parent' in bone else q

def validate(model,geo,anim,image,occupied):
 names={b['name'] for b in model.bones};assert len(names)==len(model.bones) and 'root' in names
 byname={b['name']:b for b in model.bones};points=[];count=0
 for b in model.bones:
  if 'parent' in b: assert b['parent'] in names
  for c in b['cubes']:
   count+=1;assert set(c)=={'origin','size','uv'}
   w,h,d=c['size'];u,v=c['uv'];assert min(c['size'])>0 and u+2*(w+d)<=TEX and v+h+d<=TEX
   for x in [c['origin'][0],c['origin'][0]+w]:
    for y in [c['origin'][1],c['origin'][1]+h]:
     for z in [c['origin'][2],c['origin'][2]+d]: points.append(transformed([x,y,z],b,byname))
 for i,a in enumerate(occupied):
  for b in occupied[:i]: assert a[0]+a[2]<=b[0] or b[0]+b[2]<=a[0] or a[1]+a[3]<=b[1] or b[1]+b[3]<=a[1]
 assert 60<=count<=150,(model.name,count)
 assert image.size==(512,512) and len(image.getcolors(512*512))>500
 for key,clipdata in anim['animations'].items():
  assert key.startswith('animation.'+model.name+'.')
  for name,channels in clipdata['bones'].items():
   assert name in names
   for kind,frames in channels.items():
    assert kind in ['rotation','position','scale']
    for time,value in frames.items(): assert 0<=float(time)<=clipdata['animation_length'] and len(value)==3 and all(isinstance(x,(int,float)) for x in value)
 bounds=[rounded([min(p[i] for p in points) for i in range(3)]),rounded([max(p[i] for p in points) for i in range(3)])]
 return {'id':model.name,'bones':len(model.bones),'cubes':count,'bounds_units':bounds,'size_blocks':rounded([(bounds[1][i]-bounds[0][i])/16 for i in range(3)]),'animations':list(anim['animations'])}

if __name__=='__main__':
 for factory in [willow,chimes,blood]:
  model,anim=factory();print(json.dumps(model.finish(anim),ensure_ascii=False))
