package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.NotificationChannel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationChannelStatsResponse {
    private NotificationChannel channel;
    private long pendingCount;
    private long sentCount;
    private long failedCount;
    private long skippedCount;
}
