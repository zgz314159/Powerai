# 局域网本地调试指南

目标：使用本机 embedding/ANN 服务（FastAPI/uvicorn），通过物理手机在局域网内调试 App，避免每次调试都重建 APK。

## 快速步骤

1. 在电脑上启动本地服务（绑定所有接口以允许手机访问）：

```bash
.venv/Scripts/python.exe -m uvicorn tools.embedding_prototype.service:app --host 0.0.0.0 --port 8000 --log-level info
```

2. 在手机上（已安装 debug APK）配置 `ai_base_url.txt`，内容为电脑局域网 IP，例如：

```text
http://192.168.1.100:8000/
```

推荐把仓库根目录的 `ai_base_url.txt` 作为模板，修改 IP 后推送到设备。

3. 使用 adb 推送（debug build 且允许 run-as）：

```bash
adb push ai_base_url.txt /sdcard/Download/
adb shell run-as com.example.powerai sh -c 'cat /sdcard/Download/ai_base_url.txt > files/ai_base_url.txt'
```

若 `run-as` 无效，可用文件管理器或应用内调试界面写入 `files/ai_base_url.txt`。

## Android 代码点

- 优先读取 `BuildConfig.AI_BASE_URL`，若为空则读取 `files/ai_base_url.txt`（无需重建 APK）。
- 检索 POST 路径为 `/search`，示例：

```bash
curl -X POST http://192.168.1.100:8000/search \
  -H "Content-Type: application/json" \
  -d '{"query":"示例问题","k":10}'
```

## 注意与安全

- 绑定 `0.0.0.0` 会在局域网内暴露端点，仅在受信任网络或开发环境使用。
- 调试时可在服务端临时增加简单密钥校验（HTTP header）。

## 文件位置

- 服务代码：`tools/embedding_prototype/service.py`
- 本地索引输出：`tools/embedding_prototype/output/index.faiss`
- 示例模板：仓库根目录 `ai_base_url.txt`
