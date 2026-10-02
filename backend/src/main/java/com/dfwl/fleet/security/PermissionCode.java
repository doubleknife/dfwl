package com.dfwl.fleet.security;

public enum PermissionCode {
    TEMPLATE_VIEW("template:view"),
    TEMPLATE_MANAGE("template:manage"),
    TASK_VIEW("task:view"),
    TASK_PARTICIPANTS_VIEW("task:participants:view"),
    TASK_WEIGHT_VIEW("task:weight:view"),
    DISPATCH_VIEW("dispatch:view"),
    DISPATCH_BATCH("dispatch:batch"),
    TASK_IMPORT_VIEW("task:import:view"),
    TASK_IMPORT_SUBMIT("task:import:submit"),
    TASK_CANCEL("task:cancel"),
    TASK_TRANSFER("task:transfer"),
    TASK_DEPART("task:depart"),
    TASK_UNLOAD("task:unload"),
    TASK_WEIGHT_ADJUST("task:weight:adjust"),
    TASK_AMOUNT_ADJUST("task:amount:adjust"),
    VEHICLE_LIST("vehicle:list"),
    VEHICLE_ADD("vehicle:add"),
    VEHICLE_VIEW("vehicle:view"),
    VEHICLE_EDIT("vehicle:edit"),
    VEHICLE_DELETE("vehicle:delete"),
    VEHICLE_BIND_DRIVER("vehicle:bindDriver"),
    VEHICLE_UNBIND_DRIVER("vehicle:unbindDriver"),
    VEHICLE_BINDING_HISTORY("vehicle:bindingHistory"),
    TRAILER_LIST("trailer:list"),
    TRAILER_ADD("trailer:add"),
    TRAILER_EDIT("trailer:edit"),
    TRAILER_BIND_VEHICLE("trailer:bindVehicle"),
    TRAILER_UNBIND_VEHICLE("trailer:unbindVehicle"),
    TRAILER_BINDING_HISTORY("trailer:bindingHistory"),
    DRIVER_LIST("driver:list"),
    DRIVER_ADD("driver:add"),
    DRIVER_EDIT("driver:edit"),
    DRIVER_BIND_VEHICLE("driver:bindVehicle"),
    DRIVER_UNBIND_VEHICLE("driver:unbindVehicle"),
    DRIVER_RESET_PASSWORD("driver:resetPassword"),
    SALARY_VIEW("salary:view"),
    SALARY_ADJUST("salary:adjust"),
    SALARY_EXPORT("salary:export"),
    SALARY_MINE("salary:mine"),
    ATTENDANCE_VIEW("attendance:view"),
    ATTENDANCE_EXPORT("attendance:export"),
    DISPATCH_LEDGER_VIEW("dispatch:ledger:view"),
    DISPATCH_LEDGER_EXPORT("dispatch:ledger:export"),
    APPROVAL_CREATE("approval:create"),
    APPROVAL_PROCESS("approval:process"),
    APPROVAL_RETURN("approval:return"),
    APPROVAL_HISTORY_VIEW("approval:history:view"),
    APPROVAL_FLOW_MANAGE("approval:flow:manage"),
    EXPENSE_LEDGER_VIEW("expense:ledger:view"),
    EXPENSE_ATTRIBUTION_EDIT("expense:attribution:edit"),
    EXPENSE_REVERSAL("expense:reversal"),
    EXPENSE_EXPORT("expense:export"),
    REPORT_TASK_VIEW("report:task:view"),
    REPORT_TEMPLATE_VIEW("report:template:view"),
    REPORT_COMPANY_VIEW("report:company:view"),
    SETTLEMENT_VIEW("settlement:view"),
    SETTLEMENT_GENERATE("settlement:generate"),
    USER_MANAGE("user:manage"),
    ROLE_MANAGE("role:manage"),
    PERMISSION_MANAGE("permission:manage"),
    AUDIT_VIEW("audit:view");

    private final String code;

    PermissionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
