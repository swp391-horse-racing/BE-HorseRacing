package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RaceFinalizeResultRequest {
    @Valid
    private List<RaceResultEntryRequest> results = new ArrayList<>();

    private Long draftVersion;
}
