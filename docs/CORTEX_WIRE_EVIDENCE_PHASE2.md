# Cortex：真实三态协议证据驱动编码器（第二阶段）

本阶段是代码实现，不是静态分析口头方案。新增 Android `CortexWireCodec`、双枚举层修正、离线真实样本校验器及 CI 测试；既有流体云通知、单次前台反馈、官方读后验证完全保留。

## 已经确认、不能混用的字段

上传的 5.6.10 `maimemo_cortex_reconstructed.proto` 明确区别：
- wasm 操作日志：`RESPONSE_UNCERTAIN` 代表 UI “模糊”；`OpHandleStudyResponse` 属于 `OpLog` 包装消息。
- Cortex 服务数据模型：`RESPONSE_VAGUE` 代表“模糊”，不是同一个 enum 的另一个成员。
- 两套 enum **整数值尚未由真实设备验证**；`OpLog` 的 protobuf 字段号依然不确定。

之前的 `mm_cortex_client.py` 假设 wrapper=1、word=1、response=2 并且给两套 enum 混排数字，这些只可用于离线演示；不允许放进真实发送。

## 本次真实代码

- `CortexStudyAdapter` 的 `VAGUE` 草案仅保留 wasm `UNCERTAIN` 名称；`CortexFeedbackTransaction` 不再因 wasm / Cortex 两套枚举名称不同而阻止“模糊”操作。**尚未为它填写数字或启用写回**。
- `CortexWireProfile.fromEvidenceJson` 对来源声明、三态数值、字段范围、oplog 上传端点及 checkpoint 结构进行检查。
- `CortexWireCodec.encode` 根据**真实核对后的**字段号及 wasm enum 编码 uint64 单词 ID 和三态 `OpLog`，不触发网络，不保存 Token。
- `tools/cortex_contract_from_trace.py` 验证三条本人账号**已成对采集**的真实运行时操作：三种不同 mark、各自 `voc_id`、对应 `OpLog` 字节和相同的实际 HTTP JSON body。仅输出字段编号、数值枚举及 JSON 键/类型，不输出单词 ID、原始请求、Token 或 checkpoint 值。
- 自测样本的 `source` 明确为 `_selftest_dump.json`，不能作为实际提交证据；不满足成对证据则直接退出且**不会输出“已验证清单”**。

运行（本人电脑，本地输入文件不可提交仓库）：

```bash
python3 tools/cortex_contract_from_trace.py private-cortex-paired.json --out cortex_wire_profile.redacted.json
```

**输入协议**：JSON 的 `source="device_runtime_capture"`、`wasm_enum_numbers`（含 FAMILIAR/UNCERTAIN/FORGET 的运行时 getNumber 数值），`samples` 数组恰好三条，每条 `mark`、十进制字符串 `voc_id`、`oplog_b64`、`http_body`（完整网络请求 JSON，至少有匹配的 `oplog` 与整数 `checkpoint`）。把 `mm_runtime_dump_v2.js` 原始记录在本人设备上对应组合即可，不上传含凭证的 dump。

本工具校验字段结构**不**代表已完成服务端写回验收；产出的 `ready_for_production_write` 始终是 `false`。还要补充本人账号真实回读、请求顺序和冲突测试；`CortexUnconfiguredSender` 不解除禁写。既有官方 App 前台反馈可以继续使用。

## 验收

```bash
python3 -m unittest discover -s tools -p 'test_cortex_contract_from_trace.py' -v
gradle :app:testDebugUnitTest :app:assembleDebug
```

Android 测试包含非默认字段号编码、模糊映射、非法 ID 和造假“已生产启用”报告拒绝。GitHub CI 成功只证明代码构建与离线测试，不代表真机写回成功。
