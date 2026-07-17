package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.NotificationResponse;
import com.minhthien.hoser_backend.dto.response.UnreadNotificationCountResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.NotificationReadStatus;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.service.NotificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:51093",
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn"
})
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) NotificationReadStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getMyNotifications(currentUser.getId(), status, page, size)));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> getUnreadCount(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount(currentUser.getId())));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read",
                notificationService.markRead(currentUser.getId(), id)));
    }

    @PutMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> markAllRead(
            @AuthenticationPrincipal User currentUser) {
        long updated = notificationService.markAllRead(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Notifications marked as read",
                new UnreadNotificationCountResponse(updated)));
    }

    @GetMapping("/admin/notifications")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getAdminNotifications(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Long recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getAdminNotifications(type, recipientId, page, size)));
    }
}
