# 管理端 UI/UX 现状全量审计

> 审计日期：2026-09-26。范围：`admin-web` 当前工作区的 12 个一级业务页、登录页及 11 个主要 Tab，共 **24 个页面/Tab 单元**。本文件是后续设计输入，不代表重构已经实施。

## 0. 证据、边界与判断口径

- 源码检查：`src/router/routes.js`、`App.vue`、`layouts/AppLayout.vue`、全部 `views`、`components`、`style.css`、`api/client.js`、`api/modules.js`、`stores/auth.js`、`directives/permission.js`、`utils/permissions.js` 和显示文案工具。字段、操作、权限、状态逻辑均以源码为准。
- 视觉证据：用户提供的首页、基础资料（客户产品/绑定）、线路、导入、费用、审批、审批模板、轮胎、系统用户等实际页面截图。浏览器 `http://127.0.0.1:5175/` 可打开登录页；使用现有本地管理员尝试登录时返回“请求失败”，因此本轮**未能在当前服务状态下逐一现场打开登录后的 ADMIN 页面/Tab**。下文对这些页面的视觉评价来自已有真实截图与当前源码，交互评价来自代码；不能作为新的浏览器全流程验收记录。未反复重试登录或临时改造运行环境。
- 当前工作区已存在上一任务中断时留下的未提交中文显示改动，本轮保留，不回滚、不改业务代码；审计基于其当前状态。`SystemView.vue` 使用 `displayValue` 但未导入，是当前源码可直接确认的运行风险；需在后续改造前单独复现和修复。
- 优先级：**P0**＝影响正常使用或极易误操作；**P1**＝明显影响易用性；**P2**＝视觉和体验优化。文末问题矩阵每个页面/Tab 记一项“主问题”，数量按矩阵统计；章节中提及的次要问题不重复计数。
- 路由是 `routerStore.current` + `localStorage('admin.currentRoute')` 的前端页面键，**没有独立 URL path**。下文“路由”写页面键及所属 Tab；浏览器地址本身不会随页面改变。

## 1. 全局信息架构与基础交互

### 导航、Header 与权限

`routes.js` 声明 12 个一级入口：`dashboard`、`master`、`routes`、`imports`、`expenses`、`approvals`、`approvalFlows`、`tires`、`salaries`、`reports`、`settlements`、`system`。`visibleRoutes` 根据用户权限筛选；菜单平铺在固定 240px 深色 Sidebar 中，没有业务分组、折叠或二级定位。选中态和顶部页名表达当前位置；Tab 不进入 URL，刷新后只恢复一级页，不恢复详情/Tab/筛选。浏览器前进后退与可分享深链接没有对应页面状态。

`AppLayout.vue` 的 Header 在**每个页面**重复显示页名、手机号、“同步权限”和“退出”。同步权限属于低频动作却始终位于主视线；用户资料、账户动作没有统一菜单。建议未来 `AppShell + Sidebar + Topbar + PageHeader`：稳定分组导航，Header 只保留全局上下文和用户菜单，低频账户动作收起；页内主操作置于 PageHeader。需要时才显示 Breadcrumb，不给每一页增加重复层级。

`auth.js` 将 `accessToken` 和用户存于 localStorage，登录后请求 `/me`；`api/client.js` 统一加 Bearer，401 清理会话。路由以 `canAny` 判断，按钮部分用 `v-permission`、部分用 `can()`，因此某些页面可见但首次加载执行了更高权限的 API。`v-permission` 通过移除 DOM 实施控制，权限同步后不保证已移除按钮重新出现。UI 权限只是呈现控制，服务端授权仍是最终边界。后续应统一权限感知组件和不可用说明，避免“入口可见、内容报错”。

### 视觉、密度和响应式现状

`style.css` 使用浅灰背景、白色面板、深色侧栏与青绿主按钮，方向可保留；但几乎所有区块都使用完整边框卡片，列表、表单、详情、历史常驻纵向堆叠。`.workspace` 仅有 22px 内边距、**没有内容最大宽度**；1920×1080（用户提供的宽屏截图亦可见）出现超长输入框、大面积空表和横向扫描距离。固定两列、四列指标布局仅在 `max-width:920px` 断成单列/两列，常见 1366×768 笔记本没有针对性收敛，侧栏扣除后复杂表格和双列表单容易横向滚动。小屏下 Sidebar 与主内容上下堆叠，却没有紧凑导航策略。

正文/表格密度不均：标题 24/17px，表格正文依继承字号、表头 13px，按钮最低 34px，卡片内距 16px。表格以 CSS Grid 等宽列渲染，并非语义化表格；金额/数量不系统性右对齐，列宽不按内容类型分配，长文本/操作列没有统一约束，也没有统一排序、分页或固定操作列。`DataTable` 在无数据时显示文字但仍保留宽表头；Loading 通常只表现为按钮 disabled 或文字，缺少统一骨架/局部状态；错误多在页顶用一行红字。状态色由各调用方传 `tone`，同一状态未统一映射。部分原始 ID/JSON 和技术枚举暴露给业务人员。`window.confirm` 被广泛用于删除、停用、生成等高影响操作，缺少影响范围与对象摘要。

### 2026 年目标体验边界

