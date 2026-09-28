package com.dfwl.fleet.master.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.master.dto.response.DriverResponse;
import com.dfwl.fleet.master.repository.MasterDataRepository;
import com.dfwl.fleet.system.dto.request.UserRequest;
import com.dfwl.fleet.system.dto.response.UserResponse;
import com.dfwl.fleet.system.repository.SystemManagementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Account linkage belongs to the driver workflow; system user management cannot assign DRIVER. */
@Service
public class DriverAccountService {
    private final MasterDataRepository drivers;
    private final SystemManagementRepository users;
    private final PasswordEncoder passwordEncoder;

    public DriverAccountService(MasterDataRepository drivers, SystemManagementRepository users,
                                PasswordEncoder passwordEncoder) {
        this.drivers = drivers;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** An existing DRIVER user is linked without changing its password; a new user needs an explicit password. */
    @Transactional
    public long createOrLink(long driverId, String initialPassword, long operatorId) {
        drivers.lockDriver(driverId);
        DriverResponse driver = drivers.findDriver(driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        if (drivers.activeDriversWithPhone(driver.phone()) != 1) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
        UserResponse existing = users.findUserByPhoneForUpdate(driver.phone()).orElse(null);
        if (driver.userId() != null) {
            if (existing != null && existing.id() == driver.userId() && "DRIVER".equals(existing.roleCode())) {
                return existing.id();
            }
            throw new BusinessException(ErrorCode.DATA_002);
        }
        Long userId;
        if (existing != null) {
            if (!"DRIVER".equals(existing.roleCode()) || existing.status() != 1) {
                throw new BusinessException(ErrorCode.AUTH_003);
            }
            userId = existing.id();
        } else {
            Long roleId = users.driverRoleId();
            if (roleId == null || !StringUtils.hasText(initialPassword)) {
                throw new BusinessException(ErrorCode.SYS_002);
            }
            try {
                userId = users.createUser(new UserRequest(driver.phone(), null, roleId, driver.status()),
                        passwordEncoder.encode(initialPassword), operatorId);
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.DATA_002);
            }
        }
        if (!drivers.linkDriverUser(driverId, userId)) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
        return userId;
    }
}
