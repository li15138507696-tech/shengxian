"""Original Shengxian beast art. Run with bundled Python + Pillow; no game build.
Geometry is authored at 16 units/block, without renderer scale."""
from pathlib import Path
import json, random, math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/shengxian'
SIZE = 512

def atlas(species):
    rng = random.Random(721 if species == 'horned_tiger' else 943)
    im = Image.new('RGB', (SIZE, SIZE))
    palettes = ([(48,43,35),(119,87,40),(27,85,72),(173,153,106)] if species == 'horned_tiger'
                else [(24,33,46),(77,69,53),(20,57,108),(152,151,128)])
    px = im.load()
    for mat, base in enumerate(palettes):
        ox, oy = (mat % 2)*256, (mat // 2)*256
        for y in range(256):
            for x in range(256):
                n = rng.choice([-9,-5,-3,0,0,2,4,7])
                edge = 5 if (x+y*3)%29 == 0 else 0
                px[ox+x,oy+y] = tuple(max(0,min(255,c+n+edge+rng.choice([-1,0,0,1]))) for c in base)
        d = ImageDraw.Draw(im)
        for row in range(0,256,32):
            for col in range(-16,256,32):
                xx = ox+max(1,col+(16 if row%64 else 0))
                yy = oy+row
                dark = tuple(max(0,c-22) for c in base)
                light = tuple(min(255,c+22) for c in base)
                # Engraved rectilinear meanders, weathered plate seams and rivets.
                d.line([(xx,yy+2),(min(ox+254,xx+21),yy+2),(min(ox+254,xx+21),yy+20),(min(ox+254,xx+6),yy+20),(min(ox+254,xx+6),yy+12)],fill=dark,width=1)
                d.point((min(ox+254,xx+2),yy+5),fill=light)
        if mat == 0:
            if species == 'horned_tiger':
                for y in range(0,256,24):
                    for x in range(0,256,48):
                        # Tapered broken tiger bars rather than a flat recolour.
                        xx=x+(y//24%2)*17
                        d.polygon([(ox+xx,oy+y),(min(ox+255,ox+xx+8),oy+y),(min(ox+255,ox+xx+27),min(oy+255,oy+y+18)),(min(ox+255,ox+xx+16),min(oy+255,oy+y+13))],fill=(17,20,20))
            else:
                for _ in range(80):
                    x,y=rng.randrange(250),rng.randrange(240)
                    pts=[(ox+x,oy+y),(ox+min(255,x+5),oy+y+6),(ox+max(0,x-2),oy+y+11),(ox+min(255,x+7),oy+y+15)]
                    d.line(pts,fill=(8,15,27),width=3)
                    d.line(pts,fill=(27,86,155),width=1)
        elif mat == 2:
            for _ in range(110):
                x,y=rng.randrange(252),rng.randrange(252)
                glow=(93,213,167) if species=='horned_tiger' else (69,152,238)
                d.line([(ox+x,oy+y),(ox+x+2,oy+y+3)],fill=glow)
        elif mat == 3:
            for y in range(5,256,13):
                d.line([(ox+1,oy+y),(ox+254,oy+min(255,y+5))],fill=tuple(max(0,c-26) for c in base))
            # Small rust/blood flecks in the ancient ivory.
            for _ in range(150):
                x,y=rng.randrange(255),rng.randrange(255)
                d.point((ox+x,oy+y),fill=(91,38,34))
    return im

class Model:
    def __init__(self, ident):
        self.ident=ident; self.bones=[]; self.byname={}; self.rng=random.Random(31)
    def bone(self,name,parent,pivot):
        b={'name':name,'pivot':list(pivot),'cubes':[]}
        if parent is not None: b['parent']=parent
        self.bones.append(b); self.byname[name]=b
        return name
    def cube(self,bone,origin,size,mat=0):
        w,h,d=size
        assert min(size)>0
        uw=2*(w+d); uh=h+d
        assert uw<=254 and uh<=254,(size,uw,uh)
        u=(mat%2)*256+self.rng.randrange(1,int(256-uw))
        v=(mat//2)*256+self.rng.randrange(1,int(256-uh))
        self.byname[bone]['cubes'].append({'origin':list(origin),'size':list(size),'uv':[u,v]})
    def save(self):
        desc={'identifier':'geometry.shengxian.'+self.ident,'texture_width':SIZE,'texture_height':SIZE,'visible_bounds_width':20,'visible_bounds_height':18,'visible_bounds_offset':[0,6,0]}
        geo={'format_version':'1.12.0','minecraft:geometry':[{'description':desc,'bones':self.bones}]}
        out=ASSETS/'geo'/f'{self.ident}.geo.json'; out.parent.mkdir(parents=True,exist_ok=True)
        out.write_text(json.dumps(geo,indent=2)+'\n',encoding='utf-8')
        tex=ASSETS/'textures/entity'/f'{self.ident}.png'; tex.parent.mkdir(parents=True,exist_ok=True); atlas(self.ident).save(tex)
        animations(self)
        validate(self,geo,tex)

def tiger():
    m=Model('horned_tiger'); m.bone('root',None,[0,0,0]); m.bone('body','root',[0,64,0])
    m.cube('body',[-27,44,-38],[54,36,40]); m.cube('body',[-24,47,2],[48,30,42]); m.cube('body',[-20,40,-25],[40,10,52])
    m.bone('shoulders','body',[0,72,-31]); m.cube('shoulders',[-32,61,-49],[64,23,30])
    for s in [-1,1]:
        for i in range(3):
            m.cube('shoulders',[s*27-5,58+i*7,-44+i*6],[10,14,18],1)
    # Segmented curved dark-gold dorsal armour, separated from the flesh.
    for i,(y,z) in enumerate([(78,-43),(84,-24),(87,-4),(84,16),(78,33)]):
        m.cube('body',[-22,y,z],[44,7,18],1)
        m.cube('body',[-9,y+7,z+3],[18,4,12],1)
    m.bone('neck','shoulders',[0,76,-43]); m.cube('neck',[-23,62,-65],[46,27,28])
    m.bone('head','neck',[0,80,-64]); m.cube('head',[-23,65,-84],[46,26,25]); m.cube('head',[-17,63,-98],[34,16,19]); m.cube('head',[-11,74,-100],[22,9,5],1)
    m.bone('jaw','head',[0,66,-79]); m.cube('jaw',[-16,57,-96],[32,7,20])
    for s in [-1,1]:
        x=15*s
        m.cube('jaw',[x-3,54,-93],[6,12,5],3)
        m.cube('head',[s*19-4,73,-87],[8,5,4],2)
        m.cube('head',[s*18-6,82,-79],[12,5,17],1)
        m.bone('ear_'+str(s),'head',[s*21,88,-65]); m.cube('ear_'+str(s),[s*23-6,88,-70],[12,12,8]); m.cube('ear_'+str(s),[s*23-3,91,-71],[6,7,2],1)
        m.cube('head',[s*23-4,62,-77],[8,12,17],1)
    m.bone('horn','head',[0,88,-75])
    for x,y,z,w,h,d in [(-7,88,-81,14,8,14),(-5,96,-84,10,6,11),(-3,102,-87,6,6,8),(-1,108,-90,2,4,4)]:
        m.cube('horn',[x,y,z],[w,h,d],3)
    # Broad articulated paws, individual hooked stone talons and heavy shoulder mass.
    for side,s in [('left',-1),('right',1)]:
        for end,z in [('front',-40),('rear',29)]:
            leg=f'{end}_{side}'; x=s*24
            m.bone(leg,'body',[x,62,z]); m.cube(leg,[x-10,27,z-10],[20,37,22]); m.cube(leg,[x-12,44,z-11],[24,20,24],1)
            shin=leg+'_shin'; m.bone(shin,leg,[x,29,z]); m.cube(shin,[x-8,8,z-8],[16,25,18]); m.cube(shin,[x-10,11,z-11],[20,14,8],1)
            paw=leg+'_paw'; m.bone(paw,shin,[x,9,z-4]); m.cube(paw,[x-13,1,z-20],[26,11,32])
            for dx in [-8,0,8]: m.cube(paw,[x+dx-2,0,z-25],[4,5,12],3)
    parent='body'
    for i,(x,y,z,w,h,d) in enumerate([(0,58,39,12,12,22),(4,53,57,10,10,19),(11,49,72,8,8,14),(14,50,82,7,8,10)]):
        name=f'tail_{i}'; m.bone(name,parent,[x,y,z]); m.cube(name,[x-w/2,y,z],[w,h,d],1 if i==3 else 0); parent=name
    return m

def beast():
    m=Model('ancient_beast'); m.bone('root',None,[0,0,0]); m.bone('body','root',[0,98,8])
    m.cube('body',[-31,66,-12],[62,48,48]); m.cube('body',[-28,109,-17],[56,35,48]); m.cube('body',[-25,72,-19],[50,50,9],2)
    m.bone('chest','body',[0,125,-9]); m.cube('chest',[-39,112,-26],[78,27,28],3)
    for s in [-1,1]:
        for i in range(4):
            m.cube('chest',[s*22-12,81+i*13,-25],[24,10,10],3)
        m.cube('chest',[s*32-7,135,-19],[14,13,34],3)
    m.bone('neck','body',[0,143,-5]); m.cube('neck',[-20,136,-20],[40,23,35])
    m.bone('head','neck',[0,156,-15]); m.cube('head',[-23,146,-40],[46,29,30],3); m.cube('head',[-17,143,-52],[34,18,18]); m.cube('head',[-12,151,-55],[24,11,7],3)
    m.cube('head',[-5,143,-56],[10,29,5],2)
    for s in [-1,1]:
        m.cube('head',[s*17-5,159,-43],[10,5,4],2); m.cube('head',[s*20-9,165,-44],[18,8,13],3)
        name='crown_'+str(s); m.bone(name,'head',[s*20,169,-21]); m.cube(name,[s*24-6,168,-26],[12,15,13],3); m.cube(name,[s*29-4,179,-20],[8,10,9],3)
    m.bone('jaw','head',[0,147,-25]); m.cube('jaw',[-20,137,-48],[40,9,23],3)
    for x in [-14,-6,6,14]: m.cube('jaw',[x-2,144,-50],[4,9,5],3)
    # Tall swept spinal buttresses distinguish this vertical silhouette from the tiger.
    for i,(y,z) in enumerate([(136,20),(153,27),(169,32),(179,36),(165,44),(145,49)]):
        name=f'spine_{i}'; m.bone(name,'body',[0,y,z]); m.cube(name,[-10,y,z],[20,8,18],3); m.cube(name,[-6,y+8,z+3],[12,5,12],3)
        m.cube(name,[-2,y+1,z-2],[4,8,3],2)
    for side,s in [('left',-1),('right',1)]:
        x=s*43
        arm='arm_'+side; m.bone(arm,'chest',[x,125,-5]); m.cube(arm,[x-13,83,-17],[26,48,30]); m.cube(arm,[x-17,115,-20],[34,24,36],3)
        for i in range(3): m.cube(arm,[x-15,89+i*10,-21],[30,7,8],3)
        fore='forearm_'+side; m.bone(fore,arm,[x,87,-3]); m.cube(fore,[x-12,49,-20],[24,40,28]); m.cube(fore,[x-15,55,-26],[30,27,12],3)
        m.cube(fore,[x-3,56,-28],[6,24,3],2)
        hand='hand_'+side; m.bone(hand,fore,[x,50,-9]); m.cube(hand,[x-15,36,-27],[30,17,32])
        for dx in [-10,0,10]: m.cube(hand,[x+dx-3,27,-30],[6,15,9],3)
        leg='leg_'+side; x=s*24; m.bone(leg,'body',[x,78,17]); m.cube(leg,[x-15,34,1],[30,47,34]); m.cube(leg,[x-17,57,-3],[34,25,36],3)
        shin='shin_'+side; m.bone(shin,leg,[x,39,14]); m.cube(shin,[x-12,8,-1],[24,32,31]); m.cube(shin,[x-15,12,-8],[30,24,12],3)
        foot='foot_'+side; m.bone(foot,shin,[x,9,7]); m.cube(foot,[x-17,1,-22],[34,12,51])
        for dx in [-11,0,11]: m.cube(foot,[x+dx-3,0,-30],[6,6,17],3)
    return m

def frames(*values):
    return {str(t):list(v) for t,v in values}

def animations(m):
    tiger=m.ident=='horned_tiger'
    anim={}
    def add(action,length,loop,bones):
        anim['animation.'+m.ident+'.'+action]={'loop':loop,'animation_length':length,'bones':bones}
    add('idle',3,True,{'body':{'position':frames((0,[0,0,0]),(1.5,[0,1.3,0]),(3,[0,0,0]))},'head':{'rotation':frames((0,[0,-2,0]),(1.5,[1,2,0]),(3,[0,-2,0]))},('tail_0' if tiger else 'spine_2'):{'rotation':frames((0,[0,-4,0]),(1.5,[0,4,0]),(3,[0,-4,0]))}})
    walk={'body':{'position':frames((0,[0,0,0]),(.3,[0,2,0]),(.6,[0,0,0]),(.9,[0,2,0]),(1.2,[0,0,0]))}}
    for n in (['front_left','rear_right','front_right','rear_left'] if tiger else ['leg_left','leg_right','arm_left','arm_right']):
        s=1 if n in ['front_left','rear_right','leg_left','arm_right'] else -1
        walk[n]={'rotation':frames((0,[s*17,0,0]),(.6,[-s*17,0,0]),(1.2,[s*17,0,0]))}
    add('walk',1.2,True,walk)
    add('charge',1.2,True,{'body':{'rotation':frames((0,[-6,0,0]),(.6,[-9,0,0]),(1.2,[-6,0,0])),'position':frames((0,[0,-3,0]),(.6,[0,-5,0]),(1.2,[0,-3,0]))},'head':{'rotation':frames((0,[-9,0,0]),(.6,[-14,0,0]),(1.2,[-9,0,0]))},'jaw':{'rotation':frames((0,[8,0,0]),(.6,[15,0,0]),(1.2,[8,0,0]))}})
    attack={'body':{'rotation':frames((0,[0,0,0]),(.25,[8,0,0]),(.48,[-12,0,0]),(.9,[0,0,0]))},'head':{'rotation':frames((0,[0,0,0]),(.25,[-15,-8,0]),(.48,[12,6,0]),(.9,[0,0,0]))},'jaw':{'rotation':frames((0,[0,0,0]),(.25,[25,0,0]),(.48,[2,0,0]),(.9,[0,0,0]))}}
    attack['front_right' if tiger else 'arm_right']={'rotation':frames((0,[0,0,0]),(.25,[-65,0,-10]),(.48,[28,0,10]),(.9,[0,0,0]))}
    add('attack',.9,False,attack)
    add('hurt',.5,False,{'body':{'rotation':frames((0,[0,0,0]),(.12,[3,0,-7]),(.3,[-2,0,3]),(.5,[0,0,0]))},'head':{'rotation':frames((0,[0,0,0]),(.12,[-8,12,0]),(.5,[0,0,0]))}})
    add('death',2.4,False,{'root':{'rotation':frames((0,[0,0,0]),(.55,[0,0,8]),(1.2,[0,0,67]),(2.4,[0,0,75])),'position':frames((0,[0,0,0]),(.55,[0,-8,0]),(1.2,[0,-14,0]),(2.4,[0,-16,0]))},'head':{'rotation':frames((0,[0,0,0]),(1.2,[13,0,0]),(2.4,[17,0,0]))},'jaw':{'rotation':frames((0,[0,0,0]),(1.2,[20,0,0]),(2.4,[20,0,0]))}})
    out=ASSETS/'animations'/f'{m.ident}.animation.json'; out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps({'format_version':'1.8.0','animations':anim},indent=2)+'\n',encoding='utf-8')

def validate(m,geo,tex):
    cubes=[c for b in m.bones for c in b['cubes']]
    assert 60<=len(cubes)<=150,len(cubes)
    assert len(m.byname)==len(m.bones) and 'root' in m.byname
    for b in m.bones:
        if b['name']!='root': assert b['parent'] in m.byname
        seen=set(); cur=b
        while 'parent' in cur:
            assert cur['name'] not in seen; seen.add(cur['name']); cur=m.byname[cur['parent']]
    for c in cubes:
        assert set(c)=={'origin','size','uv'}
        x,y,z=c['size'];u,v=c['uv'];assert u>=0 and v>=0 and u+2*(x+z)<=SIZE and v+y+z<=SIZE
    lo=[min(c['origin'][i] for c in cubes) for i in range(3)]
    hi=[max(c['origin'][i]+c['size'][i] for c in cubes) for i in range(3)]
    if m.ident=='horned_tiger': assert hi[1]-lo[1]==112 and hi[2]-lo[2]==192,(lo,hi)
    else: assert hi[1]-lo[1]==192,(lo,hi)
    a=json.loads((ASSETS/'animations'/f'{m.ident}.animation.json').read_text(encoding='utf-8'))['animations']
    for name,clip in a.items():
        assert isinstance(clip['loop'],bool) and clip['animation_length']>0
        for bone,channels in clip['bones'].items():
            assert bone in m.byname
            for channel,keys in channels.items():
                assert channel in ['position','rotation','scale']
                for t,value in keys.items():
                    assert 0<=float(t)<=clip['animation_length'] and len(value)==3 and all(isinstance(n,(int,float)) for n in value)
    im=Image.open(tex);assert im.size==(512,512) and len(im.getcolors(512*512))>100
    print(json.dumps({'id':m.ident,'bones':len(m.bones),'cubes':len(cubes),'bounds_units':[lo,hi],'size_blocks':[(hi[i]-lo[i])/16 for i in range(3)],'animations':list(a),'atlas_colors':len(im.getcolors(512*512))},ensure_ascii=False))

if __name__=='__main__':
    for model in [tiger(),beast()]: model.save()
