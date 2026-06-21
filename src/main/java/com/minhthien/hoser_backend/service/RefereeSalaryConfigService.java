package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.RefereeSalaryConfigRequest;
import com.minhthien.hoser_backend.dto.response.RefereeSalaryConfigResponse;

import java.util.List;

public interface RefereeSalaryConfigService {
    RefereeSalaryConfigResponse create(Long adminId, RefereeSalaryConfigRequest request);

    List<RefereeSalaryConfigResponse> getAll(Long adminId);

    RefereeSalaryConfigResponse getById(Long adminId, Long id);

    RefereeSalaryConfigResponse update(Long adminId, Long id, RefereeSalaryConfigRequest request);

    void delete(Long adminId, Long id);
}
