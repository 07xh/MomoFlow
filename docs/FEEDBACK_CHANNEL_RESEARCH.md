# MomoFlow｜墨墨原生记忆反馈交互机制研究（2026-11-09）

## 目标与范围

用户希望从 **ColorOS 流体云中的「认识 / 模糊 / 忘记」三个按钮** 发起反馈，最终修改同一墨墨账号的真实学习历史和后续复习安排，**无需手动打开墨墨 App**。

研究顺序：先确定可靠的反馈通道，再验证与官方学习记录的闭环；**暂不继续扩展 MomoFlow 的本地记忆调度**。

本文件是证据整理与验证设计。尚未证明后台直接写回可用，不能对外称“回写已实现”。

## 已核对的事实与出处

| 事实 | 证据 | 意义 |
| --- | --- | --- |
| 官方开放 API 的学习数据组只有读取进度、读取今日单词、查询学习记录、添加学习计划、提前复习五种操作，没有提交记忆反馈 | https://www.maimemo.com/pages/openapi-demo/index.html ; https://open.maimemo.com/ | 不能用 add_words/advance_study 冒充 FAMILIAR/VAGUE/FORGET |
| 识记反馈会影响墨墨的未来复习计划；当天第一次反馈与当天重复反馈的作用不同 | https://memodocs.maimemo.com/docs/Ok6YwF2X7iJZvvkCunjcmWwLnzb/ ; https://www.maimemo.com/help30 | 不能只改 is_finished、last_response 等读取结果 |
| 墨墨口袋手柄 / 外接键盘可以发出认识、模糊、忘记等操作，但官方明确**仅在墨墨 App 复习页面**支持 | https://memodocs.maimemo.com/docs/OxvKwPNcTi8PR4k6Xzyc8Ad5nMd/ | 这是一条经官方应用处理的交互通道；它不能证明独立 App 可在后台直接提交 |
| 官方墨墨单词机支持三个物理反馈按钮、离线学习、和手机账号的学习数据双向同步 | https://memodocs.maimemo.com/docs/support_wordmachine | 官方体系存在设备侧反馈同步通道，但未公开可复用的第三方协议 |
| maimemo-desktop 的开放 API 数据读取可用，但本地反馈不会写回墨墨账号 | https://github.com/qingfpc/maimemo-desktop/blob/main/README_zh.md | 不应将其本地调度逻辑复用成“官方写回” |
| 墨墨官方学习记录（如 last_response、study_count）可以只读核查；返回可能为空或同步延迟 | https://github.com/fuhan666/maimemo-sdk ; MomoFlow/MaiMemoClient.kt ; MomoFlow/OfficialRecord.kt | 回读能提供证据，但单字段未变化不等于用户未反馈 |

## 技术判断（已证实 / 仍属假设）

1. **已证实：** 墨墨自己的前台复习页面具有官方支持的外部输入映射。用户在前台正确点按三个按钮后，由墨墨自身负责合法的学习状态变更。
2. **已证实：** 独立官方单词机能离线背词、再同步账号。说明不能假设“每按一次键必然有一条立刻联网的请求”；具体同步机制没有公开资料验证。
3. **待证：** 一加 Ace 6 的墨墨版本是否将三个按钮和当前单词作为 Android 无障碍可识别节点公开。
4. **待证：** 能否在**不显示墨墨 App** 的前提下，通过受支持的第三方 SDK、官方深链、经授权的接口或官方公开协议提交反馈。现在**没有证据**。
5. **待证：** ColorOS 折叠流体云是否允许这三个直接动作；普通展开通知能有三个 Action 不代表系统胶囊也有三个按钮。

## 决策与优先级

| 路线 | 本阶段决定 | 理由 |
| --- | --- | --- |
| A. 新增官方反馈写入 API / 经许可 SDK | 作为理想目标，继续核对公告 | 唯一可直接实现后台反馈的稳定公共能力；目前未发布 |
| B. 墨墨 App 前台正常交互（官方手柄映射或页面控件） | **优先做单词级验证** | 复用墨墨现有算法，不伪造调度；可能不满足“完全不进入墨墨”的终极体验 |
| C. 官方单词机协议 | 仅记录研究，不模仿未公开的设备认证与会话 | 双向同步属官方设备能力，不等于对外开放 API |
| D. 本地记录三档并标成官方完成 | 禁止 | 会造成真实学习状态和显示不一致 |
| E. 臆造 / 重放未公开私有接口 | 不采用 | 易失效，存在身份与调度一致性问题 |

## 一次最小真机验证（MomoFlow 已有 V0.4 能力）

- **准备：** 使用本人墨墨账号、正常登录，并开启官方的自动同步；选择墨墨复习队列里**确实能看到**的一个词。测试不改他人账号。
- **基线：** 通过 `MaiMemoClient.record(vocId)` 读取官方 `last_response`、`study_count`、`last_study_date`、`next_study_date`；另外记下今日任务 `is_finished` 与总进度，但不假定它们每次点击都变。
- **页面可识别性：** 启用当前仅两分钟、只读的 `MomoInspectionService`，核对 `word_visible` 和三种按钮是否可点击；始终限定官方包名。
- **真实操作：** 在墨墨自己的复习页面确认确实是**同一单词**，由本人主动点击一次「认识 / 模糊 / 忘记」中的一个；先不让 MomoFlow 自动替代点击。
- **回读：** 重新读取官方记录，必要时让墨墨正常同步后再查看。需要记录未变化、同步延迟、无记录、反馈类型不符等状态，不能只凭 UI 下一个词就说成功。
- **判定：** 只有 `voc_id` 相符、反馈类型符合，且服务器有相应记录变化时，才可认定“官方账号记录确实改变”。这**仍然不代表**独立 MomoFlow 在后台可直接写入。
- **安全：** 不上传 Token、Cookies、Authorization、完整 HAR 或包含账号标识的响应。诊断输出只允许报告单词身份匹配与反馈按钮识别结果；网络分析只能使用脱敏字段。

### 后续若做前台受控按钮代理

仍需先证实墨墨确有目标词的精确匹配和按钮可点击，用户明确按下通知动作后才允许一次受控交互。不能盲点当前屏幕、不能后台重放旧操作。此路线需要墨墨进入正常可交互的前台页面，因此**不能宣传为“无需进入墨墨的真正后台直写”**。失败须保持原状态，并回读检查。

## 当前代码复用点

- `app/src/main/java/io/github/personal/momoflow/MomoInspection.kt`：已有包名限制、目标词白名单、两分钟只读节点识别；不自动点击。
- `app/src/main/java/io/github/personal/momoflow/OfficialRecord.kt`：前后官方反馈记录对照；需认识到服务端同步可能延迟。
- `app/src/main/java/io/github/personal/momoflow/OfficialWritebackTrial.kt`：保存单次手动实验上下文。
- `app/src/main/java/io/github/personal/momoflow/MaiMemoClient.kt`：只读官方 API；未实现不存在的反馈写入接口。

**阶段结论：** 已找到官方认可的**前台**交互渠道，也确认官方设备存在独立同步能力。但尚未找到允许第三方 App 在后台直接提交记忆反馈的公开协议。下一阶段应在一加 Ace 6 上验证当前墨墨前台控件可识别性、单词身份一致性与官方记录同步，而不是扩展本地反馈调度。
