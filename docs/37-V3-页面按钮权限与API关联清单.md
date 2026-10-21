# V3.0 页面、按钮与 API 功能关联清单

本清单由后端 `FunctionRegistry` 与 `docs/openapi.yaml` 生成，运行时以后端注册表和启动期编译的 Controller 关联为准。页面允许其限定范围内的读取；按钮写入还要求所属页面授权。工作台“待办概览”是审批与费用归属权限派生入口，不独立授权。

注册功能：41 个页面（含 5 个小程序页面）、55 个按钮。角色权限树在八个管理端业务域之后单列“小程序”大类；这只是目录归属调整，功能编码和既有授权不变。

## 页面与按钮矩阵

| 业务域 / 分组 | 页面 | 页面编码 | 数据范围 | 所属按钮 |
| --- | --- | --- | --- | --- |
| 工作台 | 经营概览 | `PAGE_DASHBOARD` | 经营汇总 | 无写入按钮 |
| 运输管理 | 货物管理 | `PAGE_PRODUCTS` | 货物资料 | 维护货物（`BUTTON_PRODUCTS_MANAGE`） |
| 运输管理 | 线路模板 | `PAGE_TEMPLATES` | 线路模板 | 维护线路模板（`BUTTON_TEMPLATES_MANAGE`） |
| 运输管理 / 调度工作台 | 批量调度 | `PAGE_DISPATCH` | 精简司机候选及调度草稿 | 预览并发布任务（`BUTTON_DISPATCH_PUBLISH`） |
| 运输管理 / 调度工作台 | 任务导入 | `PAGE_TASK_IMPORTS` | 任务导入及原始文件 | 提交任务导入（`BUTTON_TASK_IMPORTS_SUBMIT`） |
| 运输管理 / 调度工作台 | 运输任务 | `PAGE_ROUTES` | 全部运输任务及详情 | 取消未发车任务（`BUTTON_ROUTES_CANCEL`）；转派任务（`BUTTON_ROUTES_TRANSFER`）；确认发车（`BUTTON_ROUTES_DEPART`）；确认卸货（`BUTTON_ROUTES_UNLOAD`）；调整任务重量（`BUTTON_ROUTES_WEIGHT_ADJUST`）；调整任务金额（`BUTTON_ROUTES_AMOUNT_ADJUST`） |
| 运输管理 / 车辆出勤 | 自有车辆 | `PAGE_DISPATCH_INTERNAL` | 自有车辆月度台账 | 导出自有车辆月账（`BUTTON_DISPATCH_INTERNAL_EXPORT`） |
| 运输管理 / 车辆出勤 | 外协车辆 | `PAGE_DISPATCH_OUTSOURCED` | 外协车辆月度台账 | 导出外协车辆月账（`BUTTON_DISPATCH_OUTSOURCED_EXPORT`） |
| 车辆与人员 | 司机管理 | `PAGE_DRIVERS` | 司机完整资料及证件；初始十列模板下载和只读预览 | 新增司机（`BUTTON_DRIVERS_ADD`）；批量导入（`BUTTON_DRIVERS_IMPORT`）；编辑及启停司机（`BUTTON_DRIVERS_EDIT`）；重置司机密码（`BUTTON_DRIVERS_RESET_PASSWORD`） |
| 车辆与人员 | 车辆管理 | `PAGE_VEHICLES` | 车辆完整资料 | 新增车辆（`BUTTON_VEHICLES_ADD`）；编辑车辆（`BUTTON_VEHICLES_EDIT`） |
| 车辆与人员 | 车挂管理 | `PAGE_TRAILERS` | 车挂资料 | 新增车挂（`BUTTON_TRAILERS_ADD`）；编辑车挂（`BUTTON_TRAILERS_EDIT`） |
| 车辆与人员 | 绑定关系 | `PAGE_BINDING` | 车辆、司机及车挂绑定历史 | 绑定司机与车辆（`BUTTON_BINDING_BIND_DRIVER`）；解绑司机与车辆（`BUTTON_BINDING_UNBIND_DRIVER`）；绑定车挂与车辆（`BUTTON_BINDING_BIND_TRAILER`）；解绑车挂与车辆（`BUTTON_BINDING_UNBIND_TRAILER`） |
| 车辆与人员 / 轮胎管理 | 轮胎库存 | `PAGE_TIRES` | 轮胎资料及历史 | 维护轮胎资料（`BUTTON_TIRES_MANAGE`） |
| 车辆与人员 / 轮胎管理 | 轮胎入库 | `PAGE_TIRE_IMPORTS` | 轮胎导入及历史 | 提交轮胎入库（`BUTTON_TIRE_IMPORTS_SUBMIT`） |
| 费用管理 | 费用申请 | `PAGE_EXPENSE_APPLY` | 正式费用类别与本人申请 | 提交费用申请（`BUTTON_EXPENSE_APPLY_SUBMIT`） |
| 费用管理 | 费用台账 | `PAGE_EXPENSE_LEDGER` | 正式费用明细 | 冲销费用（`BUTTON_EXPENSE_LEDGER_REVERSE`）；导出费用台账（`BUTTON_EXPENSE_LEDGER_EXPORT`） |
| 费用管理 | 待归属费用 | `PAGE_EXPENSE_UNASSIGNED` | 待归属费用明细 | 调整费用归属（`BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE`） |
| 费用管理 | 充电订单导入 | `PAGE_CHARGING_IMPORTS` | 充电订单导入及原始文件 | 确认充电订单入账（`BUTTON_CHARGING_IMPORTS_IMPORT`） |
| 费用管理 | 充电公司模板配置 | `PAGE_CHARGING_COMPANIES` | 充电公司映射 | 维护充电公司映射（`BUTTON_CHARGING_COMPANIES_MANAGE`） |
| 审批中心 | 我的待办 | `PAGE_APPROVALS_TODO` | 本人符合冻结节点资格的审批待办 | 审批通过（`BUTTON_APPROVALS_TODO_APPROVE`）；打回审批（`BUTTON_APPROVALS_TODO_RETURN`） |
| 审批中心 | 我的申请 | `PAGE_APPROVALS_MINE` | 本人申请 | 撤回申请（`BUTTON_APPROVALS_MINE_WITHDRAW`）；重新提交申请（`BUTTON_APPROVALS_MINE_RESUBMIT`） |
| 审批中心 | 审批记录 | `PAGE_APPROVALS_HISTORY` | 有权查看的审批历史 | 无写入按钮 |
| 审批中心 | 流程与模板配置 | `PAGE_APPROVALS_FLOWS` | 审批模板与冻结版本 | 维护审批流程（`BUTTON_APPROVALS_FLOWS_MANAGE`） |
| 工资与考勤 | 司机出勤 | `PAGE_ATTENDANCE` | 自有司机出勤 | 导出司机出勤（`BUTTON_ATTENDANCE_EXPORT`） |
| 工资与考勤 | 工资台账 | `PAGE_SALARIES` | 参与人工资资料 | 核对参与工资（`BUTTON_SALARIES_ADJUST`）；导出工资台账（`BUTTON_SALARIES_EXPORT`） |
| 工资与考勤 | 转派工资核对 | `PAGE_SALARY_TRANSFERS` | 转派参与工资 | 核对转派工资（`BUTTON_SALARY_TRANSFERS_ADJUST`） |
| 经营与结算 / 经营 | 公司经营 | `PAGE_FINANCE_COMPANY` | 公司经营汇总 | 无写入按钮 |
| 经营与结算 / 经营 | 线路模板经营 | `PAGE_FINANCE_TEMPLATES` | 模板经营汇总 | 无写入按钮 |
| 经营与结算 / 经营 | 任务经营 | `PAGE_FINANCE_TASKS` | 任务经营汇总 | 无写入按钮 |
| 经营与结算 / 支出 | 车辆支出 | `PAGE_FINANCE_VEHICLES` | 车辆费用明细 | 无写入按钮 |
| 经营与结算 / 支出 | 司机支出 | `PAGE_FINANCE_DRIVERS` | 司机费用明细 | 无写入按钮 |
| 经营与结算 / 支出 | 能源费用 | `PAGE_FINANCE_ENERGY` | 能源费用明细 | 无写入按钮 |
| 经营与结算 | 月度结算 | `PAGE_SETTLEMENTS` | 冻结结算版本 | 生成结算版本（`BUTTON_SETTLEMENTS_GENERATE`） |
| 系统设置 | 用户管理 | `PAGE_USERS` | 用户及角色资料 | 新增用户（`BUTTON_USERS_ADD`）；编辑及启停用户（`BUTTON_USERS_EDIT`）；重置用户密码（`BUTTON_USERS_RESET_PASSWORD`） |
| 系统设置 | 角色权限 | `PAGE_ROLES` | 角色与功能授权 | 新增角色（`BUTTON_ROLES_ADD`）；编辑及启停角色（`BUTTON_ROLES_EDIT`）；配置角色权限（`BUTTON_ROLES_GRANT`） |
| 系统设置 | 操作日志 | `PAGE_AUDIT` | 审计日志 | 无写入按钮 |
| 小程序 | 司机我的线路 | `PAGE_MINI_ROUTES` | 本人当前参与任务，不含经营和工资字段 | 司机确认发车（`BUTTON_MINI_ROUTES_DEPART`）；司机确认卸货（`BUTTON_MINI_ROUTES_UNLOAD`） |
| 小程序 | 司机工资单 | `PAGE_MINI_SALARY` | 本人薪资 | 无写入按钮 |
| 小程序 | 费用申请 | `PAGE_MINI_EXPENSE_APPLY` | 本人费用申请 | 发起费用申请（`BUTTON_MINI_EXPENSE_APPLY_SUBMIT`） |
| 小程序 | 小程序审批中心 | `PAGE_MINI_APPROVALS` | 本人申请及符合节点资格的待办 | 小程序审批通过（`BUTTON_MINI_APPROVALS_APPROVE`）；小程序打回审批（`BUTTON_MINI_APPROVALS_RETURN`）；小程序撤回申请（`BUTTON_MINI_APPROVALS_WITHDRAW`） |
| 小程序 | 司机换胎申请 | `PAGE_MINI_TIRE_REQUEST` | 本人换胎申请 | 司机发起换胎申请（`BUTTON_MINI_TIRE_REQUEST_SUBMIT`） |

