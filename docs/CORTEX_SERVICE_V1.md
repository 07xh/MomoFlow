# Cortex 独立服务第一阶段接入（2026-11-18）

## 本次实际代码

- `CortexGatewayService`：独立 HTTPS 通道，支持本人旧版登录 Token 的显式交换、`study/checkpoints` 与 `study/overview` 只读查询。禁止重定向携带授权；不记录完整 HTTP 响应、Token 或请求体。
- `CortexCredentialStore`：独立 Android Keystore AES-GCM，避免把开放 API Token 当作 Cortex Token。旧版 Token 仅在内存中使用，交换完成后不落盘。
- `CortexFeedbackLedger`：每个词的事务 ID、账号摘要、三态结果跨进程持久化。同词未决事务阻止重复触发；`OfficialSyncAssessment` 回读匹配后才将结果从「待确认」变为「已成功」，冲突变为「已失败」，延迟保持「待确认」。
- `MainActivity`：增加 Cortex 授权交换与只读检查页面区域；现有通知按钮仍通过官方 App 前台点击，官方回读结果驱动三态展示。
- `FeedbackOutcomePolicyTest`：验证回读三态判定，以及未验证上传不可误发。

## 必须区分

目前的「已成功」仅表示 **墨墨官方 Open API 记录观察到目标词、目标反馈的匹配变化**，不证明由 MomoFlow 发起，更不代表独立 Cortex 写入已成功。Cortex 的概览或检查点变化也不能替代单词级回读。

**本次没有启用自动 Cortex POST /study/oplog。** 上传包的 `mm_cortex_client.py` 采用猜测字段号，存在两套枚举混淆；其中 `verify` 会覆盖快照而不对比单词记录。将其直接塞进通知按钮会冒错误提交、重复提交的风险。

## 真机验收

1. 确认旧版登录 Token 换取 Cortex Bearer Token 真实可用（**不要**在日志、截图或仓库上传凭证）。
2. 点击「检查连接与检查点」，明确只读成功或具体拒绝状态。
3. 从现有通知按钮发起一个本人真实未完成词，确认重进 App 事务仍显示「待确认」，连续点击不会触发第二次操作。
4. 在墨墨官方 App 中完成同词反馈，核对记录并显示「已成功」或「已失败」，离线/超时仍保持「待确认」。
5. 下一阶段从真实、脱敏的运行时请求确认完整上传封包、字段号、同步顺序、幂等/冲突语义后，才把 `CortexAuthorizedSender` 从禁写换成真实发送器。

未运行 Android CI 或真机验证之前，不能将其标为发布可用。原有流体云及前台反馈行为未改动。