后续设计以调度、财务、管理人员长时间使用为基准：清晰、克制、中等偏高但有呼吸感的信息密度；1920×1080 与 1366×768 均可舒适操作。**先重整任务路径和信息层级，再统一视觉**；不能只换色、圆角、阴影。避免高饱和大面积背景、强渐变、玻璃拟态、霓虹和装饰动画；不退回密集小字 ERP。任何首用员工应能迅速判断“所在位置、页面用途、主操作、待办、数据状态和危险操作后果”。

## 2. 页面逐项审计

以下“无 Label”是指缺少**永久可见的文字标签**；`aria-label` 有助于辅助技术，但不能替代屏幕上可见的字段名。多数列表由 API 默认/固定取首批（多为 100 条），页面没有对应分页；这可能造成“看起来已列完”的误解，应在后续核对各接口分页总数和权限。

### 2.1 登录页（独立）

- **路由/文件/职责**：未登录时由 `App.vue` 显示 `views/LoginView.vue`；手机号、密码登录并进入权限菜单。
- **结构/字段/操作**：居中 420px 登录卡；永久可见“手机号”“密码”标签，登录按钮；无表格、Tab、筛选、分页、编辑或详情。提交期间按钮 disabled，失败在卡片显示错误。
- **权限/问题/建议**：公开页面；表单基础可用。本地浏览器现场登录返回“请求失败”，不能据此判断是设计还是当时 API 不可达。视觉渐变和较强卡片阴影偏重，与克制的工作台风格不一致；后续保留单页登录结构，统一品牌、错误恢复说明和视觉 Tokens。**P2**。

### 2.2 首页看板（`dashboard`，`views/DashboardView.vue`）

- **职责与结构**：8 张指标卡、本月待办状态、月度趋势表、实时线路利润表；无表单/Tab/分页。字段：趋势 `month/incomeAmount/expenseAmount/profitAmount`；利润 `routeNo/customerName/driverName/incomeAmount/routeExpense/driverSalary/profitAmount`。按钮仅“刷新”。
- **状态/权限**：需要 `report:dashboard`；并行读取 summary、profit、routes、expenses、approvals；页顶显示错误，加载只禁用刷新，空表通用“暂无数据”。
- **问题/模式**：本月收入、费用、利润、趟次与年度指标同一视觉权重；待审批/未发布线路/待确认费用只是不可点击 Badge，不能直达工作；金额未统一格式，零与未加载混淆。建议首页按“本月收入/支出/利润/趟次 → 可点击待办 → 趋势 → 异常/实时线路”重排，使用 `StatCard` 和紧凑明细，年度数据降低层级。**P1**。

### 2.3 基础资料·司机（`master / drivers`，`views/MasterDataView.vue`）

- **职责/结构**：司机创建表单常驻在司机列表上方。表单 `name/phone/driverType`，只有 aria-label，类型下拉为自有/外协；“新增司机”。列表 `id/name/phone/driverType/status`，无行操作、筛选、分页。
- **状态/权限**：Tab 始终显示，新增需 `driver:add`；一级入口由多种权限满足其一即可；挂载时一次加载司机/车辆/车挂/绑定/客户/产品，单个请求失败可影响整体。页顶错误，无明确局部 Loading。
- **问题/模式**：创建与浏览同时常驻，所有输入缺可见 Label；身份、状态列用技术/弱语义表达，缺有效查找。建议**司机列表 + 筛选 + 简单 FormDialog + DetailDrawer**，按能力隐藏或解释无权操作。**P1**。

### 2.4 基础资料·车辆（`master / vehicles`，同上）

- **职责/结构**：常驻新增 `plateNo/energyType/maxLoad`，按钮“新增车辆”；表格 `id/plateNo/energyType/maxLoad/status/actions`，行操作“绑定历史”，历史数据显示在同页下方。无筛选/分页。
- **状态/权限**：新增 `vehicle:add`；历史通过行按钮请求，错误仍在页顶；挂载加载如上。
- **问题/模式**：车辆 ID、能源类型和载重单位未清楚呈现，历史与列表上下分散；字段无可见 Label。建议**车辆列表 + FormDialog + DetailDrawer（含绑定历史 Tab）**，载重单位固定可见。**P1**。

### 2.5 基础资料·车挂（`master / trailers`，同上）

- **职责/结构**：新增 `plateNo/insuranceComplete`，按钮“新增车挂”；表格 `id/plateNo/insuranceComplete/status`。无详情、筛选、分页；车挂号输入无可见 Label，保险完整复选有文本。
- **状态/权限**：新增 `trailer:add`；数据依赖共用加载，无局部空/错状态。
- **问题/模式**：布尔保险状态、业务状态缺统一标签与解释；空时表格和新增常驻。建议**列表 + 小型 FormDialog**，需要展示保险和绑定时用详情 Drawer。**P1**。

### 2.6 基础资料·绑定（`master / binding`，同上）

- **职责/结构**：两个永久并列的行内表单：`driverId + vehicleId → 司机绑定/解绑`、`trailerId + vehicleId → 车挂绑定/解绑`；历史表 `id/driverId/vehicleId/trailerId/operationTime`。无实体选择器、筛选、分页、详情。
- **状态/权限**：四个操作分别用 `driver:bindVehicle`、`driver:unbindVehicle`、`trailer:bindVehicle`、`trailer:unbindVehicle` 控制按钮，两个 form 本身仍显示；页顶错误。
- **问题/模式**：用户需手填多个无可见 Label 的数字 ID，绑定与解绑紧邻，容易把司机/车辆/车挂输错；历史也只显示 ID。建议**以车辆或司机详情为上下文的绑定 Drawer/ConfirmDialog**，可搜索实体、明确当前绑定和冲突影响；历史用 Timeline。**P0**。

