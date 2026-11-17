# Cortex 协议证据核查（离线，不联网）

已有 APK 静态分析已经找到 `/api/v1/study/oplog` 等候选路径及 `RESPONSE_*` 名称；它们还不是经验证的 HTTP 写回合同。不要直接拼接 JSON、复用开放 API Token 或让流体云按钮发未验证的 POST。

`tools/cortex_trace_audit.py` 用于检查本人账号正常操作产生的本地 HAR，只记录调用顺序、请求方法、状态码、媒体类型、敏感头是否存在及少量白名单字段的数据类型。它不会联网，不输出认证值、请求参数值、单词 ID、响应内容、原始请求体、签名或 Cookie。

```bash
python3 tools/cortex_trace_audit.py private-study.har --out cortex-structure.json
python3 -m unittest discover -s tools -p 'test_cortex_trace_audit.py' -v
```

原始 HAR 含个人数据，只存本机，不要提交 GitHub，也不要直接转发。先检查输出的 `cortex-structure.json` 不含敏感信息，再提供结构摘要。若 HAR 只含 CONNECT、没有请求正文，结果会显示 `relevant_entries: 0`，不能误判已拿到接口合同。

## 判定进度

- 静态确定：Cortex 路径、候选枚举名称、`voc_id`、`response`、`checkpoint` 等字段线索。
- 结构核对：工具确认方法、Media-Type、字段结构和调用顺序；摘要不包含真实 protobuf 字节、枚举整数或认证协议。
- 待确认：受支持的第三方授权、完整请求编码与签名、checkpoint 生命周期、同步冲突/重试行为以及同词同反馈官方回读。
- 正式接入：上述验证后才编写经过授权的 HTTP 客户端、幂等保护与真机验收。此前保留现有公开 API 只读与受控前台流程。
