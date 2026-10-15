# 架构与验证边界

```text
原版起点 → PCAPdroid（仅起点的本地 VPN）
        → PCAPdroid mitm（手机本地代理）
        → QDBlocker.py（限定域名、路径和结构）
        → 本机收到过滤后的响应

QDBlocker 界面 → 私有 SharedPreferences
              → 受限 ContentProvider → 手机脚本
```

规则 IPC 验证调用 UID 所属包名和官方插件签名。QDBlocker 不需要自建网络端口或常驻前台服务，插件通过 Binder 读取规则。

当前处理 `druidv6.if.qidian.com` 上的以下接口：

- `/argus/api/v1/message/pullSocialUnreadMessage`
- `/argus/api/v2/chapterreview/getparagraphscomments`
- `/argus/api/v1/chapterreview/getchapterreviewdetail`
- `/argus/api/v1/chapterreview/getparagraphsallimgreviews`
- `/argus/api/v1/chapterreview/getparagraphsaudiocomments`

消息作者在 `UserInfo.FromId / Name`。段评作者在 `UserId / UserName`；观察到的平铺 `DataList` 中，根评论的 `RootReviewId == Id`，回复指向根评论。先识别黑名单根评论，再移除自身及关联回复；单独命中的回复不会把其父主评论加入黑名单。

根评论缓存限制规模和有效期，修改名单时清理。保留服务端游标，只在计数恰好对应返回列表时更新整页数量；已移除回复会调整保留根评论的回复计数。请求内容和认证字段不参与修改。

手机代理使用 `connection_strategy=eager`。本次设备使用 lazy 时无法正常加载，调整后恢复。域名解密范围受启动参数限制。

## 已验证

- 原版起点 7.9.480、Android 16、PCAPdroid 2.0.2、mitm 2.4。
- 消息过滤脱离 USB、电脑代理后仍正常，浏览器联网正常。
- 阅读段评中，保留自己的主评论和另一条主评论，移除黑名单图片回复；可见数量同步变化。
- 22 项 Python 测试覆盖作者判定、引用隔离、保留主评论、根讨论串、规则热更新、缓存清理和开关。
- Android 单元测试、构建和 Lint 通过。

根评论整串删除已通过合成测试；图片/配音独立列表仅观测到空数据，不声称所有场景均经过真机验证。未知入口、其他版本、长期后台和全过滤页面的分页行为仍有局限。
