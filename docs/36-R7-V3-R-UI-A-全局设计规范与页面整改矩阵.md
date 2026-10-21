# R7-V3-R-UI-A 全局设计规范与页面整改矩阵

状态：**UI-A 设计基稿，导航矩阵已按 UI-C 第 4 项最新确认更新**。本稿不是新的业务规则，不代替 `AGENTS.md` 和 `docs/01～07 V3.0`，也不代表新导航已经通过业务方真实 GUI 验收。原核查基点为 HEAD `780ff306c5112c5444a1eb3c519f937ac5f5de2e` 加未提交的 V3-R-UI 工作区；未重置或覆盖既有修改。系统名称统一为“车队经营管理系统”，仅使用本项目的文字标识及墨绿、白色、浅灰风格。

## 1. 依据、范围与证据

- 正式规则：`AGENTS.md` 与 `docs/01～07 V3.0`。页面和导航以 `docs/05`、权限以 `docs/06`、验收状态以 `docs/07` 核对；`docs/35` 仅用于追溯已发现问题及人工验收结果。
- 最新工作区有 **37 个权限路由**和登录页。`#/my-todos` 是工作台“待办概览”两卡片页，`#/approvals-todo` 是审批中心正式待办列表；旧 `#/approvals-queue` 仅重定向至后者，不构成第二套可写页面。两页分别复用正式审批 TODO 数据，费用卡片与待归属页面复用归属权限保护的费用事实查询。
- 已有人工确认：三类导入历史**列表布局**及其视觉一致性四项为 MANUAL GUI PASS；不延伸到真实批次详情、文件下载或分页。`docs/35` 最新记录的费用申请、导入详情、充电模板编辑及三级导航修复仍待人工复验。
- 原始 41 张截图的完整逐张索引不在当前仓库资料中；本稿依据 `docs/35` 的问题归纳、目前可见截图和源码形成矩阵，**不声称逐张比对全部 41 张**。UI-B 复验时按业务方保留的截图编号补齐追踪。
- 本稿只设计布局与交互。DAILY 混合明细、TASK 单笔、普通费用审批后入账、充电订单专项直接入账、Participant 工资、统一经营公式及导入历史数据源均保持原规则。

## 2. UI-C 第 4 项确认的导航归属

业务方最新确认八个一级域，依次为工作台、运输管理、车辆与人员、费用管理、审批中心、工资与考勤、经营与结算、系统设置。独立“导入中心”取消。运输管理下原“调度中心”改名“车辆出勤”，二级叶子为“自有车辆”“外协车辆”，仍复用原月度台账组件和发车统计 API。

费用管理依次提供“费用申请”“费用台账”“待归属费用”“充电订单导入”“充电公司模板配置”。待归属费用页复用 `ExpenseLedgerV3View.vue`、费用详情、归属历史及正式归属写入；旧 `#/expense-attribution` 重定向至 `#/expense-unassigned`。充电订单导入历史留在该页面，任务与轮胎导入历史分别留在本业务页面。

审批中心依次提供“我的待办”“我的申请”“审批记录”“流程与模板配置”。“我的申请”是整行进入本人申请记录的普通二级叶子，不展开；`#/approvals-mine` 复用原申请记录页面。“费用申请”仅从费用管理进入，仍复用 `ExpenseApplicationV3View` 与正式 `/approvals` 提交链。审批中心“我的待办”直接使用正式审批列表；工作台“待办概览”只显示两个同源统计卡片。菜单按 `/me.permissions` 过滤，后端接口仍独立校验权限。

下方矩阵在 UI-A 时记录的旧菜单名称、旧路由说明以本节最新确认的归属为准；UI-C 第 4 项人工 GUI 尚待业务方复验。

## 3. 全局 UI 设计规范

以下尺寸为目标设计值；以 100% 浏览器缩放的 1366×768 与 1920×1080 验证。优先复用 `admin-web/src/design/tokens.css` 的现有墨绿令牌，收敛 `style.css` 中多段覆盖样式，避免局部页面产生第二套视觉规则。

