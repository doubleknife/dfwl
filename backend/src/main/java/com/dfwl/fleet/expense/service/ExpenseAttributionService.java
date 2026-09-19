package com.dfwl.fleet.expense.service;

import com.dfwl.fleet.expense.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseAttributionService {

    private final ExpenseRepository repository;

    public ExpenseAttributionService(ExpenseRepository repository) {
        this.repository = repository;
    }

    /** Marks matched energy expenses pending attribution without changing their route reference. */
    @Transactional(propagation = Propagation.REQUIRED)
    public int onRouteVoided(long routeId, long operatorId, String reason) {
        return repository.markEnergyExpensesPendingForVoidedRoute(routeId, operatorId, reason);
    }
}