### 2.7 基础资料·客户产品（`master / customerProduct`，同上）

- **职责/结构**：客户与产品两张 50/50 卡片，每张将新增/编辑表单与列表永久叠放。两表单均为编码、名称、状态、备注（placeholder 与 aria-label，无可见 Label），按钮“新增/保存”“清空”；两表格 `id/code/name/status/remark/actions`，行按钮“编辑”“停用/启用”。无搜索/分页/独立详情。
- **状态/权限**：创建 `route:create`，编辑与启停 `route:edit`；停用使用原生确认；整体加载和页顶错误共用。
- **问题/模式**：在宽屏两块超长输入，窄屏重排后连续出现两组表单/表格；“清空”同时承担取消编辑的语义。建议**客户/产品子 Tab，各自列表 + 筛选 + FormDialog，状态变更 ConfirmDialog**。**P1**。

### 2.8 线路管理（`routes`，`views/RoutesView.vue`）

- **职责/结构**：顶部线路表 `routeNo/businessDate/assignedDriverId/status/loadStandardMet/actions`；下方 50/50 永久展示创建/编辑表单和详情/重量历史。表单 `businessDate/customerId/productId/direction/loadingPlace/unloadingPlace/taxUnitPrice/assignedDriverId`；详情以原始对象 `<pre>` 展示，附带卸货 `grossWeight/tareWeight`、纠错 `grossWeight/tareWeight/reason/attachmentIds`；重量表 `versionNo/grossWeight/tareWeight/netWeight/loadStandardMet/reason`。无筛选/分页。
- **操作/权限/状态**：刷新、详情、发布、发车、取消、作废、重新启用、删除；创建/编辑/卸货/纠错及状态动作按相应 `route:*` 权限；取消、作废、删除等原生确认。加载只局部禁用，错误页顶，空详情仍保留半页区域。
- **问题/模式**：客户/产品/司机用 ID 输入且均无可见 Label；编辑选中态与“新建”共享表单、缺明确退出编辑；危险动作与普通动作都作为同类行按钮；原始 JSON 不是业务详情。建议**线路列表 + FilterBar + 固定简洁操作列；复杂创建/编辑独立页面或大 Drawer；详情独立页分“概况/重量版本/操作历史” Tab**。删除/作废应明确对象和后果。**P0**。

### 2.9 导入中心（`imports`，`views/ImportCenterView.vue`）

- **职责/结构**：常驻上传/预览/提交表单，字段业务类型（线路/轮胎/费用/工资）、文件、附件 ID、模板 ID；任务预览表 `rowNo/previewStatus/finalStatus/businessType/businessId/finalErrorMessage`；历史表 `id/businessType/status/successCount/unpublishedCount/failureCount/actions`，导出失败。无筛选/分页。
- **操作/权限/状态**：`import:history` 进入；预览 `import:preview`、提交 `import:commit`、失败导出 `import:failure:export`；上传后填附件 ID；提交原生确认，忙时按钮禁用、页顶错误。
- **问题/模式**：用户面对内部附件 ID/模板 ID，步骤关系只靠按钮顺序；预览与历史常驻导致空屏过长。建议**Step Form：选类型/上传 → 校验预览 → 确认提交；历史另一个列表 Tab/区域**，保留失败行定位和下载。**P1**。

### 2.10 费用管理（`expenses`，`views/ExpensesView.vue`）

- **职责/结构**：费用表 `expenseNo/expenseType/vehicleId/attributionType/routeId/amount/status/actions`；下方常驻人工新增（类型、日期、车辆 ID、归属、线路 ID、金额）与归属确认（费用 ID、归属、线路 ID、原因）/冲销（费用 ID、原因）；底部归属历史表 `beforeAttributionType/beforeRouteId/afterAttributionType/afterRouteId/operatorId/reason` 和审批链原始 JSON。无筛选/分页。
- **操作/权限/状态**：刷新、归属历史、审批链、删除、保存、确认归属、冲销；分别使用 `expense:list/add/attribution:edit/reversal/delete`。删除与冲销有原生确认，修改归属没有统一影响说明；错误页顶。
- **问题/模式**：费用 ID/车辆 ID/线路 ID 全靠输入，无永久 Label；冲销与普通归属表单近邻，易对错费用操作；列表、新增、详情、历史、审批链始终铺开，金额无统一格式。建议**费用列表 + 业务筛选；新增 FormDialog/Drawer；详情 Drawer 分“归属/审批/历史”；冲销独立危险 ConfirmDialog**。**P0**。

### 2.11 审批中心（`approvals`，`views/ApprovalsView.vue`）