| 对象 | 具体规范与状态 |
| --- | --- |
| 颜色 | 页面底 `#f4f7f6`，卡片 `#fff`，主文字 `#20312e`，次级 `#64746f`，边框 `#e0e8e5`，主按钮/链接 `#176f62`、悬停 `#10594e`，选中浅底 `#e7f4f0`；侧栏墨绿 `#193630`；成功/警示/危险沿用 `tokens.css` 语义令牌。金额负值与危险操作不能只靠颜色表达。 |
| 字体 | `Inter, Segoe UI, Microsoft YaHei, system-ui`；页面正文/表格 14px，辅助说明 12–13px，表单标签 13px/600，区块标题 17–18px/650，页面标题 22px/700，关键金额 24px/700；行高 1.45–1.6，数字等宽。 |
| 页面容器 | 侧栏 232px，正文 `min-width:0`；顶部栏高 64px；业务内容最大宽 1520px，居中，横向内边距 24px（1366 时 20px），主区块间距 16–20px；不得出现整页横向滚动。宽表只在自身容器 `overflow-x:auto`。 |
| 八域导航 | 一级高 44px、字 14px/700、左内边 12px；二级高 38px、字 13px/550、层级缩进 20px；三级高 36px、字 13px、相对二级再缩进 16px。一级展开与二级展开用不同墨绿层次；三级当前项用 `#2f5b54` + 左 3px 浅绿强调线。整行展开按钮至少 40px 触控高度。 |
| 菜单行为 | 多个一级域可同时展开；侧栏品牌固定，菜单独立纵向滚动，正文独立滚动。叶子高亮、所有祖先展开。`aria-expanded`、`aria-current="page"`、focus-visible 2px 轮廓齐全；按钮用 Enter/Space。对导航权限变化，刷新 `/me` 后重新过滤并清除不可达的当前路由。 |
| 顶部账号 | 高 64px 内保持面包屑与头像/姓名；下拉最小宽 176px，含刷新权限、修改密码、退出。下拉触发目标至少 36px，展开可键盘进入，点击外部或 Esc 关闭；反馈真实、不可用时明确提示。 |
| PageHeader | 标题 22px，说明 13px；主操作放右上角，最多一个实心主按钮，其余次级按钮；标题上下间距 24/16px。窄宽度时操作区换行，不覆盖标题。面包屑按真实菜单层级，不显示技术 route key。 |
| 筛选区 | 置于表格上方独立白色卡片或清晰分隔区，控件高 36–40px，标签在控件上方，字段间距 12–16px；主查询与重置可见，高级条件可折叠；筛选提交重置页码至第 1 页，并同步汇总和下载口径。不能仅筛当前页却标示为全量搜索。 |
| 数据表格 | 表头 40–44px，数据行 44–52px，单元格横向 12–16px；表头浅灰、细分割线、悬停浅绿；业务名称/编号可读，技术 ID 不出现在主表。金额与计数右对齐，金额统一 ¥ 和两位小数；状态中文 Tag。操作列建议右侧固定，只有真实权限和状态允许的操作可见。 |
| 汇总卡 | 4–6 个核心指标一行自适应，每卡内边距 16px、数值 24px、来源/口径 12px；汇总由正式 API 返回，不在前端另算财务事实。窄屏降为两列。 |
| 表单 | 一般字段 2 列，长文本/明细占整行；控件高 40px、标签和错误就地关联，必填标识清楚。金额与重量输入显示单位和精度；异步选择器可搜索、显示 Loading/无结果，不露数据库 ID。离开或切换有未提交明细时确认，取消恢复原草稿。 |
| 详情抽屉 | 宽 640px，长表可用 `min(760px, 100vw)`；标题/业务号固定顶部，正文独立滚动，底部仅显示本详情合法操作。采用“概况→事实明细→历史/证据”顺序；图片缩略图可放大，失败显示中文状态，原始凭证与调整凭证分组。 |
| 编辑弹窗 | 简单确认 420px，普通编辑 560px，复杂多明细使用较宽 Dialog/独立页；最大高 `100vh - 36px`，内容滚动、操作区固定；取消/确认位置一致。危险操作二次确认并说明后果；提交 busy 时禁止重复点击。 |
| 页签 | 高 40px，选中墨绿文字、浅绿底、底部 3px 强调线，未选中普通文字；悬停、焦点、左右方向键/Home/End 可用。路由级职责优先独立页面，页签只用于同一业务页的内容切换；导入操作/历史继续复用 `ImportTabs.vue`。 |
| 分页 | 表格底右侧；显示总条数、页码、页大小，切换后保留筛选并滚动回表格头；无数据时不显示伪分页。导出由服务端按全筛选条件生成，不取当前页拼表。 |
| 状态 | Loading 用占位/进度并锁定重复操作；Empty 区分“尚无数据”和“筛选无结果”，给出可执行建议；Error 明确中文原因与重试；Permission 明确无权限且不渲染敏感数据；成功提示说明真实已完成动作，不代替 API 结果。 |
| 响应式 | 1920×1080 下利用最大 1520px 内容区；1366×768 下侧栏仍 232px，页面筛选换行、指标降列，抽屉/弹窗不越屏；≤920px 时才进入窄屏导航方案。出勤/调度月表及宽财务表只在表格容器内部滚动，可固定业务主列。 |

## 4. 全页面整改矩阵

