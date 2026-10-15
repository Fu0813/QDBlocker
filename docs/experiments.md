# 早期 Hook 实验记录

此文档仅供开发研究。日常使用请阅读主 README。

## 历史 Hook 探针实验（0.1.0）

以下保留早期实验记录；当前使用手机本地网络过滤，普通安装本项目不能 Hook 原版起点。

**2026-10-15 真机结果：起点 7.9.480 的免 Root 改包启动失败。** 已测试正常内置、延后业务 Hook、保留原 dex 注入、不带任何探针的 LSPatch 对照包。正常模式和无探针对照均在 `com.stub.StubApp.interface99` 原生注册处退出；保留 dex 模式出现 `interface20` 原生实现缺失。已经卸载实验改包，重新安装手机提取的原版并确认进入书架。此结果仅适用于本次设备、起点和工具组合，不代表所有免 Root 路线均不可行。

## 已完成

- 黑名单草稿支持 `name:测试用户` 和 `uid:123456`，每行一条；默认测试对象为“测试用户”。昵称采用完全匹配，不用评论正文判断作者。
- 现代 libxposed API 102 模块，定向观察段评主评论、回复和图片评论的已知绑定签名。
- 日志记录字段结构、有限的候选数字 ID、昵称规则命中布尔值；不序列化宿主对象，不记录评论正文、其他用户昵称、Cookie、Token 或请求内容。
- 主进程诊断异步传输，队列上限 64，每分钟最多 40 个绑定采样；本机最多保留 150 条、100,000 字符。
- 原创红底书本盾牌启动图标，原图和提示词在 `assets/icon/`。
- 独立 `hooklab` 验证 App，以模拟对象验证免 Root Hook，不访问起点服务器。模拟用户 ID `10001/10002` 不是实际用户 ID。

## 免 Root 路线

采用 JingMatrix/LSPatch v1.2 (487) 的集成模式，将探针内置到目标 APK。它不要求系统 Root 或另行安装 LSPosed；普通安装 QDBlocker 仍无法修改原版起点。

工具来自 https://github.com/JingMatrix/LSPatch/releases/tag/v1.2 ，本地校验 SHA-256：

`d238fdc414d121b7fa454d8b4ccf420df3a8c97d563761861ff92bd9c5da2165`

**已验证环境：** Android 16 / 无 Root。独立实验 App 已收到 API 102 的 PACKAGE_READY、主评论与回复的 BIND 事件，并成功传回 QDBlocker。这不等于真实起点适配成功。

**真实起点结果：** 7.9.480，versionCode 1996。最初探针识别并挂接了真实 `bindData(DataListBean, BookInfoBean)` 三个方法，但应用未完成初始化，未采集到真实评论 BIND。原始 APK 从已连接手机提取，仅保存在忽略的 `work/device/` 中。

起点探针测试包与原版包名相同、签名不同，不能直接覆盖官方安装。测试涉及卸载时会影响本地数据和登录状态，必须先由用户决定，不能运行脚本自动卸载。原版 APK 副本不是应用私有数据备份。

## 构建与打包

使用 Android Studio 自带 JBR，Android SDK 37，Gradle wrapper。`compileSdk=37` 用于满足原项目 AndroidX 依赖；目标运行版本仍为 API 36，minSdk 为 28。

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :hooklab:assembleDebug
.\tools\Prepare-RootlessTest.ps1 -Target Lab
.\tools\Prepare-RootlessTest.ps1 -Target Qidian
```

打包脚本只操作本地文件：检查工具哈希、内置当前探针、16 KiB ZIP 对齐、调试签名、验签、检查内置模块与当前构建逐字节相符。实验室输出位于 `outputs/`；已知启动失败的起点实验包只保存在 `work/packaging/Qidian/`，不作为可用交付。`-WithoutProbe` 可构建无探针对照，`-InjectDex` 可测试保留原 dex 的加载方式。

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r outputs/QDBlocker-HookLab-rootless.apk
adb shell am start -n com.haoyang.qdblocker.lab/.LabActivity
.\tools\Collect-Probe.ps1
```

**只在确认需要测试起点改包后**，手动处理原版安装冲突。此仓库不提供自动卸载原版或 Root/刷机脚本。

## 真机验证顺序

1. 先打开 QDBlocker，保存测试规则 `name:测试用户`，保持诊断开启。
2. 安装并运行内置探针的起点测试包，确认能正常启动，再考虑是否登录。
3. 打开含目标用户的段评，展开其回复；采集 `CLASS_MISSING`、`HOOK_INSTALLED`、`SCHEMA`、`BIND`。
4. 昵称对应字段也尚未确认；`exactNameMatch=false` 可能表示字段不在当前候选列表，不能据此判定目标未出现。`candidateIds` 也不能直接当作作者 UID。
5. 确认作者、根评论和回复关系后，再实现列表层过滤，处理列表复用和分页；最后单独适配回复与 @ 页面。

## 源码参考

参考具体类名、绑定参数特征和公开 API 后独立实现精简探针，没有引入 QDHelper 的业务功能。

- QDHelper: https://github.com/xiyunmn/QDHelper/tree/b1963c6307ca07a574e34e4ea56b2a3e6b2020fe
  - `modules/host-api/.../symbol/HostSymbols.kt`：段评模型、主评论与回复 ViewHolder 类名。
  - `modules/host-resolver/.../query/ReaderStartupQueries.kt`：`(DataListBean, BookInfoBean) -> void` 绑定方法特征。
- libxposed API: https://github.com/JingMatrix/libxposed-api/tree/39cac0845771547c9c67a3e3ce255af110a54a0e
- LSPatch: https://github.com/JingMatrix/LSPatch

下载的参考仓库、第三方工具、原版 APK 和诊断证据均放在被忽略的 `work/`，不应随自有源码提交或公开发布。当前不承诺跨起点版本兼容。