- **职责/结构**：可展开的 `ApprovalCreateForm`；列表 Scope Tab“我的申请/待我审批/已审批/全部历史”，表 `approvalNo/approvalType/status/applicantUserId/createdAt/actions`；自定义上一页/下一页；选中后同页继续追加 `ApprovalDetailPanel` 与审批动作表单。详情包含基本信息、业务摘要、V1/V2 等 Submission Snapshot/附件、任务与动作时间线；图片在页内浮层预览。创建费用审批支持模板选择、归属、线路、多明细、Excel 本地解析、申请图片。
- **操作/权限/状态**：发起、刷新、查看详情、通过、打回申请人/节点、重新提交等；入口 `approval:history:view/create/process/return`，Scope 和动作再按权限筛选。列表/详情/创建各有 Loading/错误，但均挤在同一页；动作以原生确认。
- **问题/模式**：列表→创建→详情→历史→操作的工作流和阅读方向混在一页，详情纵向很长；原始用户/车辆/业务 ID 降低理解，复杂费用表单需要阶段反馈。建议**审批列表（Scope Tab+筛选+分页）/独立详情页（摘要、版本、证据、时间线）/独立多步骤发起页**；轻量动作可在详情侧 ConfirmDialog。**P1**。

### 2.12 审批模板（`approvalFlows`，`views/ApprovalFlowsView.vue`、`components/ApprovalTemplateForm.vue`）

- **职责/结构**：模板类别筛选与列表，字段 `displayName/templateCode/category/activeVersion/currentStatus/actions`；同页下接“创建模板与草稿 V1”、选中模板的版本表、配置详情、维护/发布确认、下一版本配置。表单字段：类别、代码、流程名、申请依据、费用类型、录入方式、费用归属、发起角色、任意多个审批节点（名称、审批人、岗位 ID、允许打回）。节点表显示顺序/名称/审批人/岗位/是否允许打回。无分页。
- **操作/权限/状态**：`approval:flow:manage` 进入；刷新、新建、查看版本、查看配置、维护、生成下一版、发布，忙时禁用、错误/通知；维护/发布在同页嵌入确认区。角色/用户通过 API 载入；空角色有提示。
- **问题/模式**：创建、版本详情和维护都向下无限堆叠；`DRAFT/ACTIVE/EXPENSE/SINGLE/ROUTE` 等技术码与中文并排呈现，业务用户需理解内部词汇；“发布”高影响操作与配置混在长页。建议**模板列表 → 独立模板详情（版本 Tab/状态）→ 草稿 Step Form**；发布用含版本和影响说明的 ConfirmDialog，技术码仅管理员高级信息处显示。**P1**。

### 2.13 轮胎管理（`tires`，`views/TiresView.vue`）

- **职责/结构**：库存/历史表 `id/tireNo/status/vehicleId/driverId/installTime`；同页常驻换胎申请 `driverId/vehicleId/itemsJson`（默认 `[]` 的原始 JSON 文本框）、申请 ID 查询、OCR ID/确认胎号输入与原始详情 `<pre>`。无筛选/分页。
- **操作/权限/状态**：一级路由允许 `tire:list` **或** `tire:request`，但页面挂载无条件调用完整库存 `tireApi.list()`；申请 form 仅 `tire:request` 控制，OCR 查询/确认按钮没有独立 UI 权限门。页顶错误，`JSON.parse` 失败会成为操作错误。
- **问题/模式**：面向业务人员暴露原始 JSON 与多个裸 ID，无法安全地按轮胎选项构建申请；仅有 `tire:request` 的用户进页就请求 `tire:list` 可能报权限错误。应先确认此页的管理端目标用户；建议**库存列表 + 搜索/详情 Drawer；换胎申请独立 Step Form（选司机/车辆/轮胎/证据）；OCR 在证据步骤**，移除原始 JSON 输入。**P0**。

### 2.14 工资管理（`salaries`，`views/SalariesView.vue`）

- **职责/结构**：工资表 `routeNo/driverName/businessMonth/salaryAmount/sourceType/actions`；下方永久两列人工录入/调整 `routeId/driverId/salaryAmount/reason` 与历史表 `beforeAmount/afterAmount/operatorId/operationTime/reason`；底部“工资导入”说明指向导入中心。无筛选/分页。
- **操作/权限/状态**：列表入口 `salary:view` 或 `salary:manage`，保存 `salary:manage`，行按钮“历史”；新增与调整共用 `create`，错误页顶，无明确局部 Loading。
- **问题/模式**：线路/司机只靠数字 ID 输入、无可见 Label 和姓名/月份核对，易记错账；历史空卡长期占半屏，说明块不能直达导入。建议**工资列表 + 月份/司机筛选；按选中线路/司机的 FormDialog/Drawer 调整；历史置于详情 Tab**，导入使用明确跳转。**P0**。

### 2.15 经营报表·利润（`reports / profit`，`views/ReportsView.vue`）

- **职责/结构**：单表 `routeNo/customerName/productName/driverName/plateNo/actualWeight/incomeAmount/routeExpense/driverSalary/profitAmount`；无表单、筛选、分页、导出、详情操作。表头由字段 key 直接生成，显示英文 API 名。
- **状态/权限/问题/模式**：需 `report:profit`；Tab 由权限控制，默认固定 profit，若用户只有其他报表权限，初次加载可能空白。页顶错误、通用空状态，无显性 Loading。建议报表 Tab 按首个有权项初始化，**FilterBar + DataTable** 使用中文列名、数值单位/右对齐与总计，复杂钻取以后再定。**P1**。

