# KB 目录分类规范（assets/kb）

目标：让 Android assets 目录结构直接体现业务分类（行业 → 内容类型 → 专业/子类 → 文档），从而支持未来面向不同行业/岗位的扩展。

## 1) 总体布局

统一使用：`app/src/main/assets/kb/` 作为知识库根目录。

推荐结构：

```
app/src/main/assets/kb/
  <行业>/<内容类型>/<专业或子类>/<文档slug>/
    knowledge_base.json
    overrides.json              (可选)
    截图/
      manifest.json             (merge 脚本用；items 列表)
      ...png
    pages/                      (可选：PDF 渲染页面，用于后续自动裁剪)
      ...png
```

说明：
- “文档slug”建议尽量短，但要稳定、可读、可区分。
- `fileId` 现在定义为：**相对路径** `"<行业>/<内容类型>/<专业或子类>/<文档slug>"`。

## 2) 推荐枚举（可按项目扩展）

### 行业（一级）
- `铁路`
- `南方电网`
- （可扩展）例如：`国网`、`城轨`、`地铁`、`电厂`…

### 内容类型（二级）
- `规章制度`
- `专业知识`
- `案例`

### 专业/子类（三级及以后）
- 示例：`电力`、`信号`、`通信`、`供电`、`调度`…
- 允许继续细分：例如 `专业知识/电力/高铁/接触网/...`

## 3) 命名规则（重要）

工具链会对你输入的路径做“安全清洗”（逐段处理）：
- 保留：中文/英文/数字/下划线 `_`
- 删除：空格、标点和大多数特殊字符
- 统一小写（对英文）
- `/` 代表目录层级（会保留层级）

所以建议：
- 尽量用中文 + 数字 + `_` 表达关键编号。
- 文档slug 建议包含年份/文号，避免同名：
  - 例：`高速铁路电力管理规则2015_49`
  - 例：`铁路电力安全工作规程1999_103`

## 4) 与现有工具的对应关系

### A) 生成 KB（DOCX）
- `tools/build_kb_from_docx.py`：
  - `--file-id` 传入上面的相对路径即可（就是 fileId）。
  - 输出会写到：`app/src/main/assets/kb/<fileId>/knowledge_base.json`
  - 里面的图片引用（table 截图）会使用：`file:///android_asset/kb/<fileId>/截图/...`

### B) PDF → pages → 自动裁剪 → manifest
- `tools/export_pdf_pages_to_original_screenshots.py`：写入 `app/src/main/assets/kb/<fileId>/pages/`
- `tools/crop_pdf_tables_and_legends.py`：写入 `app/src/main/assets/kb/<fileId>/截图/` 并生成 `截图/manifest.json`
- `tools/merge_manifest_to_kb.py`：读取 `截图/manifest.json` 合并进 `knowledge_base.json`

### C) overrides 人工纠偏
- `tools/set_kb_override.py`：默认使用 `app/src/main/assets/kb` 作为根目录。

## 5) 初始化推荐流程（最省心）

用一键脚本创建叶子目录：
- 交互式：双击 `tools/init_kb_doc.cmd`（或运行 `tools/init_kb_doc.ps1`）
- 直接指定路径：
  - `tools/init_kb_doc.cmd "铁路/规章制度/电力/高速铁路电力管理规则2015_49"`
  - `python tools/init_kb_doc.py --path "铁路/规章制度/电力/高速铁路电力管理规则2015_49"`

交互模式里“文档slug”那一步也可以直接粘贴 `.docx/.pdf` 的完整路径，脚本会自动取文件名作为 slug（再做安全清洗）。

脚本会创建目录结构，并生成：
- `overrides.json`（空）
- `截图/manifest.json`（空 items，占位；merge 脚本要求必须有 `items`）

后续就可以把截图/裁剪/merge 流程跑在这个 fileId 上。

## 6) 一键跑通（DOCX + PDF pages）

如果你希望“一条命令”同时完成：
- DOCX → `knowledge_base.json`
- PDF → `pages/`

可以使用：
- `tools/run_build_kb_doc.cmd`

它会交互提示你输入：`fileId`、DOCX 路径、PDF 路径，并确保两步使用同一个 `--file-id`。

常见启动方式（推荐用下面写法，最不容易踩引号坑）：
- 资源管理器双击：直接双击 `tools/run_build_kb_doc.cmd`
- 在 `cmd.exe` 里运行：
  - `tools\run_build_kb_doc.cmd "铁路/规章制度/电力/高速铁路电力管理规则2015_49" "<docx路径>" "<pdf路径>"`
- 在 PowerShell 里运行：
  - `& .\tools\run_build_kb_doc.cmd "铁路/规章制度/电力/高速铁路电力管理规则2015_49" "<docx路径>" "<pdf路径>"`
- PowerShell 中用 `cmd.exe /c` 包一层（只有在你必须用 `cmd /c` 的场景才需要）：
  - `cmd.exe /c '""tools\run_build_kb_doc.cmd" "铁路/规章制度/电力/高速铁路电力管理规则2015_49" "<docx路径>" "<pdf路径>""'`

## 7) 一键裁剪（生成 截图/manifest.json）

裁剪脚本必须传 `--file-id`，建议直接双击：
- `tools/run_crop_kb.cmd`

它会提示你输入 `fileId`，并运行：
- `tools/crop_pdf_tables_and_legends.py --file-id <fileId>`

你也可以在命令行追加参数（例如 `--debug`、`--ocr`）。

## 8) 一键跑完（DOCX + pages + crop + merge）

如果你希望像 `init_kb_doc.cmd` 一样“全程交互 + 每一步都有成功/失败提示 + 最后不闪退”，直接双击：
- `tools/run_full_kb_pipeline.cmd`

它会依次完成：
- DOCX → `knowledge_base.json`
- PDF → `pages/`
- crop → `截图/manifest.json`
- merge → 将图片注入 `knowledge_base.json`

说明：
- `fileId` 可以直接回车留空（或输入 `auto`），脚本会用 DOCX 文件名自动推导并新建目录。
- 如果你希望像 `init_kb_doc.py` 那样选择“行业/内容类型/专业子类”作为上级目录：当 `fileId` 留空/auto 时，脚本会询问是否进入 taxonomy 向导，最终 `fileId = <你选的前缀>/<文件名slug>`。
- merge 的 manifest 默认读取：`app/src/main/assets/kb/<fileId>/截图/manifest.json`
- 如果 crop 报缺少 OpenCV/Numpy，按提示安装：`pip install opencv-python numpy`
