# R7-V3-R-UI 管理端整体整改与验收报告

## 第四轮人工 GUI FAIL 与定向整改（2026-10-13）

业务方本轮指出费用申请布局、三类导入详情、充电模板取消与修改方式、三级导航五类问题，状态记录为 **MANUAL GUI FAIL → 修复后待复验**。第三轮已人工确认的任务、轮胎、充电历史列表布局及三类列表视觉一致性四项 **MANUAL GUI PASS** 继续有效；这些 PASS 不涵盖本轮详情、下载、模板编辑或三级导航。R7-V3-R-UI 未关闭。

| 问题 | 定向修改 | 技术核对 | 人工 GUI |
| --- | --- | --- | --- |
| 费用申请区域混杂 | `ExpenseApplicationV3View.vue`：将申请类型与费用大类、DAILY 混合明细/TASK 单笔、申请汇总和最终提交依次分区；保留类型下拉及草稿切换确认，未添加保存草稿或新写入接口。 | 5176 受控浏览器实际显示四个独立区域；管理端 test/build | **NOT RUN，待业务方复验** |
| “查看详情”不易看到批次内容 | `TaskImportsV3View.vue`、`TireImportsV3View.vue`、`ChargingImportsV3View.vue`：三类历史使用原详情 API 在抽屉内显示真实批次摘要、正式行状态/原因和原文件下载；任务/轮胎预览有效行单列“待确认”，详情加载失败不保留不完整抽屉。文件缺失和权限失败显示中文错误。 | 5176 隔离库真实任务无效行显示原始行号与失败原因，轮胎预览行显示校验状态，充电入账行显示订单号、原始行号、金额和未归属；原文件正式 API 权限与 SHA-256 验证沿用第三轮结果。 | **NOT RUN，待业务方逐类复验详情与下载** |
| 模板编辑缺取消与修改方式 | `ChargingImportsV3View.vue`：编辑必须先选“仅修改字段映射”或“上传新模板并重新匹配”；前者复用保存的真实表头且无上传要求，后者立即清空旧四字段并在解析新样本后只允许下拉选择实际列。取消脏数据有确认，确认后恢复保存值并退出编辑。未增加模板版本。 | 5176 受控浏览器实际编辑测试公司：未保存名称取消后公司列表保持原值；两种模式的输入和保存状态符合预期。 | **NOT RUN，待业务方复验** |
| 三级导航视觉层级不一 | `style.css`：统一同级二级菜单缩进与文字；二级展开使用深绿底，三级当前项使用另一层背景和左强调线。未修改 `AppLayout.vue` 的整行点击、特殊“我的申请”双点击区、权限和路由逻辑。 | 5176 实际展开运输管理、车辆与人员/轮胎管理、审批中心，并检查当前路由父级展开；管理端导航测试/build。 | **NOT RUN，待业务方核对 1366×768 与 1920×1080** |

本轮仅更改上述四个 Vue 页面、`style.css`、管理端相关测试和本报告；本轮没有修改后端业务规则、数据库、OpenAPI 或其它业务写入路径。测试仍在隔离库 `fleet_ops_v3r_ui2_20261013045339` 和 5176/8082 上进行，没有触碰 5175/8080。业务方所见原始失败状态与技术检查结果严格分列，**受控浏览器检查不等于人工 GUI PASS**。

现有充电详情 API 保留新来源订单逐行记录与批次重复数量；对全部重复的批次，没有为本次重复订单另外持久化一份逐行结果，因此抽屉明确显示“本批次没有新增来源订单，重复数量以批次汇总为准”。这属于当前正式数据的可展示边界，未伪造重复行或新建第二套来源事实。异常 Excel 行在只读预览阶段阻止正式确认，故正式充电批次没有失败行可展示。本轮不扩展后端文件或批次模型；如业务方要求重复订单逐行追溯，应单独确认持久化契约。

管理端完整测试与 build **PASS**（87 模块；原有大 chunk 提示仍为 NON-BLOCKING）；后端当前启用测试 **82 项，0 failed、0 errors、1 skipped**。隔离库正式 API 复核任务、轮胎、充电批次详情分别返回 1、1、2 条真实行记录；充电 ExpenseEntry 仍为 2 条、每条 ¥12.34，零元来源订单无 ExpenseEntry，本轮未发生新的费用写入。`git diff --check`、`git diff --cached --check` 均 PASS；本轮新增源码的常见密钥字面量扫描 0 命中，本报告 0 命中且无尾随空格。本机 `application.yml`、`project.config.json`、`.runtime/` 原状态保持、未暂存。待人工复验：费用申请 DAILY/TASK 分区和草稿保护、三类历史有数据详情与原文件下载、充电模板编辑两模式及取消、运输/车辆/审批/经营三级导航和两种分辨率。全部保持 **NOT RUN**，不提交、不关闭。

## 第三轮人工验收问题与合并整改（2026-10-13）

业务方已人工确认 **MANUAL GUI PASS**：任务导入历史列表布局、轮胎入库历史列表布局、充电订单导入历史列表布局，以及三类历史列表的视觉一致性。这四项仅覆盖当时的列表布局；真实数据、分页、文件追溯及本轮新交互仍须另行人工复验。R7-V3-R-UI **未关闭**。

| 本轮项目 | 实际处理与文件 | 当前验证 | 人工 GUI |
| --- | --- | --- | --- |
| “我的申请”主体与展开区 | `AppLayout.vue`、`style.css`：主体仍进入申请记录；右侧改为明显的“费用申请”展开按钮，与主体独立点击、阻止冒泡，原生按钮支持键盘；未改已确认的导航层级。 | 管理端导航结构测试、build | **NOT RUN，待业务方复验** |
| 三类导入页签 | 新增共享 `ImportTabs.vue`，任务、轮胎、充电导入页复用相同的墨绿文字、浅绿选中区、底部强调线、悬停反馈与方向键/Home/End 操作；保留原有历史表格布局和导入语义。 | 管理端测试、build；Codex 受控浏览器可切换真实数据页签 | **NOT RUN，待业务方复验** |
| 原文件追溯 | 任务与轮胎历史详情经原 `file_attachment → import_task.source_attachment_id` 关系查询原文件，调用既有 Attachment 下载 API；`AttachmentService.java`、`FileAttachmentRepository.java` 将 `IMPORT_FILE` 读取权限按实际 TASK/TIRE 批次收紧。充电沿用 `charging_source_order → charging_import_batch → original_storage_key` 与原 `GET /charging/imports/{id}/original-file`；缺失文件返回明确中文业务错误。未新增文件系统、附件表或下载接口。 | 隔离库正式 API 下载 SHA-256 与上传文件一致；TASK/TIRE 交叉权限 403；充电文件临时缺失返回 409 且测试后恢复 | **NOT RUN，待业务方复验** |
| 充电页面职责 | `ChargingImportsV3View.vue`：充电订单导入只显示上传与只读预览、导入历史；充电公司模板配置只显示公司列表和当前映射维护。两路由仍复用现有组件状态及 API，没有第二套导入写入链。 | 管理端测试/build；5176 受控浏览器分别看到两种独立页面 | **NOT RUN，待业务方复验** |

真实数据仅在独立测试库 `fleet_ops_v3r_ui2_20261013045339` 经正式 API 与固定测试 Excel 生成，标识 `RUI340916755`；5175/8080 环境及其他数据库未操作。任务批次 1 为一行 **全部发布**，轮胎批次 2 为一行 **全部入库**；另分别建立 21 个可识别的预览批次以核对历史第二页及失败行，未把无效行伪造成正式成功。充电 A 公司批次 1 为有效 2 条＝入账 1 条 ¥12.34＋零元不入账 1 条，原始行号 2、3；同来源重新提交批次 2 为重复 2 条、入账 0；B 公司批次 3 使用相同订单号仍入账 1 条 ¥12.34。正式费用台账 API 交叉检查仅有 2 条充电 ExpenseEntry、每条 ¥12.34，零元 SourceOrder 的 `expense_id` 为空。另有可识别的重复测试批次验证 20 条/页与第二页。所有原始文件仍与各自批次关联，同名文件不靠文件名关联；零元订单保留 SourceOrder 并参与去重，不产生 ExpenseEntry。5176 受控浏览器核对了三类有数据的历史列表、分页、任务/轮胎成功批次详情及原文件按钮、充电来源订单详情和零元原始行；轮胎页签可用方向键切换。这属于技术环境检查，**不记为业务方人工 GUI PASS**。

原文件权限及缺失状态使用正式 API 检验：任务/轮胎文件分别由对应业务只读权限下载，跨业务均 403；无 `charging:view` 者下载充电原文件为 403。将隔离测试目录内的原文件临时移开时，三类下载均返回 409 与中文缺失提示，文件在 `finally` 中恢复并再次下载成功。任务、轮胎、充电原文件下载内容的 SHA-256 均与对应批次/附件记录一致。管理端完整测试 PASS、build PASS（87 模块，大 chunk 为 NON-BLOCKING）；后端当前启用测试 82 项、0 failed、0 errors、1 skipped；隔离副本标准 package PASS。`git diff --check` 和 Secret 检查见本轮最终检查。

尚需业务方在 5176 人工复验“我的申请”双点击区、统一页签、充电双页面、三类历史的真实数据/详情/下载。人工点击下载及无权限页面中文提示目前 **NOT RUN**；已确认的四项历史列表布局 MANUAL GUI PASS 保留。本轮没有提交代码。

本轮最终检查：`git diff --check` PASS，未跟踪的共享页签与本报告末尾空格检查 0 项，变更范围的常见 Secret 字面量扫描 0 命中，暂存区为空。本机 `application.yml`、`project.config.json`、`.runtime/` 和既有未跟踪历史文档保留原状；隔离测试脚本与文件在被 Git 忽略的 `.runtime/` 内。当前未发现本轮技术 BLOCKER；上述人工 GUI 未复验是 **关闭限制**，不得标为 PASS。

## 第二轮人工 FAIL 与定向整改（2026-10-13）

业务方第二轮人工复验确认现有整改 **FAIL**：实际管理端只显示八个一级菜单；普通父级标题不能整行展开；费用申请类型仍为页签；三类导入历史需要正式表格呈现。此前“九域导航待复验”的记录由本节更新为 **MANUAL GUI FAIL → 修复后待复验**，不得记作 PASS。

| 问题 | 原因与修复 | 自动/真实环境核验 | 人工 GUI |
| --- | --- | --- | --- |
| 第九个“导入中心”未显示 | 导航模型已有第九域，但原隔离 V3-R 测试库的 ADMIN 权限目录缺少三项 `charging:*`，前端按权限正确过滤了空父级。先在该测试库核实权限原因，随后因旧库还缺少充电业务表，改用当前单一 baseline 全新初始化的独立测试库；不放宽菜单权限，也不改动原 5175/8080 服务。 | ADMIN 正式 `/me`、新库充电只读 API 及 Codex 受控 Chrome 九域页面复核见本节下方；基线 SQL 原已包含三项权限 | **FAIL → 待复验** |
| 父级点击区域 | `AppLayout.vue`、`style.css`：普通二级父级改为整行原生按钮，箭头与标题同一点击状态，支持键盘；“我的申请”主体导航、右侧箭头展开为并列按钮，事件不冒泡。 | 导航结构测试、build | **FAIL → 待复验** |
| DAILY/TASK 选择方式 | `ExpenseApplicationV3View.vue`、`expenseScopeDraft.js`：页签改“申请类型”下拉；有未提交内容时须确认切换，原 DAILY/TASK 草稿保留；原 `/approvals` 提交与明细校验不变。 | 草稿保护测试、build | **FAIL → 待复验** |
| 三类导入历史 | `TaskImportsV3View.vue`、`TireImportsV3View.vue`、`ChargingImportsV3View.vue`、`style.css`：统一历史页签、紧凑表格、分页及页面内详情；任务/轮胎逐行结果不再跳回上传页；充电原始 Excel 入口与来源订单分页保留。只复用原 API，不生成新历史表。 | 三类历史结构/API 测试、build | **历史列表布局 MANUAL GUI PASS（第三轮确认）；真实数据、文件与分页待复验** |

本轮仅修复上述四类问题；业务方需在隔离管理端重新人工确认第九域、点击/键盘行为、草稿切换、三类历史及详情。自动测试、真实 API 与 Codex 浏览器检查均不得替代业务方 **MANUAL GUI PASS**。R7-V3-R-UI 保持待验收，不提交、不关闭。

第二轮验证：原隔离测试库 ADMIN 的正式 `/me` 修复前为 67 项权限、零项 `charging:*`，补齐该库 baseline 权限后为 70 项权限，含 `charging:company:manage`、`charging:import`、`charging:view`；未修改非测试库权限。管理员登录 5176 后，Codex 受控 Chrome 页面实际显示九个一级菜单，且“导入中心”下实际显示“充电订单导入”“充电公司模板配置”。随后发现该旧隔离库缺少 `charging_company` 表，充电页面会报“系统异常”。为避免补丁式补旧库，另从当前唯一 `database/database.sql` 全新初始化 `fleet_ops_v3r_ui2_20261013045339`（53 表、三项 charging 权限、仅测试管理员），并只把 5176 对应的 8082 后端切到新库；5175/8080 原服务和旧库保留。新库正式登录、`/me`、充电公司列表、充电导入历史 API 均成功。切库后原浏览器令牌失效，业务方需要在 5176 重新登录；充电历史真实 GUI 数据与业务方复验仍未记 PASS。管理端完整测试 PASS，build PASS（85 模块；大 chunk 警告）；后端审批/充电/安全定向回归 PASS。

Codex 受控 Chrome 功能检查（不计业务方人工 PASS）：普通“调度中心”整行点击可展开、键盘 Enter 可收起；“我的申请”箭头展开费用申请时当前路由不变，点击主体才进入申请记录；DAILY 新增未提交行后切到 TASK 出现确认 Dialog，切回 DAILY 后原行仍在，验证行随后删除且未提交。三类历史页面的真实业务数据与最终视觉仍待业务方复验。

切至完整新隔离库并由业务方重新登录 5176 后，Codex 受控 Chrome 再次检查：管理员左侧实际有九个一级菜单，导入中心位于经营与结算和系统设置之间，展开后只有充电订单导入、充电公司模板配置；两项页面可进入。任务导入、轮胎入库、充电订单导入各自的“导入历史”页签均能打开，呈现中文表头、空态和分页，不再报系统异常。此段记录的是当时空库的环境检查；随后第三轮已准备真实批次并核对 API/受控浏览器，业务方也已确认四项历史列表布局 MANUAL GUI PASS。真实数据、文件追溯及本轮新交互仍待业务方复验。

## 范围与基线

- 整改前及本次导航统一开始时 HEAD：`780ff306c5112c5444a1eb3c519f937ac5f5de2e`。正式业务规则只取 `AGENTS.md` 与 `docs/01～07 V3.0`。本次在已有未提交 R7-V3-R-UI 修改上增量实施，没有重置工作区。
- 本轮只调整管理端导航、页面呈现和交互，并为权限刷新、司机全量筛选复用现有接口链作最小只读支撑；未新增第二套审批、费用、工资或经营写入流程。
- 本机 `application.yml`、`project.config.json` 与 `.runtime/` 均保持在提交范围之外。没有提交代码。
- 人工 GUI 仍为 **待业务方验收**。本报告中的测试、构建和 API PASS 均不等于人工 GUI PASS，也不代表 V3-R 整体通过。

## 八项已知问题

| 问题 | 整改结果 | 主要修改 | 自动/接口验证 | 人工 GUI |
| --- | --- | --- | --- | --- |
| 审批新增入口重复 | “发起普通审批”只在“我的申请”列表出现；费用申请主菜单移至“审批中心→我的申请”，换胎仍在小程序 | `ApprovalCenterV3View.vue`、`ExpenseApplicationV3View.vue`、`navigationModel.js` | 管理端测试通过；单一 `/approvals` 写链静态复查 | 待验收 |
| 操作日志英文 | 当前业务动作逐项映射为中文；原始动作代码保留在详情 | `AuditLogsUi2.vue` | 管理端测试/build 通过 | 待验收 |
| 导航展开后不能滚动 | 侧栏菜单独立纵向滚动，品牌和账号区不随菜单滚动；当前路由父级自动展开 | `AppLayout.vue`、`style.css` | build 通过 | MANUAL GUI PASS：全部展开与独立滚动；1366×768 另待验收 |
| 整体界面不友好 | 统一九域导航、共享页面容器和宽表内部滚动；“我的待办”两处入口复用同一组件和数据；司机列表改为全量服务端筛选/分页并显示业务车牌 | `App.vue`、`routes.js`、`navigationModel.js`、`MyTodosV3View.vue`、`DriverManagementSample.vue`、`MasterDataUi2View.vue`、`style.css` | 管理端测试/build、司机 API 定向测试通过 | 新九域结构待复验 |
| 导航划分混乱 | 九域及三级层级按最终导航调整；导入中心成为一级域，只保留充电订单导入和充电公司模板配置；三类导入历史均在各自业务页内 | `routes.js`、`navigationModel.js`、`AppLayout.vue`、`style.css` | 路由结构、权限过滤及页面内历史入口测试通过 | 原八域/滚动/高亮人工已确认；本次新层级 **NOT RUN，待复验** |
| 费用页面不规范 | 费用台账常用筛选与更多筛选分层，保留服务端分页、汇总和车牌匹配；补全临时气费筛选；DAILY 混合明细、TASK 单笔、充电只读预览保持原链 | `ExpenseLedgerV3View.vue`；复核现有 `ExpenseApplicationV3View.vue`、`ChargingImportsV3View.vue` | 管理端测试/build、小程序回归通过 | 待逐页验收 |
| 同步权限没有作用 | 账号菜单明确改为“刷新我的权限”；`/me` 从数据库重新读取角色权限并更新现有会话，失败明确提示，不显示假成功 | `AppLayout.vue`、`AuthService.java`、`TokenAuthenticationService.java` | H2 定向测试、隔离 MySQL 正式 API 授权→刷新→访问及恢复→刷新通过 | 待按钮真实点击 |
| 缺少修改密码 | 账号菜单加入当前密码/新密码/确认密码表单，复用已有正式 `PUT /auth/password`；成功后退出并提示重新登录，和管理员重置他人密码分离 | `AppLayout.vue`、`LoginView.vue`、`modules.js` | 当前密码错误拒绝、成功后旧会话失效的后端测试通过 | 待真实点击 |

## 逐项人工 GUI 复验记录

| 项目 | 测试环境与操作 | 人工结果 | 后续范围 |
| --- | --- | --- | --- |
| 1. 九域导航、三级菜单、侧栏滚动与路由状态 | 业务方此前在本地管理端实际登录，确认旧八域、独立滚动及旧三级层级；本次改为九域、重排运输/审批/经营层级及导入中心 | 旧范围 **MANUAL GUI PASS**（业务方回复 PASS）；本次新导航 **NOT RUN** | 必须重新人工核对九域顺序、父级可点击/独立展开、历史所在页、权限过滤和刷新/后退高亮；1366×768、1920×1080 另待最终整体检查 |
| 2. 审批新增入口唯一性 | 待执行 | **NOT RUN** | 待业务方操作 |
| 3. 操作日志中文与原始代码 | 待执行 | **NOT RUN** | 待业务方操作 |
| 4. 费用页面规范 | 待执行 | **NOT RUN** | 待业务方操作 |
| 5. 刷新我的权限 | 待执行 | **NOT RUN** | 待业务方操作 |
| 6. 修改本人密码 | 待执行 | **NOT RUN** | 待业务方操作 |
| 7. 整体界面与页面交互 | 待执行 | **NOT RUN** | 待业务方操作 |
| 8. 正式可达页面整体一致性 | 待执行 | **NOT RUN** | 待业务方操作 |

上述项目按顺序逐项交付，未执行项目不以自动测试、API 或构建结果代替人工 GUI 验收。

本轮费用导入业务决定已同步正式基线：不恢复旧通用 EXPENSE 导入，不开放“其它费用导入”；普通 DAILY Excel 仅辅助审批申请填表，TASK 只可单笔，只有充电公司专项允许独立确认后直接入统一 ExpenseEntry。`docs/05` 的泛化“批准来源费用数据导入”与 `docs/06` 的 `expense:import:submit` 已删除；未来第三方来源须另行确认。正式导航与权限展示进一步同步至 `docs/05～07`；未修改审批入账、充电入账或财务计算代码。

## 正式可达页面整改矩阵

### 最终导航迁移与人工复验矩阵

下表中的自动验证只证明结构和权限逻辑；本次调整后的菜单均为 **MANUAL GUI NOT RUN**，待业务方逐项打开确认。任务、轮胎和充电历史记录没有删除。

| 页面或入口 | 旧菜单位置 | 新菜单位置 | 修改文件 | 复用原页面 | 自动验证 | 人工 GUI |
| --- | --- | --- | --- | --- | --- | --- |
| 货物管理、线路模板 | 运输管理末尾、原二级 | 运输管理首位、第二位 | `navigationModel.js` | 是 | 导航测试 PASS | NOT RUN |
| 调度工作台 | 运输管理二级 | 运输管理→调度工作台→批量调度 | `routes.js`、`navigationModel.js`、`AppLayout.vue` | 是 | 导航测试 PASS | NOT RUN |
| 任务导入、运输任务 | 运输管理分散二级 | 运输管理→调度工作台三级 | `navigationModel.js`、`AppLayout.vue` | 是 | 导航测试 PASS | NOT RUN |
| 自用/外协车辆月度台账 | 运输管理→调度中心 | 同位置，原“自有”名称改“自用” | `routes.js`、`navigationModel.js` | 是 | 导航测试 PASS | NOT RUN |
| 司机/车辆/车挂/绑定、轮胎库存/入库 | 车辆与人员 | 同域且原层级，历史留轮胎入库页 | `navigationModel.js` | 是 | 导航测试 PASS | NOT RUN |
| 费用台账、费用归属 | 费用管理 | 同域，移除费用申请与导入中心并列项 | `navigationModel.js` | 是 | 导航测试 PASS | NOT RUN |
| 流程与模板配置 | 审批中心末尾 | 审批中心首位 | `navigationModel.js` | 是 | 导航测试 PASS | NOT RUN |
| 我的待办 | 工作台独立页；审批中心原“待我处理”页 | 两处同名入口，共用正式待办组件和数据；审批处理继续进入原审批页 | `routes.js`、`navigationModel.js`、`MyTodosV3View.vue` | 是 | 导航/组件引用测试 PASS | NOT RUN |
| 我的申请、费用申请 | 审批中心申请记录；费用管理申请页 | 审批中心→我的申请（可点击记录、独立展开）→费用申请 | `navigationModel.js`、`AppLayout.vue`、`style.css` | 是 | 父级权限/路径测试 PASS | NOT RUN |
| 审批记录 | 审批中心 | 同域，最后一项 | `navigationModel.js` | 是 | 导航测试 PASS | NOT RUN |
| 司机出勤、工资台账、转派工资核对 | 工资与考勤原顺序 | 工资与考勤新顺序 | `navigationModel.js` | 是 | 导航测试 PASS | NOT RUN |
| 公司/模板/任务经营 | 经营与结算平铺 | 经营与结算→经营三级 | `navigationModel.js`、`AppLayout.vue` | 是 | 导航测试 PASS | NOT RUN |
| 车辆/司机/能源支出 | 经营与结算平铺 | 经营与结算→支出三级 | `navigationModel.js`、`AppLayout.vue` | 是 | 导航测试 PASS | NOT RUN |
| 充电订单导入、模板配置 | 费用管理→导入中心 | 独立一级导入中心二级 | `routes.js`、`navigationModel.js`、`ChargingImportsV3View.vue` | 是 | 只读权限过滤测试 PASS | NOT RUN |
| 任务/轮胎/充电导入历史 | 任务和轮胎原页；充电有独立菜单 | 各导入页内历史 Tab，无统一菜单 | `routes.js`、`navigationModel.js` | 是 | 页面内历史入口/别名测试 PASS | NOT RUN |
| 用户管理、角色权限、操作日志 | 系统设置 | 同域末尾，充电模板不重复 | `navigationModel.js` | 是 | 导航测试 PASS | NOT RUN |

正式 V3.0 同步文件：`docs/05`（九域层级、页面归属与导入历史）、`docs/06`（菜单权限与后端鉴权边界）、`docs/07`（导航与权限验收用例）。旧 `#task-import-history`、`#charging-history` 深链只重定向至对应唯一业务页，不保留独立写入页面。

“共享收口”表示页面继续复用原有 V3 正式业务组件，本轮由 `routes.js`、`AppLayout.vue`、`style.css` 的布局、权限导航和表格滚动规则覆盖；没有重写该页业务请求。各页面人工 GUI 状态以逐项复验记录为准。

| 页面 | 最终菜单位置 | 原问题与本轮动作 | 自动验证 | 人工 GUI |
| --- | --- | --- | --- | --- |
| 经营概览 | 工作台 | 标题与系统名统一；修复待办跳转 | test/build | 待验收 |
| 我的待办 | 工作台 | 新增基于现有审批/经营只读 API 的权限分流页，不新增待办事实 | test/build | 待验收 |
| 批量调度 | 运输管理 / 调度工作台 | 原调度工作台页面复用；保留一行一任务与整批原子确认 | test/build | 待验收 |
| 运输任务 | 运输管理 / 调度工作台 | 共享收口；保留发布、发车、转派、重量证据正式链 | test/build | 待验收 |
| 线路模板 | 运输管理 | 共享收口；保留模板默认值与启停 | test/build | 待验收 |
| 任务导入 | 运输管理 / 调度工作台 | 共享收口；固定 Excel 正式导入和页面内历史入口不变 | test/build | 待验收 |
| 自用车辆月度台账 | 运输管理 / 调度中心 | 第三级导航；共享宽表内部滚动 | test/build | 待验收 |
| 外协车辆月度台账 | 运输管理 / 调度中心 | 第三级导航；共享宽表内部滚动 | test/build | 待验收 |
| 货物管理 | 运输管理 | 从基础资料移至运输业务域 | test/build | 待验收 |
| 司机管理 | 车辆与人员 | 前 100 条截断改为服务端全量筛选、分页；车辆显示车牌 | test/build、后端定向测试 | 待验收 |
| 车辆管理 | 车辆与人员 | 共享列表/详情/编辑规范 | test/build | 待验收 |
| 车挂管理 | 车辆与人员 | 共享列表/详情/编辑规范 | test/build | 待验收 |
| 绑定关系 | 车辆与人员 | 共享收口；保留正式绑定 API | test/build | 待验收 |
| 轮胎库存 | 车辆与人员 / 轮胎管理 | 第三级导航；Web 仍无换胎申请入口 | test/build | 待验收 |
| 轮胎入库 | 车辆与人员 / 轮胎管理 | 第三级导航；固定四字段导入与页面内历史保留 | test/build | 待验收 |
| 费用申请 | 审批中心 / 我的申请 | 维持 DAILY 混合明细、TASK 单笔；修复成功后“我的申请”跳转 | test/build | 待验收 |
| 费用台账 | 费用管理 | 筛选分层；服务端汇总、分页与详情保留 | test/build | 待验收 |
| 费用归属 | 费用管理 | 复用费用台账正式归属 API；错车后端硬拒绝规则未改 | test/build | 待验收 |
| 充电订单导入 | 导入中心 | 共享收口；只读预览、零元来源、页面内历史和原文件入口保留；仅查看权限也可进入历史 | test/build | 待验收 |
| 我的待办 | 审批中心 | 复用工作台同一待办页与数据，审批待办可继续进入正式审批处理 | test/build | 待验收 |
| 我的申请 | 审批中心 | 可点击查看申请记录，独立展开费用申请子菜单；普通审批唯一发起位置 | test/build | 待验收 |
| 审批记录 | 审批中心 | 移除重复发起入口 | test/build | 待验收 |
| 流程与模板配置 | 审批中心 | 共享收口；指定步骤仍为合法 FlowNode 选择 | test/build | 待验收 |
| 工资台账 | 工资与考勤 | 共享收口；Participant 工资读取路径不变 | test/build | 待验收 |
| 转派工资核对 | 工资与考勤 | 独立菜单入口，复用同一 Participant 核薪链 | test/build | 待验收 |
| 司机出勤 | 工资与考勤 | 共享月度宽表内部滚动 | test/build | 待验收 |
| 任务经营 | 经营与结算 / 经营 | 共享收口；不在前端另算财务值 | test/build | 待验收 |
| 线路模板经营 | 经营与结算 / 经营 | 共享收口；不在前端另算财务值 | test/build | 待验收 |
| 公司经营 | 经营与结算 / 经营 | 共享收口；不在前端另算财务值 | test/build | 待验收 |
| 车辆支出 | 经营与结算 / 支出 | 复用统一费用台账筛选与汇总 | test/build | 待验收 |
| 司机支出 | 经营与结算 / 支出 | 复用统一费用台账筛选与汇总 | test/build | 待验收 |
| 能源费用 | 经营与结算 / 支出 | 补临时气费选择；复用费用台账 | test/build | 待验收 |
| 月度结算 | 经营与结算 | 共享收口；历史版本和差异仍只读 | test/build | 待验收 |
| 用户管理 | 系统设置 | 共享收口；管理员重置他人密码与本人修改密码区分 | test/build | 待验收 |
| 角色权限 | 系统设置 | 保存后提示关联用户刷新权限；服务器 `/me` 可取得最新权限 | test/build、正式 API | 待验收 |
| 充电公司模板配置 | 导入中心 | 共享收口；四字段映射规则未改；无重复菜单 | test/build | 待验收 |
| 导入历史 | 各对应业务页面内 | 取消统一菜单；任务、轮胎、充电页面继续复用原历史 API 和组件 | test/build | 待验收 |
| 操作日志 | 系统设置 | 中文动作名称；原始代码在详情保留 | test/build | 待验收 |