### 2.16 经营报表·车辆支出（`reports / vehicle`，同上）

- **职责/结构**：`plateNo/penaltyAmount/carWashAmount/waterAmount/repairAmount/electricAmount/routeCount`；无筛选、分页或行操作。
- **状态/权限/问题/模式**：需 `report:vehicle`；点击 Tab 发请求、错误页顶。直接展示字段 key，费用项目难扫读、金额列等宽且未统一格式。建议**中文列头 + 期间/车辆筛选 + 金额右对齐 + 合计**的报表列表。**P1**。

### 2.17 经营报表·司机支出（`reports / driver`，同上）

- **职责/结构**：`driverName/routeCount/mileageKm/penaltyAmount/carWashAmount/waterAmount/repairAmount/driverSalary`；无筛选、分页、详情。
- **状态/权限/问题/模式**：需 `report:driver`；英文表头和长宽表格不利于快速比较司机。建议**可按司机/期间筛选的列表，中文字段、金额/里程单位与汇总**。**P1**。

### 2.18 经营报表·出勤（`reports / attendance`，同上）

- **职责/结构**：`businessMonth/driverName/businessDate/departed`；无日期筛选、分页、详情。
- **状态/权限/问题/模式**：需 `report:attendance`；布尔 `departed` 和英文 key 直接展示，业务含义不清。建议**月份/司机筛选 + 中文出勤状态 Tag + 紧凑表**。**P1**。

### 2.19 经营报表·电费/气费（`reports / energy`，同上）

- **职责/结构**：`plateNo/energyType/quantity/amount`；无筛选、分页、详情。
- **状态/权限/问题/模式**：需 `report:energy`；能源类型、数量单位、金额格式均未在列配置中明示，空表亦只有通用文案。建议**按能源类型/月份筛选的列表，中文类型、数量单位和金额列**。**P1**。

### 2.20 月度结算（`settlements`，`views/SettlementsView.vue`）

- **职责/结构**：月份选择、查询、生成新版本；版本表 `versionNo/totalIncome/routeExpense/dailyExpense/driverSalary/totalProfit/generatedBy/actions`；下方一直显示版本详情表 `factType/sourceId/incomeAmount/expenseAmount/profitAmount` 和差异表 `changeType/factType/sourceId/incomeDelta/expenseDelta/profitDelta/businessOperatorId/businessReason`。无分页。
- **操作/权限/状态**：查看需 `settlement:view`，生成按钮需 `settlement:generate`；生成用原生确认；默认月份为浏览器当前月，版本/详情/差异需人工点查询/行按钮；错误页顶。
- **问题/模式**：当前选中版本不突出，详情/差异在未选版本时形成两个空白大区；生成前没有汇总影响预览，事实类型/变更类型可能展示技术码。建议**月份 FilterBar + 版本列表 + 版本独立详情（事实/差异 Tab）**，生成新版本以 ConfirmDialog 展示月份与历史不变说明。**P1**。

### 2.21 系统管理·用户（`system / users`，`views/SystemView.vue`）

- **职责/结构**：同页两列常驻新增/编辑用户与重置密码；用户表 `id/phone/roleName/status/lastLogin/actions`。创建字段 `phone/password/roleId/status`，编辑复用；密码重置另需 `userId/password`；行操作编辑/停用/启用。无筛选/分页。
- **操作/权限/状态**：`user:manage`；角色选择来自已载入列表，原生确认停用/重置；挂载并发读取其他系统 Tab 的资源，页顶错误。
- **问题/模式**：重置密码与新增账号并列永久可见，用户 ID 需手填，容易误重置；字段靠 placeholder，密码输入 `type` 未明确指定 password，可能明文可见于屏幕；需后续核实安全交互。建议**用户列表 + FormDialog；从具体用户行启动独立重置密码 ConfirmDialog/表单**，显示账号摘要与后果。**P0**。

### 2.22 系统管理·角色（`system / roles`，同上；含 `rolePermissions` 子视图）

- **职责/结构**：常驻角色编码/名称/状态表单、角色表 `id/roleCode/roleName/systemFixed/status/actions`；编辑、停启、配置权限；配置权限切换到隐藏子视图，逐项显示复选框和保存，权限说明含技术码，67 项未按业务域搜索/分组。无分页。
- **操作/权限/状态**：`role:manage`；配置时读取角色详情，保存权限；停启原生确认。角色权限子视图模板调用 `displayValue('permissionType', …)`，但 `SystemView.vue` 当前**未 import `displayValue`**，当有权限项时会触发渲染异常（源码确定，尚未在本轮登录后的浏览器现场复现）。
- **问题/模式**：权限管理本是高风险操作，却使用长复选列表且缺变更差异/影响预览；当前缺失导入还可能使页面无法正常显示。建议**角色列表 + FormDialog；权限配置独立详情页/Drawer，按模块分组、搜索、变更确认**。**P0**。

### 2.23 系统管理·权限（`system / permissions`，同上）

