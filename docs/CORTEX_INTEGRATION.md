# MomoFlow｜Cortex 接口暂存与验收门禁（2026-11-17）

## 当前结论

- 分支：`feat/cortex-contract-staging-20261117`。
- 证据：用户提供的《墨墨背单词 5.6.10 接口逆向报告》；该报告依据 APK 中的明文字符串、类名与字段线索，**不是**实际 HTTP 请求的完整记录。
- **完成范围：** `CortexStudyAdapter.kt` 收录接口路径、protobuf 枚举**名称**、三档选择的候选映射、缺口清单与封闭的写入边界。
- **没有完成：** Cortex 登录、网络请求、protobuf 编码、签名、真实写回、跨设备同步验证。
- `CortexStudyAdapter.submit()` 始终返回 `NOT_SENT_UNVERIFIED` 且 `networkAttempted=false`；目前无请求发送代码，也没有运行时开关。
- 既有 `MaiMemoClient` 使用官方公开 Open API 的 Bearer Token，**不能**据此推断 Token 适用于 Cortex 私有服务。既有前台单次点击和官方只读回读流程保持原样。

## 静态分析找到的线索

| 端点（相对 `https://cortex.maimemo.com/cortex`） | 静态推定用途 | 证据级别 |
| --- | --- | --- |
| `/api/v1/study/oplog` | 上传学习操作日志 | 仅 APK 字符串与方法名 |
| `/api/v1/study/merge/memo` | 合并学习状态 | 同上 |
| `/api/v1/study/download` | 下载学习数据 | 同上 |
| `/api/v1/study/checkpoints` | 获取同步检查点 | 同上 |
| `/api/v1/study/overview` | 查询学习概览 | 同上 |
| `/api/v1/study/alg_config` | 读取算法配置 | 同上 |

报告出现 `HandleStudyResponseRequest`、`OpHandleStudyResponse(voc_id=...)`、`UploadOplogRequest(checkpoint=...)` 等类名或字段线索，**不能**由此推断完整嵌套结构、必填项、字段编号或枚举整数值。

### 三态候选

| 本项目 `LocalMark` | APK 中出现的候选枚举名称 | 验证状态 |
| --- | --- | --- |
| FAMILIAR（认识） | RESPONSE_FAMILIAR | UI 到字段映射未运行时确认 |
| VAGUE（模糊） | RESPONSE_UNCERTAIN、RESPONSE_VAGUE | **二者关系未知，不可任选其一上报** |
| FORGET（忘记） | RESPONSE_FORGET | UI 到字段映射未运行时确认 |

其余 `RESPONSE_WELL_FAMILIAR`、`RESPONSE_CANCEL_WELL_FAMILIAR`、`RESPONSE_COMPLETED`、`STUDY_RESPONSE_UNSPECIFIED` 仅登记名称，不解释数值或语义边界。

## 开通提交之前必须取得的最小证据

1. **认证边界：** 确认是否存在受支持且允许第三方使用的授权方式，避免套用公开 API Token。
2. **请求结构：** 获得本人账号正常应用操作的脱敏样本，核实媒体类型、必要请求头、字段嵌套、protobuf 结构和响应格式；不能把字符串名直接拼成 JSON 发送。
3. **同步状态机：** 核实 `oplog` 与 `merge/memo` 是否都参与一次反馈，何时更新 checkpoint，以及离线、重复、冲突或失败时的处理机制。
4. **签名约束：** 确认有无签名要求及可被授权使用的调用通道，不绕过认证或安全机制。
5. **回读闭环：** 在本人账号中，以同一 `voc_id` 对照反馈前后官方学习记录，并排除同步延迟、其他设备操作和重复提交误判。
6. **端到端验证：** 在真机验证前台流程不回退，再单独验证新接入模块；CI 单测通过不等于服务器写入可用。

**数据安全：** 不上传 Authorization/Cookie/Token、请求签名秘密、设备指纹、用户 ID 或完整未脱敏 HAR；测试材料应保留必要字段的结构与类型，使用脱敏占位符。凭证仍只在本人设备按现有安全策略处理。

## 下一个开发门槛

现在只允许 `draft(word, mark)` 生成本地意图，并由 `submit()` 明确拒绝发送。将来如果有完整授权与协议证据，再以独立可测试实现接入、加入真实回读与防重提交策略；不得因静态分析命中字符串就解除禁写。

## 已有回归测试

`CortexStudyAdapterTest.kt` 覆盖：端点注册、三档候选及模糊枚举歧义、空单词 ID 拒绝、未验证提交不发网络请求。测试需要由 GitHub Actions 的 `:app:testDebugUnitTest` 实际执行，未运行前不得宣称通过。