## 验证记录

| 项目 | 结果 |
| --- | --- |
| 当前启用后端全量测试 | `81 tests, 0 failures, 0 errors, 1 skipped`；跳过项为充电订单的条件集成测试，不列为本轮 UI PASS |
| 后端标准 `mvn -DskipTests package` | PASS；先确认并停止锁定标准 JAR 的旧本仓库后端进程，再以标准产物打包 |
| 管理端完整测试 / build | PASS；九域顺序、三级层级、父级可点击、双待办复用、历史归页、权限过滤与旧链接别名测试通过；Vite 84 模块构建成功，大 chunk 为构建警告 |
| 必要后端定向回归 | `V3ApprovalWorkflowTests`、`V3ChargingImportTests`、`SecurityResponseTests` 共 17 项，0 failures、0 errors、1 skipped；skipped 是需特定条件的充电集成项，不记 PASS |
| 小程序当前启用回归 | `38 tests PASS`；本轮未改小程序业务 |
| 隔离 MySQL / 正式 API | 测试库名称符合 `fleet_ops_v3r_YYYYMMDDHHMMSS`；后端健康 `UP`；专用只读角色权限授予后 `/me` 刷新及授权访问 PASS，恢复原权限后 `/me` 撤回 PASS。司机筛选真实 API 仅完成空列表只读探测，库内暂无司机样本；非空筛选由 H2 合同测试覆盖，不能记作真实 MySQL 非空场景 PASS |
| 管理端测试地址 | 本次导航整改结束再次只读检查：`http://127.0.0.1:5175/` 响应 200；`http://127.0.0.1:8080/actuator/health` 响应 200。该检查不等于人工 GUI PASS |
| `git diff --check` | PASS；仅出现本机 LF/CRLF 提示，无空白错误 |
| Secret / 本机配置 | 本次导航源码与报告的高风险 Secret 模式扫描 0 命中；暂存区为空；本机配置差异未暂存；`.runtime/` 未纳入版本控制 |
| Word 结构 | `docs/05～07` 均可解析、ZIP CRC 正常；`docs/05` 导航表为九域。环境未发现 `soffice.exe`，逐页视觉渲染 NOT RUN / NON-BLOCKING |

## 局部四项唯一性复审

- **需求唯一：**只依据 V3.0 正式文档与本轮 UI 任务重排信息架构；未采用历史评审稿覆盖业务规则。
- **业务唯一：**普通审批仍走唯一 `/approvals`，DAILY/TASK 费用仍走原申请链，充电订单仍走专项确认链，任务/工资/经营/轮胎没有新写入口。
- **数据唯一：**新增“我的待办”只读现有审批/经营 API；司机列表筛选只读取 `driver` 及正式车辆绑定视图；没有第二套待办、工资、费用或经营事实表。
- **代码唯一：**导入中心仅通向现有充电公司配置和充电订单确认；其历史只在充电订单导入页，任务与轮胎历史也留各自业务页。旧跨业务分流页已撤下，未新增写 API。静态复查未发现旧 `/routes`、`targetNodeOrder`、`VOIDED`、旧费用直写或旧工资 CRUD 重新进入正式管理端。

## 待验收与结论

- **剩余已知代码 BLOCKER：**无。
- **人工 GUI 闸门：**未通过、未记整体 PASS。原八域导航已人工确认；本次九域导航、独立一级导入中心、审批“我的申请”可点击且可展开、双待办、三种页面内导入历史均须重新人工验收。业务方还需在 1366×768 与 1920×1080 逐页检查权限过滤、审批唯一发起、审计中文与原始代码、费用申请/台账/归属及充电导入、权限刷新、修改密码和横向宽表。最初 41 张截图的逐张索引未在本轮工作区提供，不能声称逐张视觉对照完成。
- **NON-BLOCKING：**Vite 产物超过 500 kB 的提示；充电条件集成测试跳过；司机非空筛选的真实 MySQL 样本待业务全链测试数据建立后补验；本机缺少 `soffice.exe`，本轮正式 Word 的逐页视觉渲染 **NOT RUN**，结构、术语与交叉引用已通过程序检查。
- **R7-V3-R-UI 状态：**代码整改与自动验证完成，**待人工 GUI 验收**。不得据此将 V3-R 判为通过。

## UI-B1 全局布局、导航与公共组件增量实施记录（2026-10-13）

本轮开始时 HEAD 为 `780ff306c5112c5444a1eb3c519f937ac5f5de2e`。原有 V3-R-UI 未提交修改、本机 `application.yml`、`project.config.json`、`.runtime/` 和历史未跟踪文档均保留；本轮没有重置或提交。业务规则继续以 `AGENTS.md + docs/01～07 V3.0` 为准，`docs/36` 只用作 UI 设计尺寸与状态依据。

| UI-B1 范围 | 实际处理 | 主要文件 | 结果与待验收 |
| --- | --- | --- | --- |
| 九域与三级导航 | 将九域树、二级节点和三级叶子交给同一 `NavigationTree` 渲染；层级缩进、祖先展开、唯一叶子高亮、独立侧栏滚动和键盘按钮统一 | `admin-web/src/router/navigationModel.js`、`router/routes.js`、`components/NavigationTree.vue`、`layouts/AppLayout.vue`、`style.css` | 自动测试 PASS；5176 实际登录管理员可见九域。人工 GUI 仍 NOT RUN |
| 审批中心 | “我的申请”成为只展开的父级，“申请记录”“费用申请”复用原有两条路由及页面；面包屑显示完整层级，普通审批写入链未复制 | 同上、`ApprovalCenterV3View.vue`、正式 `docs/05～07` | 实际浏览器可见两个三级叶子，点击申请记录进入 `#/approvals-mine`；人工 GUI 待复验 |
| 全局骨架 | 232px 固定品牌侧栏、64px 顶栏、最大 1520px 内容区、墨绿/白/浅灰令牌、页面标题和局部宽表滚动规则收敛 | `design/tokens.css`、`style.css`、`AppLayout.vue` | 真实运行页面已观察；精确 1366×768 / 1920×1080 视口检查尚未完成，不能记 PASS |
| 公共组件 | 复用 PageHeader、FilterBar、DataTable、PaginationBar、DetailDrawer、FormDialog、ConfirmDialog、ImportTabs 和三态组件；增加轻量 SummaryCards/FormSection；统一空表、加载、禁用提交与弹窗 busy 关闭行为。业务页仅以经营概览作接入样例，其他业务页留 UI-B2～B5 逐步接入 | `components/DataTable.vue`、`PaginationBar.vue`、`FormDialog.vue`、`ConfirmDialog.vue`、`SummaryCards.vue`、`FormSection.vue`、`DashboardV3View.vue`、`style.css` | 管理端测试/build PASS；业务数据/API 结构未改 |
| 顶部账号 | 保留真实 `/me` 刷新权限、本人改密 API、退出；统一菜单样式，并使 Esc / 点击外部关闭 | `AppLayout.vue`、`style.css` | 5176 实际浏览器交互已观察；未改动真实密码，不把只读检查写成改密成功 |

自动验证：管理端完整 `npm test` PASS（13 项当前核心、5 项 UI1、重量证据及充电工作簿测试）；`npm run build` PASS（91 modules；749.50 kB chunk 提示为 NON-BLOCKING）；后端当前启用 `mvn test` 82 tests、0 failures、0 errors、1 skipped，BUILD SUCCESS。当前阶段没有后端业务代码新增。`git diff --check` 与空暂存区的 `git diff --cached --check` 均 PASS（仅 LF/CRLF 提示）；UI-B1 相关源码和报告的高风险 Secret 模式扫描 0 命中。

真实运行检查使用隔离测试管理端 `http://127.0.0.1:5176/`，管理员已登录。Computer Use 从实际运行的 Chrome 页面截取并保存五张证据到本机忽略目录 `.runtime/ui-b1-screenshots/`：`nine-domains.jpg`、`approval-navigation.jpg`、`third-level-tire.jpg`、`account-menu.jpg`、`table-filter.jpg`。它们分别覆盖九域、审批三级选中、轮胎三级路径、账号菜单、带两条测试数据的费用台账筛选与表格；均不是设计稿，也不纳入提交。该工具本轮取得的常规窗口截图为 2327×1104，未提供可靠的显式视口尺寸设置，因此 **1366×768、1920×1080 精确尺寸检查 NOT RUN**，不得据常规窗口截图推断这两档已通过。管理端人工 GUI 总闸门保持 NOT RUN / 待业务方复验。修改的正式 Word 可解析且 ZIP CRC 正常；本机未找到 LibreOffice `soffice.exe`，逐页视觉渲染 NOT RUN / NON-BLOCKING。

UI-B2 前置条件：业务方确认 UI-B1 的九域、审批纯父级、账号菜单及两档分辨率真实效果；随后按 `docs/36` 的页面矩阵逐批迁移公共组件。UI-B1 未改变运输、审批、费用、工资、经营、月结、导入的写入规则，未增加第二套 API/Service/数据事实。R7-V3-R-UI 和 V3-R 均未标记 PASS。

## UI-B2 运输管理与车辆人员增量实施记录（2026-10-13）

基线 HEAD 仍为 `780ff306c5112c5444a1eb3c519f937ac5f5de2e`。在 UI-B1 及前序整改的未提交工作区增量修改，没有 reset、clean、提交，也没有修改后端业务代码、数据库、OpenAPI、本机配置或小程序。实施依据为 `AGENTS.md + docs/01～07 V3.0`；`docs/36` 仅指导页面布局。继续沿用 UI-B1 的 PageHeader、FilterBar、DataTable、PaginationBar、DetailDrawer、FormDialog、StatusTag、EmptyState、ErrorState、EntityPicker 和 ImportTabs。

| 页面与路由 | UI-B2 实际处理 | 主要修改文件 | 自动 / 运行检查 | 人工 GUI |
| --- | --- | --- | --- | --- |
| 批量调度 `#/dispatch` | 模板客户、货物、路径与三项默认金额形成摘要；司机卡片突出当前车牌、可读取时显示当前车挂；待发布行编号、批量金额区和底部整批确认区更清楚。只生成前端草稿，预览/发布仍调用既有事务与幂等 API | `DispatchWorkbenchV3View.vue` | 管理端 test/build PASS；5176 实际页面用现有测试模板、司机加入 1 条未发布草稿并截屏，未正式发布 | NOT RUN |
| 运输任务 `#/routes` | 列表优先呈现状态与详情操作，补模板摘要、有效净重及按日期/状态/模板/参与司机/任务号筛选；详情改为基本信息、参与司机与工资、重量与磅单、费用与经营四个分区，保留原重量证据及纠错操作权限 | `TransportTasksV3View.vue` | 管理端 test/build PASS；5176 打开真实测试任务，基本信息、参与人与重量分区可切换 | NOT RUN |
| 线路模板 `#/templates` | 列表加入货物、装卸路径、三项默认金额、状态与紧凑筛选；新增/编辑和启停继续使用原正式 API | `TransportTasksV3View.vue` | 管理端 test/build PASS，5176 列表可见 | NOT RUN |
| 货物 `#/products` | 复用已有货物筛选、列表、新增/编辑弹窗及启停确认；未加新业务字段 | `MasterCatalogUi2.vue` 复核、`MasterDataUi2View.vue` 顶部结构收口 | 管理端 test/build PASS，5176 页面可见 | NOT RUN |
| 任务导入 `#/task-imports` | 沿用已通过的公共页签、固定模板上传、只读逐行预览、正式确认、批次历史和原文件下载；本轮未改导入语义 | `TaskImportsV3View.vue` 复核 | 管理端 test/build PASS，5176 页面可见；本轮未重复真实文件提交 | NOT RUN |
| 自用/外协月度调度 `#/dispatch-internal`、`#/dispatch-outsourced` | 统一月份、发车次数摘要、日期宽表和 Excel 操作；宽表保持局部横向滚动及首列固定；页面标题与导航“自用”一致 | `MonthlyOperationsV3View.vue` | 管理端 test/build PASS；当前月 5176 为空态，真实发车次数/导出交叉核验本轮 NOT RUN | NOT RUN |
| 司机 `#/drivers` | 移除外层重复 PageHeader，保留服务端分页筛选、类型、当前业务车牌、详情及新增弹窗 | `MasterDataUi2View.vue`，复用 `DriverManagementSample.vue` | 管理端 test/build PASS；5176 有 1 条真实测试司机 | NOT RUN |
| 车辆 `#/vehicles` | 列表直接呈现能源、限载、达标配置、保险、当前司机/车挂和状态；详情分车辆资料、当前关系及已有绑定历史 | `MasterCatalogUi2.vue` | 管理端 test/build PASS；5176 有 1 条真实测试车辆 | NOT RUN |
| 车挂 `#/trailers` | 保留业务车挂表、详情及编辑，不把当前车辆写成历史事实 | `MasterCatalogUi2.vue` 复核 | 管理端 test/build PASS，5176 页面可见 | NOT RUN |
| 绑定关系 `#/binding` | 两类绑定表上方增加明确的当前页关系摘要；继续使用业务选择器、先解绑确认、原 API 校验和历史时间线 | `BindingManagement.vue` | 管理端 test/build PASS；5176 有 1 条真实测试绑定 | NOT RUN |
| 轮胎库存 `#/tires` | 增加库存总数与当前页状态摘要；维持筛选、四字段、详情、授权维护与历史，Web 无换胎申请 | `TiresUi2View.vue` | 管理端 test/build PASS；5176 有 1 条真实测试轮胎 | NOT RUN |
| 轮胎入库 `#/tire-imports` | 复用已有固定四列上传/只读预览、批次历史、详情与原文件下载；未改入库规则 | `TireImportsV3View.vue` 复核 | 管理端 test/build PASS，5176 页面可见；本轮未重复真实文件提交 | NOT RUN |

自动验证：管理端完整 `npm test` PASS（13 项当前核心、5 项 UI1、重量证据与充电工作簿用例）；`npm run build` PASS（94 modules，单 chunk 超过 500 kB 为 NON-BLOCKING 警告）；当前启用后端 `mvn test` 为 **82 tests、0 failures、0 errors、1 skipped，BUILD SUCCESS**，包含运输、资料、绑定和轮胎回归。该 skipped 项仍不得写成通过。前端 UI-B2 没有新增正式写 API；浏览器检查只读取现有 5176 隔离测试库并在调度页面添加未提交的本地草稿，未制造正式任务或修改测试数据。

最终变更后重新运行管理端完整 `npm test` 与 `npm run build`，均 PASS。`git diff --check` 退出码 0（只有现有 LF/CRLF 提示，无空白错误）；UI-B2 涉及的 7 个 Vue 文件及本报告扫描高风险私钥、常见云密钥和长令牌模式为 0 命中文件；未暂存任何文件，本机配置及 `.runtime/` 仍不进入提交范围。

实际运行截图保存于忽略目录 `.runtime/ui-b2-screenshots/`：`dispatch-1366.png`、`dispatch-1920.png`、`tasks-1366.png`、`tasks-detail-1920.png`、`vehicles-1366.png`、`drivers-1366.png`、`binding-1366.png`、`tires-1366.png`、`monthly-internal-1366.png`、`monthly-internal-1920.png`，另含模板、货物、车挂、任务导入、轮胎入库及外协月表的 1366 截图。均来自实际运行的 `http://127.0.0.1:5176/` 管理端，不是设计稿。使用明确的 1366×768 与 1920×1080 浏览器视口进行页面布局检查；截图只能证明自动检查所见，**人工 GUI 仍 NOT RUN**。当前月月表无发车记录，不能声称有数据月表及 Excel 内容已验收。

已确认的正式 API/模型展示限制，不制作假数据或假按钮：当前 `RouteTemplateResponse` 无“去返程”独立字段，只能显示装货地→卸货地；现有司机 API 无司机资料编辑接口，司机页不制作无效编辑操作；现有车挂绑定历史只提供按**车辆**查询的端点，车挂详情无法完整查询跨车辆的历史；运输任务没有独立操作历史读取接口，页面只展示当前状态、正式参与、重量与费用事实。这些需求与现有契约的差距需在后续正式 API 决策中处理，不能在本轮 UI 改造中假造。若业务方要求这四项为 UI-B2 闸门，则相应人工验收保持待定。

局部四项唯一性复审：需求仍以 V3.0 为准；批量调度、任务导入继续调用各自唯一入口及同一服务端任务校验；模板、任务、Participant、绑定、WeightVersion、轮胎库存的权威数据未改变；未新建 Controller、Service、Repository、SQL、状态机或第二套写入路径。运输列表的金额仍取正式经营 API，司机工资仍读 Participant。`VOIDED`、旧 `/routes` 写入口、旧通用导入及 Web 换胎申请没有因 UI-B2 重新出现。

UI-B2 待业务方逐页人工复验：批量调度的草稿/行错/整批确认、运输任务四分区、模板启停与货物维护、两类月表有数据时的滚动/导出、司机/车辆/车挂/绑定、轮胎库存与两个导入页。R7-V3-R-UI 与 V3-R **均未标记 PASS**；不提交，不开始 UI-B3。

## UI-B3 费用、审批与导入中心增量实施记录（2026-10-13）

本轮从 HEAD `780ff306c5112c5444a1eb3c519f937ac5f5de2e` 和已有 UI-B1/B2 未提交工作区继续实施，没有 reset、clean、暂存或提交。业务规则以 `AGENTS.md + docs/01～07 V3.0` 为准，`docs/36` 仅提供 UI 尺寸和页面矩阵。页面改动限于 `ExpenseLedgerV3View.vue`、`ApprovalCenterV3View.vue`、`MyTodosV3View.vue`、`ChargingImportsV3View.vue`；另在 `SecurityResponseTests.java` 补充原文件权限负向用例，并更新本报告。费用申请页已具备本轮要求的结构，经过源码与运行页面复核，未为改动而重复实现。

| 页面 | 本轮处理或复核 | 正式数据/操作链 | 实际运行截图 | 人工 GUI |
| --- | --- | --- | --- | --- |
| 费用台账 | 常用筛选、服务端费用净额/已归属/未归属/冲销汇总卡、明细表与详情层级统一；金额和导出仍用正式 API | `v3ExpenseLedgerApi.list/summary/detail/export`；四张汇总卡均来自服务端 `summary`，没有前端重算经营事实 | `.runtime/ui-b3-screenshots/expense-ledger-1366.png`、`expense-ledger-1920.png` | NOT RUN |
| 费用归属 | 保留待归属 DAILY 默认筛选，详情操作改为“查看与归属”；确认弹窗展示费用日期、类型、金额、车牌及当前归属，候选任务仍按费用车辆查询，确认时服务端复验 | 原 `options('TASK',vehicleId)`、`attribute` 与归属历史；未新增写入 | `expense-attribution-1366.png`、`expense-attribution-dialog-1366.png` | NOT RUN |
| DAILY/TASK 费用申请 | 复核现有“申请信息→费用明细→汇总→核对提交”；申请类型为下拉，DAILY 手工/Excel 行混编可编辑，TASK 仅一笔且无 Excel；真实浏览器切换时未提交行触发保留草稿确认 | 原 `v3ApprovalApi` 和唯一 `/approvals` 提交链；本轮未改 | `daily-application-1366.png`、`task-application-1366.png` | NOT RUN |
| 审批待办、申请记录、审批记录与详情 | 待办页在原业务卡片下补正式 `TODO` 分页表格；申请/记录列表补业务标题、范围说明和刷新；详情把业务类型、状态、冻结流程和当前步骤分组，时间线及合法打回按钮保留 | 原 `v3ApprovalApi.list/detail/process`，工作台与审批中心待办仍复用 `MyTodosV3View` 和同一 `TODO` API | `my-todos-1366.png`、`approval-detail-1366.png` | NOT RUN |
| 流程与模板配置 | 已发布版本的业务步骤改为有序步骤卡，标明冻结版本；未更改 FlowVersion 和步骤写入 | 原 `templates/flow/createTemplate/publish` | `approval-flows-1366.png` | NOT RUN |
| 充电订单导入 | 上传、只读预览、确认及历史仍为同一正式页面的两个页签；预览增加有效候选、正金额、零元、异常、金额及“重复待服务端核对”的业务摘要，避免伪造预览重复数 | 原 `chargingApi`；确认前不保存文件、不产生 ExpenseEntry，零元和去重仍由正式后端处理 | `charging-import-1366.png` | NOT RUN |
| 充电公司模板配置 | 现有独立页面、修改方式二选一、四列映射、取消及未保存确认保留；补当前配置与重新匹配提示 | 原 `chargingApi.company*`，不复制导入逻辑，不建版本或动态费用映射 | `charging-template-1366.png`、`charging-template-edit-1366.png` | NOT RUN |

运行环境是隔离管理端 `http://127.0.0.1:5176/`，管理员可见正式路由。实际费用台账有 2 条当月充电费用，页面服务端费用净额 `¥24.68`、未归属 `¥24.68`、已归属 `¥0.00`，与当前筛选表格的两条 `¥12.34` 一致。费用详情显示来源公司、原始订单、Excel 行号和文件入口；归属候选按公司车辆返回 1 条正式任务，本轮只打开选择弹窗，没有提交归属或冲销。为检查审批详情，使用现有正式 UI 在**隔离测试库**创建并发布 `UIB3_GENERAL_TEST` 普通审批模板，再提交一条明确标记 UI-B3 的普通申请；详情真实显示待处理、冻结第 1 版、当前步骤和申请内容，待办页正式表格出现同一审批实例，没有执行通过/打回。测试数据只在隔离库，不影响 5175 或正式业务库。

截图均从运行中的 Chrome 管理端直接取得。考虑浏览器设备像素比约 1.1，本轮用页面实际 `innerWidth/innerHeight` 校准视口，而非仅依据窗口设置值：上述 `-1366.png` 文件实际为 1366×768，`expense-ledger-1920.png` 实际为 1920×1080；截图位于本机忽略目录，不进入提交。布局自动检查不等于业务方人工 GUI 验收。

Chrome Computer Use 的本地文件选择在 `fileChooser.setFiles` 阶段被扩展的“Allow access to file URLs”权限限制拦下；本轮未更改浏览器安全设置，也未执行充电 Excel 的真实 GUI 预览/确认。因此运行截图中的充电导入页是上传前状态；只读预览在自动测试中覆盖，但 **GUI 预览 NOT RUN**。隔离库当前没有费用审批模板，DAILY/TASK 两种页面结构和草稿保护可检查，但费用类正式提交及费用审批详情的有数据 GUI 复验留待业务方准备模板后进行。上述限制不是 GUI PASS。

现有审批列表 API 只提供 `scope + page`，没有服务端业务筛选字段；本轮只使用正式列表和分页，不制作只筛当前页的假筛选。若业务方将待办、申请或审批记录的更多条件筛选列为上线必需，应先在正式 API 契约中补充该展示能力。充电预览中的“已存在重复”同样在确认前只能标示**待服务端核对**，不能用前端猜测的数字冒充正式去重结果。

自动验证：管理端完整 `npm test` PASS（13 项当前核心、5 项 UI1、重量证据及充电工作簿测试）；`npm run build` PASS（95 modules，单 chunk 超过 500 kB 提示为 NON-BLOCKING）。审批、财务和充电后端定向回归 `V3ApprovalWorkflowTests,V3FinanceFactsTests,V3ChargingImportTests`：21 tests、0 failures、0 errors、1 skipped；skipped 为需特定条件的充电集成用例，不记 PASS。`SecurityResponseTests`：3 tests、0 failures、0 errors，其中新增无 `charging:view` 时原文件接口返回 403 的负向用例。本阶段无后端**业务代码**、数据库、OpenAPI 或小程序修改；此前其他阶段未提交改动保持原状。`git diff --check` 退出码 0，仅 Windows LF/CRLF 提示；相关源码和报告的高风险 Secret 模式扫描无命中；未暂存本机配置或 `.runtime/`。

局部四项唯一性：**需求**仍以 01～07 V3.0 为准；**业务**中 DAILY/TASK 费用只经原审批链，充电专项只经原确认链；**数据**中 ExpenseEntry、审批实例、充电来源订单与导入批次仍各有唯一事实，汇总完全取后端；**代码**中本轮未新增 Controller、Service、Repository、写 API、状态机或第二套费用/审批/导入入口。旧通用 EXPENSE 导入未恢复。

UI-B3 剩余人工复验：九个页面状态的 1366×768/1920×1080 实际观感、DAILY Excel 与手工行混编提交、TASK 单笔、审批待办处理/打回、费用归属车辆不匹配拒绝、冲销、充电真实 Excel 只读预览及历史原文件下载。照片为自动浏览器实际运行证据，不能代替业务方人工 GUI PASS。当前无本轮新发现的业务规则 BLOCKER；文件选择权限和缺少费用审批模板限制了本轮部分 GUI 覆盖。UI-B4 前置条件为业务方复验 UI-B1～B3 页面与提供可用费用审批测试模板；R7-V3-R-UI 和 V3-R 均未标记 PASS。本轮停止，不提交，不开始 UI-B4。

## UI-B4 工作台、工资考勤与经营结算增量实施记录（2026-10-13）

继续以 HEAD `780ff306c5112c5444a1eb3c519f937ac5f5de2e` 和现有 UI-B1～B3 未提交工作区为基础；没有 reset、clean、暂存或提交。本阶段仅调整管理端展示与交互，正式业务事实仍以 `AGENTS.md + docs/01～07 V3.0` 为准。`docs/36` 指导尺寸和页面结构，没有修改后端业务规则、数据库 baseline、正式 API 或小程序。

| 页面 | UI-B4 处理及正式数据来源 | 修改文件 | 实际运行截图 | 人工 GUI |
| --- | --- | --- | --- | --- |
| 经营概览 `#/dashboard` | 紧凑展示后端任务、收入、正式费用、Participant 工资、总支出与利润；“我的待办”数量改读与审批中心一致的当前用户 `TODO` 列表；无数据用真实空态，保留业务详情入口 | `DashboardV3View.vue` | `.runtime/ui-b4-screenshots/dashboard-1366.png`、`dashboard-1920.png` | NOT RUN |
| 我的待办 `#/approvals-todo` | 继续复用 UI-B3 的正式审批待办页面及同一 `TODO` API，概览卡只是跳转与同源数量摘要 | 复用 `MyTodosV3View.vue`；本轮没有复制页面/API | `my-todos-1366.png` | NOT RUN |
| 司机出勤 `#/attendance` | 使用服务端月出勤天数；表头按所选月份真实天数显示，仍以 Participant 实际发车日为唯一口径；表格局部横向滚动，保留日期任务详情与 Excel | `MonthlyOperationsV3View.vue` | `attendance-1366.png` | NOT RUN |
| 工资台账 `#/salary-ledger` | 汇总卡读取正式工资 API；转派任务标识、月度司机汇总、明细及 Excel 继续复用原链；任务抽屉按全部 Participant 读取各自工资调整历史 | `SalaryLedgerV3View.vue`、`test/ui1Tests.mjs` | `salary-ledger-1366.png`、`salary-detail-1366.png` | NOT RUN |
| 转派工资核对 `#/salary-transfer` | 独立页面标题与转派筛选，保留全 Participant 集中核薪、0 元工资、原因及服务端并发版本校验；没有第二套工资入口 | `SalaryLedgerV3View.vue` | `transfer-salary-1366.png` | NOT RUN |
| 公司经营 `#/finance-company` | 六项收入、信息费、Participant 工资、正式费用、总支出、利润直接取统一经营 API；增加正式任务、线路和费用明细跳转，没有前端财务重算 | `FinanceReportsV3View.vue` | `finance-company-1366.png`、`finance-company-1920.png` | NOT RUN |
| 线路模板经营、任务经营 | 保留服务端筛选与任务明细；任务详情改以服务端六项汇总和已有参与/费用明细构成业务层级；充电费用来源正确显示“充电公司” | `FinanceReportsV3View.vue` | `finance-task-1366.png` | NOT RUN |
| 车辆支出、司机支出、能源费用 | 继续复用统一费用台账/服务端汇总与导出。司机支出先选司机，避免误把未关联司机的费用呈为其支出；能源费用默认显示当前库有数据的临时电费，仍可切换正式电费/气费类别 | `ExpenseLedgerV3View.vue` | `vehicle-expense-1366.png`、`energy-expense-1366.png` | NOT RUN |
| 月度结算 `#/settlements` | 版本列表明确只读；版本详情从正式结算快照展示收入、信息费、工资、总支出和利润，保留历史差异入口及原生成 API，没有前端改写历史版本 | `SettlementV3View.vue` | `settlement-1366.png`（空版本列表） | NOT RUN |

