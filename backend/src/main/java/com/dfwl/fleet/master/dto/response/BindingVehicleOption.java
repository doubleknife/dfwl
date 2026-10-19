package com.dfwl.fleet.master.dto.response;

public record BindingVehicleOption(long id, String plateNo, int status, Long currentTrailerId,
                                   String currentTrailerPlateNo) { }
