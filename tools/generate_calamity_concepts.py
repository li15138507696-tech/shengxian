"""Generate five ORIGINAL concept references with official online FLUX.1-schnell.
Not game screenshots or renders of the shipped geo models. No weights, keys or installs.
Sequential requests only. Existing outputs are never overwritten.
Run: bundled-python tools/generate_calamity_concepts.py [--id horned_tiger]
Actual online output is stochastic even with a retained seed; recorded hashes bind results."""
from pathlib import Path
import argparse, hashlib, io, json, urllib.request, datetime, sys
from PIL import Image

BASE='https://black-forest-labs-flux-1-schnell.hf.space'
OUT=Path(__file__).resolve().parents[1]/'docs/concepts'
MODEL='black-forest-labs/FLUX.1-schnell'
COMMON='Original Chinese xianxia heavenly calamity creature design, one isolated subject, full body completely in frame, three-quarter view, readable silhouette, dark neutral grey studio background, detailed hand-painted game art modeling reference, dark stone, weathered bronze, jade and blood materials, restrained glowing accents, no scenery, no text, no logos. '
PROMPTS={
'horned_tiger':COMMON+'A colossal crouching TIGER with tiger face, thick shoulder blades and massive clawed paws, curved dark gold rock armor over the arched back, black tiger stripes, exposed long saber fangs and long segmented tail. EXACTLY ONE horn: a single long sharply tapering horn protrudes from the CENTER MIDLINE OF THE FOREHEAD above the nose, like a unicorn. Both temples are smooth and hornless, the ears are rounded furry tiger ears. ONE CENTRAL HORN ONLY, NO PAIRED HORNS. Low horizontal quadruped body twelve blocks long and seven blocks tall.',
'jade_willow':COMMON+'An uncanny living JADE WILLOW TREE calamity, a thick hollow curved jade trunk enclosing a very obvious circular EMPTY WIND EYE open hole through its center, with swirling pale green wind ribbons visible through the hollow. Asymmetric roots grip ground. Long weeping willow branches hang down in many fine strands with narrow jade leaf blades. Clear tree silhouette and a hollow wind aperture, not a human and not a normal oak.',
'eight_chimes':COMMON+'A floating ancient Chinese ritual L-SHAPED BRONZE QING CHIME instrument calamity. One monumental angular upside-down L-shaped flat thick bronze percussion slab, an unmistakable bent L / boomerang silhouette with two unequal straight arms and sharp elbow, suspended by dark jade cord from a small floating stone crossbar; seven smaller angular L-shaped bronze chime slabs orbit it. Cast taotie relief, patina green corrosion, gold worn edges, jade sound energy. Open air on both sides of flat slab. This is a Chinese qing lithophone: NOT a bell, NOT a round dome, NO bell cup, no mouth opening, no tower.',
'ancient_beast':COMMON+'A twelve-block-tall upright primordial BONE ARMORED BEAST, towering vertical silhouette, enormous thick columnar hind legs and massive long gorilla-like forearms ending in blunt talons, broad ribbed chest covered by ancient ivory bone armor, dark blue-black stone skin split with intense deep blue glowing fissures. Six tall bone spines and buttresses rise along its back. Broad blunt skull with a bony mask, heavy jaw, small blue eyes, paired short swept bone crown. Distinct monstrous biped, not a tiger and not a dragon.',
'blood_core':COMMON+'A floating BLOOD CORE calamity, one dense dark crimson crystalline heart nucleus with a deep red luminous inner cavity and concentric broken mineral rings, surrounded by suspended separate jagged black stone shards and oxidized bronze fragments in an asymmetric orbital arrangement. Tiny dark blood droplets and thin red energy veins bridge the gaps. Full isolated object silhouette, no creature face, no body, no hands, no humanoid, not a planet. Ancient ritual xianxia cosmic catastrophe.'
}

def request_json(url,payload=None):
    data=None if payload is None else json.dumps(payload).encode()
    req=urllib.request.Request(url,data=data,headers={'Content-Type':'application/json','User-Agent':'Shengxian-Art-Reference/1.0'})
    with urllib.request.urlopen(req,timeout=180) as r: return json.load(r)

def generate(ident,seed,version='v2',resolution=1024):
    OUT.mkdir(parents=True,exist_ok=True)
    target=OUT/(ident+'-'+version+'.webp'); manifest=OUT/(ident+'-'+version+'.manifest.json')
    if target.exists():
        print('SKIP existing '+ident,flush=True);return True
    if manifest.exists():
        print('STOP existing failure manifest '+ident+'; inspect before a new remote attempt',flush=True);return False
    prompt=PROMPTS[ident]
    if version=='v3' and ident=='eight_chimes':
        prompt='large ancient Chinese L-shaped bronze stone chime hanging ornament, single instrument, isolated dark background, game model reference, not a bell'
    record={'id':ident,'purpose':'AI-generated original design reference; NOT a game screenshot or geo model render','model':MODEL,'space':BASE,'prompt':prompt,'seed':seed,'parameters':{'randomize_seed':False,'width':resolution,'height':resolution,'num_inference_steps':4},'created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'visual_review':'pending','defects':[]}
    try:
        receipt=request_json(BASE+'/gradio_api/call/infer',{'data':[prompt,seed,False,resolution,resolution,4]})
        record['event_id']=receipt['event_id'];print('QUEUED '+ident+' '+receipt['event_id'],flush=True)
        req=urllib.request.Request(BASE+'/gradio_api/call/infer/'+receipt['event_id'],headers={'User-Agent':'Shengxian-Art-Reference/1.0'})
        result=None;event=''
        with urllib.request.urlopen(req,timeout=240) as stream:
            for raw in stream:
                line=raw.decode('utf-8').strip()
                if line.startswith('event:'):event=line[6:].strip()
                elif line.startswith('data:'):
                    payload=json.loads(line[5:].strip())
                    if event=='error':
                        record['remote_event']='error';record['remote_data']=payload
                        raise RuntimeError('Remote error: '+str(payload))
                    if event=='complete': result=payload;break
        if result is None:raise RuntimeError('Stream ended without completion')
        record['source_url']=result[0]['url'];record['returned_seed']=result[1]
        with urllib.request.urlopen(record['source_url'],timeout=120) as response: data=response.read()
        image=Image.open(io.BytesIO(data));image.load()
        image.convert('RGB').save(target,format='WEBP',quality=95,method=6)
        record['sha256']=hashlib.sha256(target.read_bytes()).hexdigest();record['download_sha256']=hashlib.sha256(data).hexdigest();record['size']=list(image.size);record['output']=target.name;record['status']='downloaded_pending_visual_review'
        manifest.write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print('SAVED '+str(target),flush=True);return True
    except Exception as exc:
        record['status']='stopped_remote_failure';record['error']=str(exc);record['defects']=['No successful image. Remote failure reason is unknown unless the error explicitly names quota or authentication. No automatic retry.']
        manifest.write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print('STOP '+ident+': '+str(exc),flush=True);return False

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--id',choices=list(PROMPTS));parser.add_argument('--version',choices=['v2','v3'],default='v2');parser.add_argument('--resolution',type=int,choices=[768,1024],default=1024);parser.add_argument('--remaining-v3',action='store_true');args=parser.parse_args()
    for i,ident in enumerate(PROMPTS):
        if args.id and ident!=args.id:continue
        if args.remaining_v3 and ident not in ['eight_chimes','ancient_beast','blood_core']:continue
        if not generate(ident,861470+i*193,args.version,args.resolution):sys.exit(1)