UI-B1 公共的 PageHeader、FilterBar、SummaryCards、DataTable、PaginationBar、DetailDrawer 和 EmptyState 被上述页面复用；本轮没有再建立公共状态管理或写入链。运行于隔离管理端 `http://127.0.0.1:5176/` 的截图均来自实际 Chrome 页面，并已保存为真实 PNG 于忽略目录 `.runtime/ui-b4-screenshots/`，不进入提交。1366×768 页面检查涵盖上表十类视图；经营概览及公司经营另以实际 1920×1080 视口检查，页面 `scrollWidth` 等于视口宽度，无整页横向溢出。截图与自动布局检查均不等于业务方人工 GUI PASS。

隔离库当前有两条临时电费来源的正式充电费用，各 `¥12.34`；能源费用和车辆支出所见费用净额 `¥24.68`，公司经营显示正式费用 `¥24.68`、收入和工资均 `¥0.00`、总支出 `¥24.68`、利润 `¥-24.68`，与后端同源汇总一致。概览“我的待办”与审批中心均显示当前用户 1 条。当前月没有实际发车、转派工资、已完成任务或 Settlement 版本，因此有数据的出勤日点击、转派核薪、任务经营费用详情和月结版本差异 **GUI NOT RUN**；不能以空态截图冒充这些场景通过。

现有正式 API 展示边界：任务经营列表只支持月份和模板筛选，没有任务号服务端筛选；公司经营 API 提供权威汇总，没有公司经营明细列表，页面以正式业务页跳转代替伪造明细；能源费用 API 一次按一个费用类型筛选，页面明确展示当前选中类别，不把数类费用在前端拼成另一套权威汇总。若上线必须增加这些筛选或跨类统计，应先走正式 API/需求决策，本轮未制作假数据或假按钮。

自动验证：管理端完整 `npm test` PASS（13 项当前核心、5 项 UI1、重量证据及充电工作簿用例）；`npm run build` PASS（95 modules，单 chunk 体积提示为 NON-BLOCKING）。后端当前启用全量 `mvn test` **83 tests、0 failures、0 errors、1 skipped，BUILD SUCCESS**；跳过项不记 PASS，现有测试覆盖 Participant 工资、出勤发车事实、财务归属与月结不可变等业务回归。本阶段没有修改后端业务代码。`git diff --check`、Secret 检查见本轮最终检查结果；本机配置、`.runtime/`、既有未跟踪历史文档均原样保留。

局部四项唯一性复审：**需求**只按 V3.0 与批准的 UI 布局调整；**业务**中概览/待办/工资/出勤/经营/月结仍调用既有正式 API；**数据**中 Participant 工资、实际发车、ExpenseEntry、经营汇总和 Settlement 快照分别保持唯一权威；**代码**中没有新 Controller、Service、SQL、工资/费用/利润计算器或第二套写入路径。UI-B4 范围未发现新的业务 BLOCKER。

月结版本详情的真实 GUI 尚无法只读检查：隔离库没有版本。尝试通过页面生成一版时，自动审批审查拒绝确认，因为该操作会写入不可随意替换的结算业务事实，超出本次仅整改页面与只读核查的授权；没有绕过或重试。保留空态截图，版本详情、差异和不可变性的人工 GUI 状态为 **NOT RUN**，待 V3-R 业务验收使用已授权的测试数据验证。

UI-B4 待业务方人工复验：经营概览及待办同源、工资台账/转派核薪、实际发车出勤及跨日、公司/模板/任务经营、三类支出、月结历史版本与差异，以及 1366×768 和 1920×1080 的实际观感。UI-B5 前置条件为这些业务页面提供相应正式测试事实并由业务方复验 UI-B1～B4；R7-V3-R-UI 与 V3-R **均未标记 PASS**。本轮停止，不提交，不开始 UI-B5。

## UI-B5 系统设置与全局 UI 收尾增量实施记录（2026-10-13）

开始时 HEAD 为 `780ff306c5112c5444a1eb3c519f937ac5f5de2e`。在 UI-B1～B4 及既有 V3-R-UI 的未提交工作区增量处理，未 reset、clean、暂存或提交；本机 `application.yml`、`project.config.json`、`.runtime/` 和历史未跟踪文档保持原状。正式业务依据仍为 `AGENTS.md + docs/01～07 V3.0`，`docs/36` 仅作为已批准的 UI 尺寸与 38 路由矩阵。

| 范围 | 本轮实际修复与复核 | 文件 | 自动/运行结果 | 人工 GUI |
| --- | --- | --- | --- | --- |
| 用户管理 | 标题与菜单一致；正式账号、角色、状态、最近登录使用有数据表格；新增只读详情；新增/编辑、停用/启用、管理员重置密码只调用原 API，完成后按真实响应提示；编辑或重置输入未保存时先确认放弃；登录时间改为易读格式 | `SystemUi1View.vue` | 5176 隔离库 5 条真实测试账号可见；编辑后取消弹出放弃确认，确认后原记录未变；后端合同测试与管理端测试 PASS | NOT RUN |
| 角色权限 | 权限按业务模块分组，补齐运输任务、模板、调度、充电、出勤等中文组名；保存按钮 busy/无变化禁用；角色及权限保存/启停显示真实结果反馈；权限草稿取消需确认；权限刷新仍使用正式 `/me` | `SystemUi1View.vue`、原 `AppLayout.vue`/权限 API 复用 | 角色列表 5 条、权限抽屉可读且正文独立滚动；账号菜单点击“刷新我的权限”后页面显示真实 `/me` 成功反馈。未更改角色授权事实 | NOT RUN |
| 操作日志 | 正常表格展示中文模块、业务对象类别和动作；逐项核对后把 `APPROVE` 准确表述为“同意当前审批步骤”，未知技术模块/对象不直接裸露枚举；原始动作与业务类型只在只读详情保留；时间去除 ISO `T` 和毫秒 | `AuditLogsUi2.vue` | 5176 有 31 条正式审计记录、分页 2 页；列表与详情正常打开；无删除/修改入口 | NOT RUN |
| 全局表格与导航 | `DataTable` 加载中不再同时展示旧行，防止旧结果被误读；“绑定关系”页标题与唯一导航名称一致；九域树、审批纯展开父级、顶部账号仍复用 UI-B1 | `DataTable.vue`、`BindingManagement.vue`、`test/run-tests.mjs`、`test/ui1Tests.mjs` | 38 个路由、九域菜单、前进/后退/刷新高亮均检查；未建立新业务页面或 API | NOT RUN |

系统设置功能边界：`UserResponse` 仅返回账号手机号、角色、状态、登录及审计时间，**没有姓名字段**，因此用户列表没有伪造姓名；若姓名属于最终上线必须字段，需要正式 API/业务决策，不能以 UI 假数据补齐。`AuditLogResponse` 没有“操作结果”字段或业务对象名称，只提供类型与关联 ID；本轮按现有数据展示中文对象类别，不能声称结果或业务名称已真实展示。账号修改本人密码使用原 `PUT /auth/password`，当前密码与两次新密码前端校验及服务端校验均保留，成功后现有策略退出登录；本轮只打开真实弹窗核对字段，**没有输入或提交密码**。管理员重置他人密码与本人改密继续分开。角色权限的有变化保存、撤回后受限账号即时菜单状态未在 GUI 制造权限变更，保持人工待验；后台权限负向和 `/me` 刷新合同测试仍有效。页面内搜索仅筛当前服务端分页，UI 明示“筛选当前页”，不冒充全量检索。

38 路由及登录页矩阵：以 `docs/36` 的 38 个带权限正式 route key 为准，使用已登录隔离测试管理员在 `http://127.0.0.1:5176/` 逐一打开 **38/38**，全部取得对应业务页面标题，未见页面级加载失败、整页横向溢出或要求填写数据库 ID 的表单。唯一技术词命中来自任务导入的提醒“请勿填写数据库 ID”，不是 ID 输入。`approvals-queue` 是受权限约束的非菜单深链，三条历史别名仍指向正式业务页；未发现旧通用费用导入或第二套可写页面重新可达。逐路由宽度/标题检查保存在本机忽略目录 `.runtime/ui-b5-screenshots/route-check.json`。登录页在源码和当前管理端测试中复核；由于此浏览器会话保持隔离管理员登录，**登录页真实 GUI 本轮 NOT RUN**。自动路由可达与按钮存在性检查不等于逐页业务操作或无权限账号 GUI PASS。

响应式自动检查使用浏览器实际 `innerWidth/innerHeight`，而非单凭截图像素：38 页分别在 **1366×768** 与 **1920×1080** 下检查，全部 `documentElement.scrollWidth === innerWidth`，没有整页横向溢出。九域全部展开时侧栏导航 `clientHeight=674`、`scrollHeight=1687`、`overflow-y=auto`，正文独立；当前叶子只有一项高亮。点击用户管理后浏览器后退到操作日志、前进返回、刷新后，URL 与唯一高亮均同步。1366 下角色权限抽屉的正文独立滚动（601/6147px），1920 下本人改密弹窗位于视口内；有数据费用表与运输任务宽表仅在表格容器横向滚动。月度宽表当前缺少实际发车数据，宽表内容与导出的人工验证仍 NOT RUN。临时浏览器视口覆盖在检查后已恢复默认。

真实页面截图保存于忽略目录 `.runtime/ui-b5-screenshots/`，均来自运行中的管理端，不是设计稿：`users-1366.png`、`roles-1366.png`、`audit-1366.png`、`role-permissions-1366.png`、`account-menu-1366.png`、`change-password-1366.png`、`nine-domains-1366.png`、`expense-table-1366.png`、`transport-table-1366.png`，另有用户/角色/日志和改密弹窗的 1920 截图。截图证明本轮自动页面检查所见，**不记 MANUAL GUI PASS**。

自动验证：管理端完整 `npm test` PASS（13 项当前核心、7 项 UI1、重量证据及充电工作簿测试）；`npm run build` PASS（95 modules，763.93 kB 单 chunk 为 NON-BLOCKING 提示）。后端当前启用 `mvn test` **83 tests、0 failures、0 errors、1 skipped，BUILD SUCCESS**，其中包括用户角色权限、本人改密、审计安全及业务回归；跳过项不记 PASS。`git diff --check` 与空暂存区的 `git diff --cached --check` 均 PASS（仅 LF/CRLF 提示）；本轮相关源码与报告的常见 Secret 字面量扫描 0 命中。本机配置、`.runtime/` 和历史文档保持原状，未暂存或提交；无本轮新增后端代码、数据库、OpenAPI、费用或审批写入路径。

局部四项唯一性：**需求**仍按正式 V3.0；**业务**中用户/角色/审计只调用原系统接口，工资、运输、费用、审批等正式流程未改；**数据**中账号角色权限和 Audit 继续由原系统表/API 提供；**代码**中九域路由、38 权限叶子及深链仍是同一套组件/服务，没有第二个认证、权限、费用或导入入口。本轮未发现新增的第二套业务事实。

沿用并登记功能差距：线路模板无独立去返程字段、司机资料编辑 API、车挂跨车辆完整绑定历史、任务操作历史读取、有数据月度调度台账、月结历史版本/差异、充电 Excel 真实页面预览及费用审批完整 GUI 提交链，均未在 UI-B5 伪造能力；另有上述用户姓名、审计结果/对象名称的正式 API 展示缺口。UI-C 前置条件是业务方逐项人工复验 UI-B1～B5，并提供能覆盖发车、月表、审批、费用、月结、充电的隔离测试事实；上述已冻结但缺失的能力须由业务方判定上线闸门及正式 API 处理方式。**R7-V3-R-UI 与 V3-R 均未标记 PASS**。本轮停止，不提交，不开始 UI-C。

## UI-C 第一阶段验收环境与功能差距核查（2026-10-13）

本阶段只做验收准备、隔离测试事实和只读差距核查；没有修改业务代码、单一数据库 baseline、OpenAPI 或正式 Word，也没有暂存、提交、reset/clean。冻结 HEAD 仍为 `780ff306c5112c5444a1eb3c519f937ac5f5de2e`，UI-A/B1～B5 及此前整改的未提交工作区成果均保留。正式规则为 `AGENTS.md + docs/01～07 V3.0`；本报告与 `docs/36` 只作为验收追踪和页面矩阵输入。

### 隔离环境及连接边界

| 地址/服务 | 本轮实际核对 | 数据库与写入边界 |
| --- | --- | --- |
| `http://127.0.0.1:5176/` | Vite 返回 200；代理 `/api/v1` 到 `127.0.0.1:8082`；隔离后端 `/me` 未登录返回 401、登录后返回 200 | 8082 的启动环境明确指定 `jdbc:mysql://127.0.0.1:13308/fleet_ops_v3r_ui2_20261013045339`，Flyway 关闭；以 MySQL 只读查询确认该库存在且已有测试记录。本轮仅向该库写入标记 `UIC验收` 的测试事实。 |
| `http://127.0.0.1:5175/` | 前端返回 200；仓库默认 Vite 配置代理 8080，但 8080 本轮不可达 | **当前无可用 5175 后端连接，不能声称其连接某个数据库。** 本机 `application.yml` 的默认库名为 `fleet_ops`、3306，但本轮没有启动它，也没有对其读写。 |
| 隔离 MySQL | 原有数据目录与命名测试库复用，端口 13308；未重建或覆盖库 | 库名、独立端口、既有 `RUI3` 来源及先前隔离库初始化脚本均指向测试环境，未连接生产库。司机、模板、充电公司名称均带测试标记；**一辆原有车辆未命中本轮标记规则，不能仅凭名称证明它是模拟数据**，本轮没有编辑该车或用其创建新任务。原始第三方 Excel 没有导入其他库。本机连接 Secret 仅从被忽略的 `.runtime/` 读取，未写入报告。 |

测试角色：管理员及“运输任务只读”账号的手机号和密码只在本机 `.runtime/v3r-test-accounts.txt` 中查阅，不在报告展开。原保存的只读账号不在新的隔离库中；本轮经正式 `POST /users` 创建账号，另经正式角色 API 新建 `UIC_TASK_VIEW` 并仅授予 `task:view`，没有改动既有角色。该账号重新登录后，任务列表 200，用户与费用列表均 403；管理员 `/me`、司机、模板、任务、费用、充电导入和月结只读 API 均可访问。

### 已准备及尚缺的测试事实

本轮前隔离库有自有司机/公司车/货物/模板各 1、已发布任务 1、待处理审批 1、有效充电费用 2、库存轮胎 1、任务与轮胎已完成导入各 1 批、充电导入 21 批及来源订单 3 条。经正式 API 新增 `UIC验收自有司机`、`UIC验收外协司机`、公司车辆、车挂、司机-车与车-挂绑定，并使用 `POST /dispatch-batches/preview → confirm` 整批发布 2 行有效测试任务。最终只读清点：用户 8、角色 6、司机 3、公司车 2、车挂 1、货物 1、启用线路模板 1、运输任务/Participant 各 3、实际发车事件 0、审批实例 1、正式费用 2、库存轮胎 1、任务/轮胎导入批次各 22（其中各有 1 批已完成）、充电批次 21、来源订单 3、结算版本 0。新任务分别覆盖自有与外协待发车，所有新增业务事实仅在隔离库；未直接 INSERT/UPDATE 业务表。

未准备为 PASS 的场景：实际发车、完成卸货、转派多参与、重量磅单、工资调整、出勤与有数据调度月表、DAILY/TASK 申请终态、费用归属/冲销、已使用轮胎及换胎审批、完成任务经营、结算版本/差异、12 家真实充电 Excel 浏览器预览与真实 OCR。原因分别是当前库缺少相应前置事实，或会产生不可随意替换的审批/财务终态，或需要业务方真实文件/真实 OCR 环境。**没有为填充页面而伪造表记录、生成审批终态或月结版本。** 后续按 V3-R 顺序通过正式 API/双端操作构造，每项先核验隔离库与权限；实际 GUI 均仍 `NOT RUN`。

### 四项正式能力差距：核查结果与最小建议

| 专项 | V3.0 正式依据与当前事实 | 结论及上线影响 | 最小建议（本轮未实施） |
| --- | --- | --- | --- |
| 线路模板独立去返程 | `docs/01` 模板规则、`docs/03 route_template` 字段和 `docs/04 RouteTemplate` API 都定义稳定编号、中文客户、货物、起讫地及三项默认金额，**未定义独立方向字段**。`database.sql`、`RouteTemplateRequest/Response`、OpenAPI 及模板页面也无此字段。此前 docs/35/UI-B5 将它笼统列作 API 差距，口径过宽。 | 对当前唯一正式 V3.0 基线 **不是已确认缺失**；仅“起讫地/任务展示是否需要单独方向”属于业务 `DECISION REQUIRED`，不以 UI-C 猜测字段或标阻断。 | 业务若新增独立方向，先改 01～07，再同步单一 baseline、API、页面和测试。 |
| 司机资料编辑 | `docs/04` 写 `PATCH /drivers/{id}`；`docs/06` 要求查看/维护分权。当前 `MasterDataController` 已有受 `driver:edit` 限制的 **PUT**，Service/Repository 可更新，OpenAPI 也写 PUT；但 `masterApi` 和司机管理组件只接入创建/查询，没有正式编辑入口。 | **后端能力存在、管理端未接入**，并有正式 Word PATCH 与运行契约 PUT 不一致。司机档案必须可维护时属于 V3-R 功能缺口；不能称“缺少司机编辑 API”。 | 先统一文档与既有正式 HTTP 方法，再让管理端在 `driver:edit` 权限下复用 PUT 和现有校验/反馈；不另建写入入口。 |
| 车挂完整绑定历史 | `docs/02` 要求 Vehicle↔Trailer 当前关系及含起止时间/操作者/原因的历史。`vehicle_trailer_history` 保存 `vehicle_id + trailer_id`，当前库有 1 条真实绑定史；Repository 绑定/解绑写入同表，`GET /vehicles/{id}/trailer-binding-history` 实测 200。`GET /trailers/{id}/binding-history` 实测 404；现有查询只按单车辆过滤。 | **历史事实并未丢失**，但无法以车挂为主键跨历任车辆查询；车挂档案的完整历史 GUI 待补，不能以当前车辆历史冒充。 | 基于同一历史表提供受现有历史权限保护的按车挂只读查询，并在车挂详情接入；不新增历史表。 |
| 运输任务操作历史 | `docs/02` 要求状态历史/Audit 保留；`docs/04` 明列 `GET /transport-tasks/{id}/status-history`。`task_status_history` 当前有 3 条，调度发布与状态变更写入；Audit 中另有任务记录。当前任务详情响应没有状态史字段，专用 GET 实测 404，OpenAPI 与管理端均未接入。 | **历史事实存在但查询契约/页面缺失**；任务详情不能完整呈现操作轨迹，按已冻结 API 属于验收功能缺口，需在 V3-R 关闭前解决。 | 基于现有 `task_status_history` 与必要 Audit 事实增加受 `task:view`/对象范围限制的只读 DTO/API，再接任务详情时间线；不改状态机或历史写入。 |

上述四项只读审计没有改代码。司机编辑/车挂跨车辆史/任务状态史是后续定向修复候选，**不在 UI-C 准备阶段偷补开发**；正式优先级由业务方确认。原始 Excel 下载、权限撤回、本人改密、有数据工资/出勤/经营/月结、DAILY/TASK 提交、充电真实预览及两种视口有数据页面继续作为后续人工 GUI 专项，不借空态替代。

## UI-C 第一阶段最终 GUI 验收矩阵（2026-10-13）

状态列严格使用 `PASS / FAIL / NOT RUN / BLOCKED`。角色不是固定代码常量：由管理员将列出的原子权限授予对应岗位；菜单按 `/me.permissions` 过滤，后端仍单独鉴权。下表“自动 PASS”仅指 UI-B5 已在管理员会话对 38/38 路由完成可达、标题和两种视口的基础检查；“API PASS”仅指本轮列出的只读接口实际返回，不覆盖写操作。除三类导入历史列表的既往局部人工结果外，整页人工 GUI 均为 `NOT RUN`。所有页面还需逐项检查 1366×768、1920×1080、Loading/Empty/Error/Permission、刷新/前进/后退、表格局部横向滚动与中文状态。

