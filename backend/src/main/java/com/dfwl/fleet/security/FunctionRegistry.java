package com.dfwl.fleet.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The server-side source of page and button functions. API bindings are read from FunctionAccess at startup. */
@Component
public final class FunctionRegistry {
    public record Function(String code, String name, String type, String domain,
                           String group, String pageKey, String parentCode, String dataScope) { }

    private final Map<String, Function> functions = new LinkedHashMap<>();

    public FunctionRegistry() {
        page("工作台", "", "dashboard", "经营概览", "经营汇总");
        page("运输管理", "", "products", "货物管理", "货物资料");
        page("运输管理", "", "templates", "线路模板", "线路模板");
        page("运输管理", "调度工作台", "dispatch", "批量调度", "精简司机候选及调度草稿");
        page("运输管理", "调度工作台", "task-imports", "任务导入", "任务导入及原始文件");
        page("运输管理", "调度工作台", "routes", "运输任务", "全部运输任务及详情");
        page("运输管理", "车辆出勤", "dispatch-internal", "自有车辆", "自有车辆月度台账");
        page("运输管理", "车辆出勤", "dispatch-outsourced", "外协车辆", "外协车辆月度台账");
        page("车辆与人员", "", "drivers", "司机管理", "司机完整资料及证件");
        page("车辆与人员", "", "vehicles", "车辆管理", "车辆完整资料");
        page("车辆与人员", "", "trailers", "车挂管理", "车挂资料");
        page("车辆与人员", "", "binding", "绑定关系", "车辆、司机及车挂绑定历史");
        page("车辆与人员", "轮胎管理", "tires", "轮胎库存", "轮胎资料及历史");
        page("车辆与人员", "轮胎管理", "tire-imports", "轮胎入库", "轮胎导入及历史");
        page("费用管理", "", "expense-apply", "费用申请", "正式费用类别与本人申请");
        page("费用管理", "", "expense-ledger", "费用台账", "正式费用明细");
        page("费用管理", "", "expense-unassigned", "待归属费用", "待归属费用明细");
        page("费用管理", "", "charging-imports", "充电订单导入", "充电订单导入及原始文件");
        page("费用管理", "", "charging-companies", "充电公司模板配置", "充电公司映射");
        page("审批中心", "", "approvals-todo", "我的待办", "本人符合冻结节点资格的审批待办");
        page("审批中心", "", "approvals-mine", "我的申请", "本人申请");
        page("审批中心", "", "approvals-history", "审批记录", "有权查看的审批历史");
        page("审批中心", "", "approvals-flows", "流程与模板配置", "审批模板与冻结版本");
        page("工资与考勤", "", "attendance", "司机出勤", "自有司机出勤");
        page("工资与考勤", "", "salaries", "工资台账", "参与人工资资料");
        page("经营与结算", "收入与利润", "finance-company", "公司收支利润", "公司经营汇总");
        page("经营与结算", "收入与利润", "finance-templates", "线路收支利润", "模板经营汇总");
        page("经营与结算", "收入与利润", "finance-tasks", "单趟收支利润", "任务经营汇总");
        page("经营与结算", "支出", "finance-vehicles", "车辆支出", "车辆费用明细");
        page("经营与结算", "支出", "finance-drivers", "司机支出", "司机费用明细");
        page("经营与结算", "支出", "finance-energy", "能源费用", "能源费用明细");
        page("经营与结算", "", "settlements", "月度结算", "冻结结算版本");
        page("系统设置", "", "users", "用户管理", "用户及角色资料");
        page("系统设置", "", "roles", "角色权限", "角色与功能授权");
        page("系统设置", "", "audit", "操作日志", "审计日志");
        page("小程序", "", "mini-dispatch", "单人派单", "授权非司机的运输任务摘要、有效模板和精简司机候选");
        page("小程序", "", "mini-routes", "司机我的线路", "本人当前参与任务，不含经营和工资字段");
        page("小程序", "", "mini-salary", "司机工资单", "本人薪资");
        page("小程序", "", "mini-expense-apply", "费用申请", "本人费用申请");
        page("小程序", "", "mini-approvals", "小程序审批中心", "本人申请及符合节点资格的待办");
        page("小程序", "", "mini-tire-request", "司机换胎申请", "本人换胎申请");

        button("products", "manage", "维护货物");
        button("templates", "manage", "维护线路模板");
        button("dispatch", "publish", "预览并发布任务");
        button("task-imports", "submit", "提交任务导入");
        button("routes", "cancel", "取消未发车任务");
        button("routes", "transfer", "转派任务");
        button("routes", "depart", "确认发车");
        button("routes", "unload", "确认卸货");
        button("routes", "weight-adjust", "调整任务重量");
        button("routes", "amount-adjust", "调整任务金额");
        button("dispatch-internal", "export", "导出自有车辆月账");
        button("dispatch-outsourced", "export", "导出外协车辆月账");
        button("drivers", "add", "新增司机");
        button("drivers", "import", "批量导入");
        button("drivers", "edit", "编辑及启停司机");
        button("drivers", "reset-password", "重置司机密码");
        button("vehicles", "add", "新增车辆");
        button("vehicles", "edit", "编辑车辆");
        button("trailers", "add", "新增车挂");
        button("trailers", "edit", "编辑车挂");
        button("binding", "bind-driver", "绑定司机与车辆");
        button("binding", "unbind-driver", "解绑司机与车辆");
        button("binding", "bind-trailer", "绑定车挂与车辆");
        button("binding", "unbind-trailer", "解绑车挂与车辆");
        button("tires", "manage", "维护轮胎资料");
        button("tire-imports", "submit", "提交轮胎入库");
        button("expense-apply", "submit", "提交费用申请");
        button("expense-ledger", "reverse", "冲销费用");
        button("expense-ledger", "export", "导出费用台账");
        button("expense-unassigned", "attribute", "调整费用归属");
        button("charging-imports", "import", "确认充电订单入账");
        button("charging-companies", "manage", "维护充电公司映射");
        button("approvals-todo", "approve", "审批通过");
        button("approvals-todo", "return", "打回审批");
        button("approvals-mine", "withdraw", "撤回申请");
        button("approvals-mine", "resubmit", "重新提交申请");
        button("approvals-flows", "manage", "维护审批流程");
        button("attendance", "export", "导出司机出勤");
        button("salaries", "adjust", "核对参与工资");
        button("salaries", "export", "导出工资台账");
        button("settlements", "generate", "生成结算版本");
        button("users", "add", "新增用户");
        button("users", "edit", "编辑及启停用户");
        button("users", "reset-password", "重置用户密码");
        button("roles", "add", "新增角色");
        button("roles", "edit", "编辑及启停角色");
        button("roles", "grant", "配置角色权限");
        button("mini-dispatch", "publish", "发布任务");
        button("mini-dispatch", "transfer", "转单");
        button("mini-dispatch", "cancel", "取消任务");
        button("mini-routes", "depart", "司机确认发车");
        button("mini-routes", "unload", "司机确认卸货");
        button("mini-expense-apply", "submit", "发起费用申请");
        button("mini-approvals", "approve", "小程序审批通过");
        button("mini-approvals", "return", "小程序打回审批");
        button("mini-approvals", "withdraw", "小程序撤回申请");
        button("mini-tire-request", "submit", "司机发起换胎申请");
    }

    public static String pageCode(String key) { return "PAGE_" + key.replace('-', '_').toUpperCase(); }
    public static String buttonCode(String page, String action) {
        return "BUTTON_" + page.replace('-', '_').toUpperCase() + "_" + action.replace('-', '_').toUpperCase();
    }
    private void page(String domain, String group, String key, String name, String scope) {
        put(new Function(pageCode(key), name, "PAGE", domain, group, key, null, scope));
    }
    private void button(String page, String action, String name) {
        Function parent = functions.get(pageCode(page));
        if (parent == null) throw new IllegalArgumentException("Unknown page: " + page);
        put(new Function(buttonCode(page, action), name, "BUTTON", parent.domain(), parent.group(), page, parent.code(), null));
    }
    private void put(Function function) {
        if (functions.putIfAbsent(function.code(), function) != null)
            throw new IllegalArgumentException("Duplicate function: " + function.code());
    }
    public List<Function> all() { return List.copyOf(functions.values()); }
    public Function find(String code) { return functions.get(code); }
    public Set<String> codes() { return Set.copyOf(functions.keySet()); }
    public List<Function> pages() { return new ArrayList<>(functions.values().stream().filter(f -> "PAGE".equals(f.type())).toList()); }
}
