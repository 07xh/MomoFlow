# 墨墨流云助手 · MomoFlow 0.5.6

个人学习、自用 Android 助手，面向一加 Ace 6 / Android 16。使用本人墨墨开放 API Token 读取真实数据，提供背词通知、小组件及可选的前台反馈操作。

## 0.5.6

- 「背词 / 设置」两页：首页显示官方进度、当前单词、释义、三档反馈和会话入口；连接、权限及诊断移入设置。
- 首页与通知统一使用「认识 / 模糊 / 忘记」同词反馈流程，支持按英文拼写定位今日待学词。
- 前台自动反馈默认关闭，需另开系统无障碍服务和背词会话。每次主动反馈只授权一次，60 秒过期；墨墨前台、唯一同词、三个独立可点击按钮、两次快照识别全部通过才尝试点击。
- 基线读取期间可取消；取消会清除待执行动作和页面识别。重复点击不能覆盖待核对反馈。切换账号、日期变化或 Activity 销毁后丢弃旧回调。
- 官方回读匹配后，重新读取官方进度与待学词表；不通过本地计数模拟墨墨完成量。同步延迟、冲突和数据缺失保留待核对记录，支持重查。
- 整合 PR #4 的进度请求空 JSON、每日官方公开释义优先查询和释义来源标注；公开释义为空时保留 ECDICT。
- 处理系统栏/键盘/挖孔区域，内容最大宽度 640dp，按钮高度随字体换行，更新时保留滚动位置。

## 使用

1. 在墨墨 App 初始化今日学习并开启自动同步。
2. 墨墨「我的 → 更多设置 → 实验功能 → 开放 API」创建 Token。
3. MomoFlow 设置中连接 Token，再同步今日单词。Token 仅由 Android Keystore 加密保存在本机。
4. 「开始背词」启用通知。系统是否展示原生流体云由 ColorOS 决定；可在设置中开启提升请求、打开系统实时通知设置。
5. 手动模式：点击任一反馈，切换墨墨核对同词并手动提交，返回后核对官方记录。
6. 自动模式：设置中开启前台自动反馈，并在系统中开启「墨墨页面诊断与单次反馈」。反馈会切换墨墨，识别通过后尝试一次；返回自动核对。墨墨当前词不同可先按拼写切换助手单词。
7. 同步延迟时「重新核对官方记录」；「结束本次核对」只取消未执行请求，不能撤销已经发送给墨墨的操作。

本地练习位于设置，记录不计入墨墨进度。Android 桌面可添加「墨墨流云助手」小组件。

## 验证边界

- 无障碍点击派发成功不等于服务器已更新。只有官方同词反馈字段的新变化与预期一致才显示匹配；冲突、缺失字段或仅完成数量变化均不能判定反馈匹配。
- 当前实现需要墨墨在前台，尚无不进入墨墨的后台直接写回能力。
- CI 检查包括静态校验、JUnit、APK 构建及 Android 16 无账号界面/取消操作 smoke test；以对应提交的 Actions 记录为准。
- 一加原生流体云、真实墨墨控件识别、本人账号反馈与服务器回读仍需真机验收。无账号模拟器检查不能替代这些验收。

## 技术与开源复用

Kotlin + 原生 Android Views，minSdk 26，compileSdk / targetSdk 36，JDK 21，Gradle 9.4.1。

- 读取接口：`POST /study/get_study_progress`、`POST /study/get_today_items`、`POST /study/query_study_records`、`GET /interpretations`。
- 通知复用 FluidCapsule 的 AOSP Live Update 方案，释义复用固定版本 ECDICT；许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- 不保存墨墨账号密码，不通过本地练习改写官方进度，无需 Root。

```bash
python3 tools/verify_source.py
python3 tools/build_dictionary.py
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

构建输出：`app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 自动上传 APK、单测日志及模拟器截图。