| 菜单位置与页面路由 | 角色/原子权限 | 主要业务功能 | 视觉检查点 | 关键交互 | 所需测试数据 | 正式 API | 预期结果 | 自动 | API | 人工 GUI / 当前状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 公共登录（未认证态） | 未登录；管理员/任务只读账号 | 登录、错误反馈、权限加载 | 表单居中、标签/错误可读 | 正确/错误密码、退出后回到登录 | 本机 `.runtime/v3r-test-accounts.txt`；不在报告记录密码 | POST /auth/login；GET /me | 登录后仅显示本人权限菜单，错误凭据不泄露细节 | PASS（源码与测试） | PASS（隔离账号登录） | NOT RUN |
| 工作台→经营概览 `#/dashboard` | 动态授权 `report:company:view` | 月度经营与待处理概览 | 为月份筛选→4–6 指标→待处理卡→经营口径，卡片大小和留白统一 | 切换月份并钻取经营指标 | 已发布任务、两条充电费用；完成任务/利润待准备 | GET /finance/dashboard | 金额与正式 API 同口径，未归属 DAILY 已计入公司支出 | PASS（基础路由） | NOT RUN | NOT RUN |
| 工作台→我的待办 `#/my-todos` | 动态授权 [`approval:process`, `approval:return`, `dispatch:view`, `expense:ledger:view`] | 审批/业务待办入口 | 为待办分组卡+可直达详情的真实链接 | 打开待办并与审批中心同一记录对照 | 现有待处理审批 1 笔 | GET /approvals?scope=TODO；GET /finance/dashboard | 与审批中心入口显示同一计数和详情，不造新待办事实 | PASS（基础路由） | NOT RUN | NOT RUN |
| 运输管理→货物管理 `#/products` | 动态授权 `template:view` | 货物目录 | 筛选→业务列表→编辑 Dialog | 筛选、查看与授权编辑/启停 | 货物 1 条 | GET/POST/PUT /products | 货物状态/名称中文，启停反馈真实 | PASS（基础路由） | NOT RUN | NOT RUN |
| 运输管理→线路模板 `#/templates` | 动态授权 `template:view` | 模板默认值 | 列表→摘要/默认金额→编辑 Drawer | 查看三项默认金额，编辑后核对旧任务快照 | 启用模板 1 条 | GET/POST/PUT /route-templates；POST /enable/disable | 修改模板不改变已建任务快照；启停入口有权限 | PASS（基础路由） | PASS（只读） | NOT RUN |
| 运输管理→调度工作台→批量调度 `#/dispatch` | 动态授权 `dispatch:view` | 整批发布 | 日期/模板选择→默认值摘要→待发布表格→行错/批量金额→汇总确认 | 同司机多行、行错、批量金额及整批确认 | 启用模板、自有/外协司机及绑定；本轮新增两条发布任务 | POST /dispatch-batches/preview/confirm；GET /dispatch-batches/{id} | 同司机多行、整批原子、服务端重验、双击幂等；不出现“趟数” | PASS（基础路由） | NOT RUN | NOT RUN |
| 运输管理→调度工作台→任务导入 `#/task-imports` | 动态授权 `task:import:view` | 固定 Excel | 只收口上传/预览/确认和批次详情的层级，不重做历史表 | 预览只读、批次详情、原文件下载与分页 | 任务导入已完成 1 批、预览 21 批及原文件 | GET/POST /task-imports；GET /task-imports/{id}；附件下载 | 真实行结果、失败原因、分页、原文件下载单独人工复验 | PASS（基础路由） | NOT RUN | NOT RUN |
| 运输管理→调度工作台→运输任务 `#/routes` | 动态授权 `task:view` | 任务生命周期 | 紧凑筛选→任务表→事实分组抽屉，操作仅在合法状态出现 | 详情、参与链、重量凭证及合法状态操作 | 已发布任务 3 条；发车/转派/完成与磅单待准备 | GET /transport-tasks[/{id}]；participants/weight-versions/amount-history；正式操作 API | 取消/发车/转派/卸货/重量证据与正式状态一致，无作废/恢复 | PASS（基础路由） | PASS（只读） | BLOCKED（状态历史有表，正式查询 API/页面缺失） |
| 运输管理→调度中心→自用车辆月度台账 `#/dispatch-internal` | 动态授权 `dispatch:ledger:view` | 自用车辆月度台账 | 月表宽，需要局部滚动和固定车牌/司机事实列；月份→摘要→月表→导出 | 按月查看发车次数、历史车挂与导出 | 自有司机和公司车已绑定；实际发车事件 0 | GET /dispatch-ledger/internal；导出 | 同车同日多任务计数、历史车挂/司机快照、导出同口径 | PASS（基础路由） | NOT RUN | NOT RUN |
| 运输管理→调度中心→外协车辆月度台账 `#/dispatch-outsourced` | 动态授权 `dispatch:ledger:view` | 外协车辆月度台账 | 与自用月表共用骨架，突出真实外协车牌和司机；不展示公司车辆 ID | 按外协车牌查看月表与导出 | 外协司机及发布任务已有；实际发车事件 0 | GET /dispatch-ledger/outsourced；导出 | 按实际发车及外协车牌统计，导出一致 | PASS（基础路由） | NOT RUN | NOT RUN |
| 车辆与人员→司机管理 `#/drivers` | 动态授权 `driver:list` | 司机档案 | 筛选→司机表→档案/当前绑定/历史分区 | 筛选、详情、创建及资料编辑 | 自有司机 2、外协司机 1 | GET/POST/PUT /drivers[/{id}] | 全量筛选、业务车牌、内部/外协中文身份 | PASS（基础路由） | PASS（只读） | BLOCKED（正式编辑 API 已有，管理端未接入；文档 PATCH 与实现 PUT 不一致） |
| 车辆与人员→车辆管理 `#/vehicles` | 动态授权 `vehicle:list` | 公司车辆 | 表格→详情/编辑 | 筛选、详情、编辑及状态 | 公司车 2 辆 | GET/POST/PUT /vehicles[/{id}] | 车牌和状态可读，编辑后列表刷新 | PASS（基础路由） | NOT RUN | NOT RUN |
| 车辆与人员→车挂管理 `#/trailers` | 动态授权 `trailer:list` | 车挂资料 | 与车辆页相同的列表/弹窗范式，避免平铺字段 | 详情、编辑与跨车辆绑定历史 | 车挂 1 个且已绑定；跨车历史尚无 | GET/POST/PUT /trailers[/{id}]；绑定 API | 当前绑定/历史不被误写成当前车挂 | PASS（基础路由） | NOT RUN | BLOCKED（历史事实存在，但缺少按车挂跨车辆查询） |
| 车辆与人员→绑定关系 `#/binding` | 动态授权 `vehicle:bindingHistory` | 司机/车/挂关系 | 当前关系表→绑定/解绑 Dialog→历史时间线；与轮胎三级菜单缩进一致 | 查看当前关系及绑定/解绑历史 | 司机-车绑定 2 组、车-挂绑定 1 组 | GET 当前关系及 binding-history；POST bind/unbind | 当前与历史分离，非法绑定仍由后端拒绝 | PASS（基础路由） | NOT RUN | BLOCKED（车挂跨车辆历史缺少专用查询） |
| 车辆与人员→轮胎管理→轮胎库存 `#/tires` | 动态授权 `tire:view` | 轮胎库存 | 筛选→状态汇总→库存表→维护/历史抽屉；Web 不展示换胎申请 | 库存筛选、详情及授权维护 | 库存轮胎 1 条；已使用轮胎待准备 | GET/PUT /tires[/{id}] | 四字段、库存状态、授权维护与审计清楚 | PASS（基础路由） | NOT RUN | NOT RUN |
| 车辆与人员→轮胎管理→轮胎入库 `#/tire-imports` | 动态授权 `tire:import` | 轮胎入库 | 固定模板流程与真实批次详情分区 | 固定四列预览、历史详情与原文件下载 | 轮胎入库已完成 1 批、预览 21 批及原文件 | GET/POST /tire-imports；详情；附件下载 | 原文件/行号/失败原因/分页待人工复验 | PASS（基础路由） | NOT RUN | NOT RUN |
| 费用管理→费用台账 `#/expense-ledger` | 动态授权 `expense:ledger:view` | 正式 ExpenseEntry | 紧凑筛选→服务端汇总→分页表→来源/归属/冲销详情 | 筛选、汇总、来源/归属/冲销详情 | 正式充电费用 2 条；DAILY/TASK 待准备 | GET /expense-ledger[/{id}]；/summary；导出 | 未归属 DAILY、冲销净额及来源可读，金额与 API 一致 | PASS（基础路由） | PASS（只读） | NOT RUN |
| 费用管理→费用归属 `#/expense-attribution` | 动态授权 `expense:ledger:view` | DAILY 人工绑定 | 突出待归属、车牌/任务候选和归属历史，不复制费用事实 | 选择候选任务、车牌硬校验及归属历史 | 充电费用 2 条；未归属 DAILY 待准备 | GET /expense-ledger；POST /expense-ledger/{id}/attribution | 车辆不一致被服务端硬拒绝，绑定后公司费用不重计 | PASS（基础路由） | NOT RUN | NOT RUN |
| 审批中心→流程与模板配置 `#/approvals-flows` | 动态授权 `approval:flow:manage` | 版本化流程 | 模板表→冻结版本抽屉→节点编辑/发布明确分区 | 查看冻结节点、编辑新版本与发布边界 | 流程模板 1 条 | GET/POST /approval-templates；GET/POST /approval-flows | 同岗位多节点按稳定步骤区分，在途实例不随模板改动 | PASS（基础路由） | NOT RUN | NOT RUN |
| 审批中心→我的待办 `#/approvals-todo` | 动态授权 [`approval:process`, `approval:return`] | 复用工作台待办 | 只允许第二菜单入口，不能第二套待办计算 | 与工作台待办进入同一审批详情 | 待处理审批 1 笔 | GET /approvals?scope=TODO | 与 `#/my-todos` 相同数据和处理链 | PASS（基础路由） | NOT RUN | NOT RUN |
| 审批中心→我的申请→申请记录 `#/approvals-mine` | 动态授权 [`approval:view`, `approval:create`] | 本人申请 | 移为独立三级叶子，记录表→申请详情/时间线，普通申请唯一发起入口 | 查看本人申请详情及提交历史 | 申请记录 1 笔；多明细案例待准备 | GET /approvals?scope=MINE；GET /approvals/{id} | 点击叶子进入记录；父级只展开；审批写入仍唯一 `/approvals` | PASS（基础路由） | NOT RUN | NOT RUN |
| 审批中心→我的申请→费用申请 `#/expense-apply` | 动态授权 `approval:create` | DAILY/TASK | 申请类型→申请信息→明细表→汇总→确认提交；保持草稿保护 | DAILY 混合明细、TASK 单笔与统一提交 | 模板、司机、车与发布任务已有；终态申请不预先制造 | GET /expense-applications/options；POST /approvals | DAILY Excel+手工混编，TASK 单笔；最终一审批实例，所有金额>0 | PASS（基础路由） | NOT RUN | NOT RUN |
| 审批中心→审批记录 `#/approvals-history` | 动态授权 `approval:history:view` | 全量历史 | 紧凑筛选→分页记录→流程/动作时间线；不出现重复“新增” | 按权限查询流程及动作时间线 | 待处理审批 1 笔；通过/打回史待准备 | GET /approvals?scope=HISTORY；GET /approvals/{id} | 指定步骤打回历史可读，无多余写入口 | PASS（基础路由） | NOT RUN | NOT RUN |
| 非菜单深链 `#/approvals-queue` | 动态授权 [`approval:process`, `approval:return`] | 从我的待办进入审批处理 | 保留受权限深链，面包屑应指向“审批中心→我的待办→处理”；不另造导航叶子 | 从待办深链处理并核对合法打回步骤 | 待处理审批 1 笔 | GET /approvals/{id}；POST approve/return-step/return-applicant | 无权限直链拒绝，合法目标步骤列表与服务端一致 | PASS（基础路由） | NOT RUN | NOT RUN |
| 工资与考勤→司机出勤 `#/attendance` | 动态授权 `attendance:view` | 实际发车日 | 月份/司机→月表→当日任务抽屉→导出 | 切换月份、点出勤日、核对同日多趟 | 自有司机已有；实际发车事件 0 | GET /attendance；/attendance/export | 同日多趟=1 天，外协不入表，跨日卸货不另计 | PASS（基础路由） | NOT RUN | NOT RUN |
| 工资与考勤→工资台账 `#/salaries` | 动态授权 `salary:view` | Participant 工资 | 筛选→工资汇总→分页明细→工资历史，转派标签明显 | 筛选、工资明细、历史与全量导出 | 发布任务有 Participant；转派/调整历史待准备 | GET /driver-salaries；/summary；/history；/export | 页面、导出、经营金额同一 Participant 工资 | PASS（基础路由） | NOT RUN | NOT RUN |
| 工资与考勤→转派工资核对 `#/salary-transfers` | 动态授权 `salary:view` | 转派工资核对 | 完整参与链→一次核薪 Dialog→历史 | 选择转派任务核对所有参与人及并发版本 | 转派任务/多参与工资待准备 | GET /driver-salaries；POST /transport-tasks/{id}/participant-salaries/confirm | N 人与 0 元工资可见，同额无历史、并发冲突明确 | PASS（基础路由） | NOT RUN | NOT RUN |
| 经营与结算→经营→公司经营 `#/finance-company` | 动态授权 `report:company:view` | 公司经营 | 月份→收入/支出/利润指标→费用结构→未归属 DAILY 说明 | 月度收入、费用、工资、利润交叉对账 | 充电费用 2 条；有收入完成任务待准备 | GET /finance/company | 不在前端重算，未归属费用计入一次 | PASS（基础路由） | NOT RUN | NOT RUN |
| 经营与结算→经营→线路模板经营 `#/finance-templates` | 动态授权 `report:template:view` | 线路模板经营 | 月份→模板表→其任务明细穿透，区分模板默认金额与任务实绩 | 模板汇总钻取任务 | 模板 1 条；完成任务待准备 | GET /finance/templates | 模板经营与任务/公司口径交叉对账 | PASS（基础路由） | NOT RUN | NOT RUN |
| 经营与结算→经营→任务经营 `#/finance-tasks` | 动态授权 `report:task:view` | 任务经营 | 筛选→任务经营表→参与人与费用档案抽屉，金额四列统一 | 任务收入/工资/信息费/费用/利润钻取 | 已发布任务 3 条；完成任务待准备 | GET /finance/tasks[/{id}] | 收入、信息费、工资、费用、利润与正式 API 一致 | PASS（基础路由） | NOT RUN | NOT RUN |
| 经营与结算→支出→车辆支出 `#/finance-vehicles` | 动态授权 `expense:ledger:view` | 车辆支出 | 车辆筛选→汇总→明细，不复制支出公式 | 月度车辆费用筛选及明细 | 公司车 2 辆、充电费用 2 条 | GET /expense-ledger；/summary | 当时车辆事实可追溯，不用当前绑定覆盖历史 | PASS（基础路由） | NOT RUN | NOT RUN |
| 经营与结算→支出→司机支出 `#/finance-drivers` | 动态授权 `expense:ledger:view` | 司机支出 | 与车辆支出一致 | 司机费用与工资口径分离 | 自有/外协司机已有；完整支出事实待准备 | GET /expense-ledger；/summary | 筛选与导出完整，不新建工资事实 | PASS（基础路由） | NOT RUN | NOT RUN |
| 经营与结算→支出→能源费用 `#/finance-energy` | 动态授权 `expense:ledger:view` | 能源费用 | 复用费用事实；类型名称业务中文，包含现有能源类别 | 能源费用及充电来源钻取 | 充电费用 2 条、来源批次 21 | GET /expense-ledger；/summary | 充电来源可追溯，零元来源不产生费用 | PASS（基础路由） | NOT RUN | NOT RUN |
| 经营与结算→月度结算 `#/settlements` | 动态授权 `settlement:view` | 历史版本 | 月份→版本表→只读明细/差异，生成动作明确确认，勿误解为改写旧版 | 月份、版本详情、前版差异及旧版只读 | 结算版本 0；不可逆版本未预先生成 | GET /settlements/{month}/versions；/versions/{id}/details/diff | 旧版本不可变，差异原因与当前事实对应 | PASS（基础路由） | PASS（只读） | NOT RUN |
| 导入中心→充电订单导入 `#/charging-imports` | 动态授权 [`charging:import`, `charging:view`] | 专项确认入账 | 选择公司→文件→只读预览/异常→确认→结果；历史页签保留，列表布局已人工 PASS | 真实 Excel 只读预览、零元/重复、确认及原文件 | 两家公司、批次 21、来源订单 3、费用 2 | GET/POST /charging/imports；GET /charging/source-orders；原文件下载 | 零元/重复/金额对账、原行号、原文件下载；确认前不入账 | PASS（基础路由） | PASS（只读） | NOT RUN |
| 导入中心→充电公司模板配置 `#/charging-companies` | 动态授权 `charging:company:manage` | 充电公司模板配置 | 公司列表→先选“仅改映射/上传新样本”→四字段选择→取消/保存；不显示导入页签 | 修改映射/上传新模板/取消恢复 | 两家公司当前映射 | GET/POST/PUT /charging/companies | 取消丢弃未存更改；新样本重选四字段；仅改映射不强制上传 | PASS（基础路由） | NOT RUN | NOT RUN |
| 系统设置→用户管理 `#/users` | 动态授权 `user:manage` | 用户管理 | 用户表→编辑/重置密码 Dialog | 用户筛选、详情、权限内新增/编辑/停用/重置 | 测试用户 8 个（含本轮新增） | GET/POST/PUT /users；POST reset-password | 用户改自身密码与管理员重置区分；筛选口径不误导 | PASS（基础路由） | PASS（只读） | NOT RUN |
| 系统设置→角色权限 `#/roles` | 动态授权 `role:manage` | 角色权限 | 角色表→权限分组编辑→真实保存反馈；权限目录只作配置辅助 | 权限分组、保存、刷新与撤回后的访问 | 测试角色 6 个（含本轮新增） | GET/POST/PUT /roles；PUT /roles/{id}/permissions | 权限保存后 `/me` 刷新真实生效，无假“同步成功” | PASS（基础路由） | PASS（只读） | NOT RUN |
| 系统设置→操作日志 `#/audit` | 动态授权 `audit:view` | 操作日志 | 筛选→中文动作表→详情保留原始代码；不直接主列展示英文技术枚举 | 筛选分页、中文动作及只读详情 | 审计记录已有 | GET /audit-logs | 中文含义正确、原始动作码可追溯 | PASS（基础路由） | NOT RUN | NOT RUN |

### 自动验证、隔离 API 与启动闸门

- 管理端完整 `npm test` PASS：13 项当前核心 + 7 项 UI1，以及重量证据/充电工作簿回归；`npm run build` PASS，95 modules。单 JS chunk 超过 500 kB 为性能提醒，不是本轮功能 PASS 的替代。
- 当前后端源码 `mvn package` 在仓库默认 `target` 的 repackage 文件重命名阶段遇到文件占用，测试此前已执行为 **83 tests、0 failures、0 errors、1 skipped**。为避免停止 5176 服务或清理现有构建产物，将相同源码复制到被忽略的 `.runtime/ui-c-backend-build` 后执行标准 `mvn package` **PASS**；该副本的 83 项测试仍为 0 failures、0 errors、1 skipped。将新 JAR 与 8082 当前运行的测试 JAR 逐 entry 比对，内容差异 0；所以保留现有隔离服务，不在验收准备中重启并使测试登录失效。跳过的 1 项明确不记 PASS。
- 正式隔离 API：管理员登录及 `/me`、司机、模板、任务、费用台账、充电导入、月结版本读接口成功；新建测试资料和两行调度均经正式 API，调度 Preview 两行无错误，Confirm 结果 2 行。任务只读账号 `/transport-tasks` 为 200，`/users` 和 `/expense-ledger` 均为 403。车挂按车辆历史 200，按车挂跨车辆历史 404；任务详情 200，`/status-history` 404。没有将 API 状态写成人工 GUI PASS。
- 当前工作区 `git diff --check` 与空暂存区的 `git diff --cached --check` 退出码 0（仅 LF/CRLF 提示）；本轮新增报告与运行源码差异中，排除明确不得提交的本机 `application.yml` 后，未检出高风险静态凭据字面量。本机配置、测试账号/令牌、`.runtime/` 及历史未跟踪文档均未暂存或提交。
- UI-C 正式人工启动条件：业务方使用 **5176 隔离页面**，从当前第一项开始逐项回复 PASS/问题；本报告每页 `NOT RUN/BLOCKED` 在获得真实页面操作证据后才更新。审批终态、月结版本、真实充电公司 Excel 与 OCR 需在各自验收项目开始前再次核验隔离环境和所需授权。UI-B1～B5 的先前自动截图不自动转为人工 GUI PASS。

**第一项人工 GUI 项目：登录、权限与九域导航。** 业务方在 `http://127.0.0.1:5176/` 使用本机 `.runtime/v3r-test-accounts.txt` 的管理员账号登录（密码只在浏览器输入）；确认左侧依次为工作台、运输管理、车辆与人员、费用管理、审批中心、工资与考勤、经营与结算、导入中心、系统设置；展开审批中心，确认“我的申请”仅展开，下面“申请记录/费用申请”各有独立入口；全部菜单展开后滚动到系统设置，叶子高亮、刷新及后退/前进仍对应当前页。然后退出，用同文件“运输任务只读”账号登录，预期仅能进入运输管理中的运输任务，系统设置、费用管理不可见，直接访问其路由/API 也不能显示数据。此为**一个**验收项目；本阶段只给出步骤，人工结果仍 `NOT RUN`，等待业务方实际操作后再进入下一项。

**阶段结论：UI-C 第一阶段准备已完成到当前安全边界；有数据场景仍明显不足，三个正式能力缺口与一项文档/实现 HTTP 方法冲突待业务方判定和后续定向修复。R7-V3-R-UI、V3-R 均未关闭。本轮未提交，也未开始逐页 GUI 验收。**

## UI-C 第 2 项人工验收 PARTIAL PASS 定向修复（2026-10-13）

业务方已确认货物管理等未指出的项目通过；司机管理、车辆管理、车挂管理、绑定关系仍待复验。本项整体维持 **PARTIAL PASS**，本次代码修复和自动测试不替代人工 GUI。

| 问题 | 原因与修复 | 验证状态 |
| --- | --- | --- |
| 车辆达标比例列表 8000%、编辑 80 | 正式 API 与重量计算使用 0～1 比例值，页面编辑框却直接显示 API 原值，而列表/详情乘 100，造成显示口径不一致。`MasterCatalogUi2.vue` 现以百分数展示和输入，保存时转换为比例值；若旧记录已存入超出 0～1 的数值，列表/详情提示“存储比例待核对”，编辑保存须显式勾选核对确认，避免无意改写；后端拒绝超出 0～1 的新比例。未自动改动历史业务数据。 | 前端转换回归、后端负向测试 PASS；目标截图对应库值待人工复验。 |
| 车辆、车挂启停回显 | 编辑初始化错误读取货物 `enabled`，而车辆/车挂返回 `status`。按对象类型读取，取消编辑不会调用保存接口。 | 启用/停用转换回归 PASS；人工 GUI 待复验。 |
| 司机资料编辑 | 正式后端已有 `driver:edit` 保护的 `PUT /drivers/{id}`；管理端只接入创建/查询。现复用该接口增加编辑表单，保留后端账号手机号、司机类型及历史绑定限制；`docs/04` 中旧 PATCH 文字同步为现有 PUT 契约。 | 权限正负测试 PASS；人工 GUI 待复验。 |
| 外协司机车辆绑定 | 后端原已拒绝外协绑定，页面仍显示绑定操作。现仅自有司机显示绑定、更换、解绑；服务端对外协解绑也显式拒绝。未触碰运输任务参与或卸货链。 | 后端外协绑定/解绑负向测试 PASS；人工 GUI 待复验。 |

隔离 MySQL `fleet_ops_v3r_20261011021435` 经只读查询，当前车辆数为 0，因此未能复现截图所对应的历史 80 值，也没有修改历史数据。真实库值与页面修复后效果需在业务方实际使用的测试环境再次核对。无实际记录时不得把列表、详情、编辑状态记作真实 GUI PASS。

自动验证：管理端完整测试 PASS，build PASS；后端受影响 `MasterDataApiTests` 7 项 PASS，全量 86 项中 85 通过、0 failures、0 errors、1 skipped（`V3ChargingImportTests` 条件跳过），跳过项不计通过。`docs/04` 结构性定点修订已完成；Word 渲染工具因本机无 LibreOffice `soffice.exe` 无法逐页视觉检查，记为 NOT RUN。`git diff --check` 与 Secret 检查见本轮最终检查结果。UI-C 第 2 项仍待人工复验，不开始下一项，不关闭 R7-V3-R-UI 或 V3-R。


### UI-C 第 2 项补充：车辆达标吨数

业务方要求直接显示达标吨数。车辆列表现在优先显示“达标 ≥ N.NNN 吨”，详情单列达标吨数，编辑表单随最大载重与比例/固定标准变化实时预览。比例模式按正式后端同一口径 `最大载重 × 0～1 比例` 并四舍五入到 0.001 吨；固定模式直接显示最低载重。比例仍作为辅助配置显示。若旧记录存储比例超出合法范围，页面显示“待核对”而不推断达标吨数，也不自动修改数据。该补充改动仍待业务方人工 GUI 复验，UI-C 第 2 项保持 PARTIAL PASS。

### UI-C 第 2 项补充：达标字段拆分

按业务方反馈，车辆列表和详情将“达标比例”与“达标吨数”作为两个独立字段展示，编辑表单保留独立比例输入及吨数预览。固定吨数标准的比例栏显示“—”，吨数栏显示固定值。异常存储比例继续显示待核对，不推断吨数。此项仍待人工 GUI 复验。

### UI-C 第 2 项补充：达标吨数范围校验

业务方截图发现测试车辆限载 32 吨，但固定达标吨数为 50 吨。现有固定标准在服务端只校验非空，未校验范围。车辆编辑表单的固定达标吨数输入现限制为 0～当前限载，超限/负数显示中文错误并阻止保存；后端在新增与更新时独立校验 `0 ≤ 固定达标吨数 ≤ 最大载重`，不能经 API 绕过。既有超限记录在列表/详情显示“待核对”及“原值超出限载”，没有自动修改；实际值需业务方核对后授权编辑。比例模式保持原有 0～100% 输入与服务端 0～1 比例值规则。

验证：管理端完整测试及 build PASS；后端定向 `MasterDataApiTests` 8 项 PASS，全量 87 项中 86 通过、0 失败、0 错误、1 条条件跳过。覆盖负值、0、等于限载、超限和超限更新不改变原数据。真实 GUI 仍待业务方复验，UI-C 第 2 项维持 PARTIAL PASS。

### UI-C 第 2 项最终整改：司机与车辆证件字段（2026-10-14）

本次只调整司机、车辆证件资料与外协车辆展示。前述 UI-C 第 1 项人工 GUI PASS 及第 2 项原四类问题已有的人工结论保持原样；新增证件字段的人工 GUI 为 **NOT RUN / 待业务方复验**，不能由自动测试代替。

| 对象 | 最终事实与实现 | 验证 |
| --- | --- | --- |
| 司机身份证号 | 司机独立必填；前后端校验中国大陆 18 位行政区划前缀、真实出生日期及校验码；服务端统一大写末位 `X`，数据库 `NOT NULL` + 唯一索引。 | 非法日期/校验码拒绝、缺失 400、重复 409；真实 MySQL/API PASS。 |
| 司机驾驶证号 | 与身份证号分别录入，不互相填充；仅去除首尾空格，不附加未确认格式；数据库 `NOT NULL` + 唯一索引。 | 重复 409、trim 回显与新增/编辑 PASS。 |
| 出生日期、年龄 | 由身份证号派生；不作为可写数据库字段，新增/编辑只读，详情显示出生日期和周岁，列表增加年龄；身份证号变化后重新派生。 | 前端生日边界测试与后端年龄单元测试 PASS。 |
| 车辆行驶证号 | 从司机旧的混淆字段移至车辆，独立必填；仅 trim；列表、新增、编辑、详情保持同一口径；数据库 `NOT NULL` + 唯一索引。 | 缺失 400、重复 409、trim 回显与新增/编辑 PASS。 |
| 外协司机 | 列表/详情的当前公司车辆为“不适用”，前端不显示绑定、换绑、解绑；后端保留独立的外协绑定/解绑拒绝。 | 正式 API 外协绑定 409、当前车辆为空；负向自动测试 PASS。 |

**实际修改范围：** `DriverManagementSample.vue`、`MasterCatalogUi2.vue`、新增前端身份证派生工具及管理端回归测试；司机/车辆 Request、Response、Repository、Service、新增 Java 身份证校验工具和相关后端测试/测试夹具；`database/database.sql`、Flyway 单一初始化 `V1__baseline_schema.sql`、测试 schema；`docs/01～07 V3.0` 的对应正式章节及 `docs/openapi.yaml`。保留已有绑定事实与 UI-B1～B5 未提交成果；未改运输、费用、工资、经营或审批规则。

**自动与真实验证：** 管理端完整测试 PASS（16 项当前 V3 核心、7 项 UI1 及重量证据/充电工作簿回归），Vite build PASS（98 modules）。后端最终源码的标准 package 在隔离构建目录 PASS；共 **91 tests，0 failures，0 errors，1 skipped**，条件跳过不记 PASS。隔离 MySQL `127.0.0.1:13309` 上新建 `fleet_ops_ui_c_cert_final_20261014`，最终 Flyway V1 空库初始化 PASS；三个证件字段均为 `NOT NULL` 且有唯一索引。最终 package 在独立 8085 服务连接该库，正式 API 完成司机/车辆新增、编辑、派生字段、trim、缺失、重复、身份证日期/校验码及外协绑定负向复验。`git diff --check` PASS，Secret/本机配置不纳入本次修改或提交。

**局部四项唯一性：** 证件要求已直接归入 01～07；司机身份证/驾驶证与车辆行驶证各有唯一归属、写入路径及约束；出生日期、年龄仅派生；外协车辆绑定仍由同一正式后端规则拒绝。本次范围内未新增第二套司机、车辆或绑定写入入口，局部复审 PASS。

七份 Word 已完成结构性打开与证件术语检查；本机没有可用的 Word 逐页渲染器，逐页视觉检查记为 **NOT RUN / NON-BLOCKING**。

**另行发现的既有缺口：** 在同一新建隔离 MySQL 实例中，`database/database.sql` 初始化 53 张表，而 Flyway V1 初始化 50 张表；V1 缺少 `charging_company`、`charging_import_batch`、`charging_source_order`。这是充电模块的单一 baseline 不一致，属于整体 V3-R 上线前 **BLOCKER**，不属于本次证件字段逻辑；依据本轮禁止扩大业务模块范围，未改充电表，也不得把“V1 空库可执行”误写成“全库两份 baseline 完全一致”。

**待人工 GUI 复验：** 司机新增/编辑/详情/列表的两证独立录入、身份证自动生日和周岁、年龄列表列、重复提示；车辆行驶证号新增/编辑/详情/列表；外协司机“不适用”及无公司车辆绑定按钮。业务方复验前，本项证件整改 GUI 保持 NOT RUN；UI-C 第 2 项总体仍为 PARTIAL PASS。未提交，未进入 UI-C 第 3 项，R7-V3-R-UI 与 V3-R 均未关闭。

### R7-V3-R 数据库单一 baseline 一致性专项修复（2026-10-14）

**范围和依据。** 本节关闭上节发现的“`database.sql` 53 张表、Flyway V1 50 张表”结构差异，不改变充电业务规则。只读比对发现共同的 50 张表定义完全一致，V1 独缺 `charging_company`、`charging_import_batch`、`charging_source_order`；初始化种子另缺 `charging:company:manage`、`charging:import`、`charging:view` 三项权限。`docs/01～07 V3.0` 的充电来源/零元/原文件规则、`docs/openapi.yaml` 充电接口及现行唯一 `ChargingImportService` 均要求这些表和权限，因此它们属于最终模型。项目未上线，直接修正唯一 Flyway V1；没有新增 V2 迁移、兼容表或第二套充电费用写入路径。

**修改。** 仅在 `backend/src/main/resources/db/migration/V1__baseline_schema.sql` 补入与 `database/database.sql` 完全一致的三张正式表定义（包括字段、默认值、主键、唯一/普通索引、外键、CHECK）及三项权限种子。两份 SQL 共有的其它表和已有权限未改；证件 `driver.id_card_no`、`driver.driving_license_no`、`vehicle.vehicle_license_no` 的 `NOT NULL`/唯一索引，以及 `expense_entry.application_scope/current_task_id`、Participant 工资等近期 V3 字段在两份初始化库中一致。

**真实 MySQL 结构复验。** 新建仅绑定 `127.0.0.1:13310` 的独立 MySQL 数据目录；原样执行 `database/database.sql` 得到全新 `fleet_ops`，将最终 Flyway V1 执行至另一全新 `fleet_ops_flyway_v3r_final`。在写入任何业务测试数据前，用 `information_schema` 比较：53/53 张表、520/520 列、237/237 个索引项、158/158 项表约束、31/31 项外键、22/22 项 CHECK、0/0 触发器，全部 **0 差异**。初始化角色 2/2、权限 70/70、角色权限关系 75/75，内容/关系差异 0。以上为真实库元数据比较，不以静态文本相同代替数据库验收。

**正式 API/费用链复验。** 只在上述隔离 Flyway 测试库创建合成管理员、公司车辆与两家合成充电公司，并上传合成 Excel；原始业务库和 5175/5176 环境未写入。第一批确认 3 行：正常入账 1、零元保留 1、同文件重复 1，入账 ¥12.34。相同来源重复上传：新增费用 0、重复 3；另一来源使用相同原始订单号：正常入账 1、零元保留 1，不误判跨来源重复。最终 3 个批次、4 条 `SourceOrder`（其中零元 2 条且无 `ExpenseEntry`）、2 条正式 `ExpenseEntry`，合计 ¥24.68；来源订单到批次无孤儿记录。无唯一可靠任务匹配时费用保留未归属，没有猜测绑定。授权下载原 Excel 的 SHA-256 与上传文件一致，匿名下载返回 401。充电模板创建、来源去重、零元处理、原文件关联及单一 ExpenseEntry 入账链均经真实 MySQL/API 验证；这些结果不是人工 GUI PASS。

**自动验证。** 最终源码在新的隔离构建目录完成标准 Maven package：91 tests、0 failures、0 errors、1 条环境条件跳过（不记 PASS）。管理端完整测试 PASS；Vite build PASS（98 modules，单 chunk 体积警告为非阻断性能提醒）。`git diff --check` 和 Secret 检查 PASS；本机 `application.yml`、`project.config.json`、`.runtime/` 与 Secret 未暂存或提交。

**唯一性及剩余状态。** 需求唯一：遵循 01～07；业务唯一：充电配置/确认仍走现有 Controller→Service；数据唯一：`SourceOrder→ImportBatch→OriginalFile` 与 `ExpenseEntry` 仍为各自正式事实；代码唯一：只同步初始化结构/权限，未增加公司专属解析器、第二套费用写入 API 或兼容迁移。此前三表/三权限 baseline BLOCKER 已解除；本专项未发现新的数据库结构 BLOCKER。真实管理端与小程序人工 GUI 仍按各自原验收项目 **NOT RUN/待复验**，UI-C 第 2 项仍为 PARTIAL PASS，R7-V3-R-UI 与 V3-R 均未关闭。本轮未提交、未开始 UI-C 第 3 项。

### UI-C 第 2 项补充：司机校验提示与资料表格（2026-10-14）

人工截图发现司机编辑的身份证号错误提示显示在被弹窗遮挡的列表顶部，且无法区分具体错误。截图中的测试号码出生日期有效，但末位校验码不匹配；原拒绝规则正确，提示位置与文案不合格。现将身份证号的格式、地区码、日期、末位校验码提示细分并显示在弹窗的身份证输入框下；保存 API 失败及驾驶证号错误显示在当前弹窗内，取消/重新打开会清除旧错误。没有放宽后端证件校验，也没有修改测试库中的原号码。

