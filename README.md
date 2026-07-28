# 智享自习室系统核心业务接口

成员 C 后端模块，使用 Java + Spring Boot + Maven + MyBatis 实现。

## 已实现接口

- `POST /api/wx/login`：微信登录/注册，基础版使用 `code` 模拟换取 `openid`，返回 `user-{userId}` token。
- `GET /api/user/info`：根据 `Authorization: Bearer user-{userId}` 获取用户信息。
- `POST /api/reservation/create`：创建预约，校验用户封禁、座位维修、座位冲突、用户同一时段冲突、每日最多预约次数。
- `POST /api/reservation/cancel`：取消本人待签到预约，释放座位并刷新自习室统计。
- `GET /api/reservation/my`：分页查询我的预约，可按状态筛选。
- `POST /api/checkin/sign`：扫码签到，校验本人预约、座位二维码和签到宽容时间。
- `POST /api/checkin/leave`：提前退座，完成预约并释放座位。
- `GET /api/violation/my`：查询本人违规记录。
- `ReservationTimeoutTask`：每分钟扫描超时未签到预约，标记违约、记录违规、增加违规次数、达到阈值后封禁并释放座位。
- D 成员后台管理接口已合入：楼栋、自习室、座位、Excel导入、管理员预约查询/取消、系统配置、公告/楼栋/房间/座位公共展示。

## 业务逻辑说明

核心状态值来自接口文档：用户 `1正常/0封禁`，座位 `1空闲/2已预约/3使用中/4维修中`，预约 `1待签到/2使用中/3已完成/4已取消/5已违约`。

统一返回格式为 `code/msg/data`，业务异常通过 `BusinessException` 抛出，由 `GlobalExceptionHandler` 统一转换为 JSON。

系统配置读取 `system_config`：`checkin_limit_minutes` 默认 15，`max_reservation_per_day` 默认 2，`violation_limit` 默认 3。

## 运行

先在 MySQL 创建数据库并执行 `src/main/resources/schema.sql` 和 `src/main/resources/test-data.sql`。数据库连接通过环境变量配置，避免将本地密码提交到仓库：

- `DB_URL`：数据库连接地址，默认连接本机 `zixishi` 数据库
- `DB_USERNAME`：数据库用户名，默认 `root`
- `DB_PASSWORD`：数据库密码，无默认值

```bash
mvn spring-boot:run
```

## 测试

当前测试为 Mockito 单元测试，不依赖数据库。

```bash
mvn test
```
