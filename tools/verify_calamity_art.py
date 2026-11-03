"""Validate editable art and its exact packaged resource bytes (no game execution)."""
import argparse, hashlib, json, math, struct, zipfile
from pathlib import Path
from PIL import Image
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src/main/resources/assets/shengxian'
IDS = ('horned_tiger','ancient_beast','jade_willow','eight_chimes','blood_core')

def vec(value):
    assert isinstance(value,list) and len(value)==3
    assert all(isinstance(x,(int,float)) and math.isfinite(x) for x in value)

def validate(jar=None):
    rows=[]; resources=[]
    for mid in IDS:
        geo=BASE/'geo'/f'{mid}.geo.json'
        animation=BASE/'animations'/f'{mid}.animation.json'
        texture=BASE/'textures/entity'/f'{mid}.png'
        resources.extend((geo,animation,texture))
        g=json.loads(geo.read_text('utf-8'))['minecraft:geometry'][0]
        desc=g['description']; bones=g['bones']; names=[b['name'] for b in bones]
        assert len(names)==len(set(names)) and 'root' in names
        parents={b['name']:b.get('parent') for b in bones}
        assert sum(p is None for p in parents.values())==1 and parents['root'] is None
        cubes=0; uvrects=[]
        for b in bones:
            path=set(); key=b['name']
            while key is not None:
                assert key in parents and key not in path,(mid,key,'bone cycle')
                path.add(key); key=parents[key]
            for prop in ('pivot','rotation'):
                if prop in b: vec(b[prop])
            for c in b.get('cubes',[]):
                vec(c['origin']); vec(c['size']); assert min(c['size'])>0
                for prop in ('pivot','rotation'):
                    if prop in c: vec(c[prop])
                u,v=c['uv']; w,h,d=c['size']
                assert u>=0 and v>=0 and u+2*(w+d)<=desc['texture_width']
                assert v+d+h<=desc['texture_height'],(mid,'UV overflow')
                uvrects.append((u,v,2*(w+d),d+h)); cubes+=1
        assert cubes<=160 and len(bones)<=128
        with Image.open(texture) as im:
            im.verify()
        with Image.open(texture) as im:
            assert im.size==(desc['texture_width'],desc['texture_height'])
            assert len(im.convert('RGB').getcolors(im.width*im.height))>=64
        anims=json.loads(animation.read_text('utf-8'))['animations']
        for state in ('idle','charge','attack','hurt','death'):
            assert f'animation.{mid}.{state}' in anims
        for name,clip in anims.items():
            length=clip['animation_length']; assert math.isfinite(length) and length>0
            for bone,channels in clip['bones'].items():
                assert bone in parents,(mid,bone)
                for kind,track in channels.items():
                    assert kind in ('rotation','position','scale')
                    if isinstance(track,list): vec(track)
                    else:
                        assert track
                        for time,value in track.items():
                            assert 0<=float(time)<=length+1e-6
                            vec(value)
        rows.append(dict(id=mid,bones=len(bones),cubes=cubes,animations=len(anims),atlas=[desc['texture_width'],desc['texture_height']]))
    if jar:
        with zipfile.ZipFile(jar) as z:
            for path in resources:
                relative=path.relative_to(ROOT/'src/main/resources').as_posix()
                assert z.read(relative)==path.read_bytes(),('packaged bytes differ',relative)
            assert 'net/guzhenren/shengxian/client/CalamityArtRenderer.class' in z.namelist()
    print(json.dumps(dict(models=rows,resource_files=len(resources),jar=str(jar) if jar else None,result='PASS'),ensure_ascii=False,indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--jar',type=Path)
    validate(parser.parse_args().jar)
