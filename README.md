# yuan-x — X (Twitter) Monitor System

基于 Spring Boot 的 X (Twitter) 监控系统，用于实时监控目标账号的推文、关键词、互动等数据。

---

## 技术栈

| 组件 | 版本 |
|------|------|
| JDK | 17 |
| Spring Boot | 3.4.3 |
| MySQL | 8.0 |
| Maven | 3.9+ |

---

## 项目结构

```
yuan-x/
├── src/
│   ├── main/
│   │   ├── java/com/deanrobin/yx/
│   │   │   ├── YxApplication.java       # 启动类
│   │   │   ├── config/                  # 配置类
│   │   │   ├── controller/              # REST 接口
│   │   │   ├── service/                 # 业务逻辑
│   │   │   ├── repository/              # 数据库访问
│   │   │   ├── model/                   # 实体类
│   │   │   └── scheduler/               # 定时任务
│   │   └── resources/
│   │       └── application.yml          # 配置文件
│   └── test/
├── pom.xml
└── README.md
```

---

## 快速启动

### 1. 准备数据库

```sql
CREATE DATABASE yx_db DEFAULT CHARACTER SET utf8mb4;
```

### 2. 配置 application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yx_db
    username: your_username
    password: your_password
```

### 3. 构建运行

```bash
mvn clean package -DskipTests
java -jar target/yuan-x-0.1.0-SNAPSHOT.jar
```

---

## 架构说明

```
环境变量 (X_API_KEY / X_API_SECRET)
  └─→ BearerTokenProvider  启动时换取 Bearer Token（仅存 JVM 内存）
        └─→ XStreamRuleService  同步过滤规则到 X API
              └─→ XStreamService  建立长连接，实时监听推文流
                    └─→ NotifyDispatcher  分发通知
                          ├─→ LarkNotifier    ✅ 默认开启
                          ├─→ TelegramNotifier ❌ 默认关闭
                          └─→ QqNotifier      ❌ 默认关闭
```

**监听机制：** X Filtered Stream API v2（长连接实时推送，非轮询）
**自动重连：** 断线后指数退避重连（5s → 10s → 20s ... 最长 5 分钟）
**去重：** 每条推文入库前检查 tweet_id

## 功能规划

- [x] X API 接入与认证（Key/Secret → Bearer Token）
- [x] Filtered Stream 实时监听
- [x] 数据持久化（MySQL）
- [x] Lark / Telegram / QQ 通知
- [ ] 监控账号动态管理 REST API
- [ ] 关键词过滤规则

---

## License

MIT
