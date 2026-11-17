# Cortex 三态写回事务（待协议接通）

目标仍是「流体云按钮 → 经授权的 Cortex 发送 → 官方真实回读」。**截至本次提交，真实 Cortex 请求尚未实现、未开启，流体云按钮仍按既有前台流程运行。** 静态 APK 报告只确认候选接口与枚举名称，不能据此构造可调用的 protobuf、签名或认证。

本次补上 `CortexFeedbackTransaction.kt`：

- 将真实单词/反馈意图、操作唯一 ID、官方读前/读后快照、网络发送结果统一到一个事务协调器。
- 拒绝「模糊」枚举二义性、不合法或已完成目标词、跨账号、重复操作。
- 已发出但超时的提交结果标记为 **OUTCOME_UNKNOWN**，禁止盲目重发。
- 就算网络响应接受，也只有同一单词真实官方回读发生匹配变化才显示 **OBSERVED_MATCH**；其意义是“观察到匹配”，并不凭此宣称一定由本次操作造成。
- 保留 `CortexUnconfiguredSender` 默认拒绝写入。完成授权/完整 wire schema/签名与 checkpoint 证据后，替换 `CortexAuthorizedSender`，且需持久化操作 ID 跨进程去重并通过真机闭环测试。

官方 Open API 的 `CortexOpenApiReader` 已与现有 `MaiMemoClient` 对接，只做读前、读后核验，不假装 Open API Token 可授权 Cortex。

**下一阻塞项**：本人正常墨墨操作的完整脱敏 HTTP 请求结构（认证类别、媒体类型、protobuf 整数字段、调用顺序、checkpoint）。可以使用前一提交的离线 `tools/cortex_trace_audit.py` 查看结构，原始 HAR 和 Token 不上传。此阶段不是可用的后台三态回写版本。