表内 `#/…` 为当前 hash 路由；“问题/动作”是基于源码和 `docs/35` 的设计核查点，不将未实际复验的视觉问题写成 GUI FAIL。“共用”栏指出现有组件与可抽取模式；预计文件均在 `admin-web/src/`，`routes.js`/`navigationModel.js`/`AppLayout.vue`/`style.css` 等共享文件在对应阶段统一处理。每行验收均包含 1366×768、1920×1080、Loading/Empty/Error/Permission、刷新/返回及权限负向，以下只列该页专有验收点。

| 当前路由 | 最终菜单位置与职责 | 现有组件 | 现有 API | 当前问题与目标布局 | 共用/预计修改文件 | 专项验收 |
| --- | --- | --- | --- | --- | --- | --- |
| `#/dashboard` | 工作台→经营概览；月度经营与待处理概览 | `DashboardV3View`、PageHeader、FilterBar | `v3FinanceApi.dashboard` | 当前已有指标和口径说明；目标为月份筛选→4–6 指标→待处理卡→经营口径，卡片大小和留白统一 | MetricCard；`views/DashboardV3View.vue` | 金额与正式 API 同口径，未归属 DAILY 已计入公司支出 |
| `#/my-todos` | 工作台→待办概览；仅两张统计卡片 | `MyTodosV3View` | `v3ApprovalApi.list('TODO')`、`v3ExpenseLedgerApi.pendingAttributionSummary` | 根据审批处理或费用归属权限分别显示卡片，不展示明细列表；整卡点击并支持键盘激活；该页不设独立授权项 | TodoCard；`views/MyTodosV3View.vue`、`router/pagePermissionModel.js` | 同源数量、不受分页影响；失败不显示为 0，两类权限均无时菜单及直接路由均拒绝 |
| `#/products` | 运输管理→货物管理；货物目录 | `MasterDataUi2View`、`MasterCatalogUi2` | `masterApi.products/createProduct/updateProduct/setProductStatus` | 共享资料页内部仍需明确当前对象；目标筛选→业务列表→编辑 Dialog | CatalogTable；`views/MasterDataUi2View.vue`、`components/MasterCatalogUi2.vue` | 货物状态/名称中文，启停反馈真实 ；页面查看 `PAGE_PRODUCTS`，新增/编辑/启停 `BUTTON_PRODUCTS_MANAGE`；只读货物选项 API 也可由线路模板页面授权访问，货物维护仍需本页按钮 |
| `#/templates` | 运输管理→线路模板；模板默认值 | `TransportTasksV3View` 模板模式 | `routeTemplateApi` | 与运输任务共用大 View，入口需直接定位模板；目标列表→摘要/默认金额→编辑 Drawer | BusinessSummary；`views/TransportTasksV3View.vue` | 修改模板不改变已建任务快照；启停入口有权限 |
| `#/dispatch` | 运输管理→调度工作台→批量调度；整批发布 | `DispatchWorkbenchV3View`、FormDialog | `dispatchApi`、`masterApi`、`routeTemplateApi` | 现有三段操作需压成明确工作台；目标日期/模板选择→默认值摘要→待发布表格→行错/批量金额→汇总确认 | EditableRows、SummaryBar；`views/DispatchWorkbenchV3View.vue` | 同司机多行、整批原子、服务端重验、双击幂等；不出现“趟数” |
| `#/task-imports` | 运输管理→调度工作台→任务导入；固定 Excel | `TaskImportsV3View`、ImportTabs、DataTable、DetailDrawer | `taskImportApi`、`attachmentApi` | 历史列表布局已人工 PASS；目标只收口上传/预览/确认和批次详情的层级，不重做历史表 | ImportFlow；`views/TaskImportsV3View.vue` | 真实行结果、失败原因、分页、原文件下载单独人工复验 |
| `#/routes` | 运输管理→调度工作台→运输任务；任务生命周期 | `TransportTasksV3View`、DataTable、DetailDrawer | `routeApi`、`routeTemplateApi`、`attachmentApi`、`masterApi` 等 | 大 View 包含列表/模板/重量/参与历史；目标紧凑筛选→任务表→事实分组抽屉，操作仅在合法状态出现 | DetailSection、EvidenceCard；`views/TransportTasksV3View.vue` | 取消/发车/转派/卸货/重量证据与正式状态一致，无作废/恢复 |
| `#/dispatch-internal` | 运输管理→车辆出勤→自有车辆 | `MonthlyOperationsV3View` 自用模式 | `dispatchLedgerApi` | 月表宽，需要局部滚动和固定车牌/司机事实列；月份→摘要→月表→导出 | MonthlyGrid；`views/MonthlyOperationsV3View.vue` | 同车同日多任务计数、历史车挂/司机快照、导出同口径 |
| `#/dispatch-outsourced` | 运输管理→车辆出勤→外协车辆 | `MonthlyOperationsV3View` 外协模式 | `dispatchLedgerApi` | 与自用月表共用骨架，突出真实外协车牌和司机；不展示公司车辆 ID | MonthlyGrid；`views/MonthlyOperationsV3View.vue` | 按实际发车及外协车牌统计，导出一致 |
| `#/drivers` | 车辆与人员→司机管理；司机档案与自有司机车辆初始导入 | `MasterDataUi2View`、`DriverManagementSample` | `masterApi.drivers/driver/createDriver/initialDriverVehicleTemplate/initialDriverVehiclePreview/initialDriverVehicleConfirm` | 服务端分页；新增固定十列模板下载、只读逐行预览、全部通过后整批确认；司机表与档案/当前绑定/历史仍保留 | CatalogTable、DetailSection；`views/MasterDataUi2View.vue`、`components/DriverManagementSample.vue` | 全量筛选、业务车牌、内部/外协中文身份；导入状态待人工验收 |
| `#/vehicles` | 车辆与人员→车辆管理；公司车辆 | `MasterDataUi2View`、`MasterCatalogUi2` | `masterApi.vehicles/vehicle/createVehicle/updateVehicle` | 资料页共用结构但对象专有列需保留；目标表格→详情/编辑 | CatalogTable；`views/MasterDataUi2View.vue`、`components/MasterCatalogUi2.vue` | 车牌和状态可读，编辑后列表刷新 |
| `#/trailers` | 车辆与人员→车挂管理；车挂资料 | `MasterDataUi2View`、`MasterCatalogUi2` | `masterApi.trailers/createTrailer/updateTrailer` | 目标与车辆页相同的列表/弹窗范式，避免平铺字段 | CatalogTable；同上 | 当前绑定/历史不被误写成当前车挂 |
| `#/binding` | 车辆与人员→绑定关系；司机/车/挂关系 | `MasterDataUi2View`、`BindingManagement` | `masterApi.bind*/unbind*/vehicleHistory/driverHistory/trailerHistory` | 目标当前关系表→绑定/解绑 Dialog→历史时间线；与轮胎三级菜单缩进一致 | RelationHistory；`views/MasterDataUi2View.vue`、`components/BindingManagement.vue` | 当前与历史分离，非法绑定仍由后端拒绝 |
| `#/tires` | 车辆与人员→轮胎管理→轮胎库存 | `TiresUi2View`、DataTable、DetailDrawer | `tireApi.list/detail/edit` | 目标筛选→状态汇总→库存表→维护/历史抽屉；Web 不展示换胎申请 | CatalogTable、AuditTimeline；`views/TiresUi2View.vue` | 四字段、库存状态、授权维护与审计清楚 |
| `#/tire-imports` | 车辆与人员→轮胎管理→轮胎入库 | `TireImportsV3View`、ImportTabs、DetailDrawer | `tireApi.template/preview/confirm/imports/importDetail`、`attachmentApi` | 历史列表布局已人工 PASS；目标固定模板流程与真实批次详情分区 | ImportFlow；`views/TireImportsV3View.vue` | 原文件/行号/失败原因/分页待人工复验 |
| `#/expense-ledger` | 费用管理→费用台账；正式 ExpenseEntry | `ExpenseLedgerV3View`、FilterBar、DataTable、DetailDrawer | `v3ExpenseLedgerApi`、`chargingApi.sourceOrder` | 当前常用/更多筛选已分层；目标紧凑筛选→服务端汇总→分页表→来源/归属/冲销详情 | MoneySummary、DetailSection；`views/ExpenseLedgerV3View.vue` | 未归属 DAILY、冲销净额及来源可读，金额与 API 一致 |
| `#/expense-unassigned` | 费用管理→待归属费用；独立菜单，复用正式费用台账组件 | `ExpenseLedgerV3View` UNASSIGNED 模式 | `pendingAttribution/summary`、`detail/attribute` | 服务端固定有效且未归属条件；卡片与页面同口径，详情及归属历史复用 | `views/ExpenseLedgerV3View.vue`、`router/routes.js` | 仅 `expense:attribution:edit` 可访问；分页不影响总数，已归属和已冲销不计入 |
| `#/expense-attribution`（仅旧链接） | 重定向至费用管理→待归属费用 | `ExpenseLedgerV3View` UNASSIGNED 模式 | 与正式待归属页面相同 | 不实例化第二套费用列表 | `router/routes.js` | 车辆不一致仍被服务端硬拒绝，绑定后公司费用不重计 |
| `#/approvals-flows` | 审批中心→流程与模板配置；版本化流程 | `ApprovalCenterV3View` 流程模式 | `v3ApprovalApi.templates/flow/createTemplate/nextVersion/publish` | 大 View 模式切换较多；目标模板表→冻结版本抽屉→节点编辑/发布明确分区 | FlowSteps、DetailSection；`views/ApprovalCenterV3View.vue` | 同岗位多节点按稳定步骤区分，在途实例不随模板改动 |
| `#/approvals-todo` | 审批中心→我的待办；正式审批列表 | `ApprovalCenterV3View` TODO 模式 | `v3ApprovalApi.list/detail/approve/returnStep` | 列表仅显示当前用户可处理的审批，操作在详情中完成 | DataTable、ApprovalTimeline；`views/ApprovalCenterV3View.vue` | 总数与工作台审批卡同源，不能混入费用 |
| `#/approvals-mine` | 审批中心→我的申请；本人申请普通二级页 | `ApprovalCenterV3View` MINE 模式 | `v3ApprovalApi.list/detail/resubmit/create` | 整行点击进入申请记录，记录表→申请详情/时间线，普通申请沿用原正式入口 | ApprovalTimeline；`router/navigationModel.js`、`router/routes.js`、`views/ApprovalCenterV3View.vue` | 点击后进入记录；不展开费用申请；审批写入仍唯一 `/approvals` |
| `#/expense-apply` | 费用管理→费用申请；DAILY/TASK | `ExpenseApplicationV3View`、FormDialog | `v3ApprovalApi.expenseOptions/taskCandidates/dailyTemplate/dailyPreview/create` | `docs/35` 已记录布局人工 FAIL/待复验；目标申请类型→申请信息→明细表→汇总→确认提交；保持草稿保护 | EditableExpenseItems、SummaryBar；`views/ExpenseApplicationV3View.vue` | DAILY Excel+手工混编，TASK 单笔；最终一审批实例，所有金额>0 |
| `#/approvals-history` | 审批中心→审批记录；全量历史 | `ApprovalCenterV3View` HISTORY 模式 | `v3ApprovalApi.list/detail` | 目标紧凑筛选→分页记录→流程/动作时间线；不出现重复“新增” | ApprovalTimeline；`views/ApprovalCenterV3View.vue` | 指定步骤打回历史可读，无多余写入口 |
| `#/approvals-queue` | 旧审批处理深链；重定向至 `#/approvals-todo` | `ApprovalCenterV3View` TODO 模式 | 与正式审批待办相同 | 不再实例化第二个路由页面 | `router/routes.js` | 重定向后审批中心菜单高亮，后端权限不变 |
| `#/attendance` | 工资与考勤→司机出勤；实际发车日 | `MonthlyOperationsV3View` 出勤模式 | `attendanceApi` | 月表需固定司机列、局部横滚；目标月份/司机→月表→当日任务抽屉→导出 | MonthlyGrid；`views/MonthlyOperationsV3View.vue` | 同日多趟=1 天，外协不入表，跨日卸货不另计 |
| `#/salaries` | 工资与考勤→工资台账；Participant 工资 | `SalaryLedgerV3View`、DataTable、DetailDrawer | `salaryApi`、`routeApi` | 目标筛选→工资汇总→分页明细→工资历史，转派标签明显 | MoneySummary、DetailSection；`views/SalaryLedgerV3View.vue` | 页面、导出、经营金额同一 Participant 工资 |
| `#/finance-company` | 经营与结算→收入与利润→公司收支利润 | `FinanceReportsV3View` 公司模式 | `v3FinanceApi.company` | 目标月份→收入/支出/利润指标→费用结构→未归属 DAILY 说明 | MoneySummary；`views/FinanceReportsV3View.vue` | 不在前端重算，未归属费用计入一次 |
| `#/finance-templates` | 经营与结算→收入与利润→线路收支利润 | `FinanceReportsV3View` 模板模式 | `v3FinanceApi.templates/tasks` | 目标月份→模板表→其任务明细穿透，区分模板默认金额与任务实绩 | DrilldownTable；`views/FinanceReportsV3View.vue` | 模板经营与任务/公司口径交叉对账 |
| `#/finance-tasks` | 经营与结算→收入与利润→单趟收支利润 | `FinanceReportsV3View` 任务模式 | `v3FinanceApi.tasks/task` | 目标筛选→任务经营表→参与人与费用档案抽屉，金额四列统一 | FinanceDetail；`views/FinanceReportsV3View.vue` | 收入、信息费、工资、费用、利润与正式 API 一致 |
| `#/finance-vehicles` | 经营与结算→支出→车辆支出 | `ExpenseLedgerV3View` 车辆模式 | `v3ExpenseLedgerApi.list/summary` | 复用正式费用台账；目标车辆筛选→汇总→明细，不复制支出公式 | MoneySummary；`views/ExpenseLedgerV3View.vue` | 当时车辆事实可追溯，不用当前绑定覆盖历史 |
| `#/finance-drivers` | 经营与结算→支出→司机支出 | `ExpenseLedgerV3View` 司机模式 | `v3ExpenseLedgerApi.list/summary` | 同一费用组件按司机维度展示；目标与车辆支出一致 | MoneySummary；同上 | 筛选与导出完整，不新建工资事实 |
| `#/finance-energy` | 经营与结算→支出→能源费用 | `ExpenseLedgerV3View` 能源模式 | `v3ExpenseLedgerApi.list/summary` | 复用费用事实；类型名称业务中文，包含现有能源类别 | MoneySummary；同上 | 充电来源可追溯，零元来源不产生费用 |
| `#/settlements` | 经营与结算→月度结算；历史版本 | `SettlementV3View`、FilterBar、DataTable | `v3SettlementApi.versions/generate/version/details/diff` | 目标月份→版本表→只读明细/差异，生成动作明确确认，勿误解为改写旧版 | VersionTimeline、MoneySummary；`views/SettlementV3View.vue` | 旧版本不可变，差异原因与当前事实对应 |
| `#/charging-imports` | 费用管理→充电订单导入；专项确认入账 | `ChargingImportsV3View` 导入模式、ImportTabs、DetailDrawer | `chargingApi.companies/imports/importDetail/originalFile/confirm` | 目标选择公司→文件→只读预览/异常→确认→结果；历史页签保留，列表布局已人工 PASS | ImportFlow、SourceTrace；`views/ChargingImportsV3View.vue` | 零元/重复/金额对账、原行号、原文件下载；确认前不入账 |
| `#/charging-companies` | 费用管理→充电公司模板配置 | `ChargingImportsV3View` 配置模式 | `chargingApi.companies/createCompany/updateCompany` | 目标公司列表→先选“仅改映射/上传新样本”→四字段选择→取消/保存；不显示导入页签 | MappingEditor；`views/ChargingImportsV3View.vue` | 取消丢弃未存更改；新样本重选四字段；仅改映射不强制上传 |
| `#/users` | 系统设置→用户管理 | `SystemUi1View` 用户模式 | `systemApi.users/createUser/updateUser/resetPassword/setStatus` | 现提示“筛选当前页”，需在设计中明确服务端可支持范围；目标用户表→编辑/重置密码 Dialog | CatalogTable；`views/SystemUi1View.vue` | 用户改自身密码与管理员重置区分；筛选口径不误导 |
| `#/roles` | 系统设置→角色权限 | `SystemUi1View` 角色模式 | `systemApi.roles/role/permissions/saveRolePermissions` | 八域→二/三级正式页面→操作权限树；后端功能注册表提供页面及按钮；每个按钮只归属一个页面，展示部分选中、搜索与增减确认 | `RolePermissionTree`、`RolePermissionPage`、`pagePermissionModel.js`、`SystemUi1View.vue` | 选中操作补足页面访问依赖，取消访问处理依赖；退役权限不可配置；保存后 `/me` 刷新，人工 GUI 待验收 ；每个按钮仅在所属页面可勾选；共享 API 以多个页面的 OR 关联实现，绑定操作只归“绑定关系” |
| `#/audit` | 系统设置→操作日志 | `SystemUi1View`、`AuditLogsUi2` | `systemApi.auditLogs` | 目标筛选→中文动作表→详情保留原始代码；不直接主列展示英文技术枚举 | AuditTimeline；`views/SystemUi1View.vue`、`components/AuditLogsUi2.vue` | 中文含义正确、原始动作码可追溯 |
| 登录（非权限路由） | 系统入口；账号认证 | `LoginView`、`authStore` | 认证 API（`authStore.login`） | 目标清晰系统名、手机号/密码、错误与等待状态；不沿用其它项目品牌 | FormField；`views/LoginView.vue` | 密码不明文显示/日志，失败提示准确，登录后进入首个授权页面 |

