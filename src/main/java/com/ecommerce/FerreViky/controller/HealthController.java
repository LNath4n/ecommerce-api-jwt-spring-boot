package com.ecommerce.FerreViky.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@CrossOrigin("*")
@RequestMapping("/health")
@Tag(name = "Health", description = "Checks the API status")
public class HealthController {
    @GetMapping
    @Operation(summary = "Get version", description = "Returns the current API version")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Version retrieved successfully")
    })
    public ResponseEntity<String> getVersion() {
        return ResponseEntity.ok("Version 4.0: Paypal :)");
    }
}