业务方随后要求司机、车辆、车挂列表尽量直接显示已有资料。司机表增加身份证号、驾驶证号、出生日期，保留年龄；既有身份证号无效、服务端无法可信派生生日和年龄时显示“身份证待核对 / 待核对”，不猜测年龄。车辆表增加“达标方式”，与已有行驶证号、限载、达标比例/吨数、保险、司机、车挂和状态并列；车挂表已有车挂号、车牌、保险、当前车辆和状态，未伪造其它字段。宽表仅在表格容器内横向滚动。此项只涉及管理端显示与提示，没有改变司机、车辆、车挂的 API/数据库事实。

验证：管理端完整测试 PASS；Vite build PASS（首次在受限进程中读取入口文件出现 EPERM，使用原构建命令在获准的进程环境复核通过，98 modules）；`git diff --check` PASS。新增末位校验码、日期错误与列表字段回归断言。真实 GUI 人工复验 **NOT RUN**；UI-C 第 2 项继续为 PARTIAL PASS，不提交、不进入第 3 项。

**后续截图纠正：** 前一次编辑截图的测试号码末位为 `1`，校验码确实不符；新列表截图中同一号码末位已改为 `2`，完整身份证号通过校验，出生日期为 1993-07-23，按 2026-10-14 应显示 33 周岁。先前对新截图仍称“待核对”属于错误判断。原因是列表和详情只读取响应的 `birthDate` 字段；该运行页面未收到可用的派生日期，尽管已收到有效 `idCardNo`。现改为优先依据身份证号按与编辑表单相同的校验/日期解析逻辑显示出生日期和实时周岁；号码无效时仍不推断年龄。没有变更身份证原值、数据库或后端校验。新增有效号码、年龄及列表派生回归；管理端完整测试和 build PASS。真实 GUI 复验仍 **NOT RUN**，请业务方刷新页面确认日期与年龄。

### UI-C 第 2 项补充：证件唯一性与表单只读字段（2026-10-14）

业务方新截图确认两名司机显示同一身份证号，当前 5176 GUI 此项 **FAIL / BLOCKER**。只读核查表明 5176 指向隔离测试库 `fleet_ops_v3r_ui2_20261013045339`（MySQL 127.0.0.1:13308），该库仍是证件整改前的旧结构：`driver.id_card_no`、`driver.driving_license_no` 可空，无证件唯一索引；`vehicle.vehicle_license_no` 字段尚不存在。库中确有 1 组重复身份证号、对应截图的 2 条司机记录。这是旧测试库/运行后端未切到最终模型的环境事实，不代表最终 baseline 允许重复。未修改这两条既有司机记录，也未在旧库上追加兼容补丁或清库。

当前最终 `database/database.sql`、Flyway V1 和测试 schema 已分别对司机身份证号、驾驶证号、车辆行驶证号设置 `NOT NULL` 与唯一索引；当前 `MasterDataService` 的新增/编辑路径会校验三项重复并给出中文冲突提示，数据库索引在并发下作最终兜底。本轮新增“编辑成其他记录证件号”的后端负向测试，`MasterDataApiTests` 10 项全部 PASS、0 failed、0 errors、0 skipped；管理端完整测试和 build PASS。**5176 现有库本身仍未满足该约束，不能将其 GUI 记为 PASS；后续必须在保留旧测试数据可追溯的前提下切换到单一最终 baseline 的隔离测试库，并由业务方确认两名司机的真实证件归属后再决定旧数据处理。**

同时按业务方要求，从新增/编辑司机弹窗移除不可输入的“出生日期”“年龄”字段；两者继续在司机列表/详情按有效身份证号派生显示。未改变证件写入规则、其它业务模块或正式数据库资料。

业务方选择**保留当前 5176 测试库，待人工更正重复资料后再复验**。因此本轮不替换或清理该库，也不替业务方猜测另一名司机的身份证号。需要注意：仅更正两条数据仍不会自动产生旧库缺失的唯一索引及车辆行驶证字段，不能据此宣称 5176 已满足最终数据库约束；正式唯一性 GUI/API 复验仍需在完整最终单一 baseline 环境进行。

### 5176 原隔离库原位结构更新（2026-10-14，待车辆资料补齐）

业务方已人工更正重复身份证号，并要求只保留 5176 作为当前 GUI 环境、沿用其数据库。更新前在 `fleet_ops_v3r_ui2_20261013045339` 复查：53 张表；身份证及驾驶证号重复组均为 0，两个字段的空值均为 0；原 `driver.vehicle_license_no` 和 `driver.birth_date` 均无非空值。将该库与最新已验证的最终空库逐表比较列类型、可空性、默认值、索引、约束和外键，差异仅为司机/车辆证件列及三个唯一索引，外键无差异。更新前通过 `mysqldump --single-transaction` 将现有库备份到本机未跟踪的 `.runtime/v3r-ui2-before-schema-20261014035221.sql`，SHA-256 已记录在本机操作日志；未触碰正式业务库或本机配置。

停止经进程命令行确认的 8082 旧后端后，对原 5176 库原位调整：司机身份证号收敛为 `varchar(18) NOT NULL`，驾驶证号收紧为 `NOT NULL`；删除两个确定为空且不属于最终模型的旧司机列；加上身份证号、驾驶证号唯一索引；车辆新增 `vehicle_license_no` 及唯一索引。原有两辆测试车并无行驶证号来源，**未伪造号码**，故该列暂时允许 `NULL`，与最终 baseline 的 `NOT NULL` 尚有这一处差异。待业务方在 5176 页面分别录入真实且不同的号码后，须原位执行 `NOT NULL` 收紧并再做完整结构比较，不能把当前过渡状态写成最终数据库一致性 PASS。

5176 仍由原 Vite 进程服务，代理到已重启的最新版测试后端 8082；额外的 5175 Vite 进程已停止。管理员经 5176 正式登录及车辆列表 API 成功。后端对空的行驶证号在 JSON 中省略属性，因此前端不再凭列表中是否出现该属性判断服务端版本；保存时仍要求服务端响应的行驶证号与提交值一致，否则在编辑弹窗显示错误，避免“提示保存成功、重开仍为空”。管理端完整测试 PASS，build PASS；`git diff --check` 退出码 0。

随后业务方在 5176 页面为原有两辆测试车分别填写了行驶证号。只读复核结果：车辆 2 条，行驶证号缺失 0、重复 0，司机身份证重复仍为 0。现已在**同一个** 5176 库将 `vehicle.vehicle_license_no` 收紧为最终 `varchar(64) NOT NULL`。将当前库与最新单一 baseline 空库的表、列及属性、索引、CHECK、外键和外键规则共 1,030 条结构元数据逐项比较，**差异 0**。通过 5176 正式登录与车辆列表/详情 API 复核：两辆车行驶证号均返回且互不相同，详情与列表回读一致；报告和日志均未输出号码。原有数据保留，未新增迁移链或第二套业务库。管理端完整测试 PASS、build PASS；运行 JAR 的后端测试报告为 91 tests、0 failures、0 errors、1 skipped，跳过项不计 PASS；`git diff --check` PASS。差异凭据模式检查的三个命中均为代码变量引用，未发现新增明文凭据。本机配置与 `.runtime/` 保持未暂存。

**人工 GUI 复验（2026-10-14）：**业务方在 5176 刷新车辆管理，核查原有两辆车保存后的行驶证号并重新打开详情，回复 **PASS**。该结果仅覆盖本次“行驶证号保存、刷新及回显”场景；证件重复提示等负向操作与 UI-C 第 2 项其它场景未据此自动判为 PASS。UI-C 第 2 项仍为 **PARTIAL PASS**，R7-V3-R-UI/V3-R 未关闭；本轮未提交。

### UI-C 第 4 项：八域导航与费用管理归并（2026-10-14）

业务方最新确认 UI-C 第 1、2、3 项均为 **MANUAL GUI PASS**；第 3 项的 Excel 列宽仅保留为非阻断优化。此前第 2 项报告中的 PARTIAL PASS 是当时的中间状态，不覆盖业务方最新的人工结论。第 4 项尚未由业务方人工复验，状态为 **NOT RUN**。

| 原位置或入口 | 现在的唯一位置 | 实施与边界 |
| --- | --- | --- |
| 九域中的独立“导入中心” | 取消；充电订单导入、充电公司模板配置位于费用管理 | 复用原充电页面及权限，无新导入写入链；任务、轮胎、充电历史仍分别位于对应业务页。 |
| 运输管理→调度中心→自用/外协车辆月度台账 | 运输管理→车辆出勤→自有车辆/外协车辆 | 仅改菜单、标题与面包屑；共用月表、正式发车统计和 Excel API 未变。 |
| 审批中心→我的申请→费用申请 | 费用管理→费用申请 | 仍为原 `ExpenseApplicationV3View.vue`，保留 DAILY/TASK 下拉、DAILY 混合明细、TASK 单笔和 `/approvals` 唯一正式提交。 |
| 费用管理→费用归属独立菜单和路由 | 费用管理→费用台账的“待归属日常费用”筛选与详情归属操作 | 原组件已经复用，现撤下独立权限路由；旧 `#/expense-attribution` 重定向到 `#/expense-ledger` 并默认筛选未归属 DAILY。归属、冲销、历史与车牌硬校验仍走原 API。 |
| 审批中心→我的申请展开父级 | 审批中心→我的申请普通二级页面 | 点击整行进入本人申请记录；待办、审批记录、流程配置为同级，普通审批能力仍在原页面。 |

**实际修改范围：**`admin-web/src/router/navigationModel.js`、`routes.js`、`MonthlyOperationsV3View.vue`、`VehicleMonthlyGrid.vue`、`ExpenseLedgerV3View.vue`、`MyTodosV3View.vue`、`admin-web/test/run-tests.mjs`；正式 `docs/05/06/07 V3.0` 的受影响导航、权限与验收表述；本报告及 `docs/36` 矩阵。未修改数据库、ExpenseEntry、审批入账、充电去重、月结或后端业务代码。

**运行页面检查：**5176 既有管理员会话下，Codex 受控 Chrome 实际显示八个一级域，费用管理下为费用申请、费用台账、充电订单导入、充电公司模板配置；旧归属 URL 实际重定向至费用台账，并显示真实费用金额与两条测试费用记录；审批中心展开后为四个二级叶子，点击“我的申请”进入申请记录且面包屑同步。这是运行环境技术核查，**不记为业务方人工 GUI PASS**。费用申请填写、归属调整提交和其它页面仍须业务方逐项复验。

**四项唯一性局部复查：**需求以 01～07 的最新确认导航为准；费用申请仍走 `/approvals`，充电专项仍走既有导入确认；费用台账是唯一归属主页面，`ExpenseEntry` 和归属历史无第二套写入；旧归属 key 仅是重定向，不再实例化第二个费用页面。权限沿用 `/me.permissions` 与服务端原有原子校验。第 4 项未获人工确认前，R7-V3-R-UI 和 V3-R 均不能关闭。

**本轮自动与运行验证：**管理端完整 `npm test` PASS（17 项当前 V3 核心、7 项 UI1，以及重量证据和充电工作簿回归）；`npm run build` PASS（101 modules，单 chunk 体积提醒非阻断）。后端未改业务代码，审批、充电和统一财务定向测试共 21 条，0 failures、0 errors、1 skipped；跳过项不记 PASS。`git diff --check` 退出码 0；本轮修改范围的高风险 Secret 字面量扫描无命中。原 5176 隔离库数据未重建、未新建审批或费用事实。5176 真实页面进一步核对旧归属深链重定向后默认选中“日常费用/未归属任务”，费用详情展示当前归属、充电原始订单、归属历史与授权归属操作；费用申请菜单进入现有 DAILY 表单，面包屑为“费用管理 / 费用申请”。运行页面检查不等于业务方人工 GUI 验收。

**文档核查：**`docs/05/06/07 V3.0` 均可重新打开且受影响术语、导航及验收文字已核对；`docs/05` 不再含旧独立导入中心或“我的申请”纯展开规则。尝试使用正式 Word 渲染脚本逐页检查，但本机缺少 LibreOffice `soffice.exe`，故逐页视觉检查为 **NOT RUN / NON-BLOCKING**。第 4 项待业务方人工复验八域菜单、费用台账归属操作、充电页面和刷新/后退；未提交，未进入 UI-C 第 5 项。

### R7-V3-R-UI 全站视觉还原（2026-10-15，待集中人工验收）

**依据与范围。** 已读取 `docs/ui-final/车队经营管理系统_UI最终开发交付包/START_HERE_交给Codex.md`、`页面与代码映射.csv`、38 张逐页 PNG 和 Word 设计说明书。设计包只决定视觉和信息层级，业务行为仍由 AGENTS.md、docs/01～07 及正式 API 决定。保留当前八域导航与既有未提交的 UI-B1～B5/UI-C 业务页面，没有重置、清理、提交或改动数据库。当前 HEAD 为 `780ff306c5112c5444a1eb3c519f937ac5f5de2e`。

**实际修改。** 新增 `admin-web/src/design/ui-final.css`，在 `admin-web/src/main.js` 的原有全局样式之后加载。全站统一墨绿侧栏 `#173F38`、选中态 `#347366`、主操作 `#207D6B`、浅灰内容底 `#F4F7F5`、表格行高、筛选密度、卡片、页签、弹窗/抽屉、状态与响应式尺寸。`AppLayout.vue` 将占位“车”字标记替换为本项目通用车辆 SVG，并固定侧栏底部说明；新增 `NavigationIcon.vue`，`NavigationTree.vue` 为八个业务域显示同一线性图标体系。正式页面仍复用已有 PageHeader、FilterBar、DataTable、SummaryCards、ImportTabs、FormDialog/Drawer 等组件及既有 API；本轮未新增业务按钮、写入入口、计算公式或后端契约。

| 业务域 | 对照设计包的页面数 | 实际运行截图检查 | 集中人工 GUI |
| --- | ---: | --- | --- |
| 登录 | 1 | 1366×768、1920×1080 截图已保存 | NOT RUN |
| 工作台 | 2 | 两种分辨率逐页截图 | NOT RUN |
| 运输管理 | 7 | 两种分辨率逐页截图 | NOT RUN |
| 车辆与人员 | 6 | 两种分辨率逐页截图 | NOT RUN |
| 费用管理 | 4 | 两种分辨率逐页截图 | NOT RUN |
| 审批中心 | 4 | 两种分辨率逐页截图 | NOT RUN |
| 工资与考勤 | 3 | 两种分辨率逐页截图 | NOT RUN |
| 经营与结算 | 7 | 两种分辨率逐页截图 | NOT RUN |
| 系统设置 | 3 | 两种分辨率逐页截图 | NOT RUN |
| 非菜单深链 | 1 | 两种分辨率逐页截图 | NOT RUN |

**实际页面证据。** 5176 管理端连接独立 MySQL 测试库 `fleet_ops_v3r_ui2_20261013045339`；5175 未动。受控 Chrome 通过视口缩放核实 CSS `innerWidth×innerHeight` 分别为 1366×768、1920×1080，并对登录页和全部 37 个受保护路由保存了 76 张实际运行截图于 `.runtime/ui-final-screenshots/`。`scan-1366-final.json`、`scan-1920-final.json` 逐页记录标题、导航高亮、加载状态及整页横向溢出；37 个路由均有页面标题，无持续 Loading，横向溢出均为 0。一个非菜单深链 `approvals-queue` 高亮其归属的“我的待办”叶子菜单。典型证据：`dashboard-1366.png`、`dispatch-1366.png`、`routes-1366.png`、`expense-ledger-1366.png`、`approvals-todo-1366.png`、`attendance-1366.png`、`settlements-1920.png`、`audit-1920.png`；另检查了费用详情抽屉和司机编辑弹窗。截图是运行技术检查，**不是业务方 MANUAL GUI PASS**；部分页面仍须用完整业务数据逐项核对操作、权限和异常态。

**视觉与业务取舍。** PNG 中的示例图表、演示金额、顶部搜索/通知图形和旧菜单不作为正式功能；在缺少相应正式 API 时保留现有真实指标或空态，不绘制假数据/假控件。登录页参考其配色与卡片，不复制设计图中的演示表格。所有导入历史仍在对应业务页，费用归属仍在费用台账，DAILY/TASK、充电零元订单、工资与经营事实均未改写。本轮仅做视觉统一和真实页面截图检查，不声称与 PNG 逐像素一致。

**自动验证与安全。** 管理端完整 `npm test` PASS，`npm run build` PASS（受限进程首次读取入口文件遇到 EPERM，在获准进程环境运行同一标准命令后 103 modules 构建成功；单 chunk 体积提醒非阻断）；后端 `mvn test` 为 91 tests、0 failures、0 errors、1 skipped，跳过项不计 PASS；标准 `mvn -DskipTests package` PASS。`git diff --check` 退出码 0；本轮新增样式、图标和布局代码的凭据字面量扫描无命中。本机 `application.yml`、小程序 `project.config.json`、`.runtime/` 和设计包保持未暂存；未提交。

**待验收及关闭状态。** UI-C 第 1～3 项此前业务方确认的 MANUAL GUI PASS 保留；第 4 项八域导航和费用整合仍待复验。本轮全站视觉与交互的业务方集中人工验收均为 **NOT RUN**；不能据截图或构建认定 R7-V3-R-UI / V3-R 通过。后续须在实际业务数据下逐页检查视觉、响应式、权限、弹窗/抽屉及关键操作，再由业务方决定是否关闭。

### 全站视觉差异核查与集中整改准备（2026-10-15）

**核查性质与证据。** 本节是对上一节“全站视觉还原”的逐页复核，之前的主题色、组件统一和 76 张运行截图不构成高保真视觉验收。业务方确认 **批量调度 MANUAL GUI FAIL**，其余页面本轮由 Codex 做截图技术核查，人工视觉状态仍为 **NOT RUN**；UI-C 第 1～3 项此前已确认的特定操作 PASS 保留，不扩展为整页视觉 PASS。当前 HEAD `780ff306c5112c5444a1eb3c519f937ac5f5de2e`，本轮未改 Vue、API、数据库或正式业务文档，未重置、清理、暂存或提交现有工作区。

参考图根目录为 `docs/ui-final/车队经营管理系统_UI最终开发交付包/逐页设计PNG/`；运行截图根目录为 `.runtime/ui-final-screenshots/`，每行的截图代号对应 `{代号}-1366.png` 和 `{代号}-1920.png`。38 个页面在两个 CSS 视口均有实际 5176 浏览器截图；另有本轮真实运行的 `.runtime/ui-visual-audit/dispatch-draft-1366.png` 与 `dispatch-draft-current.png`，只在本地工作台加入一条未发布草稿，没有正式发布。两种分辨率的页面级扫描未发现整页横向溢出或持续 Loading；这只能说明路由能显示，**不能说明首屏布局、视觉忠实度或关键操作通过**。实际运行环境沿用 5176 隔离测试库，不影响 5175。

以下“结构图”仅可对照区域组织、信息层级和通用视觉规范，不做像素级高保真结论。设计图中的演示数字、旧菜单、顶部搜索/通知、额外图表和不存在的业务按钮均不是正式功能依据；八域导航、司机/车辆证件字段、三段固定月表、DAILY/TASK、费用归属、Participant 工资、月结快照继续以正式 V3.0 和现有 API 为准。严重程度按本任务 P0/P1/P2 定义；“截图”列为运行证据代号，不代表人工 PASS。

| 页面与路由 | 实际组件 | 设计 PNG（依据） | 截图代号 | 布局与视觉差异、状态/业务边界 | 级别 | 整改方式 |
| --- | --- | --- | --- | --- | --- | --- |
| 登录（无保护路由） | `LoginView.vue` | `01_登录_登录.png`（结构图） | `login` | 结构图是演示界面；实际居中登录卡缺少参考的品牌层次，但不可复制演示表格/第三方登录。失败态需人工复验。 | P2 | 样式微调 |
| 经营概览 `#/dashboard` | `DashboardV3View.vue` | `02_工作台_经营概览.png` | `dashboard` | 实际 KPI 后是稀疏待办和说明卡，首屏大片空白，钻取层次弱；不得补造趋势图或金额。 | P1 | 布局重构 |
| 工作台待办 `#/my-todos` | `MyTodosV3View.vue` | `03_工作台_我的待办.png` | `my-todos` | 三张摘要卡占高，真实一行列表与详情操作分离，空白多；仍与审批中心共用待办数据。 | P1 | 组件调整 |
| 货物管理 `#/products` | `MasterDataUi2View.vue` | `04_运输管理_货物管理.png`（结构图） | `products` | 现有筛选/列表可用，少量数据时密度与空态间距可收紧；不增加图中示意的未实现操作。 | P2 | 样式微调 |
| 线路模板 `#/templates` | `TransportTasksV3View.vue` | `05_运输管理_线路模板.png`（结构图） | `templates` | 一行表格里长文本、金额与操作拥挤，页下大面积空白；不得增设未冻结的独立去返程字段。 | P1 | 组件调整 |
| **批量调度 `#/dispatch`** | `DispatchWorkbenchV3View.vue` | `06_运输管理_批量调度.png`（高保真参考 `02_批量调度.png`） | `dispatch`＋`dispatch-draft-1366` | **人工视觉 FAIL**：当前为上下堆叠的大表单卡，司机卡文字拥挤且换行；1366×768 下加入一行草稿后待发布表及确认操作仍在首屏之外，1920 下选择区留白过大；日期/模板/司机/任务表/主按钮的信息层级明显偏离高保真工作台。不得改整批原子、幂等或一行一任务。 | **P0** | **布局重构** |
| 任务导入 `#/task-imports` | `TaskImportsV3View.vue` | `07_运输管理_任务导入.png`（结构图） | `task-imports` | 上传卡较高，预览/历史入口信息密度低；只读预览和原文件追溯不能因收紧布局受损。 | P1 | 组件调整 |
| 运输任务 `#/routes` | `TransportTasksV3View.vue` | `08_运输管理_运输任务.png` | `routes` | 实际高筛选区＋横向宽表，关键任务/状态/操作被挤压，参考图是紧凑列表与侧边详情；证据列及状态机须保留。 | P1 | 布局重构 |
| 自有车辆 `#/dispatch-internal` | `MonthlyOperationsV3View.vue` | `09_运输管理_自有车辆.png` | `dispatch-internal` | 当前已通过的三段 sticky 月表不同于旧设计彩色日历；仅数字、表头和留白可微调，不以示例统计覆盖真实发车事实。 | P2 | 样式微调 |
| 外协车辆 `#/dispatch-outsourced` | `MonthlyOperationsV3View.vue` | `10_运输管理_外协车辆.png` | `dispatch-outsourced` | 同上；保留外协车牌/司机组合和固定合计，不引入公司车绑定。 | P2 | 样式微调 |
| 司机管理 `#/drivers` | `MasterDataUi2View.vue` | `11_车辆与人员_司机管理.png` | `drivers` | 证件、生日、年龄、绑定列造成宽表，当前缺少参考图的清晰主次与侧边详情；不能为视觉压缩删除已确认的证件字段。 | P1 | 组件调整 |
| 车辆管理 `#/vehicles` | `MasterDataUi2View.vue` | `12_车辆与人员_车辆管理.png` | `vehicles` | 行驶证、限载、比例、达标吨数及绑定列紧凑度不足，操作易沉入横向滚动；维持两项达标字段和证件事实。 | P1 | 组件调整 |
| 车挂管理 `#/trailers` | `MasterDataUi2View.vue` | `13_车辆与人员_车挂管理.png`（结构图） | `trailers` | 当前列表结构基本相符，表格密度/空态需小幅统一；不得借图增字段。 | P2 | 样式微调 |
| 绑定关系 `#/binding` | `MasterDataUi2View.vue` | `14_车辆与人员_绑定关系.png` | `binding` | 两类绑定页签与列表分散，当前关系和历史的主次不如参考清晰；仍由正式 API 校验换绑和外协限制。 | P1 | 布局重构 |
| 轮胎库存 `#/tires` | `TiresUi2View.vue` | `15_车辆与人员_轮胎库存.png` | `tires` | KPI 与少量列表记录间距偏大，详情入口弱于参考的列表/侧栏结构；Web 仍不得有换胎申请。 | P1 | 组件调整 |
| 轮胎入库 `#/tire-imports` | `TireImportsV3View.vue` | `16_车辆与人员_轮胎入库.png` | `tire-imports` | 上传与历史页签保留，但上传区空白偏大、说明/确认层级弱；固定四列和只读预览不变。 | P1 | 组件调整 |
| 费用申请 `#/expense-apply` | `ExpenseApplicationV3View.vue` | `17_费用管理_费用申请.png` | `expense-apply` | 当前申请信息/明细/汇总为三块高卡片，首屏行编辑区靠下；可重排紧凑分区，不得恢复互斥录入或改变 `/approvals`。 | P1 | 布局重构 |
| 费用台账 `#/expense-ledger` | `ExpenseLedgerV3View.vue` | `18_费用管理_费用台账.png` | `expense-ledger` | 筛选折叠、KPI、明细宽表层级松散，状态/操作在横向滚动后可见性差；详情、归属、冲销仍是同一正式入口。 | P1 | 布局重构 |
| 充电订单导入 `#/charging-imports` | `ChargingImportsV3View.vue` | `19_费用管理_充电订单导入.png` | `charging-imports` | 选择/上传区高而空，未形成参考图清楚的“上传→只读预览→确认”纵向进程；不得把样例订单或可编辑预览引入。 | P1 | 布局重构 |
| 充电公司模板 `#/charging-companies` | `ChargingImportsV3View.vue` | `20_费用管理_充电公司模板配置.png` | `charging-companies` | 实际为稀疏全宽公司表，映射配置需跳入编辑，参考是公司列表＋映射详情；保留仅四字段和两种修改模式。 | P1 | 布局重构 |
| 审批待办 `#/approvals-todo` | `MyTodosV3View.vue` | `21_审批中心_我的待办.png` | `approvals-todo` | 大摘要卡与一行列表导致待办优先级不突出，详情处理区不在首屏；不能增加重复申请入口。 | P1 | 组件调整 |
| 我的申请 `#/approvals-mine` | `ApprovalCenterV3View.vue` | `22_审批中心_我的申请.png` | `approvals-mine` | 申请记录列表较稀疏，状态/时间线入口不够突出；费用申请主入口已迁至费用管理。 | P1 | 组件调整 |
| 审批记录 `#/approvals-history` | `ApprovalCenterV3View.vue` | `23_审批中心_审批记录.png`（结构图） | `approvals-history` | 当前表格基本按结构呈现，数据少时空白可收敛；不增加审批写入按钮。 | P2 | 样式微调 |
| 流程与模板 `#/approvals-flows` | `ApprovalCenterV3View.vue` | `24_审批中心_流程与模板配置.png` | `approvals-flows` | 高保真为流程列表、版本/节点、详情并列；当前首屏仅筛选＋一行流程列表，“查看步骤”后才见关键结构，层次差异明显。只可用现有节点/版本能力，不新增审批类型。 | **P0** | **布局重构** |
| 司机出勤 `#/attendance` | `MonthlyOperationsV3View.vue` | `25_工资与考勤_司机出勤.png` | `attendance` | 当前正式月宽表应保留，参考的额外图表不可伪造；可压缩筛选/摘要，使月格首屏更多可见。 | P1 | 组件调整 |
| 工资台账 `#/salaries` | `SalaryLedgerV3View.vue` | `26_工资与考勤_工资台账.png` | `salaries` | 多行筛选和汇总将正式 Participant 列表压至下方，缺少参考的密集金额/明细层级；工资只读同一事实。 | P1 | 布局重构 |
| 转派核薪 `#/salary-transfers` | `SalaryLedgerV3View.vue` | `27_工资与考勤_转派工资核对.png` | `salary-transfers` | 高筛选区、摘要与空列表占比失衡；多参与人链和核薪区须在有数据详情态再验。 | P1 | 布局重构 |
| 公司经营 `#/finance-company` | `FinanceReportsV3View.vue` | `28_经营与结算_公司经营.png` | `finance-company` | 实际 KPI 后是文字说明/大片留白，缺少清晰的经营明细；设计图表若无正式 API 不可伪造。 | P1 | 布局重构 |
| 线路经营 `#/finance-templates` | `FinanceReportsV3View.vue` | `29_经营与结算_线路模板经营.png` | `finance-templates` | 当前月份筛选＋稀疏表，模板汇总/任务穿透层级弱；真实数据不足使详情态暂不可定论。 | P1 | 布局重构 |
| 任务经营 `#/finance-tasks` | `FinanceReportsV3View.vue` | `30_经营与结算_任务经营.png` | `finance-tasks` | 首屏是筛选＋空表，收入与费用、工资、利润的任务详情结构未在截图中验证；不生成前端财务算法。 | P1 | 布局重构 |
| 车辆支出 `#/finance-vehicles` | `ExpenseLedgerV3View.vue` | `31_经营与结算_车辆支出.png` | `finance-vehicles` | 多行高筛选/KPI 压缩了正式费用明细首屏；与费用台账复用可减少分叉。 | P1 | 组件调整 |
| 司机支出 `#/finance-drivers` | `ExpenseLedgerV3View.vue` | `32_经营与结算_司机支出.png` | `finance-drivers` | 同一复用页的筛选高、空表占比大；不能把司机工资与费用重复相加。 | P1 | 组件调整 |
| 能源费用 `#/finance-energy` | `ExpenseLedgerV3View.vue` | `33_经营与结算_能源费用.png` | `finance-energy` | 有数据时筛选和金额摘要仍偏高，横向表格主次欠清晰；零元充电订单仍不得计费。 | P1 | 组件调整 |
| 月度结算 `#/settlements` | `SettlementV3View.vue` | `34_经营与结算_月度结算.png` | `settlements` | 当前所选月份无版本，列表很薄且下方空白；历史版本/差异必须用有数据状态再核查，不能造结算数。 | P1 | 布局重构 |
| 用户管理 `#/users` | `SystemUi1View.vue` | `35_系统设置_用户管理.png` | `users` | 实际列表有多行，主体信息可读；操作列、筛选间距与参考仍有小差异。 | P2 | 样式微调 |
| 角色权限 `#/roles` | `SystemUi1View.vue` | `36_系统设置_角色权限.png` | `roles` | 设计为角色列表与权限分组并列，当前单张宽表把权限树放在二次入口，首屏无法理解角色权限关系；权限编辑仍须用正式 API。 | **P0** | **布局重构** |
| 操作日志 `#/audit` | `SystemUi1View.vue` | `37_系统设置_操作日志.png` | `audit` | 中文列表已有，筛选/表格密度和详情默认可见性有小差异；原始动作码只在详情中。 | P2 | 样式微调 |
| 审批处理深链 `#/approvals-queue` | `ApprovalCenterV3View.vue` | `38_非菜单深链_审批处理.png` | `approvals-queue` | 当前深链落在队列列表，参考是完整处理详情；现有详情需二次点击，处理态/合法回退步骤须用真审批复核。不得复制第二套审批页。 | P1 | 布局重构 |