以上矩阵保留旧 `#/expense-attribution` 的追溯行，当前实际可达权限路由为 **37 个**和登录页。`#/task-import-history`、`#/charging-history`、`#/import-history`、`#/expense-attribution`、`#/approvals-queue` 是历史深链别名，不进入八域菜单，只重定向到各自唯一业务页面；服务端仍执行权限检查。

## 5. 重点页面布局与操作流程

1. **经营概览：**月份与口径提示置顶，收入/支出/利润/待处理摘要并列；待办卡只显示正式 API 可得计数，点击进入同一待办页。禁止在前端拼另一套利润。
2. **批量调度：**选择业务日期和可搜索线路模板后显示客户/货物/装卸地与三项默认金额；逐行选司机、自动解析自有车辆或填写外协车牌；可批量改金额，表格内行错定位；“预览汇总→一次确认”固定在页面底部业务操作区。任何行正式校验失败则整批 0 发布。
3. **运输任务：**列表只展示任务关键事实与当前状态；详情抽屉分概况、参与历史、重量/原始磅单与调整证据、线路操作。历史司机与最终司机的操作入口区分，取消/发车/卸货/纠错随权限和状态出现；不出现 VOIDED/作废恢复。
4. **费用申请：**PageHeader→申请类型下拉→申请信息（DAILY/TASK）→明细表→金额汇总→提交确认。DAILY Excel 只加入可编辑明细，手工行与导入行统一编辑/删，最终 items[] 一次提交审批；TASK 一条、司机→参与任务选择，无 Excel。未提交草稿切换先确认。
5. **费用台账/归属：**共同使用筛选与正式汇总组件；台账强调来源、金额、当前归属、冲销，归属页优先呈现未归属 DAILY 与目标任务核对。抽屉展示来源/归属历史；错车绑定后端硬拒绝的中文错误就地呈现。
6. **审批中心：**待办与记录以表格为主、详情以申请摘要+明细+冻结步骤+Action 时间线为主。返回指定步骤时只列合法业务步骤名称；普通审批唯一发起入口位于申请记录，费用申请独立叶子；不出现重复新增按钮。
7. **工资台账/司机出勤：**工资页为筛选→汇总→Participant 行→整任务转派核对/历史；出勤页为月份→司机×自然日宽表→某天实际发车任务抽屉。导出沿用当前服务端筛选，不拿本页数据临时拼 Excel。
8. **轮胎库存：**状态与库存数摘要→轮胎表→详情/维护历史；轮胎入库独立页处理固定四字段、预览、确认和页面内批次历史；Web 不加换胎申请。
9. **充电订单导入/模板配置：**两条菜单只复用一套组件与 API；导入页文件只读预览→异常修正源文件→人工确认→历史/来源行；配置页公司列表→编辑方式选择→四个真实表头列映射→保存/取消。零元订单单列保留来源不入费用。
10. **公司经营/月度结算：**公司经营显示正式收入/支出/利润及未归属 DAILY 说明；月结显示版本、明细、与前版差异，历史版本明确只读。所有金额取后端财务 API。
11. **用户与角色：**用户页分页列表、身份与状态、编辑/重置；角色页分组权限矩阵、保存结果；账号菜单中的本人修改密码独立于管理员重置，权限刷新只显示实际 `/me` 返回结果。

