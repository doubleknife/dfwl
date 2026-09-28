package com.dfwl.fleet.route.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.notification.NotificationService;
import com.dfwl.fleet.route.domain.RouteStatus;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.system.service.AuditWriteService;
import com.dfwl.fleet.route.dto.response.DriverRouteResponse;
import com.dfwl.fleet.route.dto.request.RouteReasonRequest;
import com.dfwl.fleet.route.dto.request.RouteRequest;
import com.dfwl.fleet.route.dto.response.RouteResponse;
import com.dfwl.fleet.route.dto.response.RouteWeightVersionResponse;
import com.dfwl.fleet.route.dto.request.UnloadRequest;
import com.dfwl.fleet.route.dto.request.WeightAdjustmentRequest;
import com.dfwl.fleet.route.repository.RouteRepository;
import com.dfwl.fleet.route.repository.RouteParticipantRepository;
import com.dfwl.fleet.route.repository.RouteRepository.LoadStandardConfig;
import com.dfwl.fleet.route.repository.RouteRepository.RouteBindingSnapshot;
import com.dfwl.fleet.route.policy.RouteAccessPolicy;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteService {

    private static final DateTimeFormatter ROUTE_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RouteRepository repository;
    private final RouteParticipantRepository participants;
    private final CurrentUserService currentUserService;
    private final RouteAccessPolicy routeAccessPolicy;
    private final AuditWriteService audit;
    private final NotificationService notifications;

    public RouteService(RouteRepository repository, RouteParticipantRepository participants, CurrentUserService currentUserService, AuditWriteService audit, RouteAccessPolicy routeAccessPolicy, NotificationService notifications) {
        this.repository = repository;
        this.participants = participants;
        this.currentUserService = currentUserService;
        this.routeAccessPolicy = routeAccessPolicy;
        this.audit = audit;
        this.notifications = notifications;
    }

    public PageResponse<RouteResponse> list(int pageNo, int pageSize, String status) {
        return list(pageNo, pageSize, status, null, null, null, null);
    }

    public PageResponse<RouteResponse> list(int pageNo, int pageSize, String status, LocalDate businessDate,
                                            Long customerId, Long driverId, String keyword) {
        return repository.list(pageNo, Math.min(Math.max(pageSize, 1), 200), status,
                businessDate, customerId, driverId, keyword);
    }

    public PageResponse<DriverRouteResponse> listForDriver(AuthenticatedUser user, int pageNo, int pageSize, String status) {
        CurrentDriverContext driver = currentUserService.requireDriver(user);
        PageResponse<RouteResponse> routes = repository.listForDriver(driver.driverId(), pageNo, Math.min(Math.max(pageSize, 1), 200), status);
        return new PageResponse<>(
                routes.pageNo(),
                routes.pageSize(),
                routes.total(),
                routes.records().stream().map(DriverRouteResponse::from).toList());
    }

    public RouteResponse find(long id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_001));
    }

    public List<RouteParticipantRepository.Participant> participants(long id) {
        find(id);
        return participants.list(id);
    }

    public DriverRouteResponse findForDriver(AuthenticatedUser user, long id) {
        routeAccessPolicy.ensureDriverCanViewRoute(user, id);
        return DriverRouteResponse.from(find(id));
    }

    public List<RouteWeightVersionResponse> weightVersions(long id) {
        find(id);
        return repository.weightVersions(id);
    }

    @Transactional
    public RouteResponse create(RouteRequest request, long operatorId) {
        validateCustomerProduct(request);
        String businessKey = businessKey(request);
        if (repository.duplicateBusinessKeyExists(businessKey, null)) {
            throw new BusinessException(ErrorCode.ROUTE_005);
        }
        try {
            long id = repository.create(nextRouteNo(), businessKey, request, operatorId);
            return find(id);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public RouteResponse update(long id, RouteRequest request, long operatorId) {
        repository.lockRoute(id);
        RouteResponse current = find(id);
        if (!RouteStatus.UNPUBLISHED.name().equals(current.status()) && !RouteStatus.PUBLISHED.name().equals(current.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        validateCustomerProduct(request);
        // A published participant is a historical assignment, never an editable projection.
        if (RouteStatus.PUBLISHED.name().equals(current.status())
                && !java.util.Objects.equals(current.assignedDriverId(), request.assignedDriverId())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        String businessKey = businessKey(request);
        if (repository.duplicateBusinessKeyExists(businessKey, id)) {
            throw new BusinessException(ErrorCode.ROUTE_005);
        }
        if (!repository.updateBeforeDeparture(id, request, businessKey, operatorId)) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        return find(id);
    }

    @Transactional
    public void delete(long id, long operatorId) {
        repository.lockRoute(id);
        RouteResponse route = find(id);
        if (RouteStatus.PUBLISHED.name().equals(route.status())) {
            participants.cancelInitial(id, operatorId, "发车前删除线路");
        }
        if (!repository.logicDelete(id, operatorId)) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
    }

    @Transactional
    public RouteResponse publish(long id, long operatorId) {
        repository.lockRoute(id);
        RouteResponse route = find(id);
        if (!RouteStatus.UNPUBLISHED.name().equals(route.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        RouteBindingSnapshot snapshot = validateDriverVehicle(route.assignedDriverId());
        if (!repository.publish(id, route.status(), operatorId)) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        if (!participants.list(id).isEmpty()) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        participants.createInitial(id, snapshot.driverId(), snapshot.driverType(), snapshot.vehicleId(), null, null, operatorId);
        notifications.toDriver(route.assignedDriverId(), "route:assigned:" + id + ":publish", "ROUTE_ASSIGNED",
                "有新的运输线路", "线路 " + route.routeNo() + " 已发布，请查看任务", "ROUTE", id);
        return find(id);
    }

    @Transactional
    public RouteResponse depart(long id, long operatorId) {
        repository.lockRoute(id);
        RouteResponse route = find(id);
        if (!RouteStatus.PUBLISHED.name().equals(route.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        RouteBindingSnapshot snapshot = validateDriverVehicle(route.assignedDriverId());
        repository.lockVehicle(snapshot.vehicleId());
        if (!repository.depart(id, route.status(), snapshot, operatorId, LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
        participants.departed(id, snapshot.driverId(), snapshot.vehicleId(), operatorId, LocalDateTime.now());
        return find(id);
    }

    @Transactional
    public RouteResponse unload(long id, UnloadRequest request, long operatorId) {
        repository.lockRoute(id);
        RouteResponse route = find(id);
        if (!RouteStatus.IN_TRANSIT.name().equals(route.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        BigDecimal netWeight = calculateNetWeight(request.grossWeight(), request.tareWeight());
        LoadStandardResult loadStandard = calculateLoadStandard(route.departureVehicleId(), netWeight);
        long weightVersionId;
        try {
            weightVersionId = repository.insertWeightVersion(
                    id, 1, request.grossWeight(), request.tareWeight(), netWeight,
                    loadStandard.threshold(), loadStandard.met(), "UNLOAD", operatorId, null, List.of());
        } catch (DataIntegrityViolationException ex) {
            RouteResponse refreshed = find(id);
            if (RouteStatus.COMPLETED.name().equals(refreshed.status())) {
                return refreshed;
            }
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        if (!repository.unload(id, route.status(), weightVersionId, operatorId, LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        participants.completed(id, operatorId, LocalDateTime.now());
        return find(id);
    }

    @Transactional
    public RouteResponse adjustWeight(long id, WeightAdjustmentRequest request, long operatorId) {
        repository.lockRoute(id);
        RouteResponse route = find(id);
        if (!RouteStatus.COMPLETED.name().equals(route.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        if (!repository.weightAdjustAttachmentsBelongToRoute(id, request.attachmentIds())) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
        BigDecimal netWeight = calculateNetWeight(request.grossWeight(), request.tareWeight());
        LoadStandardResult loadStandard = calculateLoadStandard(route.departureVehicleId(), netWeight);
        long weightVersionId = repository.insertWeightVersion(
                id,
                repository.nextWeightVersionNo(id),
                request.grossWeight(),
                request.tareWeight(),
                netWeight,
                loadStandard.threshold(),
                loadStandard.met(),
                "ADJUSTMENT",
                operatorId,
                request.reason(),
                request.attachmentIds());
        repository.updateEffectiveWeight(id, weightVersionId, operatorId);
        return find(id);
    }

    @Transactional
    public RouteResponse cancel(long id, RouteReasonRequest request, long operatorId) {
        repository.lockRoute(id);
        RouteResponse route = find(id);
        if (!RouteStatus.UNPUBLISHED.name().equals(route.status()) && !RouteStatus.PUBLISHED.name().equals(route.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        if (!repository.updateStatus(id, route.status(), RouteStatus.CANCELLED.name(), "CANCEL", operatorId,
                request == null ? null : request.reason())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        participants.cancelInitial(id, operatorId, request == null ? null : request.reason());
        return find(id);
    }

    @Transactional
    public RouteResponse voidRoute(long id, RouteReasonRequest request, long operatorId) {
        repository.lockRoute(id);
        RouteResponse route = find(id);
        if (!RouteStatus.COMPLETED.name().equals(route.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        if (!repository.updateStatus(id, route.status(), RouteStatus.VOIDED.name(), "VOID", operatorId,
                request == null ? null : request.reason())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        audit.write("ROUTE", "ROUTE", id, "VOID",
                java.util.Map.of("status", route.status()),
                java.util.Map.of("status", RouteStatus.VOIDED.name()), operatorId, request.reason());
        return find(id);
    }

    @Transactional
    public RouteResponse reactivate(long id, long operatorId) {
        repository.lockRoute(id);
        RouteResponse route = find(id);
        if (!RouteStatus.VOIDED.name().equals(route.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        if (!repository.updateStatus(id, route.status(), RouteStatus.COMPLETED.name(), "REACTIVATE", operatorId, null)) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        audit.write("ROUTE", "ROUTE", id, "REACTIVATE",
                java.util.Map.of("status", route.status()),
                java.util.Map.of("status", RouteStatus.COMPLETED.name()), operatorId, null);
        return find(id);
    }

    private void validateCustomerProduct(RouteRequest request) {
        if (!repository.existsActiveCustomer(request.customerId()) || !repository.existsActiveProduct(request.productId())) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
    }

    private RouteBindingSnapshot validateDriverVehicle(Long driverId) {
        if (driverId == null) {
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
        RouteBindingSnapshot snapshot = repository.findBindingSnapshot(driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_003));
        boolean validDriver = snapshot.driverStatus() == 1;
        if (!"INTERNAL".equals(snapshot.driverType())) {
            // The existing publish request has no outsourced plate/salary fields. BIZ-1B owns that flow.
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
        boolean validVehicle = snapshot.vehicleId() != null
                && snapshot.vehicleStatus() != null
                && snapshot.vehicleStatus() == 1
                && snapshot.vehicleInsuranceComplete() != null
                && snapshot.vehicleInsuranceComplete() == 1;
        boolean validTrailer = snapshot.trailerId() == null
                || (snapshot.trailerStatus() != null
                && snapshot.trailerStatus() == 1
                && snapshot.trailerInsuranceComplete() != null
                && snapshot.trailerInsuranceComplete() == 1);
        if (!validDriver || !validVehicle || !validTrailer) {
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
        return snapshot;
    }

    private BigDecimal calculateNetWeight(BigDecimal grossWeight, BigDecimal tareWeight) {
        if (grossWeight.signum() <= 0 || tareWeight.signum() <= 0 || grossWeight.compareTo(tareWeight) <= 0) {
            throw new BusinessException(ErrorCode.ROUTE_004);
        }
        return grossWeight.subtract(tareWeight);
    }

    private LoadStandardResult calculateLoadStandard(Long vehicleId, BigDecimal netWeight) {
        if (vehicleId == null) {
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
        LoadStandardConfig config = repository.loadStandardForVehicle(vehicleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_003));
        BigDecimal threshold;
        if ("PERCENT".equals(config.loadStandardType())) {
            if (config.maxLoad() == null || config.loadStandardPercent() == null || config.loadStandardMinLoad() != null) {
                throw new BusinessException(ErrorCode.ROUTE_003);
            }
            threshold = config.maxLoad().multiply(config.loadStandardPercent()).setScale(3, RoundingMode.HALF_UP);
        } else if ("FIXED".equals(config.loadStandardType())) {
            if (config.loadStandardMinLoad() == null || config.loadStandardPercent() != null) {
                throw new BusinessException(ErrorCode.ROUTE_003);
            }
            threshold = config.loadStandardMinLoad();
        } else {
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
        return new LoadStandardResult(threshold, netWeight.compareTo(threshold) >= 0);
    }

    private String businessKey(RouteRequest request) {
        return request.customerId() + "|" + request.businessDate() + "|" + request.direction()
                + "|" + request.loadingPlace() + "|" + request.unloadingPlace();
    }

    private String nextRouteNo() {
        return "R" + LocalDateTime.now().format(ROUTE_NO_TIME) + UUID.randomUUID().toString().substring(0, 6);
    }

    private record LoadStandardResult(BigDecimal threshold, boolean met) {
    }
}
