import zipfile,hashlib,os
apk='app/build/outputs/apk/debug/app-debug.apk'
paths=[
    'app/build/intermediates/cxx/Debug/x494n1o7/obj/arm64-v8a/libllama_jni.so',
    'app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/libllama_jni.so',
    'app/build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib/arm64-v8a/libllama_jni.so'
]

def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()

print('APK:', apk, 'exists=', os.path.exists(apk))
if os.path.exists(apk):
    with zipfile.ZipFile(apk) as z:
        for info in z.infolist():
            if info.filename.endswith('libllama_jni.so'):
                data=z.read(info.filename)
                print('\nAPK entry:', info.filename, 'size=', info.file_size, 'sha256=', sha256_bytes(data))

for p in paths:
    if os.path.exists(p):
        with open(p,'rb') as f:
            data=f.read()
        print('\nFile:', p, 'size=', len(data), 'sha256=', sha256_bytes(data))
    else:
        print('\nMissing:', p)

# Additionally, search app/build recursively for any libllama_jni.so and report
print('\nScanning app/build for libllama_jni.so...')
found = False
for root, dirs, files in os.walk('app/build'):
    for name in files:
        if name == 'libllama_jni.so':
            found = True
            p = os.path.join(root, name)
            with open(p,'rb') as f:
                data = f.read()
            print('Found:', p, 'size=', len(data), 'sha256=', sha256_bytes(data))
if not found:
    print('No libllama_jni.so found under app/build')
