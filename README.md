# 墨墨流云助手 · MomoFlow v0.5.2（源码验证版）

个人学习项目，Android 16 / 一加 Ace 6 优先，无服务器，不提供模拟学习数据。已有一加 Ace 6 的实际运行截图可确认展开式通知可显示；本次新增的离线释义尚未在真机验收，原生 ColorOS 胶囊与官方记忆反馈写回仍未证实。

## 最新方向：释义补全与官方反馈边界

- 墨墨官方今日单词数据不附带内置中文释义；`/interpretations` 不保证返回官方 App 内置中文翻译，部分账号会返回空数据。
- **本次修复**：显示某个词义时，若当天尚未尝试过，自动查询一次墨墨公开释义；有结果则优先显示，空结果继续保留 ECDICT 离线翻译。用户点击「查墨墨释义」仍可强制重查。页面标明释义实际来源，不把 ECDICT 冒充成墨墨内置翻译。
- **本次修复**：`get_study_progress` 以 POST 空 JSON `{}` 请求，保留原来的官方进度/词表同步机制；本地练习反馈不会计入官方完成量。
- 每次同步今日词表后，在后台读取**离线 ECDICT** SQLite 词典，为当天单词缓存中文释义与音标，展开式通知直接显示缓存，不需要进入墨墨 App。词典内容与墨墨内置释义**不保证完全一致**。
- 构建时从固定 Git 提交 `skywind3000/ECDICT@39cfb9672c09...` 下载 62.9 MB 的 `ecdict.csv`，筛出常用/考试词条并转换为 Android 资产；生成文件不提交源码仓库。**需待 CI 和真机确认词典实际生成成功。**
- **需求目标**是原生流体云拥有「认识 / 模糊 / 忘记」三个按钮并且直接改变本人墨墨账号。当前公开 API 没有记忆反馈提交接口，所以**没有实现，也不假装本地反馈等于官方写回**。目前通知仍提供「下一个 / 释义 / 结束」用于本地浏览。
- 只有在找到真实、可用且具备权限的反馈通道，并能回读官方 `last_response` 等记录核实成功后，才能将通知改成三个正式反馈按钮。未验证前不提交虚构的请求。
- 标准通知上的三个动作也不等于 ColorOS 原生胶囊支持三个可点击按钮；系统展示能力需要独立真机验收。

## v0.4：手机云构建与通知可靠性修复

- 增加可复制的脱敏设备兼容性诊断，不收集 Token、词表、账号。
- 通知内提供「下一个」「显示/隐藏释义」「结束」真实可操作按钮，按钮绑定当前单词 ID，防止旧通知作用到新词。
- 修正停止会话时的取消路径；官方学习记录对照结果保存在本地。
- 增加自动 GitHub Actions CI，并额外生成手机端**单文件导入构建**工具，适合无电脑时创建独立仓库。
- **仍未自动写回墨墨掌握度，仍待构建/真机验证。**

## v0.3：真实反馈写回实验

**目标**：判定未来能否通过用户明确触发的流体云/通知反馈，在墨墨原 App 里完成一次真实“认识 / 模糊 / 忘记”操作。

- 新增**单词级官方学习记录基线**：只读 `POST /study/query_study_records`，解析 `last_response`、`study_count`、上次/下次复习日期。
- 新增“**开始验证 → 跳转墨墨 App → 用户手动完成同词反馈 → 回到 MomoFlow 对照官方记录**”完整流程。
- 新增精确限定墨墨包名的、**用户自愿开启**的 AccessibilityService 只读页面检测：2 分钟内检测目标单词和三个可见反馈按钮，不自动点击、无全屏文本采集。
- 通知操作从“忘记（本地）”改为“**去墨墨验证**”，避免误以为已经修改墨墨服务器记忆记录。
- 原有本地反馈、今日真实词表、加密 Token、释义、桌面小组件、标准通知继续保留。

**关键声明**：本版**没有**自动提交记忆反馈给墨墨，也**没有**真机验证。用户在墨墨原 App 手动提交后，官方记录变化只证明服务器记录更新，不证明 MomoFlow 自动写入成功。

具体步骤和验收门槛：[docs/WRITEBACK.md](docs/WRITEBACK.md)。

## 第一次使用

1. 在墨墨官方 App 内开启自动同步，并在当天打开墨墨完成初始化。
2. 墨墨“我的 → 更多设置 → 实验功能 → 开放 API”创建自己的 Token。Token 只保存于自己手机的 Android Keystore 加密存储中，**不要发到聊天或上传到 GitHub**。
3. 在 MomoFlow 连接 Token、同步今日未完成单词，选择一条真实词条。
4. “墨墨真实回写 · 验证模式”：选择“认识 · 验证”等操作。App 先保存官方原始学习记录，然后跳转到墨墨。**确认墨墨当前词与试验词一致才手动提交。** 回到 MomoFlow 点击“验证官方记录”。
5. 如果想确认自动化读取控件的先决条件，可**自愿启用**“墨墨页面只读诊断”无障碍服务，再点击“只读识别墨墨背词页面控件（2 分钟）”。没有该权限，基本词表与人工对照流程仍可使用。

## 技术栈和接口

- Kotlin 原生 Android（暂不加入 Compose/Room 重构），minSdk 26，compileSdk / targetSdk 36。
- 官方墨墨 API：`POST /study/get_study_progress`、`POST /study/get_today_items`、`GET /interpretations`、`POST /study/query_study_records`。默认请求上限 `limit=1000`。
- 认证：HTTPS + Bearer Token，Android Keystore AES/GCM 本地加密；不保存账号密码、不访问私有学习接口。
- 通知：参考 MIT 开源项目 FluidCapsule 的 AOSP Live Update Publisher；是否显示为 ColorOS 流体云由系统决定。因为 Android Live Updates 要求正在进行且有时效性的活动，普通学习提醒可能不被提升。
- 开源材料、许可证：[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 构建与验收

需 JDK 21、Android SDK 36、Gradle 9.4.1。仓库 `.github/workflows/android.yml` 支持 Actions `workflow_dispatch` 手动构建 Debug APK。

```bash
python3 tools/verify_source.py
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK 输出路径（成功构建后）：`app/build/outputs/apk/debug/app-debug.apk`。

本次运行环境没有 Android SDK/Gradle 下载条件；只完成 Python 静态检查和源码压缩包验证，**不宣称 Android 构建或真机回写成功**。
