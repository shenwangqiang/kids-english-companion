# Kids English Companion

一个面向 6–12 岁儿童的英语对话与伴读项目，包含 Android 原生、iOS 原生和 Flutter 客户端。选择年龄后可进入英语对话；聊天记录、阶段总结、记忆信息和下一步话题方向保存在设备本机。项目提供 18 篇原创分级故事，可离线阅读和逐句朗读。

这里的“已喂书籍”采用的是可审计的本地分级内容库 + 检索增强提示（RAG），不是重新训练模型。这样更快、更容易控制答案范围，也避免把受版权保护的整本教材打包进模型。

## 当前能力

- 三个年龄段：6–8、9–10、11–12 岁，每组 6 篇原创故事
- 话题目录：How to Train Your Dragon、Harry Potter、Space、Dinosaurs、Animals、School Life，以及最多 60 个字符的自定义话题
- 在“新建对话”中选择话题；“历史对话”按最近更新排序，可重新打开当前年龄段的独立对话
- 点击用户或助手的英文消息可重新朗读，不会启动语音对话
- 竖屏布局，旋转设备时主页面仍保持竖屏
- Android Text-to-Speech 逐句朗读与语速调节
- Android SpeechRecognizer 跟读转写和单词匹配分数
- 用户主动开启的免手持语音循环：听取、回答、朗读后自动继续听取
- 麦克风前台服务、常驻通知 Stop 操作和锁屏对话页可见性
- Room 本地进度、最高分和完成状态
- Ollama 原生 `/api/chat` 接口，固定关闭思考输出并检查真实回复
- 最近 8 轮上下文 + 本地持久化总结、记忆信息和下一话题方向
- 故事证据约束、儿童隐私/不安全问题拦截、离线降级
- Keystore AES/GCM 加密保存可选 Bearer Token

## iOS 客户端

原生 SwiftUI 工程位于 `ios/KidsEnglishCompanion.xcodeproj`，最低支持 iOS 17。当前包含年龄分组、Ollama 对话与模型发现、独立历史会话、Keychain Token 保存、18 篇离线故事、逐句系统朗读、故事证据问答和完成进度。主动开启语音对话后使用 Apple Speech 与 AVSpeechSynthesizer；锁屏或后台语音是否持续取决于系统语音服务和设备状态。iOS 使用设备自带英语音色，没有集成 Google 或讯飞语音 SDK。

跟读分数只是词语匹配练习，不是专业发音评估。Android 的语音识别是否完全离线取决于设备和已安装语言包。

## Flutter 客户端

Flutter 工程位于 `flutter/`，共用 Android Material 3 主题和产品流程，支持 Android 与 iOS。当前功能包括年龄分组、Ollama 对话与模型发现、独立本地历史、18 篇离线故事、故事问答、跟读评分、语音输入、英语 TTS 和连接设置。Flutter 的会话数据库与两个原生应用相互独立；当前不迁移历史记录。Android 锁屏语音后续需要原生前台服务桥接，目前 Flutter 不提供锁屏继续对话。

```bash
cd flutter
/Users/lisi/flutter/bin/flutter pub get
/Users/lisi/flutter/bin/flutter run -d <device>
```

iOS 命令需指定完整 Xcode：

```bash
cd flutter
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer /Users/lisi/flutter/bin/flutter run -d <ios-device>
```

Android 模拟器访问 Mac Ollama 使用 `http://10.0.2.2:11434`；iOS 模拟器可使用 `http://127.0.0.1:11434`；真机使用 Mac 局域网 IP。远端服务使用 HTTPS。Android 的 Google / 讯飞 TTS 引擎选择仅在设备安装对应引擎和英语数据时生效；iOS 使用系统英语语音。语音识别按单次短句启动，不会静默常开。

How to Train Your Dragon 和 Harry Potter 等品牌名称仅作为讨论话题，应用不包含这些书籍的原文或完整内容。话题对话与原创分级故事是两个独立入口。

语音对话必须在应用可见时由用户点击 `Start voice conversation` 启动，并授予麦克风及通知权限。启动后可锁屏继续；通知和页面上的 Stop 都会停止识别、TTS 与前台服务。没有唤醒词，也不会在开机或后台静默启动麦克风。部分设备会因省电策略或厂商 SpeechRecognizer 限制而在锁屏后暂停识别，文本对话不受影响。

## 构建要求

- Android Studio / Android SDK 36
- JDK 17
- Android 8.0（API 26）或更高版本
- iOS：Xcode 16+，iOS 17+

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 reverse tcp:11434 tcp:11434
```

iOS：在 Xcode 中打开 `ios/KidsEnglishCompanion.xcodeproj`，选择 `KidsEnglishCompanion` scheme 和 iOS Simulator 或已签名的 iPhone 设备后运行。也可使用完整 Xcode 执行：

```bash
xcodebuild -project ios/KidsEnglishCompanion.xcodeproj -scheme KidsEnglishCompanion -destination 'generic/platform=iOS Simulator' build
```

首次启动选择年龄段即可进入对话；即使模型离线，故事阅读、朗读、跟读和进度仍可使用。`Settings` 可配置：

- 本机 Android 模拟器：`http://10.0.2.2:11434`
- 同一局域网真机：`http://<Mac-private-IP>:11434`
- 远端 Ollama 服务：`https://ai.example.com`
- 模型：设置页自动读取 Ollama `/api/tags`，支持下拉选择、刷新、手动输入和连接失败回退
- 默认模型：`qwen3.5:4b-mlx`（当前测试机上最快且能稳定返回结构化对话）
- TTS：支持“自动 / Google / 讯飞”，自动模式按 Google 英语 → 讯飞英语 → 系统默认回退

私网 HTTP 只允许在 debug 构建中手动开启；release 构建要求 HTTPS。详细步骤见 [本地 Ollama](docs/ollama-local.md) 和 [远端部署](docs/ollama-remote.md)。

## 仓库目录

- `android/`：当前 Android 原生应用及其 Gradle 构建文件
- `flutter/`：共享 Android/iOS Flutter 客户端
- `ios/`：当前 iOS 原生 SwiftUI 客户端和 Xcode 工程
- `docs/`：共享设计说明、部署指南和平台文档

Android Studio 项目入口为 `android/`；原生 Xcode 项目入口为 `ios/KidsEnglishCompanion.xcodeproj`；Flutter 项目入口为 `flutter/`。

## 模型费用

Qwen 开放权重和 Ollama 软件本身可免费自建；本地运行不按请求收费，但会消耗电脑的内存、电力和算力。远端机器仍会产生服务器、带宽、域名和证书等成本。应用也能在不连接模型时完成阅读、朗读、跟读和进度记录。

## 隐私与安全边界

- 故事和学习进度保存在设备本地。
- 历史对话、话题、阶段总结和记忆只保存在当前设备，不提供云端同步；不同年龄段的历史分开显示。
- 音频交给 Android 系统语音识别服务处理；具体是否联网由设备实现决定。
- 对话会把最近 8 轮、阶段总结和当前输入发送到你配置的服务。
- 不记录 Token、儿童音频或识别文本到日志。
- 内容规则是产品防护层，不代表已经获得教育、医疗或儿童安全认证。

## 许可证

本项目采用 [MIT License](LICENSE)。
