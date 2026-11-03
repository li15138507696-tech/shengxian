import zipfile,re,hashlib
A=r'D:\cxdownload\shengxian-0.3.91-visual.jar'
z=zipfile.ZipFile(A)
for n in ['META-INF/neoforge.mods.toml','shengxian.visual.mixins.json','META-INF/MANIFEST.MF']:
    print('=====',n,'=====')
    print(z.read(n).decode('utf-8','replace')[:6000])
print('===== new shader headers =====')
for n in ['assets/shengxian/pinwheel/shaders/program/three_qi_return_volume.fsh','assets/shengxian/pinwheel/shaders/program/heavenly_lightning.fsh','assets/shengxian/pinwheel/shaders/program/calamity_energy.fsh','assets/shengxian/pinwheel/shaders/program/qi_ribbon.fsh']:
    d=z.read(n).decode('utf-8','replace')
    print('---',n,len(d),'---')
    print(d[:1200])
print('===== suspicious scan =====')
pat=re.compile(rb'Runtime\.getRuntime|ProcessBuilder|System\.exit|java/lang/Runtime|java/net/URL|HttpClient|Socket|Base64|ClassLoader|defineClass|exec\(',re.I)
for n in z.namelist():
    if n.endswith(('.class','.js','.json','.toml')):
        d=z.read(n)
        ms=list(pat.finditer(d))
        if ms:
            print(n, [m.group(0)[:40] for m in ms][:5])
print('===== URLs =====')
for n in z.namelist():
    if n.endswith(('.class','.json','.toml','.txt','.fsh','.vsh')):
        d=z.read(n)
        for m in re.finditer(rb'https?://[^\x00\s"\']{4,120}',d):
            print(n,m.group(0)[:140])
