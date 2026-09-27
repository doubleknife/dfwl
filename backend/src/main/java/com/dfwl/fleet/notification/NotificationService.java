package com.dfwl.fleet.notification;

import com.dfwl.fleet.common.api.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository repository;
    private final NotificationWriter writer;

    public NotificationService(NotificationRepository repository, NotificationWriter writer) {
        this.repository = repository;
        this.writer = writer;
    }

    public long unreadCount(long userId) { return repository.unreadCount(userId); }
    public PageResponse<NotificationRepository.Notification> list(long userId, int pageNo, int pageSize) {
        return repository.list(userId, pageNo, pageSize);
    }
    public void markRead(long userId, long id) { repository.markRead(userId, id); }
    public void markAllRead(long userId) { repository.markAllRead(userId); }

    public void toUser(long userId, String key, String type, String title, String summary,
                       String businessType, long businessId) {
        afterCommit(key, () -> writer.toUser(userId, key, type, title, summary, businessType, businessId));
    }

    public void toDriver(long driverId, String key, String type, String title, String summary,
                         String businessType, long businessId) {
        afterCommit(key, () -> writer.toDriver(driverId, key, type, title, summary, businessType, businessId));
    }

    public void toEligibleApprover(long userId, String key, String title, String summary, long approvalId) {
        afterCommit(key, () -> writer.toEligibleApprover(userId, key, title, summary, approvalId));
    }

    private void afterCommit(String key, Runnable action) {
        Runnable safe = () -> {
            try { action.run(); }
            catch (Exception ex) { log.warn("In-app notification delivery failed for event {}", key, ex); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safe.run(); }
            });
        } else {
            safe.run();
        }
    }
}
