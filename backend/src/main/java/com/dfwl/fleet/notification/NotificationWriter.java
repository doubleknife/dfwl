package com.dfwl.fleet.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationWriter {
    private final NotificationRepository repository;

    public NotificationWriter(NotificationRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void toUser(long userId, String key, String type, String title, String summary,
                       String businessType, long businessId) {
        repository.insert(userId, key, type, title, summary, businessType, businessId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void toDriver(long driverId, String key, String type, String title, String summary,
                         String businessType, long businessId) {
        repository.userForDriver(driverId).ifPresent(userId ->
                repository.insert(userId, key, type, title, summary, businessType, businessId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void toEligibleApprover(long userId, String key, String title, String summary, long approvalId) {
        if (repository.eligibleApprover(userId)) {
            repository.insert(userId, key, "APPROVAL_TODO", title, summary, "APPROVAL", approvalId);
        }
    }
}
