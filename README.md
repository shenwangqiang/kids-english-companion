# Kids English Companion

一个面向 6–12 岁儿童的 Android 英语对话与伴读 MVP。选择年龄后首先进入英语对话；应用会在本机保存对话、阶段总结、用户信息和下一步话题方向。另有 18 篇原创分级故事，可离线阅读、逐句朗读、跟读和记录学习进度。

这里的“已喂书籍”采用的是可审计的本地分级内容库 + 检索增强提示（RAG），不是重新训练模型。这样更快、更容易控制答案范围，也避免把受版权保护的整本教材打包进模型。

## 当前能力

- 三个年龄段：6–8、9–10、11–12 岁，每组 6 篇原创故事
- Android Text-to-Speech 逐句朗读与语速调节
- Android SpeechRecognizer 跟读转写和单词匹配分数
- 用户主动开启的免手持语音循环：听取、回答、朗读后自动继续听取
- 麦克风前台服务、常驻通知 Stop 操作和锁屏对话页可见性
- Room 本地进度、最高分和完成状态
- Ollama 原生 `/api/chat` 接口，固定关闭思考输出并检查真实回复
- 最近 12 轮上下文 + 本地持久化总结、记忆信息和下一话题方向
- 故事证据约束、儿童隐私/不安全问题拦截、离线降级
- Keystore AES/GCM 加密保存可选 Bearer Token

跟读分数只是词语匹配练习，不是专业发音评估。Android 的语音识别是否完全离线取决于设备和已安装语言包。

语音对话必须在应用可见时由用户点击 `Start voice conversation` 启动，并授予麦克风及通知权限。启动后可锁屏继续；通知和页面上的 Stop 都会停止识别、TTS 与前台服务。没有唤醒词，也不会在开机或后台静默启动麦克风。部分设备会因省电策略或厂商 SpeechRecognizer 限制而在锁屏后暂停识别，文本对话不受影响。

## 构建要求

- Android Studio / Android SDK 36
- JDK 17
- Android 8.0（API 26）或更高版本

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次启动选择年龄段即可进入对话；即使模型离线，故事阅读、朗读、跟读和进度仍可使用。`Settings` 可配置：

- 本机 Android 模拟器：`http://10.0.2.2:11434`
- 同一局域网真机：`http://<Mac-private-IP>:11434`
- 远端 Ollama 服务：`https://ai.example.com`
- 模型：设置页自动读取 Ollama `/api/tags`，支持下拉选择、刷新、手动输入和连接失败回退
- 默认模型：`qwen3.5:4b-mlx`（当前测试机上最快且能稳定返回结构化对话）
- TTS：支持“自动 / Google / 讯飞”，自动模式按 Google 英语 → 讯飞英语 → 系统默认回退

私网 HTTP 只允许在 debug 构建中手动开启；release 构建要求 HTTPS。详细步骤见 [本地 Ollama](docs/ollama-local.md) 和 [远端部署](docs/ollama-remote.md)。

## 模型费用

Qwen 开放权重和 Ollama 软件本身可免费自建；本地运行不按请求收费，但会消耗电脑的内存、电力和算力。远端机器仍会产生服务器、带宽、域名和证书等成本。应用也能在不连接模型时完成阅读、朗读、跟读和进度记录。

## 隐私与安全边界

- 故事和学习进度保存在设备本地。
- 音频交给 Android 系统语音识别服务处理；具体是否联网由设备实现决定。
- 对话会把最近 12 轮、阶段总结和当前输入发送到你配置的服务。
- 不记录 Token、儿童音频或识别文本到日志。
- 内容规则是产品防护层，不代表已经获得教育、医疗或儿童安全认证。
