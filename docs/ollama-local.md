# 在本地 Mac 运行免费 Qwen 服务

## 1. 安装并启动 Ollama

从 [Ollama 官网](https://ollama.com/download) 安装 macOS 版本，然后拉取模型：

```bash
ollama pull qwen3.5:4b-mlx
ollama list
```

应用也支持 `qwen3.5:9b`、`qwen3:4b` 和 `smollm2:1.7b`。设置页会通过 `/api/tags` 自动读取已经安装的模型；未安装的模型不会打包进 APK。可按需要额外执行：

```bash
ollama pull qwen3.5:9b
ollama pull qwen3:4b
ollama pull smollm2:1.7b
```

先确认仅本机可访问的默认服务正常：

```bash
curl http://localhost:11434/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"model":"qwen3.5:4b-mlx","messages":[{"role":"user","content":"Reply with OK."}],"stream":false,"think":false}'
```

## 2. Android 模拟器连接

Android 官方模拟器使用 `10.0.2.2` 访问开发 Mac。在应用的 Qwen settings 中填写：

- Base URL：`http://10.0.2.2:11434`
- Model：`qwen3.5:4b-mlx`
- 勾选 `Allow private-network HTTP in debug builds`

保存后点击 `Test connection`。

如果 Mac 上的 Ollama 只监听 `127.0.0.1`，模拟器可能无法通过 `10.0.2.2` 连接。调试时可使用更安全的 ADB 反向映射：

```bash
adb reverse tcp:11434 tcp:11434
```

随后把应用 Base URL 改为 `http://127.0.0.1:11434`。该映射会在模拟器或 ADB 重启后失效，需要重新执行；它不会把 Ollama 暴露给局域网。

## 3. Android 真机连接

只有真机需要让 Ollama 监听局域网地址。先查 Mac 私网地址：

```bash
ipconfig getifaddr en0
```

若 Wi-Fi 不在 `en0`，可在系统网络设置中查看当前私网 IPv4。随后仅在可信家庭/开发局域网中，按 Ollama 当前版本的设置方式将监听地址绑定到 Mac 的那个私网 IP 和端口 11434，再重启 Ollama。

应用配置为：

```text
http://<private-ip>:11434
```

同时开启 debug 私网 HTTP 开关。Mac 防火墙只应允许可信局域网访问；不要在路由器上做端口映射，也不要把未经认证的 11434 端口暴露到互联网。使用完真机调试后恢复仅本机监听。

## 4. 性能选择

`qwen3.5:4b-mlx` 是 Apple Silicon Mac 的当前默认选项，约需 4 GB 模型磁盘空间。在 12 GB 可用统一内存/显存的测试机上，它的连续结构化对话约为 7.45 秒，而 `qwen3.5:9b` 约为 16.8 秒。`smollm2:1.7b` 虽然约 5.84 秒，但实测不能稳定遵循本应用的 JSON 输出协议，因此保留为手动选项而不作为默认。9B 可作为质量优先的手动选项；更大的模型通常更慢、占用更多内存。模型权重和 Ollama 可免费自建，但本机会消耗内存、电力和计算资源。

应用使用 Ollama 原生 `POST /api/chat`，每次请求明确发送 `stream:false`、`think:false` 和 30 分钟模型常驻设置。`Test connection` 会实际请求一条非空回复；只有端口可连但模型未回答时不会显示成功。对话成功后，回答、总结、记忆信息和下一话题方向均保存在 Android 本地 Room 数据库中。
