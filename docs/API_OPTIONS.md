# X (Twitter) API 方案对比

## 官方 API

**官网：** https://developer.x.com/en/portal/products

| 套餐 | 价格 | 推文读取 | Filtered Stream | 备注 |
|------|------|---------|----------------|------|
| Free | 免费 | 10,000 次/月 | ❌ 不支持 | 几乎所有读取接口都返回 403 |
| Basic | $100/月 | 100 万次/月 | ✅ 支持 | 最低可用的付费版 |
| Pro | $5,000/月 | 1,000 万次/月 | ✅ 支持 | 企业级 |

### 推文监控方案（官方）
- **方式：** Filtered Stream API（长连接，实时推送）
- **接口：** `GET https://api.twitter.com/2/tweets/search/stream`
- **延迟：** 几乎实时（秒级）
- **最低费用：** $100/月（Basic 版）
- **代码位置：** `XStreamService.java`（已实现，等升级套餐即可启用）

### 关注监控方案（官方）
- **方式：** 轮询 `GET /2/users/:id/following`
- **延迟：** 取决于轮询频率
- **限制：** Free 版不支持该接口，Basic 版可用

---

## 第三方 API：twitterapi.io

**官网：** https://twitterapi.io  
**文档：** https://docs.twitterapi.io  
**实时流页面：** https://twitterapi.io/twitter-stream  

### 计费方式（按量付费）

| 数据类型 | 单价 | 备注 |
|---------|------|------|
| 推文 | $0.15 / 1,000 条 | 最低 $0.00015/次（即使返回 0 条也收费） |
| 用户资料 | $0.18 / 1,000 个 | |
| 关注者 | $0.15 / 1,000 个 | |
| $1 = 100,000 Credits | | |

### 方案 A：轮询 `GET /twitter/user/last_tweets`（当前已实现）

- **方式：** 定时拉取用户最新推文，手动比对 ID 过滤旧推文
- **延迟：** 等于轮询间隔（当前 15 分钟）
- **限流：** 免费 Key 约 5 秒一次请求限制
- **费用估算（2 个账户，每 15 分钟）：**
  - 每次调用最低 15 Credits
  - 每月：4次/小时 × 24h × 30天 × 2账户 = 5,760 次
  - 月费：5,760 × 0.00015 = **约 $0.86/月**
- **注意：** 如缩短到 5 分钟轮询则约 $2.6/月，需先充值

### 方案 B：Tweet Filter Rules + WebSocket（待验证）

- **方式：** 注册过滤规则 `from:Keyshotvv OR from:0xzhaozhao`，twitterapi.io 服务端轮询后通过 WebSocket 推给客户端
- **接口：**
  - `POST /oapi/tweet_filter/add_rule` — 创建规则
  - `POST /oapi/tweet_filter/update_rule` — 激活规则（`is_effect: 1`）
  - WebSocket 连接地址：文档未公开，疑需订阅后解锁
- **延迟：** 最小 `interval_seconds` 为 0.1 秒（理论接近实时）
- **费用：** 未知（可能需要订阅套餐）

### 方案 C：User Stream Monitor（真实时推流）

- **官网：** https://twitterapi.io/twitter-stream
- **方式：** 订阅制，twitterapi.io 为指定账户维护长连接，通过 WebSocket 推送
- **平均延迟：** 约 1.2 秒（P99 < 2 秒）
- **费用：** $149/月（包含 50 个账户），超出账户另收 $1/账户
- **适合场景：** 监控 20 个以上账户且对延迟敏感时，折合最便宜

---

## 方案选型建议

| 场景 | 推荐方案 | 月费 |
|------|---------|------|
| 预算极低（2 账户，15 分钟延迟可接受） | twitterapi.io 轮询（当前方案） | ~$1 |
| 预算低（2 账户，5 分钟延迟） | twitterapi.io 轮询，缩短间隔 | ~$3 |
| 预算中（想要真实时，≤20 账户） | 官方 Basic API + Filtered Stream | $100 |
| 预算充足（≥20 账户，真实时） | twitterapi.io User Stream Monitor | $149 |

---

## 当前项目状态

- **推文监控：** twitterapi.io 轮询方案已实现并测试通过（`TweetPollScheduler.java`）
- **关注监控：** 代码已实现（`FollowMonitorScheduler.java`），当前已注释暂停
- **官方 Stream：** 代码已实现（`XStreamService.java`），升级 Basic 套餐后取消禁用即可
- **项目暂停原因：** 经费问题，待资金到位后按需选择上述方案恢复运行
