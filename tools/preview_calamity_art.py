"""Offline orthographic textured z-buffer preview of the actual shipped assets.
Only writes docs/art. numpy/Pillow; no Java, game, network or OpenGL needed.
"""
from pathlib import Path
import json, math, hashlib
import numpy as np
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/shengxian'
OUT=ROOT/'docs/art'
IDS=['horned_tiger','ancient_beast','jade_willow','eight_chimes','blood_core']
TITLES=['独角虎 · 镇山','上古荒兽 · 玄甲','柳风 · 玉眼垂枝','八口音磬 · 青铜石声','血核 · 裂晶脉心']
BG=(15,25,31); GOLD=(191,158,94); WHITE=(217,229,222)
def font(size):
 for name in ['C:/Windows/Fonts/msyh.ttc','C:/Windows/Fonts/simhei.ttf']:
  if Path(name).exists(): return ImageFont.truetype(name,size)
 return ImageFont.load_default()
def rotation(r):
 x,y,z=np.radians(r);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
 return np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])
def translate(v):
 m=np.eye(4);m[:3,3]=v;return m
def sample(frames,time,default):
 if not frames:return np.array(default,float)
 keys=sorted((float(t),np.array(v,float)) for t,v in frames.items())
 if time<=keys[0][0]:return keys[0][1]
 for (a,x),(b,y) in zip(keys,keys[1:]):
  if time<=b:return x+(y-x)*(time-a)/(b-a)
 return keys[-1][1]
class Asset:
 def __init__(self,ident):
  self.ident=ident
  self.geo=json.loads((ASSETS/'geo'/f'{ident}.geo.json').read_text(encoding='utf-8'))
  g=self.geo['minecraft:geometry'][0];self.bones={b['name']:b for b in g['bones']}
  self.anim=json.loads((ASSETS/'animations'/f'{ident}.animation.json').read_text(encoding='utf-8'))['animations']
  self.texture=np.asarray(Image.open(ASSETS/'textures/entity'/f'{ident}.png').convert('RGB'))
  assert 'root' in self.bones and self.geo['format_version']=='1.12.0'
  self.count=sum(len(b.get('cubes',[])) for b in self.bones.values())
  for b in self.bones.values():
   if 'parent' in b:assert b['parent'] in self.bones
   for c in b.get('cubes',[]):
    w,h,d=c['size'];u,v=c['uv'];assert u>=0 and v>=0 and u+2*(w+d)<=self.texture.shape[1] and v+h+d<=self.texture.shape[0]
  for name,clip in self.anim.items():
   for bone,channels in clip['bones'].items():
    assert bone in self.bones
    for key,frames in channels.items():
     assert key in ['rotation','position','scale']
     for t,value in frames.items():assert 0<=float(t)<=clip['animation_length'] and len(value)==3 and all(isinstance(v,(int,float)) for v in value)
 def faces(self,clip=None,time=0):
  channels=self.anim.get('animation.'+self.ident+'.'+str(clip),{}).get('bones',{})
  cache={};active=set()
  def transform(name):
   if name in cache:return cache[name]
   assert name not in active;active.add(name)
   b=self.bones[name];p=np.array(b['pivot'],float);ch=channels.get(name,{})
   r=np.array(b.get('rotation',[0,0,0]))+sample(ch.get('rotation'),time,[0,0,0])
   s=sample(ch.get('scale'),time,[1,1,1]);pos=sample(ch.get('position'),time,[0,0,0])
   rs=np.eye(4);rs[:3,:3]=rotation(r)@np.diag(s)
   mat=translate(p+pos)@rs@translate(-p)
   if 'parent' in b:mat=transform(b['parent'])@mat
   active.remove(name);cache[name]=mat;return mat
  allfaces=[]
  # top,bottom,west,north,east,south vertices; each maps a real box-UV face.
  inds=[[2,6,7,3],[0,4,5,1],[4,0,2,6],[0,1,3,2],[1,5,7,3],[5,4,6,7]]
  for b in self.bones.values():
   mat=transform(b['name'])
   for c in b.get('cubes',[]):
    o=np.array(c['origin']);w,h,d=c['size'];u,v=c['uv']
    vertices=np.array([o+[x,y,z] for z in [0,d] for y in [0,h] for x in [0,w]],float)
    if 'rotation' in c:
     p=np.array(c.get('pivot',c['origin']));vertices=(vertices-p)@rotation(c['rotation']).T+p
    vertices=(np.c_[vertices,np.ones(8)]@mat.T)[:,:3]
    rects=[(u+d,v,w,d),(u+d+w,v,w,d),(u,v+d,d,h),(u+d,v+d,w,h),(u+d+w,v+d,d,h),(u+2*d+w,v+d,w,h)]
    for index,(x,y,fw,fh) in zip(inds,rects):
     uv=np.array([[x,y+fh-0.001],[x+fw-.001,y+fh-.001],[x+fw-.001,y],[x,y]])
     allfaces.append((vertices[index],uv))
  return allfaces

