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
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.dfwl.fleet.security.FunctionRegistry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemManagementService {
    private final SystemManagementRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditWriteService audit;
    private final TokenAuthenticationService tokens;
    private final FunctionRegistry functions;

    public SystemManagementService(SystemManagementRepository repository, PasswordEncoder passwordEncoder,
                                   AuditWriteService audit, TokenAuthenticationService tokens,
                                   FunctionRegistry functions) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.tokens = tokens;
        this.functions = functions;
    }

    public PageResponse<UserResponse> listUsers(int pageNo, int pageSize) {
        return repository.listUsers(pageNo, normalizePageSize(pageSize));
    }

    public UserResponse findUser(long id) {
        return repository.findUser(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
    }

    @Transactional
    public UserResponse createUser(UserRequest request, long operatorId) {
        if (!StringUtils.hasText(request.password()) || request.password().length() < 6) {
            throw new BusinessException("SYS_002", "初始密码至少 6 位");
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
        if ("DRIVER".equals(repository.userRoleCode(id))) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }
        validateRole(request.roleId());
        try {
            UserResponse before = findUser(id);
            if (repository.updateUser(id, request, operatorId) != 1) {
                throw new BusinessException(ErrorCode.USER_001);
            }
            if (before.roleId() != request.roleId() || before.status() != request.status()) {
                tokens.revokeUserTokens(id);
            } else if (!before.phone().equals(request.phone())) {
                tokens.invalidateUserSnapshot(id);
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
            String generatedCode = "ROLE_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
            long id = repository.createRole(new RoleRequest(generatedCode, request.roleName(), request.status()));
            return findRole(id);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("ROLE_001", "角色编码已存在，请重试新增");
        }
    }

    @Transactional
    public RoleResponse updateRole(long id, RoleRequest request) {
        try {
            RoleResponse existing = findRole(id);
            if (repository.updateRole(id, new RoleRequest(existing.roleCode(), request.roleName(), request.status())) != 1) {
                throw new BusinessException(ErrorCode.DATA_001);
            }
            if (request.status() == 0) tokens.revokeRoleTokens(id);
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
        if (status == 0) tokens.revokeRoleTokens(id);
        return findRole(id);
    }

    public List<PermissionResponse> listPermissions(String permissionType) {
        return repository.listPermissions(permissionType).stream()
                .filter(permission -> functions.find(permission.permissionCode()) != null)
                .map(permission -> {
                    FunctionRegistry.Function definition = functions.find(permission.permissionCode());
                    return new PermissionResponse(permission.id(), definition.code(), definition.name(),
                            definition.type(), permission.status(), definition.domain(), definition.group(),
                            definition.pageKey(), definition.parentCode(), definition.dataScope());
                }).toList();
    }

    @Transactional
    public RoleResponse replaceRolePermissions(long roleId, RolePermissionRequest request, long operatorId) {
        findRole(roleId);
        List<String> before = repository.rolePermissions(roleId).stream().map(PermissionResponse::permissionCode).sorted().toList();
        List<String> codes = request.permissionCodes() == null
                ? List.of()
                : new LinkedHashSet<>(request.permissionCodes()).stream().toList();
        if (!functions.codes().containsAll(codes)
                || codes.stream().anyMatch(code -> {
                    FunctionRegistry.Function function = functions.find(code);
                    return "BUTTON".equals(function.type()) && !codes.contains(function.parentCode());
                })) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        List<PermissionResponse> permissions = repository.findPermissionsByCodes(codes);
        if (permissions.size() != codes.size()) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        repository.replaceRolePermissions(roleId, permissions.stream().map(PermissionResponse::id).toList());
        tokens.invalidateRolePermissions(roleId);
        audit.write("SYSTEM", "ROLE", roleId, "REPLACE_PERMISSIONS",
                java.util.Map.of("permissions", before),
                java.util.Map.of("permissions", codes.stream().sorted().toList()), operatorId, null);
        return findRole(roleId);
    }

    public PageResponse<AuditLogResponse> listAuditLogs(String module, String businessType, Long operatorId,
                                                        String startTime, String endTime, int pageNo, int pageSize) {
        return repository.listAuditLogs(module, businessType, operatorId, startTime, endTime, pageNo, normalizePageSize(pageSize));
    }

    private void validateRole(long roleId) {
        String roleCode = repository.roleCode(roleId);
        if (roleCode == null) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        if ("DRIVER".equals(roleCode)) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }
    }

    private int normalizePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 200);
    }
}
