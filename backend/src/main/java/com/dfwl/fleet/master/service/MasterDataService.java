package com.dfwl.fleet.master.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.master.api.BindingHistoryResponse;
import com.dfwl.fleet.master.api.CustomerRequest;
import com.dfwl.fleet.master.api.CustomerResponse;
import com.dfwl.fleet.master.api.DriverRequest;
import com.dfwl.fleet.master.api.DriverResponse;
import com.dfwl.fleet.master.api.ProductRequest;
import com.dfwl.fleet.master.api.ProductResponse;
import com.dfwl.fleet.master.api.TrailerRequest;
import com.dfwl.fleet.master.api.TrailerResponse;
import com.dfwl.fleet.master.api.VehicleRequest;
import com.dfwl.fleet.master.api.VehicleResponse;
import com.dfwl.fleet.master.repository.MasterDataRepository;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataService {

    private static final String LOAD_STANDARD_PERCENT = "PERCENT";
    private static final String LOAD_STANDARD_FIXED = "FIXED";

    private final MasterDataRepository repository;
    private final CurrentUserService currentUserService;

    public MasterDataService(MasterDataRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    public PageResponse<CustomerResponse> listCustomers(int pageNo, int pageSize) {
        return repository.listCustomers(pageNo, normalizePageSize(pageSize));
    }

    public CustomerResponse findCustomer(long id) {
        return repository.findCustomer(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        try {
            return findCustomer(repository.createCustomer(request));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public CustomerResponse updateCustomer(long id, CustomerRequest request) {
        if (!repository.updateCustomer(id, request)) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        return findCustomer(id);
    }

    @Transactional
    public CustomerResponse updateCustomerStatus(long id, int status) {
        if (!repository.updateCustomerStatus(id, status)) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        return findCustomer(id);
    }

    public PageResponse<ProductResponse> listProducts(int pageNo, int pageSize) {
        return repository.listProducts(pageNo, normalizePageSize(pageSize));
    }

    public ProductResponse findProduct(long id) {
        return repository.findProduct(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        try {
            return findProduct(repository.createProduct(request));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public ProductResponse updateProduct(long id, ProductRequest request) {
        if (!repository.updateProduct(id, request)) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        return findProduct(id);
    }

    @Transactional
    public ProductResponse updateProductStatus(long id, int status) {
        if (!repository.updateProductStatus(id, status)) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        return findProduct(id);
    }

    public PageResponse<DriverResponse> listDrivers(int pageNo, int pageSize) {
        return repository.listDrivers(pageNo, normalizePageSize(pageSize));
    }

    public DriverResponse findDriver(long id) {
        return repository.findDriver(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    public PageResponse<DriverResponse> listDriversForCurrentDriver(AuthenticatedUser user, int pageNo, int pageSize) {
        DriverResponse driver = findCurrentDriver(user);
        return new PageResponse<>(pageNo, Math.min(Math.max(pageSize, 1), 200), 1, List.of(driver));
    }

    public DriverResponse findDriverForCurrentUser(AuthenticatedUser user, long id) {
        DriverResponse driver = findCurrentDriver(user);
        if (driver.id() != id) {
            throw new org.springframework.security.access.AccessDeniedException("driver is outside current scope");
        }
        return driver;
    }

    @Transactional
    public DriverResponse createDriver(DriverRequest request) {
        try {
            return findDriver(repository.createDriver(request));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public DriverResponse updateDriver(long id, DriverRequest request) {
        if (!repository.updateDriver(id, request)) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        return findDriver(id);
    }

    public PageResponse<VehicleResponse> listVehicles(int pageNo, int pageSize) {
        return repository.listVehicles(pageNo, normalizePageSize(pageSize));
    }

    public PageResponse<VehicleResponse> listVehiclesForCurrentDriver(AuthenticatedUser user, int pageNo, int pageSize) {
        CurrentDriverContext driver = currentUserService.requireDriver(user);
        return repository.listVehiclesForDriver(driver.driverId(), pageNo, normalizePageSize(pageSize));
    }

    public VehicleResponse findVehicle(long id) {
        return repository.findVehicle(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    public VehicleResponse findVehicleForCurrentDriver(AuthenticatedUser user, long id) {
        currentUserService.ensureDriverCanViewVehicle(user, id);
        return findVehicle(id);
    }

    @Transactional
    public VehicleResponse createVehicle(VehicleRequest request) {
        validateVehicleLoadStandard(request);
        try {
            return findVehicle(repository.createVehicle(request));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public VehicleResponse updateVehicle(long id, VehicleRequest request) {
        validateVehicleLoadStandard(request);
        if (!repository.updateVehicle(id, request)) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        return findVehicle(id);
    }

    @Transactional
    public void deleteVehicle(long id) {
        VehicleResponse vehicle = findVehicle(id);
        if (vehicle.currentDriverId() != null || vehicle.currentTrailerId() != null
                || repository.hasInTransitVehicleOrTrailer(id, -1L)) {
            throw new BusinessException(ErrorCode.BIND_003);
        }
        if (!repository.deleteVehicle(id)) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
    }

    public List<BindingHistoryResponse> vehicleBindingHistory(long vehicleId) {
        findVehicle(vehicleId);
        return repository.vehicleBindingHistory(vehicleId);
    }

    public PageResponse<TrailerResponse> listTrailers(int pageNo, int pageSize) {
        return repository.listTrailers(pageNo, normalizePageSize(pageSize));
    }

    public TrailerResponse findTrailer(long id) {
        return repository.findTrailer(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public TrailerResponse createTrailer(TrailerRequest request) {
        try {
            return findTrailer(repository.createTrailer(request));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    @Transactional
    public TrailerResponse updateTrailer(long id, TrailerRequest request) {
        if (!repository.updateTrailer(id, request)) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        return findTrailer(id);
    }

    @Transactional
    public void bindDriverVehicle(long driverId, long vehicleId, long operatorId) {
        findDriver(driverId);
        findVehicle(vehicleId);
        if (repository.hasInTransitDriverOrVehicle(driverId, vehicleId)) {
            throw new BusinessException(ErrorCode.BIND_003);
        }
        if (repository.existsDriverVehicleBinding(driverId, vehicleId)) {
            throw new BusinessException(ErrorCode.BIND_001);
        }
        try {
            repository.bindDriverVehicle(driverId, vehicleId, operatorId, LocalDateTime.now());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BIND_001);
        }
    }

    @Transactional
    public void unbindDriverVehicle(long driverId, long operatorId, String reason) {
        DriverResponse driver = findDriver(driverId);
        if (driver.currentVehicleId() == null) {
            throw new BusinessException(ErrorCode.BIND_002);
        }
        if (repository.hasInTransitDriverOrVehicle(driverId, driver.currentVehicleId())) {
            throw new BusinessException(ErrorCode.BIND_003);
        }
        if (!repository.unbindDriverVehicle(driverId, operatorId, reason, LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BIND_002);
        }
    }

    @Transactional
    public void bindVehicleTrailer(long trailerId, long vehicleId, long operatorId) {
        findTrailer(trailerId);
        findVehicle(vehicleId);
        if (repository.hasInTransitVehicleOrTrailer(vehicleId, trailerId)) {
            throw new BusinessException(ErrorCode.BIND_003);
        }
        if (repository.existsVehicleTrailerBinding(vehicleId, trailerId)) {
            throw new BusinessException(ErrorCode.BIND_001);
        }
        try {
            repository.bindVehicleTrailer(vehicleId, trailerId, operatorId, LocalDateTime.now());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BIND_001);
        }
    }

    @Transactional
    public void unbindVehicleTrailer(long trailerId, long operatorId, String reason) {
        TrailerResponse trailer = findTrailer(trailerId);
        if (trailer.currentVehicleId() == null) {
            throw new BusinessException(ErrorCode.BIND_002);
        }
        if (repository.hasInTransitVehicleOrTrailer(trailer.currentVehicleId(), trailerId)) {
            throw new BusinessException(ErrorCode.BIND_003);
        }
        if (!repository.unbindVehicleTrailer(trailerId, operatorId, reason, LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BIND_002);
        }
    }

    private void validateVehicleLoadStandard(VehicleRequest request) {
        boolean percent = LOAD_STANDARD_PERCENT.equals(request.loadStandardType());
        boolean fixed = LOAD_STANDARD_FIXED.equals(request.loadStandardType());
        if (!percent && !fixed) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        if (percent && (request.loadStandardPercent() == null || request.loadStandardMinLoad() != null)) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        if (fixed && (request.loadStandardMinLoad() == null || request.loadStandardPercent() != null)) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 200);
    }

    private DriverResponse findCurrentDriver(AuthenticatedUser user) {
        currentUserService.requireDriver(user);
        return repository.findDriverByUserIdOrPhone(user.id(), user.phone())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("driver binding required"));
    }
}
