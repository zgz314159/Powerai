import zipfile,os
apk='app/build/outputs/apk/debug/app-debug.apk'
if not os.path.exists(apk):
    print('apk missing')
else:
    with zipfile.ZipFile(apk) as z:
        for info in z.infolist():
            if info.filename.endswith('.so'):
                print(info.filename, info.file_size)