## API 功能关联

同一 API 的多个功能编码为 OR 关系；按钮编码还须同时拥有其页面编码。标为“本人/业务范围”的接口仍由 Service 校验身份、本人/参与范围、附件 Purpose 或审批节点资格。未注册的受保护接口默认拒绝。

| 方法 | 路径 | 功能编码 / 边界 |
| --- | --- | --- |
| GET | `/api/v1/tires` | `PAGE_TIRES` |
| GET | `/api/v1/tires/{id}` | `PAGE_TIRES` |
| PATCH | `/api/v1/tires/{id}` | `BUTTON_TIRES_MANAGE` |
| GET | `/api/v1/tires/{id}/history` | `PAGE_TIRES` |
| GET | `/api/v1/tire-imports/template` | `PAGE_TIRE_IMPORTS` |
| POST | `/api/v1/tire-imports/preview` | `BUTTON_TIRE_IMPORTS_SUBMIT` |
| POST | `/api/v1/tire-imports/{id}/confirm` | `BUTTON_TIRE_IMPORTS_SUBMIT` |
| GET | `/api/v1/tire-imports` | `PAGE_TIRE_IMPORTS` |
| GET | `/api/v1/tire-imports/{id}` | `PAGE_TIRE_IMPORTS` |
| POST | `/api/v1/tire-requests/ocr` | `BUTTON_MINI_TIRE_REQUEST_SUBMIT` |
| POST | `/api/v1/tire-requests/ocr/{id}/confirm` | `BUTTON_MINI_TIRE_REQUEST_SUBMIT` |
| GET | `/api/v1/tires/request-target` | `PAGE_MINI_TIRE_REQUEST` |
| POST | `/api/v1/tire-requests` | `BUTTON_MINI_TIRE_REQUEST_SUBMIT` |
| GET | `/api/v1/tire-requests/{id}` | `PAGE_MINI_TIRE_REQUEST` OR `PAGE_APPROVALS_TODO` OR `PAGE_APPROVALS_HISTORY` |
| POST | `/api/v1/auth/login` | 公开登录入口（仍执行账号、密码和状态校验） |
| GET | `/api/v1/system/ping` | 本人/业务范围 |
| POST | `/api/v1/auth/logout` | 本人/业务范围 |
| GET | `/api/v1/me` | 本人/业务范围 |
| PUT | `/api/v1/auth/password` | 本人/业务范围 |
| POST | `/api/v1/users/{id}/reset-password` | `BUTTON_USERS_RESET_PASSWORD` OR `BUTTON_DRIVERS_RESET_PASSWORD` |
| PUT | `/api/v1/users/{id}/status` | `BUTTON_USERS_EDIT` |
| GET | `/api/v1/users` | `PAGE_USERS` |
| POST | `/api/v1/users` | `BUTTON_USERS_ADD` |
| GET | `/api/v1/users/{id}` | `PAGE_USERS` |
| PUT | `/api/v1/users/{id}` | `BUTTON_USERS_EDIT` |
| GET | `/api/v1/roles` | `PAGE_ROLES` |
| POST | `/api/v1/roles` | `BUTTON_ROLES_ADD` |
| GET | `/api/v1/roles/{id}` | `PAGE_ROLES` |
| PUT | `/api/v1/roles/{id}` | `BUTTON_ROLES_EDIT` |
| PUT | `/api/v1/roles/{id}/status` | `BUTTON_ROLES_EDIT` |
| PUT | `/api/v1/roles/{id}/permissions` | `BUTTON_ROLES_GRANT` |
| GET | `/api/v1/permissions` | `PAGE_ROLES` |
| GET | `/api/v1/audit-logs` | `PAGE_AUDIT` |
| GET | `/api/v1/products` | `PAGE_PRODUCTS` OR `PAGE_TEMPLATES` |
| POST | `/api/v1/products` | `BUTTON_PRODUCTS_MANAGE` |
| PUT | `/api/v1/products/{id}` | `BUTTON_PRODUCTS_MANAGE` |
| PUT | `/api/v1/products/{id}/status` | `BUTTON_PRODUCTS_MANAGE` |
| GET | `/api/v1/drivers` | `PAGE_DRIVERS` |
| POST | `/api/v1/drivers` | `BUTTON_DRIVERS_ADD` |
| GET | `/api/v1/driver-vehicle-imports/template` | `PAGE_DRIVERS` |
| POST | `/api/v1/driver-vehicle-imports/preview` | `PAGE_DRIVERS`（只读上传） |
| POST | `/api/v1/driver-vehicle-imports/confirm` | `BUTTON_DRIVERS_IMPORT` |
| GET | `/api/v1/drivers/binding-candidates` | `PAGE_BINDING` |
| GET | `/api/v1/bindings/vehicle-options` | `PAGE_BINDING` |
| GET | `/api/v1/bindings/trailer-options` | `PAGE_BINDING` |
| GET | `/api/v1/drivers/{id}` | `PAGE_DRIVERS` |
| PUT | `/api/v1/drivers/{id}` | `BUTTON_DRIVERS_EDIT` |
| POST | `/api/v1/drivers/{id}/bind-vehicle` | `BUTTON_BINDING_BIND_DRIVER` |
| POST | `/api/v1/drivers/{id}/unbind-vehicle` | `BUTTON_BINDING_UNBIND_DRIVER` |
| GET | `/api/v1/drivers/{id}/binding-history` | `PAGE_BINDING` |
| GET | `/api/v1/vehicles` | `PAGE_VEHICLES` |
| POST | `/api/v1/vehicles` | `BUTTON_VEHICLES_ADD` |
| GET | `/api/v1/vehicles/{id}` | `PAGE_VEHICLES` |
| PUT | `/api/v1/vehicles/{id}` | `BUTTON_VEHICLES_EDIT` |
| GET | `/api/v1/vehicles/{id}/binding-history` | `PAGE_BINDING` |
| GET | `/api/v1/vehicles/{id}/trailer-binding-history` | `PAGE_BINDING` |
| GET | `/api/v1/trailers` | `PAGE_TRAILERS` |
| POST | `/api/v1/trailers` | `BUTTON_TRAILERS_ADD` |
| PUT | `/api/v1/trailers/{id}` | `BUTTON_TRAILERS_EDIT` |
| POST | `/api/v1/trailers/{id}/bind-vehicle` | `BUTTON_BINDING_BIND_TRAILER` |
| POST | `/api/v1/trailers/{id}/unbind-vehicle` | `BUTTON_BINDING_UNBIND_TRAILER` |
| GET | `/api/v1/route-templates` | `PAGE_TEMPLATES` OR `PAGE_DISPATCH` |
| POST | `/api/v1/route-templates` | `BUTTON_TEMPLATES_MANAGE` |
| GET | `/api/v1/route-templates/{id}` | `PAGE_TEMPLATES` OR `PAGE_DISPATCH` |
| PATCH | `/api/v1/route-templates/{id}` | `BUTTON_TEMPLATES_MANAGE` |
| POST | `/api/v1/route-templates/{id}/enable` | `BUTTON_TEMPLATES_MANAGE` |
| POST | `/api/v1/route-templates/{id}/disable` | `BUTTON_TEMPLATES_MANAGE` |
| POST | `/api/v1/dispatch-batches/preview` | `BUTTON_DISPATCH_PUBLISH` |
| GET | `/api/v1/dispatch-batches/driver-options` | `PAGE_DISPATCH` |
| GET | `/api/v1/dispatch-batches/vehicle-options` | `PAGE_DISPATCH` |
| POST | `/api/v1/dispatch-batches/confirm` | `BUTTON_DISPATCH_PUBLISH` |
| GET | `/api/v1/dispatch-batches/{id}` | `PAGE_DISPATCH` |
| GET | `/api/v1/task-imports/template` | `PAGE_TASK_IMPORTS` |
| POST | `/api/v1/task-imports/preview` | `BUTTON_TASK_IMPORTS_SUBMIT` |
| POST | `/api/v1/task-imports/{id}/confirm` | `BUTTON_TASK_IMPORTS_SUBMIT` |
| GET | `/api/v1/task-imports` | `PAGE_TASK_IMPORTS` |
| GET | `/api/v1/task-imports/{id}` | `PAGE_TASK_IMPORTS` |
| GET | `/api/v1/task-imports/{id}/rows` | `PAGE_TASK_IMPORTS` |
| GET | `/api/v1/transport-tasks` | `PAGE_ROUTES` |
| GET | `/api/v1/transport-tasks/{id}` | `PAGE_ROUTES` |
| GET | `/api/v1/transport-tasks/{id}/participants` | `PAGE_ROUTES` OR `PAGE_SALARIES` OR `PAGE_SALARY_TRANSFERS` |
| GET | `/api/v1/transport-tasks/{id}/weight-versions` | `PAGE_ROUTES` |
| POST | `/api/v1/transport-tasks/{id}/transfer` | `BUTTON_ROUTES_TRANSFER` |
| POST | `/api/v1/transport-tasks/{id}/depart` | `BUTTON_ROUTES_DEPART` OR `BUTTON_MINI_ROUTES_DEPART` |
| POST | `/api/v1/transport-tasks/{id}/unload` | `BUTTON_ROUTES_UNLOAD` OR `BUTTON_MINI_ROUTES_UNLOAD` |
| POST | `/api/v1/transport-tasks/{id}/cancel` | `BUTTON_ROUTES_CANCEL` |
| POST | `/api/v1/transport-tasks/{id}/weight-adjustments` | `BUTTON_ROUTES_WEIGHT_ADJUST` |
| POST | `/api/v1/transport-tasks/{taskId}/amount-adjustments` | `BUTTON_ROUTES_AMOUNT_ADJUST` |
| GET | `/api/v1/transport-tasks/{taskId}/amount-history` | `PAGE_ROUTES` |
| POST | `/api/v1/transport-tasks/{id}/participant-salaries/confirm` | `BUTTON_SALARIES_ADJUST` OR `BUTTON_SALARY_TRANSFERS_ADJUST` |
| GET | `/api/v1/driver/transport-tasks` | `PAGE_MINI_ROUTES` |
| GET | `/api/v1/driver/transport-tasks/{id}` | `PAGE_MINI_ROUTES` |
| GET | `/api/v1/driver-salaries` | `PAGE_SALARIES` OR `PAGE_SALARY_TRANSFERS` |
| GET | `/api/v1/driver-salaries/summary` | `PAGE_SALARIES` |
| GET | `/api/v1/driver-salaries/filter-options` | `PAGE_SALARIES` |
| GET | `/api/v1/driver-salaries/drivers` | `PAGE_SALARIES` |
| GET | `/api/v1/driver-salaries/export` | `BUTTON_SALARIES_EXPORT` |
| GET | `/api/v1/attendance` | `PAGE_ATTENDANCE` |
| GET | `/api/v1/attendance/export` | `BUTTON_ATTENDANCE_EXPORT` |
| GET | `/api/v1/attendance/drivers` | `PAGE_ATTENDANCE` |
| GET | `/api/v1/dispatch-ledger/internal` | `PAGE_DISPATCH_INTERNAL` |
| GET | `/api/v1/dispatch-ledger/outsourced` | `PAGE_DISPATCH_OUTSOURCED` |
| GET | `/api/v1/dispatch-ledger/export` | `BUTTON_DISPATCH_INTERNAL_EXPORT` OR `BUTTON_DISPATCH_OUTSOURCED_EXPORT` |
| GET | `/api/v1/driver-salaries/{participantId}/history` | `PAGE_SALARIES` |
| GET | `/api/v1/salary-slips/mine` | `PAGE_MINI_SALARY` |
| GET | `/api/v1/notifications/unread-count` | 本人/业务范围 |
| GET | `/api/v1/notifications` | 本人/业务范围 |
| POST | `/api/v1/notifications/{id}/read` | 本人/业务范围 |
| POST | `/api/v1/notifications/read-all` | 本人/业务范围 |
| GET | `/api/v1/attachments` | 本人/业务范围 |
| POST | `/api/v1/attachments` | 本人/业务范围 |
| GET | `/api/v1/attachments/{id}` | 本人/业务范围 |
| DELETE | `/api/v1/attachments/{id}` | 本人/业务范围 |
| GET | `/api/v1/approval-templates` | `PAGE_APPROVALS_FLOWS` |
| POST | `/api/v1/approval-templates` | `BUTTON_APPROVALS_FLOWS_MANAGE` |
| GET | `/api/v1/approval-templates/available` | `PAGE_EXPENSE_APPLY` OR `PAGE_MINI_EXPENSE_APPLY` OR `PAGE_MINI_TIRE_REQUEST` |
| GET | `/api/v1/approval-templates/options` | `PAGE_APPROVALS_FLOWS` |
| POST | `/api/v1/approval-templates/{id}/versions` | `BUTTON_APPROVALS_FLOWS_MANAGE` |
| GET | `/api/v1/approval-flows/{id}` | `PAGE_APPROVALS_FLOWS` |
| POST | `/api/v1/approval-flows/{id}/publish` | `BUTTON_APPROVALS_FLOWS_MANAGE` |
| GET | `/api/v1/approvals` | 本人/业务范围 |
| POST | `/api/v1/approvals` | `BUTTON_EXPENSE_APPLY_SUBMIT` OR `BUTTON_MINI_EXPENSE_APPLY_SUBMIT` |
| GET | `/api/v1/approvals/{id}` | 本人/业务范围 |
| GET | `/api/v1/approvals/{id}/returnable-steps` | `BUTTON_APPROVALS_TODO_RETURN` OR `BUTTON_MINI_APPROVALS_RETURN` |
| POST | `/api/v1/approvals/{id}/approve` | `BUTTON_APPROVALS_TODO_APPROVE` OR `BUTTON_MINI_APPROVALS_APPROVE` |
| POST | `/api/v1/approvals/{id}/return-step` | `BUTTON_APPROVALS_TODO_RETURN` OR `BUTTON_MINI_APPROVALS_RETURN` |
| POST | `/api/v1/approvals/{id}/return-applicant` | `BUTTON_APPROVALS_TODO_RETURN` OR `BUTTON_MINI_APPROVALS_RETURN` |
| POST | `/api/v1/approvals/{id}/resubmit` | `BUTTON_APPROVALS_MINE_RESUBMIT` OR `BUTTON_MINI_EXPENSE_APPLY_SUBMIT` |
| POST | `/api/v1/approvals/{id}/withdraw` | `BUTTON_APPROVALS_MINE_WITHDRAW` OR `BUTTON_MINI_APPROVALS_WITHDRAW` |
| GET | `/api/v1/expense-applications/options` | `PAGE_EXPENSE_APPLY` OR `PAGE_MINI_EXPENSE_APPLY` OR `PAGE_APPROVALS_FLOWS` |
| GET | `/api/v1/expense-applications/task-candidates` | `PAGE_EXPENSE_APPLY` OR `PAGE_MINI_EXPENSE_APPLY` |
| GET | `/api/v1/expense-applications/daily-template` | `PAGE_EXPENSE_APPLY` OR `PAGE_MINI_EXPENSE_APPLY` |
| POST | `/api/v1/expense-applications/daily-preview` | `BUTTON_EXPENSE_APPLY_SUBMIT` OR `BUTTON_MINI_EXPENSE_APPLY_SUBMIT` |
| GET | `/api/v1/charging/companies` | `PAGE_CHARGING_COMPANIES` OR `PAGE_CHARGING_IMPORTS` |
| POST | `/api/v1/charging/companies` | `BUTTON_CHARGING_COMPANIES_MANAGE` |
| PUT | `/api/v1/charging/companies/{id}` | `BUTTON_CHARGING_COMPANIES_MANAGE` |
| GET | `/api/v1/charging/imports` | `PAGE_CHARGING_IMPORTS` |
| POST | `/api/v1/charging/imports` | `BUTTON_CHARGING_IMPORTS_IMPORT` |
| GET | `/api/v1/charging/imports/{id}` | `PAGE_CHARGING_IMPORTS` |
| GET | `/api/v1/charging/imports/{id}/original-file` | `PAGE_CHARGING_IMPORTS` |
| GET | `/api/v1/charging/source-orders/by-expense/{expenseId}` | `PAGE_CHARGING_IMPORTS` OR `PAGE_EXPENSE_LEDGER` |
| GET | `/api/v1/expense-ledger` | `PAGE_EXPENSE_LEDGER` OR `PAGE_FINANCE_VEHICLES` OR `PAGE_FINANCE_DRIVERS` OR `PAGE_FINANCE_ENERGY` |
| GET | `/api/v1/expense-ledger/filter-options` | `PAGE_EXPENSE_LEDGER` OR `PAGE_EXPENSE_UNASSIGNED` OR `PAGE_FINANCE_VEHICLES` OR `PAGE_FINANCE_DRIVERS` OR `PAGE_FINANCE_ENERGY` |
| GET | `/api/v1/expense-ledger/summary` | `PAGE_EXPENSE_LEDGER` OR `PAGE_FINANCE_VEHICLES` OR `PAGE_FINANCE_DRIVERS` OR `PAGE_FINANCE_ENERGY` |
| GET | `/api/v1/expense-ledger/pending-attribution` | `PAGE_EXPENSE_UNASSIGNED` |
| GET | `/api/v1/expense-ledger/pending-attribution/summary` | `PAGE_EXPENSE_UNASSIGNED` |
| GET | `/api/v1/expense-ledger/export` | `BUTTON_EXPENSE_LEDGER_EXPORT` |
| GET | `/api/v1/expense-ledger/{id}` | `PAGE_EXPENSE_LEDGER` OR `PAGE_EXPENSE_UNASSIGNED` OR `PAGE_FINANCE_VEHICLES` OR `PAGE_FINANCE_DRIVERS` OR `PAGE_FINANCE_ENERGY` |
| POST | `/api/v1/expense-ledger/{id}/attribution` | `BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE` |
| POST | `/api/v1/expense-ledger/{id}/reverse` | `BUTTON_EXPENSE_LEDGER_REVERSE` |
| GET | `/api/v1/finance/task-summaries` | `PAGE_FINANCE_TASKS` |
| GET | `/api/v1/finance/tasks` | `PAGE_FINANCE_TASKS` |
| GET | `/api/v1/finance/tasks/{id}` | `PAGE_FINANCE_TASKS` |
| GET | `/api/v1/transport-tasks/{id}/expenses` | `PAGE_FINANCE_TASKS` |
| GET | `/api/v1/finance/templates` | `PAGE_FINANCE_TEMPLATES` |
| GET | `/api/v1/finance/company` | `PAGE_FINANCE_COMPANY` |
| GET | `/api/v1/finance/dashboard` | `PAGE_DASHBOARD` |
| GET | `/api/v1/settlements/{month}/versions` | `PAGE_SETTLEMENTS` |
| POST | `/api/v1/settlements/{month}/versions` | `BUTTON_SETTLEMENTS_GENERATE` |
| GET | `/api/v1/settlements/versions/{id}` | `PAGE_SETTLEMENTS` |
| GET | `/api/v1/settlements/versions/{id}/details` | `PAGE_SETTLEMENTS` |
| GET | `/api/v1/settlements/versions/{id}/diff` | `PAGE_SETTLEMENTS` |

OpenAPI 已列出 159 个方法/路径关联；测试在应用启动时检查 Controller 实际受保护路由的功能注册，运行时不扫描注解。

## 旧权限清理

原 `PermissionCode` 接口编码类及逐接口权限种子已经从运行代码和三份单一 baseline 移除。旧编码不能作为角色授权保存；权限表只保留注册表中的 `PAGE` / `BUTTON` 功能定义。启动同步不会给普通业务角色自动添加新功能。

缓存策略：应用启动时编译 API 关联；令牌会话持有权限集合，请求中做内存集合判断。角色授权修改会撤销该角色的会话，用户或司机停用会撤销相关会话；失效后的新登录重新读取当前授权。角色启用及司机禁用状态仍逐请求做轻量校验，避免已停用主体继续使用旧令牌。无独立 Redis。
