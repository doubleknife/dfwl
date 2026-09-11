package com.dfwl.fleet.tire.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.tire.api.OcrConfirmRequest;
import com.dfwl.fleet.tire.api.OcrRecordResponse;
import com.dfwl.fleet.tire.api.OcrTireNumberRequest;
import com.dfwl.fleet.tire.api.TireRequestCreateRequest;
import com.dfwl.fleet.tire.api.TireRequestResponse;
import com.dfwl.fleet.tire.api.TireResponse;
import com.dfwl.fleet.tire.repository.TireRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TireService {

    private final TireRepository repository;

    public TireService(TireRepository repository) {
        this.repository = repository;
    }

    public PageResponse<TireResponse> list(int pageNo, int pageSize) {
        return repository.list(pageNo, Math.min(Math.max(pageSize, 1), 200));
    }

    public TireRequestResponse findRequest(long id) {
        return repository.findRequest(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public OcrRecordResponse recognize(OcrTireNumberRequest request) {
        if (!repository.attachmentExists(request.attachmentId())) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        long id = repository.createOcr(request);
        return findOcr(id);
    }

    @Transactional
    public OcrRecordResponse confirm(long id, OcrConfirmRequest request, long operatorId) {
        findOcr(id);
        repository.confirmOcr(id, request.confirmedText(), operatorId);
        return findOcr(id);
    }

    @Transactional
    public TireRequestResponse createRequest(TireRequestCreateRequest request) {
        if (!repository.driverExists(request.driverId()) || !repository.vehicleExists(request.vehicleId())) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        request.items().forEach(this::validateRequestItem);
        long requestId = repository.createRequest(request.driverId(), request.vehicleId());
        request.items().forEach(item -> {
            if (!repository.reserveTire(item.tireId())) {
                throw new BusinessException(ErrorCode.TIRE_001);
            }
            repository.createRequestItem(requestId, item.tireId(), item.confirmedTireNo(), item.ocrRecordId());
        });
        return findRequest(requestId);
    }

    private OcrRecordResponse findOcr(long id) {
        return repository.findOcr(id).orElseThrow(() -> new BusinessException(ErrorCode.TIRE_003));
    }

    private void validateRequestItem(TireRequestCreateRequest.Item item) {
        TireResponse tire = repository.findTire(item.tireId()).orElseThrow(() -> new BusinessException(ErrorCode.TIRE_002));
        if (repository.tireHasActiveRequest(item.tireId()) || repository.tireClaimed(item.tireId())) {
            throw new BusinessException(ErrorCode.TIRE_001);
        }
        if (!"IN_STOCK".equals(tire.status())) {
            throw new BusinessException(ErrorCode.TIRE_001);
        }
        if (!tire.tireNo().equals(item.confirmedTireNo())) {
            throw new BusinessException(ErrorCode.TIRE_004);
        }
        if (item.ocrRecordId() != null) {
            OcrRecordResponse ocr = findOcr(item.ocrRecordId());
            if (ocr.confirmedText() == null || !tire.tireNo().equals(ocr.confirmedText())) {
                throw new BusinessException(ErrorCode.TIRE_004);
            }
        }
    }
}
