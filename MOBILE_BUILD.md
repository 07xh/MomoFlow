# 用手机构建 MomoFlow v0.4.0 APK（不需要电脑）

## 推荐方式：GitHub Actions + 单文件构建包

你只需要 GitHub 账号、手机浏览器，**不用 Termux、不用安装 Android Studio，也不用上传工程文件夹**。

1. 用手机浏览器打开 https://github.com/new，新建一个**空仓库**，例如 `MomoFlow`。建议选择 Private（私有），不要勾选 README。
2. 打开新仓库，选择 `Add file` → `Create new file`。在文件名填写：`.github/workflows/build-momoflow.yml`（注意输入完整的子目录路径）。
3. 打开随包提供的 `MomoFlow-mobile-build-v0.4.0.yml`，**复制全部文本**，粘贴到 GitHub 文件编辑区域，选择 `Commit changes`。
4. 打开仓库的 `Actions` 标签，找到 `MomoFlow APK · 手机单文件构建`。通常提交后自动触发；如果没有，点击 `Run workflow`。
5. 只有当构建任务显示绿色成功后，进入该运行详情页面底部的 `Artifacts`，下载 `MomoFlow-v0.4.0-debug-apk`。GitHub 下载的是 ZIP，手机解压后找到 `app-debug.apk` 并安装。
6. 若红色失败，下载 `MomoFlow-v0.4.0-build-log`，把日志文件上传给 ChatGPT 进行定位；**不需要抓包或提交墨墨 Token**。

> **重要**：这个 YML 是“手机方便用”的单文件构建版，已压缩内嵌完整项目源代码；不是 APK，本机不能直接安装。构建必须成功才能获得 APK。GitHub 上传 `source.zip` 本身并不能自动构建。

## 后续真正开发的仓库（推荐）

要持续编辑代码时，建议用 `MomoFlow-v0.4.0-source.zip` 中的文件原样推送到 GitHub 仓库。它的 `.github/workflows/android.yml` 可对每次源码提交自动构建，长远比内嵌的单文件 YML 更易维护。

## 当前功能与边界

- 官方只读 API 获取真实今日词表、学习进度、词条释义与记忆记录。
- 通知「下一个 / 显示或隐藏释义 / 结束」回写到 **MomoFlow 本地状态**，不是墨墨服务器。
- 通过人工同词反馈 + 官方学习记录前后对照，测试原 App 是否更新记录。
- **不保证 ColorOS 流体云提升成功、不具备自动回写墨墨掌握度的能力**。在一加 Ace 6 上需要真机检查。
- Token 只在安装后的 App 中输入；不要上传 GitHub、日志或聊天。

## 构建组件

- GitHub Actions Ubuntu Linux
- Temurin JDK 21 / Gradle 9.4.1 / Android Gradle Plugin 9.2.0 / SDK 36
- 流水线先执行静态检查，再执行 Kotlin/JUnit 单测和 Android Debug APK 打包。
- 失败时收集 build.log，成功时导出 Debug APK（调试签名，仅用于个人测试）。
