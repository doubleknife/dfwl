package com.dfwl.fleet.settlement.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.settlement.repository.SettlementRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SettlementService {

    private final SettlementRepository repository;
    private final TransactionTemplate generationTransaction;

    public SettlementService(SettlementRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.generationTransaction = new TransactionTemplate(transactionManager);
        this.generationTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public List<Map<String, Object>> versions(String yearMonth) {
        return repository.versions(yearMonth);
    }

    public Map<String, Object> generate(String yearMonth, long operatorId) {
        YearMonth month = YearMonth.parse(yearMonth);
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.plusMonths(1).atDay(1);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return generationTransaction.execute(status -> {
                    long versionId = repository.generate(yearMonth, startDate, endDate, operatorId);
                    return repository.version(versionId);
                });
            } catch (TransientDataAccessException | DataIntegrityViolationException | EmptyResultDataAccessException ex) {
                if (attempt == 2) throw ex;
            }
        }
        throw new IllegalStateException("Settlement generation retry exhausted");
    }

    public List<Map<String, Object>> details(long versionId) {
        ensureVersion(versionId);
        return repository.details(versionId);
    }

    public List<Map<String, Object>> diff(long versionId) {
        ensureVersion(versionId);
        return repository.diff(versionId);
    }

    public PageResponse<Map<String, Object>> detailsPage(long versionId, int pageNo, int pageSize) {
        ensureVersion(versionId);
        return repository.detailsPage(versionId, pageNo, pageSize);
    }

    public PageResponse<Map<String, Object>> diffPage(long versionId, int pageNo, int pageSize) {
        ensureVersion(versionId);
        return repository.diffPage(versionId, pageNo, pageSize);
    }

    private void ensureVersion(long versionId) {
        repository.versionExists(versionId).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }
}
