package com.dfwl.fleet.master.dto.response;

public record BindingDriverOption(long id, String name, String phone, String driverType,
                                  int status, Long currentVehicleId, String currentVehiclePlateNo) { }