## 6. 公共组件与样式收口清单

| 优先级 | 现有基础 | UI-B 以后建议动作 | 适用范围与边界 |
| --- | --- | --- | --- |
| P0 | `AppLayout.vue`、`navigationModel.js`、`routes.js` | 审批纯展开父级/两个叶子；统一祖先高亮、面包屑、键盘与权限过滤 | 只调整导航层，保留现有正式 View/API |
| P0 | `design/tokens.css`、`style.css`、`design/ui1.css`、`design/ui2.css` | 统一颜色/字号/间距/侧栏层级；把后加的重复覆盖规则合并到单一设计令牌和结构样式 | 不借 CSS 改业务状态或数据口径 |
| P0 | `PageHeader`、`FilterBar`、`DataTable`、`PaginationBar` | 固定页面骨架、筛选/汇总/服务端分页、宽表局部滚动与金额列对齐 | 不让前端分页/汇总冒充全量业务数据 |
| P0 | `LoadingState`、`EmptyState`、`ErrorState`、`StatusTag` | 状态文案、重试、权限态统一；技术枚举映射中文 | 原始技术值仅必要详情显示 |
| P1 | `DetailDrawer`、`FormDialog`、`ConfirmDialog` | 抽屉分组、固定操作栏、编辑确认与危险动作一致 | 权限、附件 Purpose、服务端状态校验原样 |
| P1 | `ImportTabs`、三个导入 View | 页签统一，历史布局保留已有人工 PASS；细化真实批次详情和原文件错误态 | 历史/API/文件存储不重建 |
| P1 | `EntityPicker` | 司机、模板、车辆、任务等选择器统一搜索/异步状态与业务标签 | 不暴露数据库 ID，不跳过正式候选资格 |
| P2 | 当前各页手写指标卡/金额区 | 抽 `MoneySummary`/`MetricCard` 展示组件 | 只读展示正式 API 返回金额，不集中重算 |
| P2 | 当前审批、工资、任务详情中的历史块 | 抽可配置 `AuditTimeline`、`EvidenceCard`/`DetailSection` | 仅展示原有事实和图片，不改变证据关联 |
| P2 | 月度出勤/调度宽表 | 抽 `MonthlyGrid` 的布局与固定列能力 | 日数/次数仍来自服务端正式统计 |