**严重程度与整改对象。** 本轮矩阵为 **P0 3 页、P1 27 页、P2 8 页**。P0 为批量调度、流程与模板配置、角色权限；前者已由业务方人工 FAIL，后两者是截图技术审计结论，尚待业务方确认优先级。需要页面级布局重构的还包括经营概览、运输任务、绑定关系、费用申请/台账、充电订单/模板、工资/转派核薪、公司/线路/任务经营、月度结算及审批处理深链。上述“重构”仅指现有页面排版与信息层级，不代表重做业务状态、API 或数据模型。P2 页及自有/外协月表不宜为追求旧稿形式整体推翻。

**可由公共组件解决的差异。** `PageHeader` 统一标题/说明/主操作相对位置；`FilterBar`/`FilterPanel` 控制筛选在 1366 宽度的行数及折叠反馈；`DataTable`/`Pagination` 统一列优先级、表头和金额对齐，宽表只在容器内滚动；`SummaryCards` 改紧凑高度而不改变金额来源；`DetailDrawer`、`FormDialog`、`ImportTabs` 和空/错/加载态统一标题、操作区、留白及键盘焦点。公共组件只能解决跨页重复症状，不能代替批量调度等页面自身的布局重排；尤其不能仅再次换色。

**集中整改建议（待业务方确认，尚未实施）。** 第一批处理 P0：`DispatchWorkbenchV3View.vue` 及其司机/草稿表格样式、`ApprovalCenterV3View.vue` 的流程配置和处理详情、`SystemUi1View.vue` 的角色权限布局。第二批处理主业务 P1：`TransportTasksV3View.vue`、`MasterDataUi2View.vue`、`ExpenseApplicationV3View.vue`、`ExpenseLedgerV3View.vue`、`ChargingImportsV3View.vue`，优先修复首屏任务/费用表和列表＋详情关系。第三批处理工资、经营与结算：`SalaryLedgerV3View.vue`、`MonthlyOperationsV3View.vue`、`FinanceReportsV3View.vue`、`SettlementV3View.vue`。最后以公共组件和 P2 页面做跨站一致性检查。每批均需复拍 1366×768/1920×1080 的有数据与关键交互态，并回归相应业务；本轮未获整改实施授权，不改页面代码。

**状态覆盖与证据缺口。** 76 张既有截图覆盖默认/当前数据页面；本轮另核对批量调度一条本地草稿、既有费用详情抽屉及司机编辑弹窗。多数路由当前只有 0～2 条测试记录。状态覆盖如下，`已截图` 仅表示技术证据，不等于视觉 PASS。

| 状态 | 本轮可核查证据 | 尚需补拍/人工复核 |
| --- | --- | --- |
| 有数据、无数据 | 38 个路由的现有隔离库默认截图，两种视口；各页数据量不一 | 真实多行、跨页、长字段和关键经营数据，NOT RUN |
| 筛选结果 | 默认筛选界面可见 | 多条件查询后表格与汇总联动，NOT RUN |
| 详情抽屉 | 既有 `expense-detail-1366.png` | 任务、审批、月结与充电订单详情，NOT RUN |
| 编辑弹窗 | 既有 `driver-edit-1366.png` | 车辆、模板、权限等长表单与未保存确认，NOT RUN |
| 上传与预览 | 导入默认上传页可见 | 真实 Excel 解析后的表格、错误行及只读预览，NOT RUN |
| 错误与无权限 | 默认页面有通用组件 | 字段错误、API 失败、权限撤回与附件下载拒绝，NOT RUN |
| 确认操作 | 批量调度本地加入草稿，未发布 | 发布、审批、费用、导入的确认弹窗，NOT RUN |

批量调度的 1366 草稿截图证明首屏布局 P0，但未执行发布。1366×768 与 1920×1080 的技术截图均可比对；没有把截图尺寸猜作视口，视口值来自浏览器运行时记录。后续整改批次须先补齐真实数据和状态证据，再做逐页集中人工复验。

**阶段状态。** 本轮仅追加视觉审计；R7-V3-R-UI 与 V3-R 均未关闭。批量调度为 **MANUAL VISUAL FAIL**，其余本轮全站视觉为 **NOT RUN（人工）**，等待业务方确认整改范围。

## 全站集中视觉整改实施记录（2026-10-15；待业务方集中复验）

本轮以 `docs/ui-final/车队经营管理系统_UI最终开发交付包` 为视觉参考，以正式 V3.0 文档和既有 API 为业务边界，在现有未提交工作区增量修改。原始 P0/P1/P2 判级和人工结论保留；下表的“已调整”只表示代码和技术截图，不代表人工 GUI PASS。原先批量调度的 **MANUAL VISUAL FAIL** 在业务方复验前不改写。

**共用层。** `design/ui-final.css` 收敛内容最大宽度、标题/卡片/筛选/摘要/表格密度、空态、分页和 1366 宽度间距；`DataTable.vue` 对金额与次数列右对齐并使操作列在表格内部横向滚动时可见。没有修改后端、数据库、OpenAPI、状态机、金额计算或业务写入契约。1920 宽度下正文限制为 1520px，避免字段/表格无限拉伸；较宽的业务表仍只在自身容器滚动。

**38 页逐项处置。** 运行截图目录为 `.runtime/ui-visual-remediation/`；除登录页外，每个截图代号都有 `{代号}-1366x768.png` 和 `{代号}-1920x1080.png`。两组图由 5176 实际管理端截取，浏览器报告的 CSS 视口分别为准确的 1366×768、1920×1080，图像尺寸亦一致。默认页截图可能包含数据请求中的短暂加载态，因此仅用作布局技术证据；有数据的详情、弹窗、预览、错误和权限状态需单独复验。登录页仍参考此前 `.runtime/ui-final-screenshots/login-{1366,1920}.png`，因已登录会重定向，本轮未为重拍而退出隔离测试账号。

| 序号 | 页面/截图代号 | 本轮布局处理 | 未覆盖的状态 |
| --- | --- | --- | --- |
| 01 | 登录 `login` | 沿用全局颜色、字号和按钮规范；结构图不作为新业务入口 | 本轮未重拍登录失败态 |
| 02 | 经营概览 `dashboard` | 六项经营指标改为均衡三列；待办与经营口径并排，正式金额仍由 API 返回 | 真实多月份/无数据交互 |
| 03 | 工作台待办 `my-todos` | 共用摘要卡、表格和空态收紧；与审批待办共用数据 | 多条待办详情 |
| 04 | 货物管理 `products` | 共用筛选、表格、分页和操作列密度 | 长名称编辑弹窗 |
| 05 | 线路模板 `templates` | 默认金额改为紧凑标签；共用宽表操作列 | 多条长名称与详情 |
| 06 | 批量调度 `dispatch` | **P0 布局重构**：日期/模板横排、模板金额摘要、司机搜索与清晰卡片占满工作区，待发布任务及整批确认保持独立；`dispatch-draft-{1366x768,1920x1080}.png` 为仅在浏览器内加入一行的证据，未正式发布 | 整批确认弹窗与多行长表需人工复验 |
| 07 | 任务导入 `task-imports` | 压缩上传卡，保留只读预览/历史页签 | 实际 Excel 预览与失败行 |
| 08 | 运输任务 `routes` | 筛选项顶部对齐，金额标签简化，表格操作列固定在容器中 | 详情抽屉及长任务号多行 |
| 09 | 自有车辆 `dispatch-internal` | 共用紧凑标题/筛选；保留已人工通过的 sticky 月表 | 有数据跨页/导出视觉 |
| 10 | 外协车辆 `dispatch-outsourced` | 同上，不照搬旧稿的假车辆资产 | 有数据跨页/导出视觉 |
| 11 | 司机管理 `drivers` | 共用表格密度与操作列；身份证、驾驶证、生日和年龄未删 | 证件编辑弹窗与长文本 |
| 12 | 车辆管理 `vehicles` | 共用表格密度与操作列；达标比例/吨数及行驶证均保留 | 编辑/详情和验证错误 |
| 13 | 车挂管理 `trailers` | 结构图只指导排版；共用表格/空态 | 绑定历史长列表 |
| 14 | 绑定关系 `binding` | 当前关系摘要改为横向紧凑条，列表/历史操作保留 | 换绑、解绑确认弹窗 |
| 15 | 轮胎库存 `tires` | 共用摘要/表格间距与操作列，Web 换胎入口未增加 | 库存详情和授权编辑 |
| 16 | 轮胎入库 `tire-imports` | 压缩上传区；历史表格未重构 | 四列真实预览/错误行 |
| 17 | 费用申请 `expense-apply` | 申请信息双列、明细/汇总/提交区紧凑，修正步骤标题挤压；DAILY/TASK 原规则不变 | 多行混合编辑、TASK 切换、提交确认 |
| 18 | 费用台账 `expense-ledger` | 共用紧凑金额摘要、宽表及固定操作列；归属仍在唯一详情入口 | 归属抽屉、冲销确认、长字段 |
| 19 | 充电订单导入 `charging-imports` | 压缩上传区与无文件空态，保留只读预览和原文件入口 | 真实 Excel 预览/异常/确认 |
| 20 | 充电公司模板 `charging-companies` | 公司列表与现有编辑器并列，`charging-companies-edit-1366x768.png` 为编辑态截图 | 新样本重新匹配、取消确认 |
| 21 | 审批待办 `approvals-todo` | 共用摘要/列表密度；处理仍复用正式审批详情 | 多节点退回选择和处理确认 |
| 22 | 我的申请 `approvals-mine` | 申请列表压缩，状态和详情入口保留 | 不同状态详情/时间线 |
| 23 | 审批记录 `approvals-history` | 结构图只指导表格间距，不新增申请入口 | 多记录/长备注 |
| 24 | 流程与模板 `approvals-flows` | **P0 布局重构**：进入页面即只读加载首个流程，左侧版本列表与右侧冻结步骤并列；仍使用既有版本 API | 多节点、无流程、新版本编辑 |
| 25 | 司机出勤 `attendance` | 月份/司机筛选横排，摘要紧凑，保留真实发车宽表 | 点击有出勤日期的任务抽屉 |
| 26 | 工资台账 `salaries` | 六项筛选改成规则网格，实体搜索/选择在同一行，Participant 工资表格更早进入首屏 | 多参与人详情及导出 |
| 27 | 转派核薪 `salary-transfers` | 复用相同筛选/摘要，未复制工资计算 | 有转派数据的全参与人核薪抽屉 |
| 28 | 公司经营 `finance-company` | 正式汇总后把经营明细入口与费用归属口径分栏，不伪造曲线或利润 | 历史月份和钻取后明细 |
| 29 | 线路模板经营 `finance-templates` | 共用月份筛选/表格/空态；不伪造未完成任务收入 | 有经营数据的模板钻取 |
| 30 | 任务经营 `finance-tasks` | 共用紧凑筛选/表格；仍由正式财务 API 提供金额 | 有数据的任务详情 |
| 31 | 车辆支出 `finance-vehicles` | 复用费用台账的紧凑筛选、金额卡与固定操作列 | 多车辆费用与详情 |
| 32 | 司机支出 `finance-drivers` | 同一只读费用事实页，不前端叠加工资 | 多司机费用与详情 |
| 33 | 能源费用 `finance-energy` | 同一费用表格/金额规则，不将零元来源订单计费 | 充电与普通费用混合来源 |
| 34 | 月度结算 `settlements` | 共用紧凑月份/版本表及正式空态，不生成示例结算版本 | 有数据的旧版、差异、详情 |
| 35 | 用户管理 `users` | 共用表格/筛选/操作列和弹窗规范 | 重置密码及权限撤回状态 |
| 36 | 角色权限 `roles` | **P0 布局重构**：角色列表左、正式权限分组只读预览右；授权仍在原编辑器和 API，保存后刷新右侧预览 | 多权限角色编辑和撤权反馈 |
| 37 | 操作日志 `audit` | 共用筛选、表格和详情样式；原始动作码仍只在详情 | 长审计内容/错误态 |
| 38 | 审批处理深链 `approvals-queue` | 同一审批组件在深链进入时自动打开首条待办详情，未新增审批页面或写入 API | 多实例/无待办及合法指定步骤打回 |

设计包中仅有结构图的登录、货物管理、任务导入、车挂管理和审批记录仅按统一规范收口，**不声称像素级还原**。高保真稿中的“预建待分配任务、模拟经营图表、示例车队数字”等与当前正式 V3 事实或 API 不符，未照搬。截图证据是自动技术检查；38 页的业务方集中视觉结论仍为 **NOT RUN**，批量调度原有 **MANUAL VISUAL FAIL** 等待复验。

**验证及限制。** 管理端完整 `npm test` PASS（17 项当前 V3 核心、7 项 UI1、重量证据与充电工作簿用例）；标准 `npm run build` PASS（105 modules，单 chunk 大小提示 NON-BLOCKING）。受限进程直接构建曾因读取 `main.js` 的 EPERM 失败，在获准构建进程中运行相同标准命令通过。本轮只动管理端布局/只读展示和前端导航属性，后端源码未改；后端 91 tests/0 failures/0 errors/1 skipped 的先前证据仍按原报告保留，本轮未重新声称执行。`git diff --check` 退出码 0，涉及前端文件的高风险 Secret 字面量扫描 0 命中。`.runtime/` 截图不暂存，原有本机配置与全部未提交成果保持原状。

**阶段状态。** P0 三页已有代码和两种视口技术截图；其余 35 页按上表完成布局处置或明确结构/数据边界。真实 Excel 预览、复杂详情/编辑/确认、错误/无权限、多记录分页及结算差异等状态尚未完成集中人工视觉复验，不能将本轮技术取证写成 MANUAL GUI PASS。R7-V3-R-UI 和 V3-R 均保持未关闭；不提交，不进入其它业务阶段。

## 最新 38 页集中视觉审查收尾（2026-10-15；待业务方集中复验）

依据业务方提供的《全站集中视觉审查报告.md》《38 页视觉审查矩阵.csv》再次收口。**最新截图审查分级为 P0 0 页、P1 25 页、P2 13 页**；这是自动/技术审查的优先级变化，不撤销此前批量调度的 **MANUAL VISUAL FAIL**，也不意味着任何页面已获新的人工 PASS。正式业务规则、API、数据库及八域导航未因本轮视觉调整而改变。

**本轮增量修改。** 在前批页面布局基础上，`DispatchWorkbenchV3View.vue` 把日期/模板摘要与司机选择安排成更清楚的双区工作台，司机卡片显示完整名称及车辆信息，三行本地草稿能在首屏进入可编辑表格，行错误与发布汇总更醒目；发布仍由原正式 API 整批确认。`ApprovalCenterV3View.vue` 收紧流程版本编辑区与节点卡片，角色选择按内容排布、保存区保持可达。`SystemUi1View.vue` 将权限域改为可折叠分组，搜索、已选数与保存区在长权限树中保持可见，权限提交仍使用原接口。`DataTable.vue` 增加列标识供共用样式统一处理长编号和数字列；`ui-final.css` 收敛表格、抽屉及权限树。工资、费用、经营、运输任务列表将金额/司机/车辆等主要列提前，保留全部原字段和操作；费用编号仍完整显示，不以隐藏正式字段解决拥挤。此前 UI-B1～B5 及视觉集中整改的未提交成果全部保留。

**逐页处置与证据。** 路由和截图路径见 [截图索引](../.runtime/ui-visual-final/screenshot-index.csv)。下表“技术处理”指本轮及保留的前批视觉代码；“待验”表示仍需集中人工视觉复验，尤其数据稀少或仅有结构图的页面。默认截图来自真实 5176 管理端，不把截图记为 GUI PASS。

| 页面/路由 | 最新级别 | 技术处理或明确边界 | 待验 |
| --- | --- | --- | --- |
| 经营概览 `dashboard` | P1 | 保留 API 汇总/待办分层，紧凑指标卡；无正式趋势数据不造图 | 多月份明细 |
| 工作台待办 `my-todos` | P2 | 复用正式待办及共用表格/空态 | 不同权限 |
| 货物管理 `products` | P2 | 共用筛选、表格与表单密度 | 长名称编辑 |
| 线路模板 `templates` | P2 | 长字段列宽与金额标签复核 | 多模板详情 |
| 批量调度 `dispatch` | P1 | 司机卡片、草稿行、发布摘要针对性重排；三条未提交草稿双视口截图 | 整批确认、人工 FAIL 复验 |
| 任务导入 `task-imports` | P2 | 上传/历史沿用共用页签和表格 | 真实预览/错误行 |
| 运输任务 `routes` | P1 | 司机、车辆列提前，操作列保持表格内可达 | 多参与人详情 |
| 自有车辆 `dispatch-internal` | P1 | 共用固定列月表；本月 1 次实际发车数据加载后重拍 | 横向滚动/导出 |
| 外协车辆 `dispatch-outsourced` | P1 | 同一月表组件，2 次实际发车数据加载后重拍 | 历史司机/车牌 |
| 司机管理 `drivers` | P1 | 证件与年龄保留；共用长字段和状态规范 | 长证件/编辑 |
| 车辆管理 `vehicles` | P1 | 比例、吨数及行驶证保留，表格列密度复核 | 编辑详情 |
| 车挂管理 `trailers` | P2 | 沿用车辆资料表格规范 | 绑定历史 |
| 绑定关系 `binding` | P1 | 司机/车与车/挂仍用正式两区；共用弹窗 | 换绑冲突 |
| 轮胎库存 `tires` | P2 | 胎号/条码与状态保留 | 详情编辑 |
| 轮胎入库 `tire-imports` | P2 | 共用导入页签和历史布局 | 真实 Excel |
| 费用申请 `expense-apply` | P1 | 三段式申请信息/明细/汇总；DAILY 两条本地明细及 TASK 单笔双视口截图 | Excel 混合/提交确认 |
| 费用台账 `expense-ledger` | P1 | 金额列提前，编号完整可读、详情与归属原入口保留 | 长编号窄屏最终视觉、冲销 |
| 充电订单导入 `charging-imports` | P1 | 上传/只读预览/历史层级沿用前批布局 | 真实 Excel/异常 |
| 充电公司模板 `charging-companies` | P1 | 四字段两种修改模式及保存/取消保留 | 新样本重选 |
| 审批待办 `approvals-todo` | P2 | 复用正式待办与共用状态标签 | 多节点处理 |
| 我的申请 `approvals-mine` | P2 | 状态/详情入口沿用共用表格 | 时间线 |
| 审批记录 `approvals-history` | P2 | 结构图仅指导密度，不称像素级还原 | 长备注详情 |
| 流程与模板 `approvals-flows` | P1 | 节点编辑表单双列、步骤卡片紧凑、保存区可达；两节点未保存截图 | 流程发布/人工复验 |
| 司机出勤 `attendance` | P1 | 沿用实际发车月宽表与独立滚动 | 同日多趟详情 |
| 工资台账 `salaries` | P1 | Participant 金额列前移、数字对齐，详情截图 | 多参与人/导出 |
| 转派核薪 `salary-transfers` | P1 | 与工资台账共用筛选和摘要，不复制工资事实 | 全参与人核薪 |
| 公司经营 `finance-company` | P1 | 正式汇总与明细入口保留；无 API 的曲线不绘制 | 历史月份 |
| 线路模板经营 `finance-templates` | P1 | 收入/利润前移，共用空态 | 模板穿透 |
| 任务经营 `finance-tasks` | P1 | 收入/利润前移，共用紧凑表格 | 任务详情 |
| 车辆支出 `finance-vehicles` | P1 | 复用费用台账金额优先列及筛选 | 多车费用 |
| 司机支出 `finance-drivers` | P1 | 同一费用页，不额外叠加工资 | 多司机费用 |
| 能源费用 `finance-energy` | P1 | 同一费用页，充电来源与零元边界不变 | 混合来源 |
| 月度结算 `settlements` | P1 | 版本列表/详情沿用前批密度，不造结算数据 | 旧版差异 BLOCKED |
| 用户管理 `users` | P2 | 共用表格/状态/操作列 | 密码重置 |
| 角色权限 `roles` | P1 | 列表/只读权限摘要保留，编辑权限树按域折叠、搜索和已选数可见 | 保存/撤权人工复验 |
| 操作日志 `audit` | P2 | 中文列表与详情保留，长文本共用换行规则 | 长审计记录 |
| 审批处理深链 `approvals-queue` | P1 | 仍用唯一审批详情组件与合法步骤，未复制写入链 | 多实例/回退 |
| 登录 `login` | P2 | 沿用登录表单与全局焦点/颜色规范 | 本轮截图 BLOCKED：已登录重定向 |

**双视口技术取证。** `.runtime/ui-visual-final/` 已记录 37 个已登录页面在浏览器报告 CSS 视口 1366×768、1920×1080 下的默认页面截图（74 张），另有批量草稿、流程编辑、角色权限、费用详情/归属、DAILY/TASK、工资详情和车辆日任务等 9 组关键状态（18 张）。截图索引逐条标明路径与状态。登录页因 5176 当前保持管理员登录、访问登录路由会重定向，两个视口均记 **BLOCKED**；没有退出账号或伪造登录截图。部分默认页面数据少，且个别即时截图可能处于加载阶段；自有/外协月表已单独待加载结束重拍。所有截图只证明真实运行布局，不证明人工视觉 PASS 或业务操作通过。

**验证。** 本轮结束时管理端 `npm test` PASS（17 项当前 V3 核心、7 项 UI1，另含任务重量证据与充电工作簿用例），`npm run build` PASS（Vite 105 modules；单 chunk 体积提示为非阻断）。受限构建进程对 `src/main.js` 的 `realpath` 返回 EPERM，在获准的相同标准命令中执行成功。`git diff --check` 退出码 0，仅有 Git 的换行提示。本轮视觉文件的 Secret 样式扫描发现两处变量引用，均非字面 Secret；未将本机 `application.yml`、`project.config.json`、`.runtime/` 暂存或提交。本轮没有触及后端业务代码，先前后端测试结果不伪称本轮重跑。

**未覆盖与状态。** 真文件上传预览、审批/费用提交确认、无权限与 API 错误态、多页分页、历史 Settlement 差异仍需有代表性数据和集中人工复验，维持 **NOT RUN/BLOCKED**，不能写为 PASS。批量调度原人工视觉 **FAIL** 仍待业务方复验；R7-V3-R-UI 和 V3-R 均未关闭。本轮未提交代码。

## V3.0 角色审批与司机禁用定向整改（自动验证；人工 GUI 待验）

本轮在既有未提交工作区增量实施，未重置、清理或提交。正式审批节点改为必选角色、可选指定审批人；一个冻结节点只有一个待办，角色内当前有效且具备动作权限的用户可见并抢办，指定人员失效不自动放开。审批详情、待办、附件可见性和动作均核对当前角色与权限，实际处理人保存在 `approval_action.operator_id`。申请人可撤回未终态且未生效的实例，待办失效、原版本和动作保留；重新发起建立新实例，不新增改派链。轮胎撤回同步撤销未生效请求；已批准费用仍只能通过正式冲销处理。

司机禁用前锁定司机并查询当前责任 Participant 对应的已发布、在途或转派待重新发车任务；存在未完成任务时后端返回阻断数量。任务分配与发车校验同一司机状态锁；禁用成功后在事务提交后撤销其关联账号令牌，登录和旧令牌请求均拒绝；历史事实及绑定不变。V3 正式状态机无 `VOIDED`，故未引入作废状态。

受影响范围：审批 V3 Service/Controller、附件资格查询、司机 MasterData Service/Repository、任务司机校验、Auth/Token、轮胎撤回效果、管理端审批配置及撤回操作；同步 `docs/01～07` 相关段落、`docs/openapi.yaml`、`database.sql`、Flyway V1、测试 schema 和回归测试。保留唯一 ApprovalInstance/ExpenseEntry 写入链及单一司机状态事实；没有建立改派接口或兼容迁移。

验证记录：管理端测试 PASS（18 项当前 V3 核心、7 项 UI1 及专项用例），build PASS（105 modules）。后端测试首次全量 97 项、0 failures、0 errors、1 skipped；标准 package 在原工作目录因运行中 JAR 占用无法完成重命名，在隔离构建副本使用同一 Maven package 命令 PASS。两份 MySQL 初始化脚本分别在全新隔离库执行成功，均为 53 表；520 个字段定义、237 条索引记录、160 条约束记录完全一致，审批角色 NOT NULL、指定用户可空，待办唯一索引不依赖用户。隔离正式 API 验证：角色内两人待办、无关角色拒绝、一人处理后重复拒绝、非本人撤回拒绝、本人撤回 PASS；审批多人抢办及审批/撤回并发均恰一方成功且终态一致。司机禁用、禁用后登录和旧令牌拒绝、重新启用后旧令牌仍失效且新登录可用，隔离正式 API PASS。仅向新隔离库写入标识明确的测试记录，未操作 5176 既有在途审批事实。

本轮真实管理端及小程序人工 GUI：**NOT RUN / 待业务方验收**，不得记为 PASS。正式 DOCX 的逐页渲染检查：**NOT RUN / NON-BLOCKING**（本机缺少可用的文档渲染器）。司机禁用与任务发布、司机发车竞争的回归测试已补齐并通过；最终完整自动验证结果见下方收尾记录。本记录不代表 R7-V3-R-UI 或 V3-R 关闭。


**本轮最终收尾。** 后端最终标准 `package` 在与当前源文件一致的隔离构建副本 PASS；自动测试共 100 项、0 failures、0 errors、1 skipped。跳过项为缺少 12 家原始充电文件的既有 `twelveRealFilesPassGenericServerVerificationAndReconcile`，本轮不将其计为通过。原目录的 package 因 5176 正在使用目标 JAR 无法重命名；隔离副本包含相同源代码、单一数据库 SQL、OpenAPI 与打包配置，避免中断 5176。管理端最终 `npm test`、`npm run build` PASS（105 modules）。角色选择器只列出具备审批处理权限的角色，换胎流程进一步要求换胎处理权限；业务类型变化会清除不再合法的节点角色。待办、详情、附件和正式处理执行相同角色及权限约束。

本轮隔离 MySQL 正式 API 对角色共享待办、无关角色拒绝、重复审批拒绝、申请人撤回、非申请人拒绝、并发抢办、审批/撤回竞争，以及司机禁用后登录与旧 Token 拒绝、恢复后新登录均 PASS。司机禁用与任务发布、发车竞争的自动回归 PASS。`git diff --check` PASS；变更文本 Secret 样式扫描 90 文件，仅发现表单初始化的空密码字面量，未发现真实 Secret。截图核对未在新版隔离前端完成，保持 **NOT RUN**；业务方真实 GUI 仍为 **NOT RUN / 待验**，不可将自动测试或 API 结果写为人工 PASS。系统尚未提交，R7-V3-R-UI、V3-R 均未关闭。

