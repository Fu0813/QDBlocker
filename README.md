# QDBlocker

<img src="assets/icon/qdblocker-icon.png" alt="QDBlocker 图标" width="96" />

起点读书的非官方 Android 本地黑名单工具。通过手机本地 VPN 和过滤脚本，隐藏黑名单用户的消息及已适配的阅读段评。**日常使用无需 Root**

当前版本：**0.3.1**。公开版本默认名单为空，请自行添加规则。本项目与起点读书、阅文无隶属关系。

## 功能

- 按数字 UID 或完整昵称维护黑名单，保存后约 2 秒刷新页面生效。
- 屏蔽“评论和 @”中黑名单作者的消息。
- 阅读段评中，隐藏黑名单回复及其图片。
- 主评论作者命中时，隐藏该主评论及关联回复。
- 提供总过滤开关、段评独立开关及过滤计数。

**本项目仅修改本机收到的列表，不向服务器发送删除评论的请求**。

## 安装与使用

需要安装三个应用：

1. [QDBlocker](https://github.com/Fu0813/QDBlocker/releases)：黑名单、开关及状态界面。
2. [PCAPdroid](https://github.com/emanuele-f/PCAPdroid/releases)：仅接管起点的本地 VPN。
3. [PCAPdroid mitm](https://github.com/emanuele-f/PCAPdroid-mitm/releases)：在手机上解密并执行过滤脚本。

已验证组合：原版起点 7.9.480、Android 16、PCAPdroid 2.0.2、mitm 插件 2.4。其他版本尚未全面验证。

首次需要安装手机插件生成的本地 CA，并在插件的 Addons 中启用 `QDBlocker.py`。此后从 QDBlocker 开启屏蔽即可。完整步骤见 **[使用指南](docs/usage.md)**。

```text
uid:123456789
name:测试用户
```

每行一条；例子均为占位数据。任一规则匹配即屏蔽。

## 当前限制

- 整页全被过滤后，起点可能提前停止加载更早的内容。
- 仅适配已识别接口，不保证起点更新后兼容。
- 占用 Android VPN 通道，不能与同一用户下的另一个 VPN 同时使用。
- 需要 PCAPdroid 及插件后台运行。

## 数据与网络

- VPN 仅加入起点；解密域名限定为 `druidv6.if.qidian.com`，其余连接转发。
- 黑名单保存在 QDBlocker 私有存储，脚本通过受限 Binder 接口读取；验证官方插件包名和签名。
- 不依赖云代理，不保存认证信息或评论正文，启动配置不写 PCAP 文件。

## 构建与检查

需要 JDK 21、Android SDK Platform 37（SDK 包名 `platforms;android-37.0`）、Build Tools 37.0.0、SDK Command-line Tools 22+ 和 Python 3。使用 Android Studio 打开项目，或配置 `JAVA_HOME`、`ANDROID_HOME` 后运行：

```bash
python -m unittest discover -s proxy -p 'test_*.py'
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows 使用 `gradlew.bat`。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。CI 自动运行相同检查。

## 项目结构

| 路径 | 用途 |
| --- | --- |
| `app/` | Android 管理界面、规则接口、手机过滤脚本 |
| `app/src/main/assets/QDBlocker.py` | 当前手机端过滤逻辑 |
| `proxy/` | Python 测试及可选电脑端调试代理 |
| `hooklab/` | 独立模拟实验 App，非日常使用依赖 |
| `tools/` | 开发用采集、代理及历史打包脚本 |
| `docs/` | 使用指南、架构和实验记录 |
| `assets/icon/` | 图标与生成说明 |

`work/`、`outputs/`、构建缓存、第三方工具及本地配置不提交。历史 Hook 代码仅用于实验，普通安装本应用无法 Hook 原版起点，见 [实验记录](docs/experiments.md)。

## 许可证与致谢

自有源码使用 [MIT License](LICENSE)。依赖、工具和独立配套应用保持各自许可证，见 [第三方说明](THIRD_PARTY_NOTICES.md)。