- **职责/结构**：权限类型筛选/查询、按类型分组的表，字段 `permissionCode/permissionName/permissionType/description`；无编辑动作、分页。
- **操作/权限/状态**：`permission:manage`；查询触发 API；当前模板分组标题同样调用未导入的 `displayValue`，有权限数据时有渲染异常风险。权限码对管理员可作为辅助信息，但不能成为唯一易懂名称。
- **问题/模式**：这一权限清单是角色配置的依据，渲染异常会直接妨碍日常管理；列表缺搜索和清晰业务分组。建议**可搜索/按模块筛选的只读权限目录列表**；修复运行错误后再设计。**P0**。

### 2.24 系统管理·操作日志（`system / audits`，同上）

- **职责/结构**：筛选 `module/businessType/operatorId/startTime/endTime`；表 `id/module/businessType/operationType/operatorId/operationTime/reason/beforeJson/afterJson`。无行详情/分页，前后值 JSON 直接放表格单元格。
- **操作/权限/状态**：`audit:view`，按钮“查询”；筛选字段仅 placeholder/aria-label，无永久 Label；错误页顶。
- **问题/模式**：宽表容纳长 JSON，不利于检索和阅读；操作人靠 ID，时间范围不清楚；首批记录易误认为全集。建议**筛选列表 + 明确分页 + 右侧 DetailDrawer 显示前后快照 Diff/原始 JSON**。**P1**。

## 3. 全局细项审计与统一改造要求

| 项 | 当前事实 | 后续设计方向 |
|---|---|---|
| 页面宽度 | 工作区无 `max-width`；宽屏输入框/表格拉满 | 内容容器建议最大约 1440–1600px，详情与编辑表单更窄；长表可在自身滚动，不让整页横向漂移 |
| 字号/间距 | 24/17px 标题与各处继承字号；卡片和表格间距未形成统一等级 | 正文约 14px、表格 13–14px、页题 22–24px；基于 4px 间距尺度，确保笔记本可读 |
| 卡片/分割 | 所有区块普遍有边框大白框 | 页面内容以白/近白 Surface 为主，按任务用留白和细分割线，浮层才用轻阴影 |
| 表格 | Grid 等宽列、无统一数字对齐/分页/固定操作列 | 按列类型配宽；数字右对齐、货币与日期格式统一；分页/总数清晰、紧凑 EmptyState、固定简洁操作列 |
| 按钮与危险动作 | 很多普通/危险行操作均为同类描边按钮；原生 `window.confirm` | 明确主/次/文字/危险层级；低频操作进“更多”；确认时显示对象、后果与可逆性 |
| 状态 Tag | `StatusBadge` 接受调用方 tone，同一状态可能颜色不一致 | 状态字典统一中文、语义色和文案；原始码仅辅助技术信息 |
| 表单 | 大量 `aria-label`/placeholder，但无可见 Label；原始 ID 输入 | 每字段永久 Label，Placeholder 只示例；必填、错误、帮助文本统一，实体优先搜索选择器 |
| Dialog/Drawer | 仅图片预览有浮层；新增、编辑、详情多在页内堆叠 | 简单创建/编辑 Dialog；上下文较复杂 Drawer；审批、线路等多步骤独立页 |
| 空/载入/错误 | 通用“暂无数据”；按钮禁用或页顶红字，缺局部状态 | 区分初次加载/无结果/无权限/请求失败；局部 Skeleton、重试与上下文 EmptyState |
| 分页 | 审批页手写分页，其余多取首批，无总数与翻页 | 统一分页、总数、每页条数；不将 API 首 100 条暗示为全集 |
| 响应式 | 仅 920px 断点，侧栏在小屏直接堆叠 | 以 1920×1080、1366×768、1280×800 验证；中宽屏收窄卡片/列密度，小屏有可用导航与表格策略 |
| 浏览器可定位性 | 一级/Tab 只保存在内存/localStorage，URL 不更新 | 重要详情、审批版本、筛选状态有可复制的路由；至少支持刷新/前进后退回到同一工作上下文 |

### 统一 Design Tokens 提案（仅设计，不实现）

此处为后续共用的**起始值**，需在真实页面和对比度检查中定稿；不得由各页面自定色、间距或阴影。

| Token 类 | 建议值/语义 |
|---|---|
| Typography | 字体栈 `Inter, "Segoe UI", "Microsoft YaHei", system-ui, sans-serif`；正文 `14px/1.5`，辅助 `12–13px/1.45`，表格 `13–14px`，小节 `16–18px/1.35`，页题 `22–24px/1.25`；数字用 tabular numerals |
| Spacing | 4px 基准：`4/8/12/16/24/32/40`；字段间 12–16，区块间 24–32，页面左右留白随视口变化；使用统一密度档，不逐页任意压缩 |
| Radius | 输入/按钮 `6px`，卡片 `8px`，Drawer/Dialog `10px`，Tag 可用轻胶囊；避免所有元素都做大圆角 |
| Border | 1px 中性浅灰，仅输入、表格行和必要分区可见；焦点态有清楚 outline，不靠阴影替代 |
| Shadow | 基础卡片默认无或极轻；浮层/Drawer/Dialog 两级柔和阴影，避免悬浮大白框效果 |
| Surface | 页面 `#F5F7F7` 附近；内容 `#FFFFFF`；次级 `#FAFBFB`；边线约 `#E4E9E9`；正文深中性、辅助灰保证对比 |
| Primary | 沿用低饱和青绿方向，基准可从现有 `#147064` 微调；主操作实色、小面积使用；hover/focus/disabled 成套定义 |
| Success | 低饱和绿色文字+浅绿底，仅完成/有效等正向语义 |
| Warning | 琥珀文字+浅黄底，仅待处理/风险提醒，避免与危险混用 |
| Danger | 深红文字+浅红底，专用于删除/冲销/失效/重大失败，危险按钮与普通按钮明显区分 |
| Info | 克制蓝色文字+浅蓝底，用于已发布/处理中等信息，不代替主色 |
| Disabled | 中性灰文字/背景/边框，保持可读；不得仅用透明度 0.55 导致低对比，必要时说明禁用原因 |