### 5176 审批角色选项报错定向复核（2026-10-15）

- 人工截图显示流程配置页原先在 `approverRoles.filter` 渲染崩溃；前端已对缺失选项做归一化处理，并显示可操作的服务端版本提示，不将旧接口的普通角色列表冒充可审批角色。
- 核查发现原 5176 隔离库 `fleet_ops_v3r_ui2_20261013045339` 缺少 `approval_flow_node.approver_role_id`，且有 1 条进行中的审批实例。业务方选择保留旧库，5176 切换至新隔离测试库；旧库及在途审批未修改。
- 新库 `fleet_ops_v3r_ui2_20261015161000` 由当前单一 `database.sql` 初始化，53 张表、0 条审批实例；审批节点 `approver_role_id NOT NULL`、`approver_user_id NULL`。5176 → 8082 → 新库已运行；管理员正式 API `/approval-templates/options` 返回 `approverRoles`（1 个可审批角色、1 名候选审批人）。
- 管理端回归测试和 build PASS，`git diff --check` PASS；后端当前源代码 JAR 已在隔离构建目录打包（本次跳过重复测试；上一轮全量 100 项、0 failure、0 error、1 skipped 的结果保留）。浏览器重新登录后的业务方 GUI 复验 **NOT RUN**，不记为人工 PASS。

### 默认端口测试环境切换（2026-10-16）

业务方明确授权清空本机 3306 的 `fleet_ops` 旧库并执行单一初始化 SQL。执行前核对目标为 `fleet_ops:3306`、55 张旧表和 2 条审批实例；已将完整旧库备份至忽略目录 `.runtime/fleet_ops_3306_before_v3r_20261016_verified.sql`，验证 55 个建表定义和 dump 完成标记。随后仅删除该库旧表并执行当前 `database/database.sql`，生成 53 张最终表；本机测试管理员已重新初始化。旧 13308 实例未被复制或合并进 3306，随后停止该额外实例。管理端 5173、后端 8080、小程序项目继续使用默认开发入口。正式 API 健康检查 UP、测试管理员登录 PASS、审批角色选项返回 1 个可审批角色及 1 名候选人员。旧库的 2 条审批实例已按业务方清库指令删除；业务方 GUI 复验仍为 NOT RUN。

### UI-C 集中验收 17 项定向整改（2026-10-19）

本轮在既有未提交工作区增量完成，未 reset、clean 或提交。业务方确认 GENERAL 为保留历史/内部类型：旧模板、实例、版本和历史可读，前后端均禁止新建 GENERAL 模板；不新增普通审批申请页。正式业务仍使用唯一审批实例与 ExpenseEntry 入账链。

| 验收项 | 整改与核查结果 | 真实 GUI |
| --- | --- | --- |
| 1 管理员小程序菜单 | 管理员不显示司机专用“我的线路”，使用审批待办；司机线路及发车、卸货保留 | NOT RUN |
| 2 待办资格 | 待办使用现有服务端角色、指定人员和动作权限过滤，管理端与小程序复用正式审批数据 | NOT RUN |
| 3 登录视觉与备案 | 登录背景使用项目内图片；备案号仅从配置显示，未配置不显示虚构号码 | NOT RUN |
| 4 密码校验 | 新增用户、司机和修改/重置密码统一最短 6 位；原密码错误返回明确中文提示 | NOT RUN |
| 5 司机初始密码 | 创建司机前后端必填并校验，司机禁用、令牌失效和任务限制保留 | NOT RUN |
| 6 角色编码 | 新建角色由服务端生成稳定唯一编码；页面不要求填写，重复码由服务端拒绝 | NOT RUN |
| 7 用户角色选择 | 单一下拉支持搜索；用户“更多”仅保留密码重置，启停放入编辑表单 | NOT RUN |
| 8 车辆删除 | 菜单/权限种子和正式 DELETE 写入入口移除；旧地址返回“此操作不可用”的 405，MockMvc 与新 JAR 正式 API 均已验证 | NOT RUN |
| 9 全局错误弹窗 | 保存、提交和密码 API 保留服务端业务原因；网络、权限、重复、并发和规则拒绝由高层级弹窗显示 | NOT RUN |
| 10 审批待办分离 | 审批中心“我的待办”仅显示审批，工作台不再混入费用待归属列表 | NOT RUN |
| 11 待归属费用 | 费用管理新增待归属页面，复用费用台账查询与归属操作；工作台两个数量卡片分别导航至审批和费用 | NOT RUN |
| 12 月结版本 | 调整返回位置，查看版本读取正式版本详情并切换详情/差异；未生成新结算事实 | NOT RUN |
| 13 费用类别 | 单一 baseline 初始化 10 个正式类别，含临时气费，不含“其他费用”；审批选项与台账从同一类别表读取 | NOT RUN |
| 14 模板入口 | 移除“返回审批列表”，首字段为业务类型；新建仅费用申请、换胎申请，稳定业务编号后端生成 | NOT RUN |
| 15 模板费用大类 | 选项来自正式类别表，不提供“其他费用”，角色必选/审批人选填及冻结版本保持 | NOT RUN |
| 16 小程序/权限边界 | 管理员待办与司机线路分离，正式接口继续执行资格和身份校验；车辆删除与 GENERAL 新建均后端拒绝 | NOT RUN |
| 17 唯一性与契约 | 同步 docs/01～07、OpenAPI、三份单一 baseline、页面和测试；无第二套审批、费用、待办或角色事实 | NOT RUN |

隔离验证环境为 13319 的新建 MySQL 实例及其 `fleet_ops` 测试库，业务方既有 3306/5175 环境未被本轮测试改写。`database.sql` 与 Flyway V1 分别在两个全新 schema 执行：54/54 表、524/524 列、239/239 索引、162/162 约束、33/33 外键、22/22 CHECK，无结构差异；10 个费用类别一致。测试管理员仅写入该隔离库，密码保存在忽略的 `.runtime/`，本文不记录凭据。正式 API 验证管理员登录、类别、角色自动编码、GENERAL 新建拒绝、车辆删除拒绝、密码错误、未认证拒绝及两个待办摘要；发现旧 JAR 对车辆 DELETE 返回 500 后，补充统一 405 错误响应，最终 JAR 在 18080 重启并以正式 API 复测返回 405。

最终自动验证：隔离构建副本执行标准 Maven `package` **PASS**，103 项测试、0 失败、0 错误、1 跳过；跳过项为既有 12 家原始充电样本测试，不能记为通过。管理端 19 项 V3、7 项 UI1 及其它启用测试 PASS，build PASS（110 modules，单 chunk 体积提示为非阻断）；小程序 39 项 PASS。`git diff --check` PASS（仅换行警告）；对本轮已跟踪增量及相关未跟踪源码执行高置信 Secret 样式扫描，候选文件为 0，本机配置与 `.runtime/` 未进入版本控制。新管理端在 5176、隔离后端在 18080，浏览器已实际查看登录、工作台与角色页；登录页在真实浏览器通过视口覆盖分别检查 1366×768 与 1920×1080，未发现表单溢出。两种尺寸的登录页截图仅在本次浏览器工具输出中展示，未保存本地证据文件；已登录业务页双视口仍为 **NOT RUN**。全部新交互的业务方人工 GUI 复验仍为 **NOT RUN**，不能用自动测试替代。DOCX 逐页视觉检查因本机缺少可用渲染器为 **NOT RUN / NON-BLOCKING**。R7-V3-R-UI 和 V3-R 均未关闭。

### 审批模板创建页面定向整改（2026-10-19；人工复验待定）

业务方在旧页面看到业务名称输入框、费用大类空选项及拥挤的审批节点。核查确认：正式费用类别由 `expense_category` 提供，当前单一 baseline 初始化 10 个启用类别且不含 OTHER；本机旧 8080 服务连接的 3306 开发库没有该类别表，不能作为新版类别接口的验证环境。本轮在独立 MySQL 13319 新建 `ui_c_template_utf8` 测试 schema，由 Flyway V1 单一 baseline 初始化，实查 10 个类别和 `approval_template` 业务唯一索引；未清理或改写既有库。5176 管理端代理到隔离后端 18080，旧 5173/8080 不作为本轮验收依据。

`ApprovalCenterV3View.vue` 新建表单仅选择业务类型、费用大类、发起角色和审批步骤；不再输入业务名称或稳定编号。切换到换胎时清除旧费用大类。费用类别从 `/approval-templates/options` 读取，空列表显示原因，失败沿用全局 API 错误弹窗。节点改为“名称+角色”“指定审批人+可退回+移除”两行，保留角色必选、指定人选填。`ApprovalWorkflowService` 服务端按类别生成“类别名称+申请”，换胎固定“换胎申请”，稳定编号生成且忽略客户端自定义值；同业务类型/费用大类有效模板以数据库生成列唯一索引兜底，GENERAL 新建仍拒绝而历史读取不变。下一版本不覆盖模板名称。同步了三份单一 schema、OpenAPI、正式 docs/01/03/04/05/07 和对应前后端测试。

隔离正式 API 已验证：10 个类别可读取；费用和换胎模板名称及稳定编号自动生成；重复模板、GENERAL 新建、非法审批角色和匿名读取分别被拒绝。管理端完整测试和 build、后端 105 项测试（0 失败、0 错误、1 跳过）及标准 package 均 PASS；跳过项不计作通过。`git diff --check` PASS，新增代码 Secret 高置信扫描候选 0。业务方明确改为自行在原 5173／8080／3306 环境验证，不要求登录 5176 截图；本轮双视口真实截图及新建费用/换胎模板、费用下拉和节点布局的 MANUAL GUI 均为 **NOT RUN**，不改变此前人工验收结论。正式 DOCX 已核对文字可读；逐页视觉渲染因本机缺少 LibreOffice `soffice.exe` 为 **NOT RUN / NON-BLOCKING**。R7-V3-R-UI 和 V3-R 保持未关闭。

环境交接：依据业务方此前明确同意清空 3306 `fleet_ops` 测试库的授权，先核对目标 `fleet_ops:3306` 并保存包含 53 张旧表的备份 `.runtime/fleet_ops_3306_before_template_fix_20261019.sql`，再从当前单一 `database.sql` 初始化为 54 张表、10 个正式费用类别，并仅加入一个本机测试管理员。新构建后端启动在 8080，原管理端 Vite 在 5173，3306 MySQL 保持运行；隔离验证使用的 13319／18080／5176 已停止。原端口正式 API 再次通过类别、自动命名与编号、重复模板、GENERAL/非法角色及匿名负向验证。3306 旧测试业务数据已按既有授权清空，备份留在未跟踪 `.runtime/`，未提交。管理员密码不写入报告。

收尾时同步移除了创建下一版本对客户端稳定编号的依赖，编号由服务端已有模板事实保持，正式 API 已验证不传业务名称或编号可得到第 2 版且名称不变。完成最终后端重构建与管理端测试/build 后，再次按同一 baseline 清除本轮 API 验证产生的测试模板，额外备份在 `.runtime/fleet_ops_3306_after_template_api_checks_20261019.sql`。最终 5173 → 8080 → 3306 链路实查健康响应 200、正式费用类别 10 项、审批模板 0 条；端口 5173、8080、3306 保持运行供业务方手工验收。

### 全局错误弹窗规范补充（2026-10-19；人工复验待定）

按业务方确认，管理端 API 错误统一通过高于编辑弹窗的紧凑错误弹窗展示。标题区分登录、权限、加载、上传、下载、提交、保存等场景，正文仅保留可理解的业务原因，按钮统一为“确定”；请求编号和技术详情不再出现在用户界面，后端请求追踪能力保持不变。字段必填及格式错误仍在字段附近提示。同步了正式 docs/05、docs/06，修改管理端公共错误弹窗、API 客户端、登录页及审批附件错误文案，并补充管理端测试。管理端完整测试 PASS（21 项 V3、7 项 UI1 及其它启用测试），build PASS；git diff --check PASS。真实 GUI 错误弹窗的业务方复验为 NOT RUN，不得记为人工 PASS。业务方将在 5173／8080／3306 自行验证，本轮未改动后端和数据库。

补充验证：正式 docs/05、docs/06 的规则文字已回读确认；DOCX 逐页渲染因本机缺少 LibreOffice `soffice.exe` 为 NOT RUN，不计视觉 PASS。当前端口监听核对：5173、8080、3306 均在运行；用户可在原环境手工复验。错误弹窗的 GET、密码保存及附件下载失败路径均有自动回归，真实 GUI 仍为 NOT RUN。

### UI-C 第 11 项补漏：待办概览与业务待办分离（2026-10-19；MANUAL GUI FAIL 待复验）

旧工作台 `#/my-todos` 仍显示审批统计及审批明细，费用卡按台账查看权限展示且仅统计当月 DAILY，不符合待办职责和权限口径。本轮将其更名为“待办概览”，只保留“我的待审批”“待归属费用”两张整卡按钮；审批卡读取正式 `GET /approvals?scope=TODO` 的总数，费用卡读取新增的只读 `GET /expense-ledger/pending-attribution/summary` 的全部有效、未归属、未冲销费用总数。数量为 0 仍可进入；加载、失败和无权限分别显示，失败不折算成 0。审批中心 `#/approvals-todo` 直接复用 `ApprovalCenterV3View` 的正式 TODO 列表，旧 `#/approvals-queue` 重定向至该路由。费用管理 `#/expense-unassigned` 复用 `ExpenseLedgerV3View` 的详情、归属历史和原有 `POST /expense-ledger/{id}/attribution`，列表与卡片共用归属权限保护的只读列表/汇总口径；`expense:ledger:view` 单独不能访问该待办，旧 `#/expense-attribution` 仍重定向至正式待归属页面。经营概览不再重复加载两类待办。没有新增审批实例、费用事实、归属写入或财务计算链。

实际改动：`MyTodosV3View.vue`、`DashboardV3View.vue`、`ExpenseLedgerV3View.vue`、`router/routes.js`、`api/modules.js`、`FinanceV3Controller.java`、管理端及后端定向测试；同步 `docs/05`、`docs/06`、`docs/07`、`docs/36` 和 OpenAPI。后端权限负向验证台账只读账号不能读取待归属统计，具有归属权限的账号可读；服务测试覆盖分页、跨月、归属后减少和冲销后减少。既有审批测试覆盖角色多人、指定人员、资格失效和一人处理后待办消失。

自动验证：管理端 22 项 V3、7 项 UI1 及其它启用测试 PASS，build PASS（111 modules，单 chunk 体积提示非阻断）；后端定向 28 项 PASS；隔离构建副本标准 Maven `package` PASS，107 项测试、0 失败、0 错误、1 跳过（既有 12 家充电原始样本条件测试，不能计为通过）。工作区原 `backend/target` JAR 被运行中的 8080 锁定，直接 repackage 失败；隔离构建副本成功生成同源 JAR，并已替换 8080 测试服务。5173、8080、3306 均监听，后端健康接口返回 200。原端口正式 API 验证审批 TODO 总数 0、费用待归属汇总与列表总数均为 0；真实有数据场景本轮 API 未准备，服务层测试覆盖口径。`git diff --check` PASS（仅换行提示），Secret 扫描候选 0。本轮没有进行浏览器 GUI 验收或截图；第 11 项保持 **MANUAL GUI FAIL**，待业务方对双卡片、权限组合、跳转、高亮、归属页及异常态复验。R7-V3-R-UI 与 V3-R 均未关闭，未提交代码。

正式 DOCX 文字回读校验 PASS；逐页渲染因本机缺少 LibreOffice `soffice.exe` 为 NOT RUN / NON-BLOCKING，不据此声称视觉通过。

### 小程序首页今日工作按身份显示（2026-10-19；人工复验待定）

业务方截图显示非司机管理员仍看到“当前运输”空卡。已调整 `mini-program/pages/home/index.js`、`index.wxml`、`index.wxss`：司机继续显示正式运输任务；非司机在有 `approval:process` 或 `approval:return` 权限时调用现有 `GET /approvals?scope=TODO`，显示服务端总数及最近三条有资格处理的审批任务，并进入原审批中心处理。无审批处理权限不请求待办；接口失败显示明确错误和重试，不把失败伪装成零条。同步正式 docs/05 的小程序首页描述。本轮没有新增审批查询、写入接口或处理链。

小程序自动测试 42 项 PASS，覆盖管理员与司机分支、待办 API 来源、失败提示及无权限时不查询；`git diff --check` PASS，Secret 扫描候选 0。后端未改动，沿用现有审批待办资格过滤。小程序真实 GUI **NOT RUN**，待业务方在原测试环境验证；不记为人工 PASS。docs/05 DOCX 已回读，因本机缺少 LibreOffice `soffice.exe`，逐页视觉检查 **NOT RUN / NON-BLOCKING**。

### 两端当前账号角色回显修复（2026-10-19；人工复验待定）

业务方截图显示小程序个人信息的角色为“当前账号”，管理端右上角只显示手机号。原因是正式 `GET /me` 返回嵌套 `role: { id, code, name }`，两端原显示代码读取不存在的顶层 `roleName`。本轮仅修正展示取值：小程序首页及个人信息优先读取 `role.name`，管理端侧栏和右上角账号区显示同一角色名称；没有角色时明确显示“角色未配置”。没有变更账户角色、权限或后端 API。

修改 `mini-program/pages/home/index.js`、`mini-program/pages/profile/index.js` 与 `index.wxml`、`admin-web/src/layouts/AppLayout.vue`、`admin-web/src/design/ui-final.css`，并补充两端回归测试。小程序 43 项 PASS；管理端 22 项 V3、7 项 UI1 及其它启用测试 PASS，build PASS。小程序个人信息、管理端账号区及侧栏的真实 GUI **NOT RUN**，待业务方复验；不将构建结果记为人工 PASS。

### 角色停用后禁止登录与旧会话访问（2026-10-19；人工复验待定）

业务方发现财务角色停用后，所属启用用户仍可登录。根因是 `AuthService` 仅检查 `sys_user.status` 与司机状态，未检查关联 `sys_role.status`；已有令牌也未校验角色状态。现于用户读取中取得角色状态：角色停用时登录返回 `AUTH_006 / 所属角色已停用`，`/me` 同样拒绝；令牌认证逐次检查角色当前状态，停用角色时立即拒绝并移除令牌；角色状态更新及角色编辑中停用操作还会撤销该角色的现有会话。重新启用角色后必须重新登录。未改角色、用户或历史业务事实。

修改 `UserAccount`、`UserAccountRepository`、`AuthService`、`TokenAuthenticationService`、`SystemManagementService`、`ErrorCode`、认证/系统集成测试及 MasterData 测试角色数据；同步正式 docs/06、docs/07 和 OpenAPI。定向认证/系统测试 14 项 PASS；后端全量 108 项、0 失败、0 错误、1 跳过（既有条件跳过项）；标准 Maven package 在全量测试后以 `-DskipTests` 构建 PASS。`git diff --check` PASS，Secret 扫描候选 0。原 8080 测试服务已替换为本轮 JAR，沿用原本机 `application.yml` 与 3306 数据库连接；`/actuator/health` 返回 200；5173 与 3306 仍保持监听。docs/06、docs/07 的规则文字已回读；DOCX 逐页视觉检查因本机缺少 LibreOffice `soffice.exe` 为 **NOT RUN / NON-BLOCKING**。实际财务角色登录由业务方复验，**MANUAL GUI NOT RUN**，不因 API 或测试通过标记人工 PASS。

### 月度结算确认弹窗与月份筛选（2026-10-19；人工复验待定）

业务方反馈“生成新版本”仍使用浏览器原生确认框，月份筛选旁“查看版本”又与版本行“查看详情”混淆。`SettlementV3View.vue` 现复用管理端 `ConfirmDialog`，确认后才调用原有月结生成 API，取消不产生结算版本；API 错误仍由统一业务错误弹窗显示。移除“查看版本”，切换结算月份即自动查询该月版本，保留版本行“查看详情”。未生成或修改任何月结事实，后端契约与结算版本规则未变。管理端 22 项 V3、7 项 UI1 及其它启用测试 PASS，build PASS；受影响文件 `git diff --check` PASS；本次增量 Secret 候选 0。真实 GUI 与生成操作均 **NOT RUN**，待业务方在 5173 环境复验。

### 换胎申请按角色审批流程整改（2026-10-19；人工复验待定）

核查结论：小程序从正式可发起模板中选择当前已发布的 `TIRE_REPLACEMENT` 模板，后端使用统一 `approval_instance`、冻结 `approval_flow`、节点待办和 `approval_action`；审批动作没有 `ADMIN` 角色硬编码。初始数据库只为管理员角色预授全部权限，其他角色如未取得 `approval:process` 与 `tire:request:process`，不能作为换胎审批节点；打回还需 `approval:return`。审批资格仍由当前有效用户、节点角色、可选指定人和当前动作权限共同决定，管理员不属于节点角色时不能绕过。未自动扩张任何现有角色的权限，业务方可在角色权限中授权目标角色并发布换胎流程版本。

本轮在 `TireV3Service` 增加提交时对当前有效换胎模板的明确校验，错误模板或未发布模板不得创建换胎申请；原幂等请求仍先返回既有实例。流程配置页说明所需权限并在无可用角色时提示。新增后端换胎专项测试覆盖非管理员两人待办、管理员跨角色拒绝、指定人失效、权限撤回、附件资格、多节点打回、退回申请人后新实例重提、冻结版本、并发抢办及轮胎只生效一次。同步正式 docs/06、docs/07 与 OpenAPI 权限和流程说明；数据库结构、审批引擎、换胎字段及费用链未改。

自动验证：后端标准 Maven `package` PASS；启用测试共 113 项、0 失败、0 错误、1 跳过，跳过项不计通过。管理端完整测试与 build PASS，小程序 43 项 PASS，`git diff --check` PASS（仅已有换行提示），Secret 候选文件 0。正式 DOCX 已回读文字；因缺少 LibreOffice `soffice.exe`，逐页渲染 **NOT RUN**。本轮未做自动 GUI 验收或截图，非管理员换胎模板配置、待办、审批与附件的业务方人工 GUI 均为 **NOT RUN**。R7-V3-R-UI 与 V3-R 保持未关闭，未提交代码。

业务方随后截图复验发现，换胎审批角色下拉框只显示管理员，配置页的“可发起角色”却包含其他角色。只读查询当前 3306 `fleet_ops` 的授权事实：财务有 `approval:process`、缺 `tire:request:process`；调度有 `tire:request:process`、缺 `approval:process`；管理员同时具备两项，因此原下拉框仅有管理员。可发起角色与审批角色属于不同资格，不能据前者推断后者可审批。此项人工视觉反馈保留为 **FAIL / 待复验**，不自动扩权。

为使授权原因可见，`ApprovalWorkflowService.flowOptions` 现返回所有有效非司机角色及两项权限标志；`ApprovalCenterV3View.vue` 显示缺权限角色、逐项说明缺少的权限，并在下拉框中禁用，服务端原有角色/权限校验不变。管理端新增角色选项测试，后端新增授权选项与非法建模回归。定向后端 22 项、0 失败、0 错误、0 跳过；管理端完整测试与 build PASS；后端 `-DskipTests package` PASS，`git diff --check` PASS（仅换行提示），Secret 候选文件 0。5173、8080、3306 保持运行，8080 已更新为本轮 JAR，健康检查 200。授权变更、换胎模板发布及实际待办处理仍需业务方人工复验，**MANUAL GUI NOT RUN**。

### 换胎审批权限统一为正式审批动作（2026-10-19；人工复验待定）

业务方进一步确认：换胎申请的发起权限是 `tire:request`；节点通过与打回分别只使用 `approval:process`、`approval:return`，并同时校验冻结节点角色、可选指定人及当前有效用户。此前本报告关于换胎审批还需要 `tire:request:process`、以及财务和调度因缺少该专属权限不能被选择的结论，**已由本次正式规则取代**。管理员无节点外特权；发起权限不赋予审批资格。未建立第二套换胎审批或费用写入链。

实际修改：`ApprovalWorkflowService` 移除换胎审批专属授权条件，待办分页、详情资格和审批动作继续读取统一权限与冻结节点；`FileAttachmentRepository` 的申请、动作及提交证据查看同步使用同一节点资格；`ApprovalCenterV3View.vue`、`approvalFlowOptions.js` 只依据“处理审批”决定节点角色能否选择，并说明“打回审批”只影响打回操作。`TireV3Controller`、`ApprovalV3Controller`、`AttachmentService`、小程序换胎入口及主页统一使用 `tire:request` 发起权限。三份单一初始化 SQL 的权限种子完全一致，删除 `tire:request:process`，用 `tire:request` 替代旧发起编码，并将它授予固定 DRIVER 角色；服务端仍按自有司机、本人公司车和库存规则拒绝无资格发起。OCR 与照片上传也核验自有司机及当前公司车，外协司机即使拥有同一角色的发起权限也不能上传换胎照片。`PermissionCode`、OpenAPI、正式 docs/01、04、05、06、07 与相关前后端测试已同步；docs/02、03 的实体和物理结构未变。

自动验证：后端标准 `mvn package` PASS，116 项、0 失败、0 错误、1 跳过（跳过不计通过）；管理端完整测试与 build PASS；小程序 43 项 PASS；三份 SQL 的 59 个权限编码集合一致；`git diff --check` PASS；Secret 扫描候选 0。正反向测试覆盖非管理员角色内多人待办、指定人与非指定人、管理员不符节点角色、符合角色但无审批动作权限、权限撤回、附件可见性、合法打回、多节点与冻结版本、并发抢办、换胎事实仅生效一次，以及外协司机换胎照片上传拒绝。Word 正式文档内容已回读；本机缺少 LibreOffice `soffice.exe`，逐页渲染 **NOT RUN**。按本轮范围未更新运行中的 8080 或 3306 开发库，也未进行 GUI 操作或截图；业务方人工 GUI 保持 **NOT RUN／待复验**，先前截图问题不因自动测试转为 PASS。未提交代码，R7-V3-R-UI 与 V3-R 保持未关闭。

### 旧轮胎专属审批路径定向清理（2026-10-19；人工复验待定）

全仓调用核查覆盖微信小程序、管理端、Java Controller/Service/Repository/Security、权限种子、OpenAPI、三份 baseline、当前测试和退役 V2 测试。正式运行路径为 `POST /tire-requests` → `TireV3Service.submitRequest` → `ApprovalWorkflowService.create` → 冻结流程节点待办；处理路径统一为 `/approvals/{id}/approve`、`return-step`、`return-applicant` → 节点角色/可选指定人及 `approval:process`/`approval:return` 校验 → `TireApprovalEffect.onApproved`，在同一事务内锁定申请和轮胎、更新轮胎状态并写 `tire_history`/审计。旧 `ApprovalService` 不被当前 Controller、管理端或小程序调用；旧 `ApprovalTemplateController` 无 `@RestController`，不注册为正式接口。旧服务的 `TIRE_REQUEST` 创建方法、专属通知/打回分支及重新提交分支已删除；旧 `ApprovalInitiationService.forTire` 和孤立的 `ApprovalCreationCommand` 已删除；旧模板服务不再允许建立 `TIRE` 类别模板。旧审批服务、发起服务和模板服务撤除 Spring Bean 注册，避免运行时保留第二套审批写入服务。旧通用审批源码仍保留供后续逐文件确认；本轮未批量删除其公共 DTO、Repository、历史/费用策略。原因是自动审批审查拒绝了一次拟删除 31 个审批源文件和退役测试的操作，理由是未充分证明每个文件均废弃，可能误删公共能力；不得以另一种批量删除方式绕过。

保留能力：V3 `ApprovalWorkflowService`、`ApprovalV3Controller`、`TireApprovalEffect`、`TireV3Service`、`TireService` OCR、附件、通知、审计，以及统一审批实例/流程版本/提交版本/动作/待办、轮胎申请/库存/历史表。`tire:request` 是发起权限，`tire:request:view` 是申请详情读取权限，均不是审批动作权限；后端仍校验自有司机、当前公司车辆、合法 OCR/胎号、库存可用、冲突申请，审批生效时再次锁定并校验，失败则事务回滚。当前正式 SQL/OpenAPI 和 docs/01～07 的统一审批规则已在上一节同步，本轮没有更改业务模型、表结构或正式 API；历史报告中出现的旧权限文字只作为已被上述正式规则取代的审计记录。

新增回归断言：三份种子不含 `tire:approve`、`tire:approval`、`tire:approval:view`、`tire:request:process`、`tire:request:create`；当前 `TireV3Controller` 无专属通过/拒绝/打回/待办写入路由；旧 `ApprovalService` 不再作为 Spring Bean 注册。全新临时隔离 MySQL 的两个空库分别初始化 `database.sql` 和 Flyway V1：两边各 54 张表、525 个字段、240 个索引元数据行、163 个约束、33 个外键元数据行、22 个 CHECK，六类元数据差异均为 0；统一审批和轮胎事实表仍存在，废弃权限种子为 0，临时服务已停止。测试与安全结果见后续最终校验记录；运行中的 3306 业务库未修改，未做 GUI 功能验收，人工状态仍为 **NOT RUN**。残留风险是退役 V2 测试和旧实施记录仍含历史 `TIRE_REQUEST` 文本，但它们不属于构建或运行路径；旧通用审批写入服务虽不再注册，相关源码仍需逐文件论证后才能物理删除。

