# Build-level Deprecation Report

日期：2026-02-22

概述：在最近一次 `./gradlew :app:compileDebugKotlin --warning-mode all` 运行中，Gradle/AGP 在配置阶段输出了若干弃用警告。它们大多来自 AGP 或其传递依赖（例如 aapt2 / lint 插件）在内部使用已弃用的 API。下面列出当前捕获到的警告和建议的后续步骤。

发现的警告（摘录）：

- Declaring dependencies using multi-string notation has been deprecated. This will fail with an error in Gradle 10. Please use single-string notation instead: "com.android.tools.lint:lint-gradle:31.9.1".
- Declaring dependencies using multi-string notation has been deprecated. This will fail with an error in Gradle 10. Please use single-string notation instead: "com.android.tools.build:aapt2:8.9.1-12782657:windows".
- The StartParameter.isConfigurationCacheRequested property has been deprecated. This is scheduled to be removed in Gradle 10. Please use 'configurationCache.requested' property on 'BuildFeatures' service instead.
- The ReportingExtension.file(String) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the getBaseDirectory().file(String) or getBaseDirectory().dir(String) method instead.

备注：在项目脚本中并未找到明显的 `ReportingExtension.file(...)` 或多字符串写法的直接实例。上述警告由 AGP 或相关插件在配置期间触发（即插件内部或依赖库中存在旧用法）。因此，简单的字符串替换在本仓库中并没有立即可应用的目标。

建议的后续操作（优先级与风险）：

1. 保守跟进（推荐） — 生成一个升级分支并在该分支上逐步升级 Gradle Wrapper / AGP /插件（先升级到最新兼容版本），在本地或 CI 上运行完整构建与测试，记录哪些插件/库仍触发警告。可回退且可分阶段进行。

2. 监控与收集 — 在 CI（或本地构建脚本）中启用 `--warning-mode all` 并把构建日志收集到构建报告中，长期跟踪这些警告并在 PR 检查中把它们列为技术债务项。

3. 直接修复（仅当项目脚本中存在可变更位置时） — 如果将来在脚本或自定义插件中发现 `reporting.file(...)` / 多字符串依赖写法，立即改为 `getBaseDirectory().file("...")` / 单字符串依赖写法 `"group:name:version"`。

下一步（我可以代劳）：

- 在新分支上做一次受控升级试验（Gradle Wrapper + AGP + 常用插件），记录结果并生成一份升级影响报告。（需要你确认创建分支）
- 或者我继续在源码层消化剩余弃用/警告（低风险、小批次修复）。

文件由自动化检查生成，若需我现在开始执行升级试验，请回复 "开始升级试验"。
