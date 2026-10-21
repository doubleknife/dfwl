package com.dfwl.fleet.route.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.route.dto.request.RouteTemplateRequest;
import com.dfwl.fleet.route.dto.response.RouteTemplateResponse;
import com.dfwl.fleet.route.repository.RouteTemplateRepository;
import com.dfwl.fleet.system.service.AuditWriteService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RouteTemplateResponse create(RouteTemplateRequest request, long actor) {
        validate(request);
        rejectBusinessDuplicate(request, null);
        for (int attempt = 0; attempt < 10; attempt++) {
            String stableCode = String.format(Locale.ROOT, "L%07d", ThreadLocalRandom.current().nextInt(10_000_000));
            try {
                long id = repository.create(request, stableCode, actor);
                RouteTemplateResponse created = find(id);
                audit.write("TEMPLATE", "ROUTE_TEMPLATE", id, "CREATE", null, snapshot(created), actor, null);
                return created;
            } catch (DuplicateKeyException ex) {
                // A unique business key is final; a generated-code collision can use another code.
                rejectBusinessDuplicate(request, null);
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.DATA_002);
            }
        }
        throw new BusinessException(ErrorCode.DATA_002);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RouteTemplateResponse update(long id, RouteTemplateRequest request, long actor) {
        if (request.expectedVersion() == null) throw new BusinessException(ErrorCode.SYS_002);
        validate(request);
        RouteTemplateResponse before = find(id);
        if (!sameBusinessKey(before, request)) rejectBusinessDuplicate(request, id);
        try {
            if (!repository.update(id, request, actor)) throw new BusinessException(ErrorCode.ROUTE_002);
        } catch (DuplicateKeyException ex) {
            rejectBusinessDuplicate(request, id);
            throw new BusinessException(ErrorCode.DATA_002);
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
        if (!repository.lockEnabledProduct(request.productId())) throw new BusinessException(ErrorCode.DATA_001);
        if (request.defaultUnitPrice().scale() > 2 || request.defaultInformationFee().scale() > 2
                || request.defaultDriverSalary().scale() > 2) throw new BusinessException(ErrorCode.SYS_002);
    }

    private void rejectBusinessDuplicate(RouteTemplateRequest request, Long excludedId) {
        if (repository.existsBusinessDuplicate(request, excludedId)) {
            throw new BusinessException(ErrorCode.DATA_002.name(),
                    "相同客户、货物、装卸货地、单价、信息费和工资的线路模板已存在");
        }
    }

    private boolean sameBusinessKey(RouteTemplateResponse before, RouteTemplateRequest request) {
        return Objects.equals(before.customerName(), request.customerName().trim())
                && Objects.equals(before.productId(), request.productId())
                && Objects.equals(before.loadingPlace(), request.loadingPlace().trim())
                && Objects.equals(before.unloadingPlace(), request.unloadingPlace().trim())
                && before.defaultUnitPrice().compareTo(request.defaultUnitPrice()) == 0
                && before.defaultInformationFee().compareTo(request.defaultInformationFee()) == 0
                && before.defaultDriverSalary().compareTo(request.defaultDriverSalary()) == 0;
    }

    private Map<String, Object> snapshot(RouteTemplateResponse template) {
        return Map.of("stableCode", template.stableCode(), "name", template.name(),
                "customerName", template.customerName(), "productId", template.productId(),
                "defaultUnitPrice", template.defaultUnitPrice(),
                "defaultInformationFee", template.defaultInformationFee(),
                "defaultDriverSalary", template.defaultDriverSalary(), "enabled", template.enabled());
    }
}