def render(asset,width,height,yaw=-28,pitch=12,clip=None,time=0,framing=None):
 faces=asset.faces(clip,time);view=rotation([pitch,yaw,0]);transformed=[(verts@view.T,uv) for verts,uv in faces]
 points=np.concatenate([p for p,u in transformed]);lo=points.min(0);hi=points.max(0)
 if framing is None:framing=((lo+hi)/2,min((width-44)/(hi[0]-lo[0]),(height-44)/(hi[1]-lo[1]))*.93)
 center,scale=framing
 image=np.zeros((height,width,3),dtype=np.uint8);image[:]=BG;depth=np.full((height,width),-np.inf)
 light=np.array([-.35,.65,-.68]);light/=np.linalg.norm(light)
 for xyz,uv in transformed:
  normal=np.cross(xyz[1]-xyz[0],xyz[3]-xyz[0]);norm=np.linalg.norm(normal)
  shade=.86+.28*abs(np.dot(normal/norm,light)) if norm else 1
  screen=np.c_[(xyz[:,0]-center[0])*scale+width/2,-(xyz[:,1]-center[1])*scale+height/2,-xyz[:,2]]
  for order in [[0,1,2],[0,2,3]]:
   p=screen[order];tx=uv[order];mn=np.maximum(np.floor(p[:,:2].min(0)).astype(int),[0,0]);mx=np.minimum(np.ceil(p[:,:2].max(0)).astype(int),[width-1,height-1])
   if np.any(mx<mn):continue
   xx,yy=np.meshgrid(np.arange(mn[0],mx[0]+1)+.5,np.arange(mn[1],mx[1]+1)+.5)
   den=(p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
   if abs(den)<1e-8:continue
   a=((p[1,1]-p[2,1])*(xx-p[2,0])+(p[2,0]-p[1,0])*(yy-p[2,1]))/den
   b=((p[2,1]-p[0,1])*(xx-p[2,0])+(p[0,0]-p[2,0])*(yy-p[2,1]))/den;c=1-a-b
   z=a*p[0,2]+b*p[1,2]+c*p[2,2];region=depth[mn[1]:mx[1]+1,mn[0]:mx[0]+1];mask=(a>=-1e-7)&(b>=-1e-7)&(c>=-1e-7)&(z>region)
   su=np.clip((a*tx[0,0]+b*tx[1,0]+c*tx[2,0]).astype(int),0,asset.texture.shape[1]-1)
   sv=np.clip((a*tx[0,1]+b*tx[1,1]+c*tx[2,1]).astype(int),0,asset.texture.shape[0]-1)
   color=np.minimum(asset.texture[sv,su].astype(float)*shade+14,255).astype(np.uint8)
   target=image[mn[1]:mx[1]+1,mn[0]:mx[0]+1];target[mask]=color[mask];region[mask]=z[mask]
 return Image.fromarray(image),framing

def label(image,xy,text,size=22,color=WHITE):ImageDraw.Draw(image).text(xy,text,font=font(size),fill=color)
def main():
 OUT.mkdir(parents=True,exist_ok=True)
 contact=Image.new('RGB',(2400,1500),BG);draw=ImageDraw.Draw(contact)
 label(contact,(50,22),'升仙 · 五类灾劫原创模型',44,GOLD)
 label(contact,(52,85),'实际 GEO + ATLAS + ANIMATION 数据 ｜ 离线模型预览，非游戏截图',23)
 report=[]
 for i,(ident,title) in enumerate(zip(IDS,TITLES)):
  asset=Asset(ident);x=i*480
  label(contact,(x+20,152),title,24,GOLD)
  main_view,_=render(asset,472,790)
  contact.paste(main_view,(x+4,205))
  for j,(yaw,text) in enumerate([(0,'正视'),(90,'侧视')]):
   thumb,_=render(asset,236,320,yaw,0);contact.paste(thumb,(x+j*240,1035));label(contact,(x+j*240+16,1008),text,19,GOLD)
  label(contact,(x+20,1380),f'{len(asset.bones)} bones / {asset.count} cubes',20)
  draw.line((x+479,145,x+479,1435),fill=(68,64,49),width=1)
  sheet=Image.new('RGB',(1600,1100),BG)
  label(sheet,(35,22),title,38,GOLD);label(sheet,(35,78),'离线模型预览，非游戏截图 · 真实贴图 / 正交投影 / 骨骼层级变换',22)
  hero,_=render(asset,950,900);sheet.paste(hero,(0,155))
  for j,(yaw,text) in enumerate([(0,'正视 FRONT'),(90,'侧视 SIDE')]):
   view,_=render(asset,600,425,yaw,0);sheet.paste(view,(980,150+j*455));label(sheet,(1000,125+j*455),text,20,GOLD)
  sheet.save(OUT/f'{ident}_views.png')
  report.append({'id':ident,'bones':len(asset.bones),'cubes':asset.count,'animations':list(asset.anim),'validation':'JSON + root/parent + cycle traversal + UV bounds + numeric keyframes passed'})
 contact.save(OUT/'calamity_contact_sheet.png')
 # Fixed framing across frames, so all motion comes from the real numeric animation.
 willow=Asset('jade_willow');_,framing=render(willow,660,720,clip='idle',time=0)
 frames=[]
 for j in range(30):
  frame,_=render(willow,660,720,clip='idle',time=j/10,framing=framing)
  label(frame,(20,15),'柳风 · idle 骨骼动画',24,GOLD)
  label(frame,(20,680),'离线模型预览，非游戏截图',19)
  frames.append(frame)
 assert any(np.any(np.asarray(f)!=np.asarray(frames[0])) for f in frames[1:])
 frames[0].save(OUT/'jade_willow_idle.gif',save_all=True,append_images=frames[1:],duration=100,loop=0,optimize=False)
 (OUT/'preview_validation.json').write_text(json.dumps({'note':'Offline model previews, not in-game screenshots. Rest bone rotation plus interpolated numeric animation rotation; orthographic z-buffer rasterization samples original box-UV atlas faces.','models':report,'gif_frames':30,'gif_seconds':3},ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps(report,ensure_ascii=False));print('Wrote 2400x1500 contact sheet, five 1600x1100 view sheets, 30-frame GIF and validation report.')
if __name__=='__main__':main()
