package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.codigoVirtual.CodigoVirtualDTO.CargarCodigosRequest;
import com.ecommerce.FerreViky.dto.dtos.codigoVirtual.CodigoVirtualDTO.CodigosCargadosResponse;
import com.ecommerce.FerreViky.service.VirtualCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
            @ApiResponse(responseCode = "400", description = "The list is empty, contains blank codes, or has more than 500 codes"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "409", description = "One or more codes already exist")
    })
    public ResponseEntity<CodigosCargadosResponse> uploadProductCodes(
            @Parameter(description = "Product ID", example = "1") @PathVariable Long productId,
            @Validated @RequestBody CargarCodigosRequest request) {
        int savedCount = virtualCodeService.addCodesToProduct(productId, request.codigos());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CodigosCargadosResponse(savedCount));
    }
}