最终自动校验：后端 `mvn package` 117 项、0 失败、0 错误、1 跳过，标准打包成功；管理端完整测试与 `vite build` 成功；小程序 43 项通过；`git diff --check` 返回 0；本轮增量扫描 442 行，字面 Secret 候选 0。正式运行源码、OpenAPI 与数据库种子中没有上述废弃权限、旧换胎专属创建方法或专属审批路由。全仓搜索仍命中退役 `backend/retired-v2-tests/TireApiTests.java` 的 `tire:approval:view`、`docs/33` 和本报告旧阶段记录的历史权限文字，以及当前测试对这些旧编码的缺席断言；均未成为正式可配置权限或运行入口。HTTP 端到端请求、真实 MySQL 业务 API 场景及 GUI 功能验收本轮 **NOT RUN**；业务方需在人工环境复验换胎模板授权、待办、附件及审批后唯一生效。旧源码物理删除尚未完成，因此本次不宣称“全仓零历史文本”或人工 PASS。


### 旧审批代码物理清理逐文件证据（2026-10-19）

核查范围：31 个原候选文件（含上轮已删除的 ApprovalCreationCommand.java）；对每个类名检查现行 backend/src/main/java、backend/src/test/java、退役 V2 测试、后端资源配置，另核查管理端、小程序、OpenAPI、数据库 SQL 的旧入口与权限字符串。除当前测试对旧 ApprovalService 未注册 Bean 的断言外，30 个现存候选文件没有旧包外生产调用方或 V3 依赖；旧 ApprovalTemplateController 虽有映射方法但类无 @RestController，没有注册正式路由。旧 ApprovalDisplayNameService、两个旧 Repository 及 ApprovalAccessPolicy 当前仍注册为 Bean，但没有 V3 注入或外部调用，删除风险是退役 V2 测试无法再编译；这些测试不在 src/test，其 README 与夹具明确对应已删除的 V2 表。下表逐文件列出引用与建议；所有候选均为 A。旧 ApprovalExpenseResourcePolicy 与 ExpenseApplicationPolicy 不在这 31 个候选内，仍存在外部费用策略引用，列为 C 暂不删除。

| 文件完整路径 | 类及主要方法 | 生产外部引用 | 旧包内部引用 | Bean / 路由 | 配置/反射/任务/事件 | V3 依赖 | 当前测试 | 退役测试 | 风险与分类 |
|---|---|---|---|---|---|---|---|---|---|
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\controller\ApprovalTemplateController.java` | `ApprovalTemplateController`：available、list、create、detail | 无 | 无 | 无；否；类无@RestController | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\domain\ApprovalEvidenceRequirement.java` | `ApprovalEvidenceRequirement`：接口/DTO | 无 | ApprovalTemplateService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\domain\ApprovalFlowStatus.java` | `ApprovalFlowStatus`：接口/DTO | 无 | 无 | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\domain\ApprovalTemplateCategory.java` | `ApprovalTemplateCategory`：接口/DTO | 无 | ApprovalTemplateService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\query\ApprovalListScope.java` | `ApprovalListScope`：接口/DTO | 无 | ApprovalRepository.java、ApprovalService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\request\ApprovalActionRequest.java` | `ApprovalActionRequest`：接口/DTO | 无 | ApprovalService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\request\ApprovalCreateRequest.java` | `ApprovalCreateRequest`：接口/DTO | 无 | ApprovalInitiationService.java、ApprovalService.java | 无；无 | 无 | 无 | 无 | ApprovalInitiationApiTests.java | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\request\ApprovalExpenseConfigRequest.java` | `ApprovalExpenseConfigRequest`：接口/DTO | 无 | ApprovalFlowUpdateRequest.java、ApprovalTemplateCreateRequest.java、ApprovalTemplateService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\request\ApprovalFlowUpdateRequest.java` | `ApprovalFlowUpdateRequest`：Node | 无 | ApprovalTemplateCreateRequest.java、ApprovalRepository.java、ApprovalService.java、ApprovalTemplateService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\request\ApprovalTemplateCreateRequest.java` | `ApprovalTemplateCreateRequest`：接口/DTO | 无 | ApprovalTemplateController.java、ApprovalTemplateService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\request\ExactExpenseItemDeserializer.java` | `ExactExpenseItemDeserializer`：deserialize | 无 | ApprovalCreateRequest.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\response\ApprovalAvailableTemplateResponse.java` | `ApprovalAvailableTemplateResponse`：接口/DTO | 无 | ApprovalTemplateController.java、ApprovalTemplateService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\response\ApprovalDetailResponse.java` | `ApprovalDetailResponse`：SubmissionVersion、TaskHistory、ActionHistory | 无 | ApprovalRepository.java、ApprovalDisplayNameService.java、ApprovalService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\response\ApprovalFlowConfigResponse.java` | `ApprovalFlowConfigResponse`：ExpenseConfig | 无 | ApprovalTemplateController.java、ApprovalDetailResponse.java、ApprovalTemplateService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\response\ApprovalFlowDetailResponse.java` | `ApprovalFlowDetailResponse`：接口/DTO | 无 | ApprovalRepository.java、ApprovalService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\response\ApprovalFlowNodeResponse.java` | `ApprovalFlowNodeResponse`：接口/DTO | 无 | ApprovalFlowConfigResponse.java、ApprovalFlowDetailResponse.java、ApprovalRepository.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\response\ApprovalFlowResponse.java` | `ApprovalFlowResponse`：接口/DTO | 无 | ApprovalTemplateController.java、ApprovalFlowConfigResponse.java、ApprovalFlowDetailResponse.java、ApprovalRepository.java、ApprovalService.java、ApprovalTemplateService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\response\ApprovalResponse.java` | `ApprovalResponse`：接口/DTO | 无 | ApprovalDetailResponse.java、ApprovalRepository.java、ApprovalInitiationService.java、ApprovalService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\dto\response\ApprovalTemplateResponse.java` | `ApprovalTemplateResponse`：接口/DTO | 无 | ApprovalTemplateController.java、ApprovalTemplateService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\policy\ApprovalAccessPolicy.java` | `ApprovalAccessPolicy`：ensureCanSubmit、ensureCanSubmitTemplate、ensureCanProcess、ensureCanManageFlows | 无 | ApprovalTemplateController.java、ApprovalInitiationService.java | Component；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\repository\ApprovalRepository.java` | `ApprovalRepository`：activeFlow、findFlow、flowResponse、listFlows | 无 | ApprovalInitiationService.java、ApprovalService.java、ApprovalTemplateService.java | Repository；无 | 无 | 无 | 无 | ApprovalTemplateApiTests.java | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\repository\ApprovalTemplateRepository.java` | `ApprovalTemplateRepository`：create、find、findByCode、activeCountForType | 无 | ApprovalInitiationService.java、ApprovalService.java、ApprovalTemplateService.java | Repository；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\service\ApprovalDisplayNameService.java` | `ApprovalDisplayNameService`：resolve | 无 | ApprovalService.java | Service；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\service\ApprovalInitiationService.java` | `ApprovalInitiationService`：forCreate、forResubmit、Selected | 无 | ApprovalService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\service\ApprovalService.java` | `ApprovalService`：list、listFlows、flowDetail、flowVersions | 无 | 无 | 无；无 | 无 | 无 | V3ApprovalWorkflowTests.java | ApprovalInitiationApiTests.java、ApprovalTemplateApiTests.java | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\service\ApprovalTemplateService.java` | `ApprovalTemplateService`：available、list、find、versions | 无 | ApprovalTemplateController.java、ApprovalService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\spi\ApprovalBusinessContext.java` | `ApprovalBusinessContext`：接口/DTO | 无 | ApprovalService.java、ApprovalBusinessHandler.java | 无；无 | 无 | 无 | 无 | ApprovalInitiationApiTests.java | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\spi\ApprovalBusinessHandler.java` | `ApprovalBusinessHandler`：接口/DTO | 无 | ApprovalService.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\spi\ApprovalResubmissionContext.java` | `ApprovalResubmissionContext`：接口/DTO | 无 | ApprovalService.java、ApprovalBusinessHandler.java | 无；无 | 无 | 无 | 无 | 无 | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\spi\ApprovalSubmissionContext.java` | `ApprovalSubmissionContext`：接口/DTO | 无 | ApprovalService.java、ApprovalBusinessHandler.java | 无；无 | 无 | 无 | 无 | ApprovalInitiationApiTests.java | A：仅旧包内部或退役测试；可删除 |
| `D:\code\dfwl\backend\src\main\java\com\dfwl\fleet\approval\spi\ApprovalCreationCommand.java` | `ApprovalCreationCommand`：旧创建命令 DTO | 无 | 上轮旧服务方法，现已移除 | 无 | 无 | 无 | 无 | 退役 V2 测试间接涉及 | A：上轮已删除 |



#### 物理清理结果与保留边界

上表 31 个 A 类文件已全部物理删除（其中 `ApprovalCreationCommand.java` 在上一轮已删，本轮删除其余 30 个）：旧 `ApprovalTemplateController`；旧服务 `ApprovalService`、`ApprovalInitiationService`、`ApprovalTemplateService`、`ApprovalDisplayNameService`；旧 `ApprovalRepository`、`ApprovalTemplateRepository` 和 `ApprovalAccessPolicy`；`dto/query` 1 个、`dto/request` 6 个、`dto/response` 8 个；旧 domain 枚举 3 个；旧 SPI 记录/处理接口 4 个。逐文件完整路径、主要方法、Bean/路由/配置/测试引用和风险见上表。本轮同步删除五个只验证 V2 审批/轮胎路径的退役测试：`ApprovalApiTests.java`、`ApprovalBusinessEffectTests.java`、`ApprovalInitiationApiTests.java`、`ApprovalTemplateApiTests.java`、`TireApiTests.java`。这些测试的夹具使用 V3 baseline 不存在的 `tire_request_item`、旧 `approval_template.template_code/category/approval_type` 等结构；当前 V3 审批、费用、换胎和附件测试承担正式回归。已更新退役测试 README；混合业务的 `P17BMiniProgramContractTests.java` 仍含历史审批夹具，但该文件还涉及小程序其它业务合同，因此保留，不作为当前自动测试。

保留的 B 类正式能力：`ApprovalV3Controller`、`ApprovalWorkflowService`、`DailyExpenseWorkbook`，`TireV3Service`、`TireApprovalEffect`、OCR、附件、审计、通知、费用入账与全部现行 V3 测试；数据库统一审批事实表、轮胎事实表、权限模型和 OpenAPI 均未改动。C 类暂留 `ApprovalExpenseResourcePolicy` 与 `ExpenseApplicationPolicy`：后者仍实现前者，包含费用申请资源校验；虽然本次引用图没有发现 V3 调用，但删除会触及费用策略边界，不在 31 个已证明候选中，本轮不冒险删改。旧 `ApprovalTemplateController` 原本只有类级 `@RequestMapping` 而无 `@RestController`，此前便没有注册正式路由；删除后现行审批入口仍只有 V3 Controller。前后生产源码没有对已删类的引用；包内旧类在新 JAR 中为 0，保留的非 V3 审批包产物只有上述 C 类接口。没有可达的旧审批写入入口。

分批验证：删除旧 Controller/服务、Repository/策略、15 个 DTO 后各自完成编译及 `V3ApprovalWorkflowTests` 定向测试；最后删除旧枚举/SPI 与退役测试，后端标准 `mvn package` 通过：117 项、0 失败、0 错误、1 跳过。其中特定回归 `V3ApprovalWorkflowTests` 24 项、费用事实 7 项、换胎 4 项、附件 API/服务 5 项，均 0 失败/错误；充电导入 7 项中 1 项按既有条件跳过，不记为通过。管理端完整测试与 build 通过，小程序 43 项通过。全仓现行生产源码、正式 OpenAPI 和单一 baseline 无旧轮胎专属审批权限或接口引用；历史报告原文及保留的混合退役测试仍可出现 V2 文字，但不参与现行构建。`git diff --check` 通过，本轮报告及测试增量字面 Secret 候选 0。未做 GUI 验收或截图，没有提交代码，R7-V3-R-UI 与 V3-R 保持未关闭；业务方仍需人工复验统一审批模板授权、待办、附件和换胎生效。

### 角色权限页面树与待办概览派生权限（2026-10-19；人工 GUI 待验收）

角色授权抽屉改为当前八域导航的页面树：一级业务域、实际二三级页面、页面下操作权限；保留搜索、展开/折叠、父级全选与部分选中、已选及新增/移除数量、确认和服务端保存回显。`routes.js` 的现有访问权限是树中页面访问依据；`pagePermissionModel.js` 仅维护页面内操作映射和共用编码，不建立第二套后端权限。数组条件保持 OR；共用权限在多个页面显示同一勾选事实，仅向 `PUT /roles/{id}/permissions` 提交一次。取消共用访问权限会影响所有引用页面，抽屉明确提示；选操作时补足页面访问依赖，取消访问时处理该页面依赖操作。无法明确归属管理端页面的小程序与公共能力保留在独立区域。工作台“待办概览”只按 `approval:process`/`approval:return` 或 `expense:attribution:edit` 派生显示，不设独立授权编码；审批与费用卡继续读取各自原有正式 API，直接打开无权页面显示无权状态。服务端权限目录和角色授权按正式 `PermissionCode` 集合过滤/拒绝旧库残留的车辆删除、轮胎专属审批等退役编码，不改变现行 API 的业务权限语义。

修改文件：`admin-web/src/router/pagePermissionModel.js`、`routes.js`、`MyTodosV3View.vue`、`SystemUi1View.vue`、`RolePermissionTree.vue`、`RolePermissionPage.vue`、`App.vue`、`ui-final.css`、管理端测试；`backend/.../SystemManagementService.java` 与 `SystemManagementContractTests.java`；正式 docs/05～07 与 docs/36。保存角色授权后当前账号调用 `/me` 刷新，服务端角色结果重新读取；撤销当前账号页面权限后导航即时收敛。单一数据库 baseline、OpenAPI 和业务事实未改。自动验证：管理端完整测试与 build PASS；后端 `SystemManagementContractTests`、`V3ApprovalWorkflowTests`、`V3FinanceFactsTests` 共 42 项，0 失败、0 错误、0 跳过；标准 package 共 118 项，0 失败、0 错误、1 跳过（跳过项不计通过）。`git diff --check` PASS；新增源码的 Secret 字面值扫描未发现凭据，测试文件中的 password 字样均为测试夹具/断言。DOCX 已回读新增规则；视觉渲染因本机缺少 LibreOffice `soffice.exe` 为 NOT RUN。已将新 JAR 切至原 8080，健康接口返回 200；5173 与 3306 继续监听，未执行浏览器 GUI 验收。尚需业务方在该环境人工复验长权限树展开、共用权限撤回、保存后刷新权限、待办四种权限组合和浏览器直达无权路由；自动测试不记作 MANUAL GUI PASS。未提交代码，不关闭 R7-V3-R-UI 或 V3-R。

### 2026-10-19 货物管理权限映射核对

- 核对结论：正式 `docs/06` 已将货物定义为独立的 `product:view`、`product:manage`；此前运行代码误以 `template:view`、`template:manage` 保护货物页面及 `/products`，角色权限树因而把货物与线路模板显示成同一组权限。
- 已在工作区修正：货物菜单、路由、货物新增/编辑/启停按钮分别使用产品权限；`/products` 的写接口要求 `product:manage`，读接口要求 `product:view` 或 `template:manage`（后者仅供线路模板表单选择已有货物，不提供货物管理页面及写权限）。权限枚举、OpenAPI、三份单一 baseline、docs/04～07 相关章节及正反向测试同步更新。正式线路模板权限保持原义。
- 自动验证：管理端 25 项活动测试、7 项系统设置测试和其它组件测试通过；管理端 build 通过。后端标准 package 的 118 项测试中 0 失败、0 错误、1 跳过。`git diff --check` 无空白错误。未进行人工 GUI 验收。
- 3306 当前隔离验证库的两项新权限种子及既有角色授权尚未同步；自动权限审查拒绝了将旧模板授权直接映射到货物授权的数据库写入。现有 5173 前端读取工作区新代码，8080 后端仍在运行上一版 JAR；因此当前环境不能认定为本项已可人工复验。保留现有数据及授权，待明确批准具体授权映射后再同步并重启 8080。本项人工状态：NOT RUN。

#### 货物权限拆分部署补记（2026-10-19）

业务方明确批准将旧 `template:view`、`template:manage` 在 3306 `fleet_ops` 的现有授权等价映射为 `product:view`、`product:manage`，仅涉及 `ADMIN` 与现有“调度”角色。已添加两项权限种子及对应四条角色授权；其他角色未新增货物授权，业务表及历史事实未修改。8080 已替换为本轮 `mvn package` 产出的 JAR（SHA-256 `E95AED26994E23A939DC2B06F637A36ECE49609983100BD8445A36C7EE7F4C3F`），健康接口返回 200；5173 和 3306 仍运行。上节数据库写入受阻的状态现已解除，货物权限人工 GUI 复验仍为 **NOT RUN**，不得记为 PASS。

### 2026-10-19 角色权限树全页去重与页面映射核对

- 业务方发现“司机管理”和“绑定关系”重复展示相同的绑定、解绑权限。全量静态核查八域导航的 37 个菜单路由：共 14 组权限编码在多个页面复用，涉及绑定关系、两类车辆出勤、费用台账与支出页、工资台账与转派核对、经营概览与公司经营等；原树为每个出现位置都渲染可操作复选框，造成误解。`approval:view` 不是正式 PermissionCode，已从“我的申请”路由访问条件移除；该页继续使用现有 `approval:create` 授权。
- 修复后的树对 70 项有效 PermissionCode 逐项归属：66 项在正式页面树中各只有一个可勾选位置，`salary:mine`、`task:depart`、`task:unload`、`tire:request` 四项小程序司机能力保留在公共区。司机/车辆/车挂的绑定、解绑及绑定历史统一归“绑定关系”配置；费用归属归“待归属费用”。其它使用同一编码的页面显示共享来源说明，不重复渲染复选框，也不新增第二套权限。无独立编码的复用页面仍按真实路由授权出现；角色预览按实际路由访问条件判断。
- 操作依赖回归：选择绑定动作自动补足绑定关系页面访问；撤销绑定关系访问时移除该页专属绑定动作；父级计数与部分选中只统计唯一权限；共享页面无独立勾选。后端 PermissionCode、数据库及正式 API 鉴权均未改变。
- 验证：管理端完整测试通过（26 项活动 V3 测试、7 项系统设置测试及其它组件测试）；管理端 build 通过；`git diff --check` 通过；本轮修改代码的字面 Secret 扫描 0 命中。未修改后端代码或契约，故本轮未重复执行后端测试/package。5173、8080、3306 在运行，8080 健康接口返回 200。人工 GUI 复验状态：**NOT RUN**；需检查角色权限树中绑定关系、费用归属、工资及车辆出勤的唯一勾选位置、共享提示、保存回显和无权限路由。

### 2026-10-19 批量调度已授权却显示“无权限”修复

- 业务方截图中角色已有 `dispatch:view`、`dispatch:batch`，可进入“批量调度”，但页面首次加载调用完整 `GET /drivers`；该接口正式要求 `driver:list`，`Promise.all` 因 403 整体失败，页面误显示“无权限”。该角色未授予司机资料权限，不能通过扩大角色授权掩盖错误。
- 调度页改用 `GET /dispatch-batches/driver-options`（`dispatch:view`，仅有效司机分页候选，返回 id、类型、姓名、手机号、状态、当前车与车牌，不包含身份证号、驾驶证号、年龄等司机资料）；完整 `GET /drivers` 继续仅由 `driver:list` 访问。线路模板列表允许 `template:view` 或 `dispatch:view` 只读；模板写权限、司机资料写权限及调度正式发布规则未变。车辆/车挂补充信息仍只在已有 `vehicle:list` 权限下读取。
- 更新文件：`admin-web/src/api/modules.js`、`admin-web/src/views/DispatchWorkbenchV3View.vue`、`admin-web/test/run-tests.mjs`、`backend/src/main/java/com/dfwl/fleet/route/controller/DispatchBatchController.java`、`RouteTemplateController.java`、`backend/src/test/java/com/dfwl/fleet/system/SystemManagementContractTests.java`、`docs/openapi.yaml`、正式 docs/04、06、07。本轮无数据库结构、权限种子或角色授权变更。
- 自动验证：管理端 27 项活动 V3 测试、7 项系统设置测试及其它组件测试通过，build 通过。后端标准 package 的 119 项测试中 0 失败、0 错误、1 跳过；定向测试证明调度角色可取精简候选和模板但不可取完整司机资料，无调度权限者不可直调候选。`git diff --check` 通过，修改文件字面 Secret 扫描 0 命中。
- 已更新 8080 JAR（SHA-256 `7D7063A9D804EEE9CD87B8CD51C265FC30CED660B8D0663AE57DB0AF421CD478`），健康检查 200；5173、8080、3306 均运行。业务方人工 GUI 复验状态仍为 **NOT RUN**：刷新 5173，使用只有调度权限而没有司机资料权限的角色检查模板、司机候选、预览与发布；勿将自动测试记为人工 PASS。


### 2026-10-19 V3.0 页面＋按钮权限统一重构（代码级验证）

- 实施范围：后端 `FunctionRegistry` 是唯一功能定义，登记 41 个页面（36 个管理端菜单页、5 个小程序页面）和 54 个按钮。`FunctionAccessGuard` 在启动时一次编译受保护 Controller 的 API 关联；未登记功能或未标注的受保护路由启动失败，请求期仅查询内存映射和会话权限集合。安全共享读取 API 使用 OR；按钮还要求所属页面授权。角色页面只展示页面和按钮；待办概览由审批与归属权限派生。完整矩阵及 159 个 OpenAPI 方法/路径条目见 `docs/37-V3-页面按钮权限与API关联清单.md`。
- 敏感范围：绑定关系使用精简司机、车辆、车挂候选 DTO，批量调度使用精简司机和车辆候选 DTO；不因页面共享 API 暴露身份证、驾驶证、行驶证等完整资料。审批冻结节点、本人/参与范围、附件 Purpose、费用、工资、运输、轮胎和结算业务强校验继续由服务层执行。
- 旧体系：删除运行期 `PermissionCode` 常量与逐接口权限种子，三份单一 baseline 保留授权表但功能定义由启动同步。普通角色不因新功能注册自动加权；ADMIN 及全新空库首次初始化的系统 DRIVER 按固定初始化策略赋权。已有开发库的旧授权不设计兼容迁移，重新授权需管理员按新页面树确认。
- 性能与失效：令牌会话持有权限快照；正常请求不重新扫描注解，也不查询完整角色授权表。角色授权变化、用户或司机停用撤销相关会话，并在事务提交后再次撤销事务期间签发的会话。角色启用及司机禁用状态仍逐请求轻量校验。H2 本机样本：缓存命中平均约 0.037 ms，重新加载约 0.059 ms；为测试环境一次性样本，不作为生产延迟承诺。未引入 Redis。
- 自动验证：后端标准 package 125 项测试，0 失败、0 错误、1 跳过；管理端完整测试和 build 通过，小程序 43 项测试通过。以上结果以最后一次代码修改后的复跑为准；隔离 MySQL 空库导入 54 张表，首次后端启动同步 41 个页面和 54 个按钮；ADMIN 授权 95 项、普通角色新增授权 0 项。随后在该隔离库撤销系统 DRIVER 的全部授权并重启，复查 DRIVER 仍为 0 项，证明启动同步不恢复被撤销的授权。OpenAPI 的 159 个方法/路径条目与运行时关联由自动测试逐项核对。最终 `git diff --check` 通过；新增代码与资源扫描未发现字面 Secret。原 `backend/target` JAR 被既有进程占用，标准 package 在同仓库临时 Maven POM 指定的隔离 `.runtime/permission-target` 中完成，未停止既有 8080 验证服务；本轮限定代码级验证，现有 8080 进程仍为上一版 JAR，5173/8080 的人工复验须在后续部署新版后进行。未进行 GUI 功能验收或截图，人工状态 **NOT RUN**。


- 本轮主要变更：后端 `security/FunctionRegistry.java`、`FunctionAccess*.java`、`PermissionCatalogSynchronizer.java`、`TokenAuthenticationService.java`、各受保护 Controller 及相关 Service、受限绑定/调度候选 DTO 与 API；管理端路由、导航、角色权限树及按钮判断；小程序权限与页面判断；三份单一 baseline、OpenAPI、正式 docs/01～07、docs/36 和新 `docs/37`。管理端 36 菜单页、审批深链及登录页的实际 GUI 权限和视觉状态均待业务方人工验收。
- 文档结构及 DOCX ZIP 完整性已验证；本机缺少 LibreOffice，`render_docx.py` 无法渲染 01～07 的页面图，因此未声称完成 Word 排版视觉检查。

### V3.0 Token 持久化与权限无感刷新整改（2026-10-20，代码与自动验证）

本轮在现有未提交工作区增量实现统一 WEB/MINI 会话：`TokenAuthenticationService` 使用安全随机 Token，MySQL `auth_session` 仅保存 SHA-256 摘要、用户、终端、签发/到期/撤销时间和签发时认证版本；WEB 有效期 7 天、MINI 有效期 30 天。缓存未命中时从 MySQL 恢复，并重新检查用户、角色、司机状态和当前授权。`sys_user.auth_version` 用于阻止密码/身份变更后的旧会话恢复，`sys_role.permission_version` 用于授权变更后刷新权限集合。角色授权事务提交后失效缓存而不撤销 Token；回滚不影响会话。用户、司机、角色停用，修改或重置密码，以及主动退出，仍撤销相关服务端会话；普通资料修改不撤销。司机禁用的数据库会话撤销改为在禁用事务内执行，缓存提交后失效，避免在 `afterCommit` 中写数据库导致撤销未持久化。

管理端和小程序登录分别声明 WEB、MINI；两端退出均调用正式 `/auth/logout`，失败仍清除本地凭据并提示服务端撤销未确认。关键审批、费用归属/冲销、调度发布/导入、运输任务与月结写入增加事务内当前授权复核，并通过角色行锁与并发撤权排序；原有业务状态、审批节点资格、司机范围和财务事实校验保留。未引入 Redis 或第二套 Token/权限机制。

修改范围：`backend/src/main/java/com/dfwl/fleet/security/{TokenAuthenticationService,AuthSessionRepository,TransactionalAuthorizationGuard}.java`、认证/用户/角色仓储与服务、上述关键写入服务及其测试；`admin-web/src/stores/auth.js`、`admin-web/src/layouts/AppLayout.vue` 和管理端测试；`mini-program/pages/{login,profile}/index.js`、`mini-program/utils/request.js` 和小程序测试；三份单一数据库 baseline、`docs/openapi.yaml`、正式 docs/01～07。本轮未改正式财务、审批或运输状态规则。

自动验证：后端完整 package **133 项，0 失败、0 错误、1 跳过**；管理端完整测试、build 通过；小程序 **46 项**通过。后端新增重启恢复、WEB/MINI 期限、摘要唯一、到期/退出拒绝、角色和用户停用、多终端、角色授权刷新与回滚、数据库异常安全拒绝及并发撤权与关键写入测试。隔离 MySQL `13329` 的两个空库分别导入 `database.sql` 与 Flyway V1：各 **55 张表**，会话表各 8 列、5 个索引项，列类型对照差异 0；隔离 `18080` 后端启动健康检查 200，功能注册 95 项，隔离账号正式 API 登录后重启，原 Token 继续访问 `/me`，退出后 401，数据库仅检出对应摘要且已撤销。隔离验证进程已停止，未触碰原 3306 数据。使用 `core.whitespace=cr-at-eol` 处理现有工作区 CRLF 后 `git diff --check` 通过；新增源码字面 Secret 扫描候选 0。

人工 GUI 验收 **NOT RUN**，未生成截图或记为人工 PASS。现有 8080/5173/3306 仍为此前验证环境，本轮没有在保留现有测试数据的 3306 上应用新 baseline，也没有把新版 JAR 切到 8080；业务方在该环境复验前须先安排数据库结构和应用版本同步。正式 docs/01～07 文本已回读；本机缺少 LibreOffice `soffice.exe`，Word 页面渲染检查为 **BLOCKED**。未提交代码，R7-V3-R-UI 与 V3-R 保持开放。
