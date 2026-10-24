package com.dfwl.fleet.master.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.master.dto.response.DriverResponse;
import com.dfwl.fleet.master.repository.MasterDataRepository;
import com.dfwl.fleet.system.dto.request.UserRequest;
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

    /** A new driver receives a new DRIVER account in the same transaction. */
    @Transactional
    public long createForDriver(long driverId, String initialPassword, long operatorId) {
        if (!StringUtils.hasText(initialPassword) || initialPassword.length() < 6) {
            throw new BusinessException("SYS_002", "司机初始密码至少 6 位");
        }
        drivers.lockDriver(driverId);
        DriverResponse driver = drivers.findDriver(driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        if (drivers.activeDriversWithPhone(driver.phone()) != 1) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
        if (driver.userId() != null) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
        if (users.findUserByPhoneForUpdate(driver.phone()).isPresent()) {
            throw new BusinessException("DATA_002", "该手机号已有登录账号，不能新增司机");
        }
        Long roleId = users.driverRoleId();
        if (roleId == null) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        long userId;
        try {
            userId = users.createUser(new UserRequest(driver.phone(), null, null, roleId, driver.status()),
                    passwordEncoder.encode(initialPassword), operatorId);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("DATA_002", "该手机号已有登录账号，不能新增司机");
        }
        if (!drivers.linkDriverUser(driverId, userId)) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
        return userId;
    }
}
