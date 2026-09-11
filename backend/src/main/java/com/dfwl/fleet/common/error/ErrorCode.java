package com.dfwl.fleet.common.error;

public enum ErrorCode {
    AUTH_001("手机号或密码错误"),
    AUTH_002("账号停用"),
    AUTH_003("无权限"),
    SYS_001("系统异常"),
    SYS_002("参数校验失败"),
    SYS_003("请求格式错误"),
    SYS_004("认证令牌无效"),
    USER_001("用户不存在"),
    DATA_001("数据不存在"),
    DATA_002("数据已存在"),
    BIND_001("绑定关系已存在"),
    BIND_002("绑定关系不存在"),
    BIND_003("运输中禁止修改绑定关系"),
    ROUTE_001("线路不存在"),
    ROUTE_002("线路状态不允许当前操作"),
    ROUTE_003("司机车辆校验失败"),
    ROUTE_004("毛重必须大于皮重"),
    ROUTE_005("线路重复"),
    APPROVAL_001("审批流程不可用"),
    APPROVAL_002("审批状态不允许当前操作"),
    APPROVAL_003("当前审批人不匹配"),
    EXPENSE_001("费用不存在"),
    EXPENSE_002("费用状态不允许当前操作"),
    EXPENSE_003("费用归属不合法"),
    EXPENSE_004("审批费用必须冲销后重提"),
    EXPENSE_005("费用已冲销"),
    ATTACHMENT_001("附件不存在"),
    ATTACHMENT_002("附件参数不合法"),
    ATTACHMENT_003("附件类型不支持"),
    ATTACHMENT_004("附件归属无权限"),
    ATTACHMENT_005("审批附件不能通过普通接口删除"),
    TIRE_001("轮胎已申领或已有在途申请"),
    TIRE_002("轮胎不存在"),
    TIRE_003("OCR记录不存在"),
    TIRE_004("OCR确认胎号与库存胎号不一致");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
