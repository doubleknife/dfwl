# R7-BIZ-1B V2 转派与多参与工资实施报告

## 范围与基线

- Git 基线：`24efa5b25589ccd8c13cd91c0c3ba0f96eff4bdc`；本阶段**未提交**。
- 遵循 `docs/15`～`docs/18` 的冻结决策和 BIZ-1B 任务要求；未实施 BIZ-1C、BIZ-1D、UI-FIX、R8-2。
- V8 已覆盖参与人、参与工资历史和线路当前参与指针。本阶段无必要迁移，未创建 V9，也未修改既有 V8。
- 开始时已有的本机 `backend/src/main/resources/application.yml`、`mini-program/project.config.json` 差异保持原状；`.runtime/` 仅存本机验收脚本、日志和 JAR。

## 实现事实

| 项目 | 实现与边界 |
| --- | --- |
| 初始参与 | `POST /routes/{id}/publish` 要求显式 `initialSalary >= 0`；INTERNAL 由后端读取司机当前公司车辆绑定；OUTSOURCED 要求真实 `outsourcedPlateNo`，不创建、查询或绑定公司车辆。初始参与人和工资投影与发布同事务。 |
| 转派 | `POST /routes/{id}/transfer` 要求当前参与 ID、线路版本、新司机、原因、新参与工资及所有有效旧参与工资。锁定线路后由后端确认司机类型、车辆与占用条件，旧参与保留为 `TRANSFERRED`，新参与递增 sequence 成为 current，线路进入 `TRANSFER_PENDING`。 |
| 等待接手 | `TRANSFER_PENDING` 可再次转派；所有已有参与工资仍必须完整提交。当前司机重新发车回 `IN_TRANSIT`；历史司机不能执行当前线路。原首次发车时间保留，新的发车司机/车/挂快照按接手人重建。 |
| N 次参与 | 无固定次数上限；连续三次转派的真实数据产生 4 个参与人，序列为 1～4。未发车的 TRANSFER 参与同样保留参与事实与核定工资。 |
| 工资核定 | 转派可调整所有有效旧参与工资，包括 0；相同金额不写无意义历史，变化写 `route_participant_salary_history`。新参与工资写入创建事实。独立 `POST /routes/{id}/participants/{participantId}/salary-adjustments` 需要 `route:salary:adjust`、原因和参与版本；已完成线路仍可核定。 |
| 有效合计 | 统一从有效参与人的工资求和；`CANCELLED`、`LEGACY_ENDED` 不计，`TRANSFERRED`、等待接手、`COMPLETED` 均计。`route_task.driver_salary` 仅作为该合计的兼容投影；本阶段未改 BIZ-1C 利润 SQL。工资投影变化推进线路版本，调薪前打开的转派表单会因版本过期被拒绝。 |
| 取消/作废 | 普通未执行 INITIAL 可取消，参与事实保留为 `CANCELLED` 且工资投影为 0；进入转派链后普通取消拒绝。转派中作废拒绝，只有完成线路沿用正式作废规则。 |
| 司机账号 | 基础资料创建司机时通过现有 `DriverAccountService` 原子创建或关联合法 DRIVER User；新账号初始密码由创建人提供并用现有编码器哈希，已有合法 User 不改密码。响应、Audit、日志不输出明文；重置仍走原流程。 |
| 参与历史 | `GET /routes/{id}/participants` 返回顺序、司机/类型、公司车辆或外协车牌、来源、状态、分配/发车/结束时间、原因与 current 标记。工资仅对 `salary:view`、`route:transfer` 或 `route:salary:adjust` 可见；工资历史单独要求 `salary:view`。司机身份不能使用管理端参与历史入口。 |
| 权限与 Audit | 转派仅 `route:transfer`；独立调薪仅 `route:salary:adjust`，均硬拒绝司机。`ROUTE_TRANSFER` 与 `ROUTE_PARTICIPANT_SALARY_ADJUST` 记录操作人、线路/参与人、前后事实及原因。 |
| 并发/重试 | 线路行锁、当前参与 ID、线路版本、参与版本和数据库唯一约束共同防重。过期转派/调薪拒绝；失败回滚参与、工资历史、线路状态和 Audit。通知等外围机制未改。 |

## API、界面与文件范围

- 后端：`master` 司机创建入口及请求、服务；`route` 发布、转派、工资调整、参与查询、状态/工资投影 Repository、请求/响应 DTO 与相关测试。
- 管理端：司机创建初始密码字段；线路发布初始工资/外协车牌、转派全员工资核定、参与历史和独立工资调整最小入口；沿用既有 Design System。
- 小程序：线路列表、首页当前任务与详情识别 `TRANSFER_PENDING`，只给当前参与司机显示可执行动作；无磅单或新业务页面。
- 契约：`docs/openapi.yaml` 已同步上述请求、响应、权限与新状态；YAML 语法解析通过。

