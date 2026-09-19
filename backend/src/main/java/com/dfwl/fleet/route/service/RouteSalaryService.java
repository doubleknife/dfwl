package com.dfwl.fleet.route.service;

import com.dfwl.fleet.route.repository.RouteRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteSalaryService {

    private final RouteRepository repository;

    public RouteSalaryService(RouteRepository repository) {
        this.repository = repository;
    }

    /** Synchronizes the salary result without owning salary validation or history. */
    @Transactional
    public void updateSalary(long routeId, BigDecimal amount, String sourceType, long operatorId) {
        repository.updateRouteSalary(routeId, amount, sourceType, operatorId);
    }
}
