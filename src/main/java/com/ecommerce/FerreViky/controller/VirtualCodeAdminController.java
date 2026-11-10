package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.virtualCode.VirtualCodeDTO.UploadProductCodesRequest;
import com.ecommerce.FerreViky.dto.dtos.virtualCode.VirtualCodeDTO.UploadProductCodesResponse;
import com.ecommerce.FerreViky.service.VirtualCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/admin/products")
@RestController
@AllArgsConstructor
public class VirtualCodeAdminController {

    private final VirtualCodeService virtualCodeService;

    @PostMapping("/{productId}/codes")
    @Operation(summary = "Upload product codes",
            description = "Uploads a list of new codes for the specified product. They are created with AVAILABLE status. Leading and trailing whitespace is trimmed, and duplicates within the same list are ignored. The operation is atomic: if a code already exists in the database, none are saved.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Codes saved; returns the number saved"),
            @ApiResponse(
                    responseCode = "400",
                    description = "The request must contain 1 to 500 valid, non-blank codes",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "EmptyCodeList",
                                    summary = "At least one code must be provided",
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"codes\":\"At least one code is required\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 1")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "At least one submitted code already exists; no codes from this upload were saved",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "1 code(s) already exist; none were saved")
                    )
            )
    })
    public ResponseEntity<UploadProductCodesResponse> uploadProductCodes(
            @Parameter(description = "Product ID", example = "1") @PathVariable Long productId,
            @Validated @RequestBody UploadProductCodesRequest request) {
        int savedCount = virtualCodeService.addCodesToProduct(productId, request.codes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UploadProductCodesResponse(savedCount));
    }
}