import os
for root, dirs, files in os.walk('.'):
    for f in files:
        if f == 'libllama_jni.so':
            print(os.path.join(root, f))
