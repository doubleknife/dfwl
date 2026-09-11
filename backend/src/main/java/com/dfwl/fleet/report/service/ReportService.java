package com.dfwl.fleet.report.service;

import com.dfwl.fleet.report.repository.ReportRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> dashboard() {
        return repository.dashboard();
    }

    public List<Map<String, Object>> profit() {
        return repository.profit();
    }

    public List<Map<String, Object>> vehicleExpense() {
        return repository.vehicleExpense();
    }

    public List<Map<String, Object>> driverExpense() {
        return repository.driverExpense();
    }

    public List<Map<String, Object>> attendance() {
        return repository.attendance();
    }

    public List<Map<String, Object>> energy() {
        return repository.energy();
    }
}
