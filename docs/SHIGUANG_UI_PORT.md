# MomoFlow v0.7.0 · 拾光英语原生 UI 源码移植

源仓库：07xh/shiguang-english
源文件：native-android/app/src/main/java/com/shiguang/english/nativeapp/ui/{Theme.kt,VocabComponents.kt}
固定基线：c0e3fc517857601c0a2f5ecb4f23041ae974fd78

## 具体复用
- 直接复制 Theme.kt 的 Compose Material 3 主题、颜色 token。
- 直接复制 VocabComponents.kt 的 VocabGlassDock、WordSection、WordMetric、memoryColor 与 memoryName；只调整底栏四个项目名称以匹配 MomoFlow 的既有四页。
- 采用同一风格的白底轻边框卡片、悬浮胶囊底栏、背词卡片、统计布局与响应式圆角组件。
- 新的 MomoShiguangUi.kt 负责 UI；原有 MainActivity 服务调用、真实 API、流体云通知、Cortex 只读、官方回读和单次前台反馈仍沿用。
- 保留旧 View render* 实现作为回退参考，不在 v0.7.0 的入口渲染；后续确认真机无回归后清理。

## 原则
- 不引入拾光英语的模拟词书、SQLite 学习记录或离线数据源。
- MomoFlow 的本地三态反馈仍明确标为“本地”，不冒充官方提交。
- Cortex 不存在后台三态写回；只读及凭证隔离规则不动。
- Token 输入只存于 Compose remember 中，不使用 rememberSaveable / saved instance state。
- 使用 MomoFlow 原 APK 包名与 Keystore，不清除原有安装数据。

## 验收
- GitHub Actions 静态校验、单元测试、assembleDebug。
- Android 17 一加真机手工验收：底部 safe drawing inset、悬浮底栏点击、单词卡显隐、同步、流体云、无障碍单次验证与 401/403 显示。
- 静态校验与 CI 只能证明构建，不代表 Cortex 已写入或真机无问题。
