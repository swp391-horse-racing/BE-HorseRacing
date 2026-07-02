package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ViolationTypeOptionResponse {
    private String code;
    private String label;
    private Boolean active;
}
