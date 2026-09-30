package com.dfwl.fleet.salary.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.salary.dto.response.ParticipantSalaryHistoryResponse;
import com.dfwl.fleet.salary.dto.response.SalarySlipResponse;
import com.dfwl.fleet.salary.repository.DriverSalaryRepository;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Read projections of the single Participant salary fact. Writes use the task salary confirmation command. */
@Service
public class DriverSalaryService {
    private final DriverSalaryRepository repository;
    private final CurrentUserService users;

    public DriverSalaryService(DriverSalaryRepository repository, CurrentUserService users) {
        this.repository = repository;
        this.users = users;
    }

    public SalarySlipResponse slip(AuthenticatedUser user, String month, int pageNo, int pageSize) {
        CurrentDriverContext current = users.requireDriver(user);
        if (current.outsourced()) throw new AccessDeniedException("外协司机不能查看工资单");
        String period = parseMonth(month).toString();
        return new SalarySlipResponse(repository.monthlySummary(current.driverId(), period),
                repository.list(current.driverId(), period, Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    public List<ParticipantSalaryHistoryResponse> history(long participantId) {
        return repository.history(participantId);
    }

    private YearMonth parseMonth(String month) {
        try { return YearMonth.parse(month); }
        catch (DateTimeParseException | NullPointerException ex) { throw new BusinessException(ErrorCode.SYS_002); }
    }
}
