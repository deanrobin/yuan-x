# y-x — X (Twitter) Monitor System

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
y-x/
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
java -jar target/y-x-0.1.0-SNAPSHOT.jar
```

---

## 功能规划

- [ ] X API 接入与认证
- [ ] 目标账号监控
- [ ] 关键词监控
- [ ] 数据持久化
- [ ] 告警通知

---

## License

MIT
