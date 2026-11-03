import zipfile,hashlib,sys,os,re
A=r'D:\cxdownload\shengxian-0.3.91-visual.jar'
B=r'D:\daima\mcmod\升仙\build\libs\shengxian-0.3.91.jar'
def info(p):
    h=hashlib.sha256(open(p,'rb').read()).hexdigest()
    z=zipfile.ZipFile(p)
    return h,z
ha,za=info(A); hb,zb=info(B)
print('A size',os.path.getsize(A),'sha256',ha)
print('B size',os.path.getsize(B),'sha256',hb)
na={i.filename:i for i in za.infolist()}
nb={i.filename:i for i in zb.infolist()}
only_a=sorted(set(na)-set(nb)); only_b=sorted(set(nb)-set(na))
print('entries A',len(na),'B',len(nb),'onlyA',len(only_a),'onlyB',len(only_b))
print('--- only in A ---')
for n in only_a[:80]: print('A+',n,na[n].file_size)
print('--- only in B ---')
for n in only_b[:80]: print('B+',n,nb[n].file_size)
changed=[]
for n in sorted(set(na)&set(nb)):
    if na[n].CRC!=nb[n].CRC or na[n].file_size!=nb[n].file_size:
        changed.append((n,na[n].file_size,nb[n].file_size))
print('--- changed entries',len(changed),'---')
for n,sa,sb in changed[:120]: print('CHG',n,sa,'->',sb)
for name in ['META-INF/neoforge.mods.toml','META-INF/MANIFEST.MF','META-INF/mods.toml']:
    for label,z in (('A',za),('B',zb)):
        if name in z.namelist():
            print('---',label,name,'---')
            print(z.read(name).decode('utf-8','replace')[:4000])
# suspicious strings scan in class files and scripts
pat=re.compile(rb'https?://|Runtime\.getRuntime|ProcessBuilder|System\.exit|exec\(|URLClassLoader|defineClass|Base64|java/lang/reflect',re.I)
hits=[]
for n in za.namelist():
    if n.endswith(('.class','.js','.json','.toml','.txt','.properties')):
        data=za.read(n)
        for m in pat.finditer(data):
            s=data[max(0,m.start()-60):m.start()+120]
            hits.append((n,s[:180]))
            break
print('--- suspicious pattern files',len(hits),'---')
for n,s in hits[:40]: print('SUS',n,s)
