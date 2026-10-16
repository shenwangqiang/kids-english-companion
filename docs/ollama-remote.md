# 在远端机器部署 Qwen 服务

远端部署必须使用 HTTPS 和认证。不要直接公开 Ollama 的 11434 端口；让它只监听服务器回环或私有网络，由 Caddy、Nginx 或受管 API 网关终止 TLS、校验 Bearer Token、限流并转发请求。

## 推荐拓扑

```text
Android app → HTTPS :443 → authenticated reverse proxy → private Ollama :11434
```

建议同时配置：

- 有效域名和自动续期的 TLS 证书
- 长随机 Token，在代理层以环境变量或密钥管理服务注入
- 按 IP/Token 限流、请求体大小限制和超时
- 防火墙拒绝公网访问 11434，只开放 443
- 日志脱敏，不记录 Authorization、儿童输入或完整请求体

应用中填写：

- Base URL：`https://ai.example.com/v1`
- Model：服务器已经拉取的 Qwen 模型名
- Bearer token：代理签发的 Token

Token 会通过 Android Keystore 生成的 AES/GCM 密钥加密后保存在本机。应用不会在 UI 中回显已保存 Token；留空保存不会覆盖现有 Token。

## 不泄露 Token 的健康检查

先在 shell 环境安全地设置 `QWEN_TOKEN`，不要把真实值写入脚本、Git 或聊天记录。下面的请求只打印 HTTP 状态码，不打印 Token 或响应正文：

```bash
curl --silent --show-error --output /dev/null \
  --write-out '%{http_code}\n' \
  -H "Authorization: Bearer ${QWEN_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"model":"qwen3:4b","messages":[{"role":"user","content":"Reply with OK."}],"stream":false,"max_tokens":8}' \
  https://ai.example.com/v1/chat/completions
```

成功时通常返回 `200`。`401/403` 表示认证配置问题；`502/503/504` 通常表示代理无法连接模型服务或模型尚未就绪。

## 运营提示

Qwen 权重可以免费自建，但云服务器、GPU、流量、域名和运维不是免费的。先用本地 Mac 验证课程内容和交互，再根据并发量选择远端 CPU/GPU 规格。生产环境还应增加监控、备份、依赖更新和儿童隐私合规评审。
