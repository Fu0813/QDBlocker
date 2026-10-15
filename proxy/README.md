# 可选电脑端调试代理

日常使用不需要本目录的电脑代理。手机端脚本位于 `app/src/main/assets/QDBlocker.py`；参见 [手机使用指南](../docs/usage.md)。

本目录保留早期免 Root 网络验证工具和 Python 测试。所有示例名单均为空或虚构数据，原始流量、证书、安装包及本地状态保存在忽略的 `work/` 中。

```powershell
python -m venv work/proxy-venv
work/proxy-venv/Scripts/python.exe -m pip install -r proxy/requirements-lock.txt
.\tools\Start-CaptureProxy.ps1 -Mode socks5 -Port 18108
```

代理仅监听电脑回环地址。USB 转发、证书安装和手机设置需自行配置，不能将此视为手机独立版启动步骤。`capture-config.json` 默认为 observe；需要调试时自行设置目标，切勿提交真实用户名单。

观察日志仅包含去除查询参数的接口路径、有限结构和匹配元数据，不保存认证头、请求体或完整响应体。仍应将日志视为本地诊断数据，不公开上传。

测试：

```bash
python -m unittest discover -s proxy -p 'test_*.py'
```

开发手机上若配置了 Wi-Fi 手动代理，停止电脑代理或拔线前必须先恢复原设置；`Stop-CaptureProxy.ps1` 会检查这一步。设备序列号由脚本自动选择唯一授权设备，也可用 `-Serial` 显式指定。
