package com.dfwl.fleet.system.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.system.dto.response.AuditLogResponse;
import com.dfwl.fleet.system.dto.response.PermissionResponse;
import com.dfwl.fleet.system.dto.request.RolePermissionRequest;
import com.dfwl.fleet.system.dto.request.RoleRequest;
import com.dfwl.fleet.system.dto.response.RoleResponse;
import com.dfwl.fleet.system.dto.request.UserRequest;
import com.dfwl.fleet.system.dto.response.UserResponse;
import com.dfwl.fleet.system.repository.SystemManagementRepository;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemManagementService {

    private final SystemManagementRepository repository;
    private final PasswordEncoder passwordEncoder;

    public SystemManagementService(SystemManagementRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<UserResponse> listUsers(int pageNo, int pageSize) {
        return repository.listUsers(pageNo, normalizePageSize(pageSize));
    }

    public UserResponse findUser(long id) {
        return repository.findUser(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
    }

    @Transactional
    public UserResponse createUser(UserRequest request, long operatorId) {
        if (!StringUtils.hasText(request.password())) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        validateRole(request.roleId());
        try {
            long id = repository.createUser(request, passwordEncoder.encode(request.password()), operatorId);
            return findUser(id);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public UserResponse updateUser(long id, UserRequest request, long operatorId) {
        validateRole(request.roleId());
        try {
            if (repository.updateUser(id, request, operatorId) != 1) {
                throw new BusinessException(ErrorCode.USER_001);
            }
            return findUser(id);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    public PageResponse<RoleResponse> listRoles(int pageNo, int pageSize) {
        return repository.listRoles(pageNo, normalizePageSize(pageSize));
    }

    public RoleResponse findRole(long id) {
        return repository.findRole(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        try {
            long id = repository.createRole(request);
            return findRole(id);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public RoleResponse updateRole(long id, RoleRequest request) {
        try {
            if (repository.updateRole(id, request) != 1) {
                throw new BusinessException(ErrorCode.DATA_001);
            }
            return findRole(id);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public RoleResponse updateRoleStatus(long id, int status) {
        if (repository.updateRoleStatus(id, status) != 1) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        return findRole(id);
    }

    public List<PermissionResponse> listPermissions(String permissionType) {
        return repository.listPermissions(permissionType);
    }

    @Transactional
    public RoleResponse replaceRolePermissions(long roleId, RolePermissionRequest request) {
        findRole(roleId);
        List<String> codes = request.permissionCodes() == null
                ? List.of()
                : new LinkedHashSet<>(request.permissionCodes()).stream().toList();
        List<PermissionResponse> permissions = repository.findPermissionsByCodes(codes);
        if (permissions.size() != codes.size()) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        repository.replaceRolePermissions(roleId, permissions.stream().map(PermissionResponse::id).toList());
        return findRole(roleId);
    }

    public PageResponse<AuditLogResponse> listAuditLogs(String module, String businessType, Long operatorId,
                                                        String startTime, String endTime, int pageNo, int pageSize) {
        return repository.listAuditLogs(module, businessType, operatorId, startTime, endTime, pageNo, normalizePageSize(pageSize));
    }

    private void validateRole(long roleId) {
        if (!repository.roleExists(roleId)) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
    }

    private int normalizePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 200);
    }
}
