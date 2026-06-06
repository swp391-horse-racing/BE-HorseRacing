package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.NotificationAudienceType;
import com.minhthien.hoser_backend.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationAudienceCountResponse {
    private NotificationAudienceType audienceType;
    private UserRole audienceRole;
    private long count;
}
