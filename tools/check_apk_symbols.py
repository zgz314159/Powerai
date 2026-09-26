import zipfile
apk='app/build/outputs/apk/debug/app-debug.apk'
entry='lib/arm64-v8a/libpowerai_llama_jni.so'
syms=[b'Java_com_example_powerai_domain_llm_LlamaJni_initBackend',
      b'Java_com_example_powerai_domain_llm_LlamaJni_setOption',
      b'Java_com_example_powerai_domain_llm_LlamaJni_loadModel']

with zipfile.ZipFile(apk) as z:
    data=z.read(entry)
    print('apk entry', entry, 'size', len(data))
    for s in syms:
        print(s.decode(), s in data)
