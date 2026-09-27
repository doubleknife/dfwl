package com.dfwl.fleet.notification;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unread(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(new UnreadCount(service.unreadCount(user.id())), RequestIdHolder.get());
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationRepository.Notification>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.list(user.id(), pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/{id}/read")
    public ApiResponse<UnreadCount> read(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        service.markRead(user.id(), id);
        return unread(user);
    }

    @PostMapping("/read-all")
    public ApiResponse<UnreadCount> readAll(@AuthenticationPrincipal AuthenticatedUser user) {
        service.markAllRead(user.id());
        return unread(user);
    }

    public record UnreadCount(long unreadCount) {}
}