公共组件抽取以出现两处以上相同交互且业务含义一致为条件；只为“代码看起来统一”而大规模拆业务 View 不属于本计划。

## 7. 原 UI-A 分阶段计划与当前进度

| 阶段 | 主要页面与交付 | 自动核验 | 人工 GUI 闸门 |
| --- | --- | --- | --- |
| UI-B：壳层与导航 | 侧栏、面包屑、账号、设计令牌、基础组件；最新结构为八域及审批“我的申请”普通二级页 | 当前 37 个权限路由、权限矩阵、刷新/后退、管理端 test/build | 管理员和最小权限账号于 1366/1920 验菜单顺序、层级、滚动与直链 |
| UI-C：运输与人员 | 概览、模板、批量调度、任务/任务导入、月度调度、司机/车辆/车挂/绑定 | 当前前后端受影响测试、正式 API 负向与状态机回归 | 按业务链逐页验，任务/导入用真实测试记录 |
| UI-D：审批与费用 | 审批四页面、DAILY/TASK 申请、台账/归属、充电导入/配置 | DAILY 混编、审批幂等、TASK 单笔、充电只读/零元/权限回归 | 分页、详情、下载、权限、草稿保护逐项确认 |
| UI-E：工资、轮胎、经营、系统 | 工资/出勤、轮胎库存/入库、经营/月结、用户/角色/日志 | 金额一致性、导出、权限、原始文件及管理端 test/build | 真实数据与异常态，两个分辨率逐页核对 |
| UI-F：全局终验 | 重复样式和死入口清理、全站一致性矩阵、`docs/35` 证据收口 | 当前启用测试、标准 package、`git diff --check`、Secret | 业务方逐项 MANUAL GUI PASS；自动/API 不替代 GUI |

