package com.dfwl.fleet.route.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.route.dto.request.TaskCancelRequest;
import com.dfwl.fleet.route.dto.request.TaskDepartRequest;
import com.dfwl.fleet.route.dto.request.TaskSalariesConfirmRequest;
import com.dfwl.fleet.route.dto.request.TaskTransferRequest;
import com.dfwl.fleet.route.dto.request.TaskUnloadRequest;
import com.dfwl.fleet.route.dto.request.TaskWeightAdjustmentRequest;
import com.dfwl.fleet.route.dto.response.DriverTaskResponse;
import com.dfwl.fleet.route.dto.response.TaskWeightVersionResponse;
import com.dfwl.fleet.route.dto.response.TransportParticipantResponse;
import com.dfwl.fleet.route.dto.response.TransportTaskResponse;
import com.dfwl.fleet.route.repository.TaskEvidenceRepository;
import com.dfwl.fleet.route.repository.TransportTaskRepository;
import com.dfwl.fleet.route.service.TaskDriverValidationService.DriverSnapshot;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import com.dfwl.fleet.security.TransactionalAuthorizationGuard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransportTaskService {
    private final JdbcTemplate jdbc;
    private final TransportTaskRepository tasks;
    private final TaskDriverValidationService drivers;
    private final TaskEvidenceRepository evidence;
    private final CurrentUserService users;
    private final TransactionalAuthorizationGuard authorization;

    public TransportTaskService(JdbcTemplate jdbc, TransportTaskRepository tasks,
                                TaskDriverValidationService drivers, TaskEvidenceRepository evidence,
                                CurrentUserService users, TransactionalAuthorizationGuard authorization) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.drivers = drivers;
        this.evidence = evidence;
        this.users = users;
        this.authorization = authorization;
    }

    public PageResponse<TransportTaskResponse> list(int pageNo, int pageSize, LocalDate businessDate,
                                                     String status, Long templateId, Long driverId, String taskNo) {
        return tasks.list(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 200),
                businessDate, status, templateId, driverId, taskNo);
    }

    public java.util.Map<Long, com.dfwl.fleet.route.repository.TransportTaskRepository.LastIdentity> lastIdentities(List<Long> ids) {
        return tasks.lastIdentities(ids);
    }

    public PageResponse<DriverTaskResponse> listForDriver(AuthenticatedUser user, int pageNo, int pageSize, String status) {
        return tasks.listForDriver(users.requireDriver(user).driverId(), Math.max(pageNo, 1),
                Math.min(Math.max(pageSize, 1), 200), status);
    }

    public TransportTaskResponse find(long id) {
        return tasks.find(id).orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_001));
    }

    public DriverTaskResponse findForDriver(AuthenticatedUser user, long id) {
        return tasks.findForDriver(id, users.requireDriver(user).driverId())
                .orElseThrow(() -> new AccessDeniedException("任务不属于当前司机"));
    }

    public List<TransportParticipantResponse> participants(long id, boolean includeSalary) {
        find(id);
        return tasks.participants(id, includeSalary);
    }

    public List<TaskWeightVersionResponse> weightVersions(long id) {
        find(id);
        return tasks.weightVersions(id);
    }

    @Transactional
    public List<TransportParticipantResponse> confirmParticipantSalaries(long taskId,
                                                                         TaskSalariesConfirmRequest request, long actor) {
        authorization.require(actor, "BUTTON_SALARIES_ADJUST");
        lockTask(taskId);
        TransportTaskResponse task = find(taskId);
        if (request == null || request.salaries() == null || request.salaries().isEmpty()
                || request.reason() == null || request.reason().isBlank())
            throw new BusinessException(ErrorCode.ROUTE_002);
        if (repeat(taskId, "SALARY_CONFIRM", request.requestKey(), request.toString()))
            return tasks.participants(taskId, true);
        if ("CANCELLED".equals(task.status())) throw new BusinessException(ErrorCode.ROUTE_002);
        List<Participant> participants = listParticipantFacts(taskId);
        Set<Long> ids = new HashSet<>();
        for (TaskSalariesConfirmRequest.SalaryDecision decision : request.salaries()) {
            if (decision == null || decision.participantId() == null || decision.expectedVersion() == null
                    || !validAmount(decision.amount()) || !ids.add(decision.participantId()))
                throw new BusinessException(ErrorCode.SYS_002);
        }
        if (ids.size() != participants.size() || participants.stream().anyMatch(p -> !ids.contains(p.id())))
            throw new BusinessException(ErrorCode.SYS_002);
        for (Participant participant : participants) {
            TaskSalariesConfirmRequest.SalaryDecision decision = request.salaries().stream()
                    .filter(item -> item.participantId() == participant.id()).findFirst().orElseThrow();
            if (participant.version() != decision.expectedVersion()) throw new BusinessException(ErrorCode.ROUTE_002);
            if (participant.salary().compareTo(decision.amount()) != 0)
                adjustSalary(participant, decision.amount(), request.reason().trim(), actor);
        }
        saveCommand(taskId, "SALARY_CONFIRM", request.requestKey(), request.toString());
        return tasks.participants(taskId, true);
    }

    @Transactional
    public TransportTaskResponse depart(long id, TaskDepartRequest request, AuthenticatedUser user) {
        authorization.require(user.id(), "BUTTON_ROUTES_DEPART", "BUTTON_MINI_ROUTES_DEPART");
        long driverId = users.requireDriver(user).driverId();
        lockTask(id);
        if (repeat(id, "DEPART", request.requestKey(), request.toString())) return find(id);
        TransportTaskResponse task = find(id);
        if (request.expectedVersion() != task.version()
                || !("PUBLISHED".equals(task.status()) || "TRANSFER_PENDING".equals(task.status())))
            throw new BusinessException(ErrorCode.ROUTE_002);
        Participant current = current(id);
        if (current.driverId() != driverId || !"ASSIGNED".equals(current.status()))
            throw new AccessDeniedException("仅当前参与司机可发车");
        DriverSnapshot snapshot = drivers.require(driverId, current.outsourcedPlate());
        if (!snapshot.driverType().equals(current.driverType())) throw new BusinessException(ErrorCode.ROUTE_003);
        if (snapshot.vehicleId() != null) drivers.lockAvailableCompanyVehicle(snapshot.vehicleId(), id);
        LocalDateTime now = LocalDateTime.now();
        int participantUpdated = jdbc.update("""
                UPDATE transport_participant SET status = 'IN_TRANSIT', departed_at = ?,
                    company_vehicle_id = ?, vehicle_plate_snapshot = ?, trailer_id = ?, trailer_plate_snapshot = ?,
                    updated_by = ?, version = version + 1 WHERE id = ? AND status = 'ASSIGNED' AND is_current = 1
                """, now, snapshot.vehicleId(), snapshot.vehiclePlate(), snapshot.trailerId(),
                snapshot.trailerPlate(), user.id(), current.id());
        if (participantUpdated != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        jdbc.update("""
                INSERT INTO departure_event (task_id, participant_id, departed_at, driver_id_snapshot,
                    driver_type_snapshot, company_vehicle_id, vehicle_plate_snapshot, trailer_plate_snapshot,
                    outsourced_plate_no)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, current.id(), now, driverId, snapshot.driverType(), snapshot.vehicleId(),
                snapshot.vehiclePlate(), snapshot.trailerPlate(), snapshot.outsourcedPlate());
        changeStatus(id, task.status(), "IN_TRANSIT", user.id(), null);
        saveCommand(id, "DEPART", request.requestKey(), request.toString());
        return find(id);
    }

    @Transactional
    public TransportTaskResponse transfer(long id, TaskTransferRequest request, long actor) {
        authorization.require(actor, "BUTTON_ROUTES_TRANSFER", "BUTTON_MINI_DISPATCH_TRANSFER");
        lockTask(id);
        TransportTaskResponse task = find(id);
        if (request.expectedVersion() != task.version()
                || !("IN_TRANSIT".equals(task.status()) || "TRANSFER_PENDING".equals(task.status())))
            throw new BusinessException(ErrorCode.ROUTE_002);
        Participant current = current(id);
        if (request.expectedCurrentParticipantId() != current.id() || request.newDriverId() == current.driverId()
                || request.reason() == null || request.reason().isBlank() || !validAmount(request.newSalary()))
            throw new BusinessException(ErrorCode.SYS_002);
        List<Participant> existing = listParticipantFacts(id);
        Set<Long> ids = new HashSet<>();
        for (TaskTransferRequest.SalaryDecision decision : request.allParticipantSalaries()) {
            if (decision == null || decision.participantId() == null || !validAmount(decision.amount())
                    || !ids.add(decision.participantId())) throw new BusinessException(ErrorCode.SYS_002);
        }
        if (ids.size() != existing.size() || existing.stream().anyMatch(p -> !ids.contains(p.id())))
            throw new BusinessException(ErrorCode.SYS_002);
        DriverSnapshot next = drivers.require(request.newDriverId(), request.outsourcedPlateNo());
        if (next.vehicleId() != null) drivers.lockAvailableCompanyVehicle(next.vehicleId(), id);
        for (Participant p : existing) {
            BigDecimal after = request.allParticipantSalaries().stream()
                    .filter(item -> item.participantId() == p.id()).findFirst().orElseThrow().amount();
            if (p.salary().compareTo(after) != 0) adjustSalary(p, after, request.reason().trim(), actor);
        }
        int previousUpdated = jdbc.update("""
                UPDATE transport_participant SET status = 'TRANSFERRED', is_current = 0,
                    ended_at = CURRENT_TIMESTAMP(3), end_reason = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND is_current = 1 AND status IN ('ASSIGNED', 'IN_TRANSIT')
                """, request.reason().trim(), actor, current.id());
        if (previousUpdated != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        long newId = insertTransferredParticipant(id, current.sequenceNo() + 1, next,
                request.newSalary(), request.reason().trim(), actor);
        int taskUpdated = jdbc.update("""
                UPDATE transport_task SET current_participant_id = ?, updated_by = ?, version = version + 1,
                    status = 'TRANSFER_PENDING' WHERE id = ? AND version = ? AND current_participant_id = ?
                """, newId, actor, id, task.version(), current.id());
        if (taskUpdated != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        insertStatus(id, task.status(), "TRANSFER_PENDING", actor, request.reason().trim());
        return find(id);
    }

    @Transactional
    public TransportTaskResponse unload(long id, TaskUnloadRequest request, AuthenticatedUser user) {
        authorization.require(user.id(), "BUTTON_ROUTES_UNLOAD", "BUTTON_MINI_ROUTES_UNLOAD");
        long driverId = users.requireDriver(user).driverId();
        lockTask(id);
        if (repeat(id, "UNLOAD", request.requestKey(), request.toString())) return find(id);
        TransportTaskResponse task = find(id);
        if (!"IN_TRANSIT".equals(task.status())) throw new BusinessException(ErrorCode.ROUTE_002);
        Participant current = current(id);
        if (current.driverId() != driverId || !"IN_TRANSIT".equals(current.status()))
            throw new AccessDeniedException("仅当前参与司机可卸货");
        BigDecimal net = netWeight(request.grossWeight(), request.tareWeight());
        Standard standard = standard(current, net);
        long versionId = insertWeight(id, 1, request.grossWeight(), request.tareWeight(), net,
                standard, "UNLOAD", user.id(), null, List.of());
        evidence.bindOriginalTickets(id, current.id(), versionId, user.id(), request.ticketAttachmentIds());
        int participantUpdated = jdbc.update("""
                UPDATE transport_participant SET status = 'COMPLETED', ended_at = CURRENT_TIMESTAMP(3),
                    updated_by = ?, version = version + 1 WHERE id = ? AND status = 'IN_TRANSIT' AND is_current = 1
                """, user.id(), current.id());
        if (participantUpdated != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        int taskUpdated = jdbc.update("""
                UPDATE transport_task SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP(3),
                    effective_weight_version_id = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND status = 'IN_TRANSIT'
                """, versionId, user.id(), id);
        if (taskUpdated != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        insertStatus(id, "IN_TRANSIT", "COMPLETED", user.id(), null);
        saveCommand(id, "UNLOAD", request.requestKey(), request.toString());
        return find(id);
    }

    @Transactional
    public TransportTaskResponse adjustWeight(long id, TaskWeightAdjustmentRequest request, long actor) {
        authorization.require(actor, "BUTTON_ROUTES_WEIGHT_ADJUST");
        lockTask(id);
        TransportTaskResponse task = find(id);
        if (!"COMPLETED".equals(task.status()) || request.expectedVersion() != task.version()
                || request.reason() == null || request.reason().isBlank())
            throw new BusinessException(ErrorCode.ROUTE_002);
        BigDecimal net = netWeight(request.grossWeight(), request.tareWeight());
        List<Long> attachments = request.evidenceAttachmentIds() == null ? List.of() : request.evidenceAttachmentIds();
        evidence.validateAdjustmentEvidence(id, attachments);
        Participant current = current(id);
        Integer latest = jdbc.queryForObject("SELECT MAX(version_no) FROM task_weight_version WHERE task_id = ?",
                Integer.class, id);
        long weightId = insertWeight(id, latest == null ? 1 : latest + 1,
                request.grossWeight(), request.tareWeight(), net, standard(current, net),
                "ADJUSTMENT", actor, request.reason().trim(), attachments);
        int updated = jdbc.update("""
                UPDATE transport_task SET effective_weight_version_id = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND version = ? AND status = 'COMPLETED'
                """, weightId, actor, id, task.version());
        if (updated != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        return find(id);
    }

    @Transactional
    public TransportTaskResponse cancel(long id, TaskCancelRequest request, long actor) {
        authorization.require(actor, "BUTTON_ROUTES_CANCEL", "BUTTON_MINI_DISPATCH_CANCEL");
        lockTask(id);
        if (repeat(id, "CANCEL", request.requestKey(), request.toString())) return find(id);
        TransportTaskResponse task = find(id);
        if (request.expectedVersion() != task.version() || request.reason() == null || request.reason().isBlank()
                || !"PUBLISHED".equals(task.status()))
            throw new BusinessException(ErrorCode.ROUTE_002);
        Integer departed = jdbc.queryForObject("SELECT COUNT(*) FROM departure_event WHERE task_id = ?", Integer.class, id);
        Integer participantCount = jdbc.queryForObject("SELECT COUNT(*) FROM transport_participant WHERE task_id = ?", Integer.class, id);
        if (departed != null && departed > 0 || participantCount != null && participantCount > 1)
            throw new BusinessException(ErrorCode.ROUTE_002);
        jdbc.update("""
                UPDATE transport_participant SET status = 'CANCELLED', is_current = 0,
                    ended_at = CURRENT_TIMESTAMP(3), end_reason = ?, updated_by = ?, version = version + 1
                WHERE task_id = ? AND status = 'ASSIGNED'
                """, request.reason().trim(), actor, id);
        int taskUpdated = jdbc.update("""
                UPDATE transport_task SET status = 'CANCELLED', current_participant_id = NULL,
                    cancel_reason = ?, cancelled_at = CURRENT_TIMESTAMP(3), updated_by = ?, version = version + 1
                WHERE id = ? AND version = ?
                """, request.reason().trim(), actor, id, task.version());
        if (taskUpdated != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        insertStatus(id, task.status(), "CANCELLED", actor, request.reason().trim());
        saveCommand(id, "CANCEL", request.requestKey(), request.toString());
        return find(id);
    }

    private Participant current(long taskId) {
        return jdbc.query("""
                SELECT p.* FROM transport_task t JOIN transport_participant p ON p.id = t.current_participant_id
                WHERE t.id = ? AND p.task_id = t.id AND p.is_current = 1
                """, this::mapParticipant, taskId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_003));
    }

    private List<Participant> listParticipantFacts(long taskId) {
        return jdbc.query("""
                SELECT * FROM transport_participant WHERE task_id = ? AND status <> 'CANCELLED' ORDER BY sequence_no
                """, this::mapParticipant, taskId);
    }

    private Participant mapParticipant(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        long vehicle = rs.getLong("company_vehicle_id");
        Long vehicleId = rs.wasNull() ? null : vehicle;
        return new Participant(rs.getLong("id"), rs.getInt("sequence_no"), rs.getLong("driver_id"),
                rs.getString("driver_type_snapshot"), vehicleId, rs.getString("outsourced_plate_no"),
                rs.getString("status"), rs.getBigDecimal("salary_amount"), rs.getInt("version"));
    }

    private void adjustSalary(Participant p, BigDecimal amount, String reason, long actor) {
        if (jdbc.update("UPDATE transport_participant SET salary_amount = ?, updated_by = ?, version = version + 1 WHERE id = ? AND version = ?",
                amount, actor, p.id(), p.version()) != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        jdbc.update("""
                INSERT INTO participant_salary_history
                    (participant_id, before_amount, after_amount, reason, operator_id)
                VALUES (?, ?, ?, ?, ?)
                """, p.id(), p.salary(), amount, reason, actor);
    }

    private long insertTransferredParticipant(long taskId, int sequenceNo, DriverSnapshot driver,
                                              BigDecimal salary, String reason, long actor) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO transport_participant
                    (task_id, sequence_no, driver_id, driver_type_snapshot, company_vehicle_id,
                     vehicle_plate_snapshot, trailer_id, trailer_plate_snapshot, outsourced_plate_no,
                     source_type, status, is_current, assigned_at, transfer_reason, salary_amount, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'TRANSFER', 'ASSIGNED', 1, CURRENT_TIMESTAMP(3), ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, taskId);
            ps.setInt(2, sequenceNo);
            ps.setLong(3, driver.driverId());
            ps.setString(4, driver.driverType());
            ps.setObject(5, driver.vehicleId());
            ps.setString(6, driver.vehiclePlate());
            ps.setObject(7, driver.trailerId());
            ps.setString(8, driver.trailerPlate());
            ps.setString(9, driver.outsourcedPlate());
            ps.setString(10, reason);
            ps.setBigDecimal(11, salary);
            ps.setLong(12, actor);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private Standard standard(Participant p, BigDecimal net) {
        if ("OUTSOURCED".equals(p.driverType())) return new Standard(null, null);
        if (p.vehicleId() == null) throw new BusinessException(ErrorCode.ROUTE_003);
        return jdbc.query("""
                SELECT max_load, load_standard_type, load_standard_percent, load_standard_min_load
                FROM vehicle WHERE id = ? AND deleted_at IS NULL
                """, (rs, row) -> {
            BigDecimal threshold;
            if ("PERCENT".equals(rs.getString("load_standard_type"))) {
                if (rs.getBigDecimal("max_load") == null || rs.getBigDecimal("load_standard_percent") == null)
                    throw new BusinessException(ErrorCode.ROUTE_003);
                threshold = rs.getBigDecimal("max_load").multiply(rs.getBigDecimal("load_standard_percent"))
                        .setScale(3, RoundingMode.HALF_UP);
            } else if ("FIXED".equals(rs.getString("load_standard_type"))) {
                threshold = rs.getBigDecimal("load_standard_min_load");
                if (threshold == null) throw new BusinessException(ErrorCode.ROUTE_003);
            } else throw new BusinessException(ErrorCode.ROUTE_003);
            return new Standard(threshold, net.compareTo(threshold) >= 0);
        }, p.vehicleId()).stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_003));
    }

    private long insertWeight(long taskId, int versionNo, BigDecimal gross, BigDecimal tare,
                              BigDecimal net, Standard standard, String source, long actor,
                              String reason, List<Long> attachmentIds) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO task_weight_version (task_id, version_no, gross_weight, tare_weight, net_weight,
                        load_standard_threshold, load_standard_met, source_type, operator_id, reason, attachment_ids)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, taskId);
            ps.setInt(2, versionNo);
            ps.setBigDecimal(3, gross);
            ps.setBigDecimal(4, tare);
            ps.setBigDecimal(5, net);
            ps.setBigDecimal(6, standard.threshold());
            ps.setObject(7, standard.met() == null ? null : standard.met() ? 1 : 0);
            ps.setString(8, source);
            ps.setLong(9, actor);
            ps.setString(10, reason);
            ps.setString(11, attachmentIds.isEmpty() ? null : attachmentIds.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(",")));
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private BigDecimal netWeight(BigDecimal gross, BigDecimal tare) {
        if (gross == null || tare == null || gross.signum() <= 0 || tare.signum() <= 0 || gross.compareTo(tare) <= 0)
            throw new BusinessException(ErrorCode.ROUTE_004);
        return gross.subtract(tare);
    }

    private boolean validAmount(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.scale() <= 2;
    }

    private void lockTask(long id) {
        if (jdbc.queryForList("SELECT id FROM transport_task WHERE id = ? AND deleted_at IS NULL FOR UPDATE",
                Long.class, id).isEmpty()) throw new BusinessException(ErrorCode.ROUTE_001);
    }

    private void changeStatus(long id, String before, String after, long actor, String reason) {
        if (jdbc.update("UPDATE transport_task SET status = ?, updated_by = ?, version = version + 1 WHERE id = ? AND status = ?",
                after, actor, id, before) != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        insertStatus(id, before, after, actor, reason);
    }

    private void insertStatus(long id, String before, String after, long actor, String reason) {
        jdbc.update("""
                INSERT INTO task_status_history (task_id, from_status, to_status, operator_id, reason)
                VALUES (?, ?, ?, ?, ?)
                """, id, before, after, actor, reason);
    }

    private boolean repeat(long id, String type, String requestKey, String payload) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 128)
            throw new BusinessException(ErrorCode.SYS_002);
        List<String> stored = jdbc.query("""
                SELECT request_digest FROM task_command_request
                WHERE task_id = ? AND command_type = ? AND request_key = ?
                """, (rs, row) -> rs.getString(1), id, type, requestKey);
        if (stored.isEmpty()) return false;
        if (!stored.getFirst().equals(digest(payload))) throw new BusinessException(ErrorCode.DATA_002);
        return true;
    }

    private void saveCommand(long id, String type, String key, String payload) {
        jdbc.update("""
                INSERT INTO task_command_request (task_id, command_type, request_key, request_digest)
                VALUES (?, ?, ?, ?)
                """, id, type, key, digest(payload));
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Participant(long id, int sequenceNo, long driverId, String driverType,
                               Long vehicleId, String outsourcedPlate, String status,
                               BigDecimal salary, int version) {}
    private record Standard(BigDecimal threshold, Boolean met) {}
}
