# Seal

Seal 是本地运行的 MySQL 8 风险 SQL 观测台。它从 Linux 网卡实时抓包或读取上传的 PCAP，重组 TCP/MySQL 协议，脱敏并聚合 SQL 指纹，再给出可解释的静态风险分与按需执行计划。

默认管理台：`http://127.0.0.1:7070/seal`

## 能力

- 全局实时抓包：进程状态、丢包/盲流指标、SQL 流、筛选、暂停、手动启停。
- PCAP 分析：异步上传任务、历史、脱敏 SQL 聚合、次数与延迟。
- MySQL 协议：`COM_QUERY`、prepared statement prepare/execute/close、多 MySQL 包、TCP 乱序/重传。
- 风险分析：`0=安全`、`100=严重`；每条规则提供证据、权重与修复建议。
- 数据库连接：AES-GCM 加密密码、连接测试、端点映射、用户触发的 `EXPLAIN FORMAT=JSON`。
- 本地安全：Argon2id 管理口令、会话过期、SameSite Strict、CSRF、登录限速与严格 CSP。
- 单二进制：templ 页面、htmx 2.0.10、SSE 扩展、Tailwind CSS 4.3 与 templUI 源码全部嵌入。

## 边界

- 仅支持 Linux、MySQL 8.x、网卡可见的明文 TCP 流量。
- TLS、MySQL compression、Unix socket 流量不解析；界面会计入盲流，不尝试解密。
- 逐条原始 SQL 与 prepared 参数永不落盘。原始 SQL 仅在受控内存中短时保留，供即时详情/EXPLAIN 使用。
- 旧 EclipseStore `storage/` 不读取、不迁移、不自动删除。
- 运行时只依赖 `tcpdump`；不再依赖 Java、Maven、EclipseStore 或 SOAR。

## 构建

要求 Go 1.26 与 Node.js 20+。Node 只用于生成前端资源，不是运行时依赖。

```bash
go generate ./...
go test ./...
CGO_ENABLED=0 go build -trimpath -o seal ./cmd/seal
```

也可执行：

```bash
make build
```

发布时仅需 `seal`、README 和可选 systemd unit。

## 运行

安装 Linux `tcpdump` 后：

```bash
./seal
```

首次启动必须监听 loopback。打开 `/seal/setup` 创建管理员后，才允许改为非 loopback 地址。应用不执行 `sudo` 或 `setcap`；权限不足会进入 degraded 状态并在诊断页说明。

常用配置优先级：CLI > 环境变量 > 默认值。

| CLI | 环境变量 | 默认值 |
| --- | --- | --- |
| `--listen` | `SEAL_LISTEN` | `127.0.0.1:7070` |
| `--base-path` | `SEAL_BASE_PATH` | `/seal` |
| `--data-dir` | `SEAL_DATA_DIR` | `data` |
| `--tcpdump` | `SEAL_TCPDUMP_PATH` | `tcpdump` |
| `--interface` | `SEAL_INTERFACE` | `any` |
| `--mysql-ports` | `SEAL_MYSQL_PORTS` | `3306` |
| `--retention` | `SEAL_RETENTION` | `720h` |
| `--max-upload` | `SEAL_MAX_UPLOAD` | `268435456` |
| `--ring-capacity` | `SEAL_RING_CAPACITY` | `10000` |
| `--queue-capacity` | `SEAL_QUEUE_CAPACITY` | `2048` |

实时抓包等价于无 shell 拼接执行：

```text
tcpdump --immediate-mode -U -n -s 0 -i any -w - 'tcp and (port 3306)'
```

## 最小权限服务

示例见 [`deploy/seal.service`](deploy/seal.service)。unit 仅授予：

- `CAP_NET_RAW`
- `CAP_NET_ADMIN`

TLS 应由反向代理终止。反向代理必须关闭 SSE 缓冲，并保留长连接。

## 数据文件

- `data/seal.db`：bbolt 数据库，0600。
- `data/seal.key`：自动生成的 AES-256 密钥，0600。
- `data/tmp/`：PCAP 任务临时文件，任务结束即删除。

请把 `data/` 纳入受控备份；不要复制或公开 `seal.key`。

## 健康检查

- `/healthz`：进程存活。
- `/readyz`：本地数据存储可用。

## 设计与产品契约

- [`PRODUCT.md`](PRODUCT.md)：用户、用途、原则与可访问性目标。
- [`DESIGN.md`](DESIGN.md)：高密度浅色界面、组件与 WCAG 2.2 AA 规则。

## License

见 [LICENSE](LICENSE)。
