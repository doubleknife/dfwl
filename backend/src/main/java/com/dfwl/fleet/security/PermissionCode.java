package com.dfwl.fleet.security;

public enum PermissionCode {
    TEMPLATE_VIEW("template:view"),
    TEMPLATE_MANAGE("template:manage"),
    TASK_VIEW("task:view"),
    TASK_PARTICIPANTS_VIEW("task:participants:view"),
    TASK_WEIGHT_VIEW("task:weight:view"),
    TASK_CREATE("task:create"),
    TASK_PUBLISH("task:publish"),
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
    SALARY_MINE("salary:mine"),
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
