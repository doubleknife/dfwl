# R7-BIZ-1A V2 数据模型、状态机与安全边界实施报告

验收状态：**待业务验收；代码未提交**。本报告只覆盖 BIZ-1A，不代表 BIZ-1B/1C/1D、UI-FIX 或 R8-2 已完成。

1. **Git 基线与工作区**：以 `ad63b3a99ce50a485a89b320cd42ac5ceac9a27b` 及 `docs/15`～`docs/17` 为业务基线。开始前已存在本机 `backend/src/main/resources/application.yml`、`mini-program/project.config.json` 差异；两者未被本阶段修改或暂存。`.runtime/` 仅放本地测试日志、配置和 SQL 夹具。
2. **V8**：新增 `V8__route_participant_foundation.sql`，在既有 V7 上增加参与、工资历史、线路当前参与指针、审计 request ID 和两个权限码；既有 V1～V7 未修改。
3. **Participant 结构**：`route_participant` 记录 route、顺序、司机、类型快照、公司车或外协车牌、INITIAL/TRANSFER、状态、指派/发车/结束时间、结束/转派原因、DECIMAL 工资、版本和审计列；`(route_id, sequence_no)` 唯一。
4. **工资历史**：`route_participant_salary_history` 保存变更前后金额、原因、操作人和时间。允许 0；负值在服务和 MySQL CHECK 被拒绝。1A 仅让既有单司机工资入口投影至首参与，不实现 BIZ-1B 的多参与工资调整入口。
5. **当前参与唯一性**：`route_task.current_participant_id` 精确定位当前参与；线路服务先锁 route 行再改变指针或参与。MySQL 生成列 `active_route_id` 的唯一键阻止同线路两个 ASSIGNED/IN_TRANSIT 参与。`current_participant_id` 没有数据库外键，服务事务维护其指向；迁移后专库核对孤儿指针为 0。
6. **参与状态**：ASSIGNED、IN_TRANSIT、TRANSFERRED、COMPLETED、CANCELLED、LEGACY_ENDED；来源与状态分离。三段参与的有序读取和当前指针已验证，但正式转派动作留 BIZ-1B。
7. **等待接手状态**：RouteStatus/OpenAPI 增加 `TRANSFER_PENDING`；现阶段不开放生成该状态的动作。车辆占用及司机范围查询已识别该状态，避免被旧状态枚举静默忽略。
8. **作废/恢复**：仅 COMPLETED 可作废，原因必填；VOIDED 恢复固定为 COMPLETED。未完成线路及 TRANSFER_PENDING 拒绝作废。保留状态历史并写审计。作废不删除费用事实，也不在 1A 提前实现 BIZ-1C 经营聚合。旧 V1 “运输中作废、能源费用立即待归属”的测试断言已按冻结 V2 规则替换。
9. **历史回填**：专用 V7 夹具有 3 条线路（2 完成、1 已发布），V8 回填 3 条首参与、3 个当前指针；迁移前后的已知工资合计均为 1.00。只使用 assigned/departure 司机及可靠发车车快照；不从当前绑定推断历史外协车牌或转派。所有回填参与标记 `historical_data_complete=0`。
10. **旧 route_task 兼容与权威**：参与历史及 current 指针为 V2 权威；`assigned_driver_id`、`departure_driver_id`、`departure_vehicle_id`、`driver_salary` 暂留 V1 API/报表兼容。发布、发车、卸货和工资投影在服务事务内同步。后续 BIZ-1B/1C 改为从参与汇总后，才可规划退场旧字段；不得在 1A 删除。
11. **Driver/User**：普通用户管理创建/编辑拒绝分配 DRIVER。`DriverAccountService` 在事务中锁司机、查手机号、复用合法 DRIVER 用户或安全哈希创建新用户，再原子写入 `driver.user_id`；重复调用不新增孤儿用户。初始密码交互尚未冻结，因此底座未开放新的管理 API/UI。
12. **driverType 保护**：切换前检查当前绑定、线路/参与/工资历史；有事实则拒绝，既有历史不改写。已关联登录用户的手机号不能经司机编辑入口变更。
13. **外协绑定隔离**：绑定候选只返回 INTERNAL；bind API 对 OUTSOURCED 强拒绝；现有 INTERNAL 绑定正常。历史测试夹具中的 1 条外协脏绑定只读记录，未自动清除。
14. **权限**：新增 `route:transfer`、`route:salary:adjust`，写入 PermissionCode、V8、database.sql 和 OpenAPI，默认授予 ADMIN 且可按角色配置。1A 尚无实际转派或独立工资调整 API。
15. **Audit 底座**：`AuditWriteService` 复用 audit_log，接入角色权限变更、线路作废/恢复和允许的 driverType 改变；记录对象、动作、操作人、前后值、原因和 request ID。状态专项历史继续存在。
16. **Audit 事务与敏感信息**：审计写入与高风险业务操作处于同一事务，审计失败使操作回滚。JSON 使用稳定键序列化；敏感键及原因内的密码、Token、Secret 等片段脱敏。调用方只传最小业务摘要，不传请求体或凭据。
17. **历史资料不完整**：完成线路的历史司机类型、外协车牌及不可可靠推断的事实保持 NULL；`historical_data_complete=0` 明示缺口。没有补造旧磅单、车牌、转派或工资。
18. **Repository/查询**：支持按线路顺序读取、按司机/公司车读取、精确 current 定位及首参与创建/生命周期/工资历史写入。司机线路范围改按参与存在性查询，分页 total 不因 N 个参与重复；定向及全量回归通过。
19. **契约同步**：`database/database.sql` 和 `docs/openapi.yaml` 仅同步 1A 已实现的结构、状态、权限、绑定候选和作废规则；没有把 1B～1D 提案标为已上线。
20. **修改文件**：后端 master/route/system/security 的服务、Repository、Controller；V8；`database.sql`、OpenAPI；H2 测试 schema；新增参与/账号/审计测试与旧夹具适配；本报告。未修改管理端或小程序业务代码。
21. **迁移测试**：真实 MySQL 的 V7→V8 与空库 V1→V8 均成功；Flyway 记录到 8。专库回填数量、金额、外协留空与约束均核验。
22. **Driver/User 测试**：人工分配 DRIVER 被拒、缺初始密码不创建用户、合法创建/重复调用保持一条用户及关联；后端定向测试通过。
23. **Binding 测试**：INTERNAL 候选和绑定成功，OUTSOURCED 不在候选且 bind 拒绝；旧绑定回归通过。
24. **Route/Participant 测试**：发布→首参与→发车→卸货→完成后作废/恢复、三段参与结构、current 指针、工资 0/负数及既有并发用例通过。完整转派业务仍属 BIZ-1B。
25. **Permission/Audit 测试**：新权限可配置、作废/恢复审计写入、敏感字段脱敏测试通过；本阶段未新增转派授权入口。
26. **真实 MySQL V7→V8**：MySQL 8.0.31，专库 `fleet_ops_biz1a_v7`；3 路线→3 参与→3 当前指针，回填标记 3，外协车牌 0，孤儿指针 0；Flyway V8 成功。
27. **空库 V1→V8**：专库 `fleet_ops_biz1a_empty`，8 条成功迁移、0 路线/参与、新权限 2 项。事务内 DML 验证 sequence 唯一、同线路活动参与唯一、负工资 CHECK 分别被 MySQL 拒绝；测试事务回滚后 0 测试参与。
28. **旧 Settlement**：V8 不写 settlement 表；V7 专库迁移后原 `settlement_detail` 仍有 1 条。既有月结快照未由迁移重新生成；本轮未进行跨迁移逐字段/hash 对比。此项为 **NON-BLOCKING**，纳入 BIZ-1R 最终验收，不能写成快照内容已逐字段验证通过。
29. **`mvn test`**：最终 537 tests、0 failures、0 errors、2 skipped。两项 skipped 为既有条件集成测试，如实保留。第一次全量回归发现旧直接 SQL 夹具缺参与和 V1 作废断言，修复测试事实后再次全量通过。
30. **`mvn -DskipTests package`**：通过，生成可执行 JAR。第一次尝试因本机 8080 开发服务占用同名 JAR 而无法重命名；确认进程后短暂停止、打包成功并恢复服务，`/actuator/health=UP`。恢复后的本机开发服务按 Flyway 将其原有本地测试库 `fleet_ops` 也升至 V8；专库验证仍独立在 `fleet_ops_biz1a_v7` 和 `fleet_ops_biz1a_empty` 完成，未连接生产库。
31. **双端受影响测试**：`npm --prefix admin-web test`、`npm --prefix admin-web run build`、`node mini-program/test/run-tests.js` 均通过；小程序为 85 项。没有将前端静态测试冒充真实 UI 验收，本阶段不要求新 UI。
32. **Git diff**：实施验收时 `git diff --check` 返回 0；仅有 Windows CRLF 转换警告，无空白错误。验收后的收尾提交另做暂存区及差异检查，不重复全量测试。
33. **Secret/工作区**：本机 `application.yml`、微信 `project.config.json` 和忽略的 `.runtime/` 保留原样且不在 1A 提交范围；新 1A 代码及文档未写入真实 OCR Secret。数据库凭据只在本机进程环境中用于专库验证。冻结补充涉及五份正式 Word，原正文、表格和节数通过结构比对保持不变；本机缺少 `soffice.exe`，未完成逐页渲染，保留为文档视觉核验限制。
34. **BLOCKER**：未发现阻止 1A 数据模型/安全边界落地的阻塞项。当前旧线路/费用/报表的 V1 兼容字段仍存在；V2 多参与经营口径须在后续阶段实现，不能将 1A 视为全业务完成。
35. **决策已冻结**：BIZ-1A 验收后明确：后台从“基础资料 → 司机”创建新手机号时由创建人填写初始密码并创建安全哈希 DRIVER User，已有合法 DRIVER User 只关联且原密码不变；密码不得明文返回、记 Audit/日志，重置走正式流程。外协首发布需真实外协车牌和必填初始工资（允许 `0.00`），不要求 `vehicleId`、不查询/创建/绑定公司车，服务端依据 `Driver.driverType` 分支校验。当前 1A 仅有安全服务及数据底座，正式页面/API 入口尚未接入，不得误报已上线。
36. **1A 关闭判断**：业务方已验收通过并同意关闭 BIZ-1A；代码与必要基线文档按本次收尾提交，未将 1B 功能提前实现。
37. **进入 1B 判断**：参与数据、当前指针、状态、权限和审计底座已具备；账号入口与外协首发布按上述冻结规则在后续阶段实现。BIZ-1C/1D 仍保持独立边界，BIZ-1R 需补旧 Snapshot 逐字段/hash 比较。
