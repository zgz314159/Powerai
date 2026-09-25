放弃命令行重定向（绝对）

永远不要在 Windows 终端里使用 `>` 导出文件。在 Windows 下通过 `>` 或其他 shell 重定向导出的字节流可能会被终端或外壳自动转码或拆分，导致 UTF-8 文本损坏或二进制不一致。

必须使用二进制对等拷贝：

```bash
adb pull /data/user/0/com.example.powerai/files/last_res.txt final_raw.txt
```

该命令保证设备上字节与主机上字节一模一样。若因权限问题无法直接 `adb pull`，请在设备上以 app 身份将文件复制到可读位置再拉取，但仍然不要使用 `>` 重定向。