# 第三方项目与来源

MIT License 仅覆盖本仓库自有源码和文档，不改变配套应用、依赖或工具的许可条款。项目不分发起点安装包、第三方工具或其证书。

| 项目 | 用途与说明 |
| --- | --- |
| [PCAPdroid](https://github.com/emanuele-f/PCAPdroid) | 独立安装的本地 VPN 应用，GPL-3.0；保留原项目许可 |
| [PCAPdroid mitm](https://github.com/emanuele-f/PCAPdroid-mitm) | 独立安装的手机代理及自定义脚本宿主，GPL-3.0；保留原项目许可 |
| [mitmproxy](https://github.com/mitmproxy/mitmproxy) | 代理和脚本 API，MIT；由插件或开发者独立安装 |
| [libxposed API](https://github.com/JingMatrix/libxposed-api) | 历史 Hook 实验 compileOnly API，Apache-2.0 |
| [QDHelper](https://github.com/xiyunmn/QDHelper/tree/b1963c6307ca07a574e34e4ea56b2a3e6b2020fe) | AGPL-3.0；参考公开类名及绑定参数特征，未引入其业务实现或源码 |
| [JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch) | 历史实验使用的外部打包工具，不随仓库分发 |
| [Gradle](https://github.com/gradle/gradle) | Wrapper 与构建工具，Apache-2.0 |

AndroidX、Material、JUnit 等构建依赖遵循各自项目许可证，通过标准依赖管理获取。详细版本见 Gradle 配置和 `proxy/requirements-lock.txt`。若分发包含第三方代码的派生组件，需要另行履行相应许可义务。

Apache-2.0 许可证副本见 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)；Gradle Wrapper JAR 也保留其内置许可证。

图标是为本项目生成的书本与盾牌组合，生成说明在 `assets/icon/README.md`。不是起点官方图标；相关名称、商标归其权利人所有。
