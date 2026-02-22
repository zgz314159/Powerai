# 原件截图（人工/半自动补充）

这个目录用于存放**原件截图**：针对“表格/特殊图例（图标、小插图、符号）”等**容易被重建误导**的内容，直接保存来自原文档渲染后的截图，并在知识块中通过 `imageUri` 挂载。

## 目录结构

每个源文件一个子目录，目录名建议使用与构建脚本 `--file-id` 一致的值。

注意：为了避免终端里输入 `—`、`《》`、`〔〕` 等符号导致路径不一致，脚本会把目录名规范化为 **docKey**：只保留“字母/数字/中文”，其余符号会被去掉。

例如：
- `1—普铁电力线路工岗位必知必会手册` -> `1普铁电力线路工岗位必知必会手册`
- `铁运〔1999〕103号《铁路电力安全工作规程》` -> `铁运1999103号铁路电力安全工作规程`

目录结构如下：

```
app/src/main/assets/原件截图/
  <fileId>/
    截图/
      table_p{page}_pos{position}.png
      table_pos{position}.png
```

- `page`：页码（从 1 开始，和 blocks 里的 `pageNumber` 对应）
- `position`：Entry 的序号（和 `entry.position`/`entryId` 里的 table# 对应）

## 命名规则（当前支持）

构建脚本会按以下候选名寻找截图（命中一个就会写入 table block 的 `imageUri`）：

- `table_p{page}_pos{position}.png|jpg|jpeg`
- `table_pos{position}.png|jpg|jpeg`

例如：`table_p3_pos12.png`

## 注意

- 这里的“原件截图”与 `assets/images/` 不同：后者是 DOCX 内嵌图片（rId）等资源的抽取，不等同于表格/图例的原样截图。
- 只要你把截图放到这里并按命名规则命中，重新跑构建脚本即可让 App 自动挂载显示。

## PDF 快速起步（先把“原件截图”做出来）

如果你希望从 PDF 开始自动化，仓库提供了一个最小工具把 PDF 每页渲染成图片，方便你后续裁剪出表格/图例截图：

- 脚本：tools/export_pdf_pages_to_original_screenshots.py
- 输出：app/src/main/assets/原件截图/<fileId>/pages/page_XXX_p{page}.png

示例：

1) 先导出页面图：
  - `C:/Users/zgz31/AndroidStudioProjects/PowerAi/.venv/Scripts/python.exe tools/export_pdf_pages_to_original_screenshots.py --pdf "app/src/main/assets/原文档/1—普铁电力线路工岗位必知必会手册.pdf" --file-id "1—普铁电力线路工岗位必知必会手册" --dpi 180`

2) 再把你裁剪得到的表格/图例截图放到：
  - app/src/main/assets/原件截图/<fileId>/截图/
  - 命名如：table_p3_pos12.png