金额统一含千分位、小数位/币种；重量、数量和里程显示单位；日期时间统一为业务本地时区格式。颜色不能作为状态唯一表达，始终提供中文文字。加载、空态和错误也必须共享 Token 与组件语义。

## A. 页面问题矩阵

一行对应一个审计单元及其最高优先级主问题；`P0/P1/P2` 数量按本表计数。

| 页面 | 信息架构问题 | 表单问题 | 表格问题 | 操作问题 | 视觉问题 | 建议重构模式 | 优先级 |
|---|---|---|---|---|---|---|---|
| 登录 | 单卡结构基本清楚 | Label 齐全 | — | 错误可恢复说明弱 | 渐变/阴影偏重 | 保留登录页 | P2 |
| 首页看板 | 核心指标与年度同权、待办不直达 | — | 趋势/实时表缺情境 | 仅刷新 | 八卡平铺 | StatCard+待办+趋势 | P1 |
| 基础·司机 | 新增常驻列表上方 | 无可见 Label | 无搜索/分页 | 缺详情 | 空表占宽 | 列表+FormDialog+Drawer | P1 |
| 基础·车辆 | 历史与列表分离 | 无可见 Label/单位 | 等宽列 | 查历史上下跳 | 宽输入 | 列表+FormDialog+Drawer | P1 |
| 基础·车挂 | 新增常驻 | 车挂号无 Label | 状态弱 | 缺详情 | 空卡 | 列表+FormDialog | P1 |
| 基础·绑定 | 两类绑定/解绑混在一起 | 多个裸 ID | 历史只显示 ID | 易绑错/解绑错 | 宽且无层级 | 实体详情+绑定 Drawer+Timeline | P0 |
| 基础·客户产品 | 两业务并列混合编辑 | placeholder 代 Label | 两表均无分页 | 清空/取消编辑不清 | 50/50 超宽 | 子 Tab+列表+FormDialog | P1 |
| 线路 | 列表/编辑/详情/重量同屏 | 裸 ID、无 Label | 无分页/列宽约束 | 删除/作废同级 | 纵横均过载 | 列表+独立详情+编辑 Drawer | P0 |
| 导入 | 上传/预览/提交不成步骤 | 内部附件/模板 ID | 历史无分页 | 提交后果不突出 | 空表过宽 | Step Form+历史列表 | P1 |
| 费用 | 新增/归属/冲销/历史同屏 | 裸 ID、无 Label | 金额/状态不统一 | 冲销易误选对象 | 大块平铺 | 列表+Drawer+ConfirmDialog | P0 |
| 审批中心 | 列表/发起/详情同屏 | 多明细需分步 | 部分原始 ID | 操作与证据距离远 | 长页面 | 列表+独立详情+Step Form | P1 |
| 审批模板 | 多版本/发布/建模纵向堆叠 | 技术码、长表单 | 版本上下跳 | 发布入口弱 | 过长重边框 | 列表+详情+Step Form | P1 |
| 轮胎 | 库存/申请/OCR 混同页 | 原始 JSON/裸 ID | 无筛选/分页 | 请求权限与库存加载不一致 | 空区巨大 | 库存列表+申请 Step Form | P0 |
| 工资 | 列表/录入/历史常驻 | 线路/司机裸 ID | 无筛选/分页 | 易记错账 | 半屏空历史 | 列表+调整 Dialog+历史 Drawer | P0 |
| 报表·利润 | 缺筛选/钻取 | — | 英文 key 表头 | 无导出/详情 | 宽表 | FilterBar+DataTable | P1 |
| 报表·车辆 | 缺期间维度 | — | 英文 key/金额无格式 | 无汇总 | 宽表 | FilterBar+DataTable | P1 |
| 报表·司机 | 缺人员/期间维度 | — | 英文 key/多金额 | 无详情 | 宽表 | FilterBar+DataTable | P1 |
| 报表·出勤 | 缺月份/司机筛选 | — | `departed` 布尔不直观 | 无详情 | 空表 | FilterBar+StatusTag | P1 |
| 报表·能源 | 缺能源/月份筛选 | — | 单位/类型不明 | 无详情 | 空表 | FilterBar+DataTable | P1 |
| 月结 | 版本/事实/差异常驻 | 月份无可见 Label | 空详情/差异无上下文 | 生成影响说明弱 | 三段堆叠 | 列表+版本详情 Tabs | P1 |
| 系统·用户 | 创建与重置并列 | 无 Label、密码重置手填 ID | 无筛选/分页 | 易误重置账号 | 双列常驻 | 列表+FormDialog+ConfirmDialog | P0 |
| 系统·角色 | 编辑/权限配置混合 | 67 项难查 | 无分页 | 配权限无变更摘要；渲染风险 | 长复选页 | 列表+角色详情/权限 Drawer | P0 |
| 系统·权限 | 分类与目录薄弱 | 筛选缺可见 Label | 无搜索/分页 | `displayValue` 未导入有渲染风险 | 长目录 | 只读权限目录+FilterBar | P0 |
| 系统·操作日志 | 记录详情被塞进列表 | 筛选无 Label | 原始 JSON/无分页 | 难定位前后变化 | 过宽表 | 列表+DetailDrawer | P1 |

