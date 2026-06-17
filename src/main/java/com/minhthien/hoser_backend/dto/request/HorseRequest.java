package com.minhthien.hoser_backend.dto.request;

import com.minhthien.hoser_backend.enums.HorseGender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Data
public class HorseRequest {
    @NotBlank(message = "Horse name is required")
    @Size(max = 120, message = "Horse name must be at most 120 characters")
    private String name;

    @Size(max = 120, message = "Breed must be at most 120 characters")
    private String breed;

    @Min(value = 0, message = "Age must be positive")
    private Integer age;

    private HorseGender gender;

    @Size(max = 80, message = "Color must be at most 80 characters")
    private String color;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    @Schema(type = "string", format = "binary", description = "Horse image file")
    private MultipartFile image;

    @Schema(type = "string", format = "binary", description = "Horse document file")
    private MultipartFile document;
}
