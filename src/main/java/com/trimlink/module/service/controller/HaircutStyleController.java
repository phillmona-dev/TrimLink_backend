package com.trimlink.module.service.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.service.entity.HaircutStyle;
import com.trimlink.module.service.repository.HaircutStyleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Haircut Styles Library", description = "Hairstyle catalog for inspiration and assignments")
@RestController
@RequestMapping("/haircut-styles")
@RequiredArgsConstructor
public class HaircutStyleController {

    private final HaircutStyleRepository haircutStyleRepository;

    @Operation(summary = "Get all haircut styles in library")
    @GetMapping
    public ResponseEntity<ApiResponse<List<HaircutStyle>>> getAllStyles() {
        return ResponseEntity.ok(ApiResponse.ok(haircutStyleRepository.findAll()));
    }

    @Operation(summary = "Get haircut styles by category")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<HaircutStyle>>> getStylesByCategory(@PathVariable String category) {
        if ("ALL".equalsIgnoreCase(category)) {
            return ResponseEntity.ok(ApiResponse.ok(haircutStyleRepository.findAll()));
        }
        return ResponseEntity.ok(ApiResponse.ok(haircutStyleRepository.findByCategory(category)));
    }
}
