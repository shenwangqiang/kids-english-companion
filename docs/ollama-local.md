# 在本地 Mac 运行免费 Qwen 服务

## 1. 安装并启动 Ollama

从 [Ollama 官网](https://ollama.com/download) 安装 macOS 版本，然后拉取模型：

```bash
ollama pull qwen3:4b
ollama list
```

先确认仅本机可访问的默认服务正常：

```bash
curl http://localhost:11434/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"qwen3:4b","messages":[{"role":"user","content":"Reply with OK."}],"stream":false}'
```

## 2. Android 模拟器连接

Android 官方模拟器使用 `10.0.2.2` 访问开发 Mac。在应用的 Qwen settings 中填写：

- Base URL：`http://10.0.2.2:11434/v1`
- Model：`qwen3:4b`
- 勾选 `Allow private-network HTTP in debug builds`

保存后点击 `Test connection`。

## 3. Android 真机连接

只有真机需要让 Ollama 监听局域网地址。先查 Mac 私网地址：

```bash
ipconfig getifaddr en0
```

若 Wi-Fi 不在 `en0`，可在系统网络设置中查看当前私网 IPv4。随后仅在可信家庭/开发局域网中，按 Ollama 当前版本的设置方式将监听地址绑定到 Mac 的那个私网 IP 和端口 11434，再重启 Ollama。

应用配置为：

```text
http://<private-ip>:11434/v1
```

同时开启 debug 私网 HTTP 开关。Mac 防火墙只应允许可信局域网访问；不要在路由器上做端口映射，也不要把未经认证的 11434 端口暴露到互联网。使用完真机调试后恢复仅本机监听。

## 4. 性能选择

`qwen3:4b` 是默认平衡选项。内存紧张时可换更小的 Qwen 模型并在应用同步修改 Model；更大的模型通常更慢、占用更多内存。模型免费不等于运行零成本，本机会消耗内存、电力和计算资源。