## 自动化结果

- JDK 21；后端受影响类定向测试通过。
- 最终 `mvn test`：**545 tests，0 failures，0 errors，2 skipped**；2 项为既有条件集成测试跳过，未伪报为执行通过。
- `mvn -DskipTests package`：正常 `target` 输出首次因旧 JAR 被另一现存 JVM 占用而无法改名；随后用同一 `pom.xml` 的临时副本仅切换输出目录，在 `.runtime/biz1b-target/` **成功打包**。临时 POM 已删除，源码构建配置未改。
- `npm --prefix admin-web test` 与 `npm --prefix admin-web run build`：通过。
- 小程序正式测试：**87 项通过**（本阶段小程序适配完成后执行；最终只变更了后端工资版本校验及其测试，未重复运行未受影响的小程序测试）。
- `git diff --check`：通过；只有 Git 的 LF→CRLF 提示，无空白错误。

## 真实 MySQL + HTTP 验收

- 专用测试库：`fleet_ops_biz1b_test`，MySQL 8.0，Flyway V1～V8 共 8 次迁移；Spring Boot 真机启动，`GET /actuator/health` 为 `UP`。
- 正式 API 创建客户 1、产品 1、公司车辆 1～3、司机 1～5（其中自有 3、外协 2，均原子创建登录账号）；无直接 SQL 插入业务数据。
- 线路 1：自有司机初始工资 100，发车后转给外协司机（旧工资核为 0、新工资 30）；接手人未发车再次转给自有司机（工资 0/30/40）；又转给外协司机（0/30/40/20）。4 个参与人事实依次 `TRANSFERRED ×3 + IN_TRANSIT`，最终有效工资 95（首参与独立调薪至 5）。最终 current 重新发车成功。转派中取消、作废以及过期转派请求均被真实 HTTP 拒绝。
- 线路 2：外协司机初始工资 0、真实外协车牌；参与人没有公司车辆 ID。
- 线路 3：自有→自有转派，等待接手；随后真实 HTTP 独立调薪使线路版本推进，旧版本转派请求 409，参与人未重复生成。
- 数据库只读核对：线路 1/2/3 的参与人分别为 4/1/2，工资历史 3 条，`ROUTE_TRANSFER` Audit 4 条、独立工资调整 Audit 2 条；5 个司机账号密码哈希均为 60 字符，明文密码与 Audit 不匹配。数据库未用于制造业务事实。

## 管理端真实操作与小程序验收程度

- 管理端新页面与 API 的自动测试、构建通过。Computer Use 在接管 Chrome 前无法可靠确认浏览器 URL，按安全校验停止，没有执行点击。随后定位到旧 Java 进程占用 `8080`，使 `5175` 登录请求转发到旧服务并持续 pending；用户停止旧进程后，本轮后端在 `8080` 启动成功，且经 `5175` 代理的真实 HTTP 登录、`/me`、线路 1 与 4 条参与历史均成功。用户在真实 Chrome 页面核验：线路 3 的转派表单显示两名既有参与司机及工资 12/15、接手司机、新参与工资和原因；关闭旧抽屉后按精确线路号 `R20260928171458674003ae9` 查询线路 1，新打开的参与历史显示 4 条。先前的“两条记录”是仍查看线路 3，不是事实丢失或渲染缺陷。管理端本轮最小真实页面验收通过；未通过 GUI 再提交一笔业务转派，实际转派已由真实 HTTP/API 验证。
- 小程序线路状态与当前任务适配经 87 项正式测试通过；本阶段未再次执行微信开发者工具的真实点击，不把静态/组件测试写成真机验收。

## 剩余限制与结论

- **BLOCKER：无。** 后端业务、MySQL/HTTP、管理端最小真实页面、构建与自动化均通过。
- **NON-BLOCKING**：两项既有 skipped 条件集成测试；正常 target JAR 被既有 JVM 占用但隔离输出打包成功；BIZ-1A 继承的 Settlement Snapshot 逐字段/hash 迁移前后对比留到 BIZ-1R；缺少 `soffice.exe` 的 Word 逐页视觉核验限制仍保留。**R8-2 正式部署前必须确保无文件占用，重新执行标准 `mvn -DskipTests package` 并使用标准构建产物**；本阶段的隔离输出 JAR 不作为部署产物。
- **DECISION REQUIRED：无。** BIZ-1B 技术实施与本阶段验收项目已完成，可提交业务方关闭确认；未开始 BIZ-1C/1D/UI-FIX/R8-2，**未提交代码**，不自动进入下一阶段。
