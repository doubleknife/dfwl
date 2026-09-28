package com.dfwl.fleet.route.service;

import com.dfwl.fleet.route.repository.RouteRepository;
import com.dfwl.fleet.route.repository.RouteParticipantRepository;
import java.math.BigDecimal;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteSalaryService {

    private final RouteRepository repository;
    private final RouteParticipantRepository participants;

    public RouteSalaryService(RouteRepository repository, RouteParticipantRepository participants) {
        this.repository = repository;
        this.participants = participants;
    }

    /** Synchronizes the salary result without owning salary validation or history. */
    @Transactional
    public void updateSalary(long routeId, BigDecimal amount, String sourceType, long operatorId) {
        repository.lockRoute(routeId);
        if (repository.find(routeId).isEmpty()) return;
        if (participants.list(routeId).size() != 1)
            throw new BusinessException(ErrorCode.ROUTE_002);
        participants.updateInitialSalary(routeId, amount, operatorId);
        repository.updateRouteSalary(routeId, participants.effectiveSalary(routeId), sourceType, operatorId);
    }
}
