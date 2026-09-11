package com.dfwl.fleet.security;

public record CurrentDriverContext(
        long driverId,
        String driverType,
        Long currentVehicleId
) {
    public boolean outsourced() {
        if (driverType == null) {
            return false;
        }
        String normalized = driverType.trim().toUpperCase(java.util.Locale.ROOT);
        return "OUTSOURCED".equals(normalized)
                || "OUTSOURCE".equals(normalized)
                || "EXTERNAL".equals(normalized)
                || "外协".equals(driverType.trim());
    }
}