**统计：24 个页面/Tab，P0 8、P1 15、P2 1。**

## B. 公共组件候选（本阶段不实现）

| 组件 | 来自当前重复问题 | 推荐职责 |
|---|---|---|
| `AppShell` / `Sidebar` / `Topbar` | 12 个平铺入口、每页重复账号按钮 | 分组导航、当前页、用户菜单与低频动作 |
| `PageHeader` | 页名在全局 Header，页内主操作散落 | 标题/说明/主操作/必要 Breadcrumb |
| `StatCard` | 首页 8 张同权指标卡 | 数值、期间、趋势和优先级 |
| `FilterBar` | 大量列表无查找、报表无期间 | 有标签的筛选、重置、查询和结果数 |
| `DataTable` | 等宽 Grid、无数字格式/分页 | 语义表格、列类型、排序、分页、行操作与响应式横滚 |
| `StatusTag` | 各页独立传 tone、技术枚举泄漏 | 统一状态文案/色/解释 |
| `EmptyState` / `LoadingState` / `ErrorState` | 空表和错误信息泛化 | 区分无数据、无结果、无权限、失败；局部重试 |
| `FormDialog` | 简单新增/编辑永久展开 | 有 Label/校验/保存反馈的短表单 |
| `DetailDrawer` | 详情/历史占用列表页 | 列表上下文中浏览摘要、历史和附件 |
| `ConfirmDialog` | 原生 confirm、危险动作无影响说明 | 显示对象/后果/危险层级/取消 |
| `SectionCard` / `StepForm` | 审批模板、导入、换胎表单很长 | 复杂业务分组和可回退步骤 |
| `Timeline` | 审批、绑定、重量、归属历史 | 版本与动作先后顺序 |
| `FilePreview` | 审批附件当前单点实现 | 安全预览/下载、加载/失败、焦点管理 |

组件共用 Design Tokens 和交互状态；按实际页面复用价值提取，避免为“组件库齐全”而引入空抽象。

## C. 页面重构分组建议（仅排期，不实施）

1. **批次一：全局底座与高风险操作**。导航/Header、Tokens、表单 Label/状态/空载错、系统用户重置、角色/权限渲染风险及权限配置确认。风险：权限可见性和现有日常管理入口；需保留原 API 与授权行为。
2. **批次二：基础资料与线路调度**。司机、车辆、车挂、绑定、客户产品、线路列表/详情。风险：绑定对象、线路状态动作及重量历史；先统一实体选择与危险操作反馈。
3. **批次三：费用、工资、导入与月结**。以财务准确性优先，重建筛选列表、录入/调整、版本详情和导入步骤。风险：金额、归属、冲销、工资与月结版本；UI 不改变计算和提交契约。
4. **批次四：审批、审批模板、轮胎**。审批版本/证据时间线、模板建模与发布、换胎申请步骤。风险：Submission 版本、附件证据、审批流状态和库存预留；分场景验收。
5. **批次五：首页、报表与全屏适配**。指标层级、可点击待办、五类报表字段/筛选、1920×1080 与笔记本适配。风险：报表字段解释与首页指标口径；对照原 API 核实。

每批完成后用真实业务场景检查“在哪、做什么、主操作、待办、当前状态、危险后果”，再进入下一批；不要只验收视觉截图。

## D. 截图索引与现场核对范围

| 证据 | 页面/用途 |
|---|---|
| 用户提供截图 1 | 基础资料·客户产品：50/50 常驻表单与空表 |
| 用户提供截图 2 | 系统管理·用户：新增与密码重置并列 |
| 用户提供截图 3 | 轮胎管理：库存、原始 JSON 申请和 OCR 同屏 |
| 用户提供截图 4 | 审批模板：长表单、技术码与多段配置 |
| 用户提供截图 5 | 审批中心：列表与空态比例 |
| 用户提供截图 6 | 费用管理：列表、创建、归属/冲销、历史同时出现 |
| 用户提供截图 7 | 导入中心：上传/预览/提交与空历史 |
| 用户提供截图 8 | 线路管理：列表、创建/编辑、详情/重量历史 |
| 用户提供截图 9 | 基础资料·绑定：裸 ID 与绑定/解绑按钮排列 |
| 用户提供截图 10 | 首页看板：八指标、待办 Badge 与空表 |
| 本轮浏览器现场 | `127.0.0.1:5175` 登录页可见；登录提交返回“请求失败”，后续 ADMIN 页面/Tab 未完成本轮实时浏览器复核。未新增截图入 Git。 |

图片仍在用户原附件位置；本审计文档不复制截图到仓库。缺少现场核对的页面在后续设计前可在 API 正常运行时作**一次针对性补充观察**，尤其是角色权限/权限目录和常见笔记本视口；本轮不将其伪称为已通过。
