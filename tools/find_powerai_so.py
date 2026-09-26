import os,hashlib
matches=[]
for root, dirs, files in os.walk('app/build'):
    for f in files:
        if 'llama_jni' in f or 'powerai_llama_jni' in f:
            p=os.path.join(root,f)
            with open(p,'rb') as fh:
                data=fh.read()
            print(p, len(data), hashlib.sha256(data).hexdigest())
            matches.append(p)
if not matches:
    print('none found')
