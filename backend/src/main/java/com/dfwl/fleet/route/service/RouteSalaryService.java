package com.dfwl.fleet.route.service;

import com.dfwl.fleet.route.repository.RouteRepository;
import com.dfwl.fleet.route.repository.RouteParticipantRepository;
import java.math.BigDecimal;
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
        participants.updateInitialSalary(routeId, amount, operatorId);
        repository.updateRouteSalary(routeId, amount, sourceType, operatorId);
    }
}