本表保留 UI-A 原先的分工思路；实际 UI-B1～B5 已实施且未提交，当前进行 UI-C 第 4 项导航与费用归并，人工 GUI 尚待业务方复验。正式规则只以 01～07 为准；保留本机配置、`.runtime/`、Secret 与未跟踪历史文档，不 reset/clean 工作区。

## 8. 设计确认点与下一步

业务方已确认八域结构、“我的申请”普通二级页、费用申请移至费用管理、费用归属并入费用台账，以及充电两页移至费用管理；上述内容已同步正式 05～07、导航和本矩阵。UI-C 第 1～3 项原有人工 PASS 按业务方最新结论保留；第 4 项仍需人工复验，不能用自动测试替代。


## 页面＋按钮权限树同步说明（2026-10-19）

八域导航中的 36 个管理端菜单页面及 5 个小程序业务页面，统一由后端 `FunctionRegistry` 登记页面功能；页面下登记其实际业务按钮。工作台“待办概览”由审批待办页面＋处理按钮、待归属费用页面＋归属按钮派生，不列为独立授权项。角色权限树只展示页面和按钮，按钮依赖所属页面，编码作为辅助信息。完整页面矩阵与 API 关联见 `docs/37-V3-页面按钮权限与API关联清单.md`。本更新是权限配置结构说明，不改变本文件已记录的视觉验收结论；人工 GUI 待复验。

## 正式 Excel 导入视觉规范（2026-10-20）

- 费用申请的 Excel 只是日常费用明细的辅助填表方式。模板下载和原生文件选择控件统一尺寸、边框与颜色；文件选择仍调用现有 `chooseFile`，服务端预览行直接追加到原费用明细表。手工行与 Excel 行继续同表编辑、删除和汇总，保留原提交前核对与审批生效链，不接入公共导入状态组件。
- 司机车辆初始导入、任务导入、轮胎入库和充电订单导入共用文件选择视觉、结果统计卡片和“全部／通过／失败”展示筛选。筛选只影响当前可见结果；确认时仍使用各自的完整预览和原有 API。任务与轮胎的逐行部分成功、司机车辆的整批回滚、充电订单的只读候选与人工确认，分别保留。
- 司机车辆导入仍使用固定高度的宽弹窗：标题和操作区不随预览滚动，结果表格单独滚动；其余导入仍在原页面。充电公司样本仅负责字段映射，不与订单结果筛选混用。
- 纠错依据图片属于证据上传，不属于 Excel 导入；保持原文件输入、即时上传和逐图移除，不套用公共导入文件控件。上述调整均未完成 GUI 功能验收。
