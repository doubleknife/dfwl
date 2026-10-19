package com.dfwl.fleet.master.dto.response;

public record BindingTrailerOption(long id, String plateNo, String trailerNo, int status,
                                   Long currentVehicleId, String currentVehiclePlateNo) { }
