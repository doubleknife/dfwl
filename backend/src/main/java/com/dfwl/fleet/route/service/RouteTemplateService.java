package com.dfwl.fleet.route.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.route.dto.request.RouteTemplateRequest;
import com.dfwl.fleet.route.dto.response.RouteTemplateResponse;
import com.dfwl.fleet.route.repository.RouteTemplateRepository;
import com.dfwl.fleet.system.service.AuditWriteService;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteTemplateService {
    private final RouteTemplateRepository repository;
    private final AuditWriteService audit;

    public RouteTemplateService(RouteTemplateRepository repository, AuditWriteService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public List<RouteTemplateResponse> list() { return repository.list(); }

    public RouteTemplateResponse find(long id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public RouteTemplateResponse create(RouteTemplateRequest request, long actor) {
        validate(request);
        try {
            long id = repository.create(request, actor);
            RouteTemplateResponse created = find(id);
            audit.write("TEMPLATE", "ROUTE_TEMPLATE", id, "CREATE", null, snapshot(created), actor, null);
            return created;
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public RouteTemplateResponse update(long id, RouteTemplateRequest request, long actor) {
        if (request.expectedVersion() == null) throw new BusinessException(ErrorCode.SYS_002);
        validate(request);
        RouteTemplateResponse before = find(id);
        try {
            if (!repository.update(id, request, actor)) throw new BusinessException(ErrorCode.ROUTE_002);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
        RouteTemplateResponse after = find(id);
        audit.write("TEMPLATE", "ROUTE_TEMPLATE", id, "UPDATE", snapshot(before), snapshot(after), actor, null);
        return after;
    }

    @Transactional
    public RouteTemplateResponse setEnabled(long id, int expectedVersion, boolean enabled, long actor) {
        RouteTemplateResponse before = find(id);
        if (before.enabled() == enabled) return before;
        if (!repository.setEnabled(id, expectedVersion, enabled, actor)) throw new BusinessException(ErrorCode.ROUTE_002);
        RouteTemplateResponse after = find(id);
        audit.write("TEMPLATE", "ROUTE_TEMPLATE", id, enabled ? "ENABLE" : "DISABLE",
                snapshot(before), snapshot(after), actor, null);
        return after;
    }

    private void validate(RouteTemplateRequest request) {
        if (!repository.productEnabled(request.productId())) throw new BusinessException(ErrorCode.DATA_001);
        if (request.defaultUnitPrice().scale() > 2 || request.defaultInformationFee().scale() > 2
                || request.defaultDriverSalary().scale() > 2) throw new BusinessException(ErrorCode.SYS_002);
    }

    private Map<String, Object> snapshot(RouteTemplateResponse template) {
        return Map.of("stableCode", template.stableCode(), "name", template.name(),
                "customerName", template.customerName(), "productId", template.productId(),
                "defaultUnitPrice", template.defaultUnitPrice(),
                "defaultInformationFee", template.defaultInformationFee(),
                "defaultDriverSalary", template.defaultDriverSalary(), "enabled", template.enabled());
    }
}
