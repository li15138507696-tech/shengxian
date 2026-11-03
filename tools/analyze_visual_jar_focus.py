import zipfile,re
A=r'D:\cxdownload\shengxian-0.3.91-visual.jar'
z=zipfile.ZipFile(A)
print(z.read('META-INF/neoforge.mods.toml').decode('utf-8','replace'))
print('===== visual mixins =====')
print(z.read('shengxian.visual.mixins.json').decode())
print('===== shader sizes/first lines =====')
for n in ['three_qi_return_volume.fsh','heavenly_lightning.fsh','calamity_energy.fsh','qi_ribbon.fsh']:
    p='assets/shengxian/pinwheel/shaders/program/'+n
    d=z.read(p).decode('utf-8','replace')
    print('---',n,'lines',d.count(chr(10))+1,'---')
    print(chr(10).join(d.splitlines()[:12]))
print('===== class string hints =====')
for n in ['net/guzhenren/shengxian/client/HeavenlySpectacle.class','net/guzhenren/shengxian/client/CalamitySpectacleRenderer.class','net/guzhenren/shengxian/client/SharedSpectacleClient.class','net/guzhenren/shengxian/server/VisualExperienceService.class','net/guzhenren/shengxian/client/AscensionCameraMixin.class']:
    d=z.read(n)
    ss=re.findall(rb'[ -~]{6,80}',d)
    print('---',n,'---')
    print([s.decode('latin1') for s in ss if any(k in s.lower() for k in [b'camera',b'spectacle',b'lightning',b'ribbon',b'scene',b'visual',b'tian',b'jie',b'qi',b'calamity',b'event'])][:25])
print('===== exact dangerous calls =====')
for n in z.namelist():
    if n.endswith('.class'):
        d=z.read(n)
        for pat in [b'getRuntime',b'ProcessBuilder',b'System.exit',b'java/net/URL',b'Socket',b'Base64']:
            if pat in d: print(n,pat)
