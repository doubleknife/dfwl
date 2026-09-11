package com.dfwl.fleet.settlement.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.settlement.repository.SettlementRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {

    private final SettlementRepository repository;

    public SettlementService(SettlementRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> versions(String yearMonth) {
        return repository.versions(yearMonth);
    }

    @Transactional
    public Map<String, Object> generate(String yearMonth, long operatorId) {
        YearMonth month = YearMonth.parse(yearMonth);
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.plusMonths(1).atDay(1);
        long versionId = repository.generate(yearMonth, startDate, endDate, operatorId);
        return repository.version(versionId);
    }

    public List<Map<String, Object>> details(long versionId) {
        ensureVersion(versionId);
        return repository.details(versionId);
    }

    public List<Map<String, Object>> diff(long versionId) {
        ensureVersion(versionId);
        return repository.diff(versionId);
    }

    private void ensureVersion(long versionId) {
        repository.versionExists(versionId).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }
}
