package com.dfwl.fleet.route.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DispatchPreviewResponse(String previewId, boolean valid, List<Row> rows) {
    public record Row(int rowNo, Long templateId, String templateName, Long driverId, String driverType,
                      String vehiclePlate, BigDecimal actualUnitPrice, BigDecimal actualInformationFee,
                      BigDecimal initialSalary, List<String> errors) {}
}
