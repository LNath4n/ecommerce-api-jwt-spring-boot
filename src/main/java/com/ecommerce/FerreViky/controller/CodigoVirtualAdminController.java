package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.codigoVirtual.CodigoVirtualDTO.CargarCodigosRequest;
import com.ecommerce.FerreViky.dto.dtos.codigoVirtual.CodigoVirtualDTO.CodigosCargadosResponse;
import com.ecommerce.FerreViky.service.CodigoVirtualService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/admin/productos")
@RestController
@AllArgsConstructor
public class CodigoVirtualAdminController {

    private final CodigoVirtualService codigoVirtualService;

    @PostMapping("/{id}/codigos")
    @Operation(summary = "Cargar códigos de un producto",
            description = "Carga una lista de códigos nuevos para el producto indicado. Nacen con estado DISPONIBLE. Los espacios sobrantes se recortan y los repetidos dentro de la misma lista se ignoran. La operación es atómica: si un código ya existe en la BD, no se guarda ninguno")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Códigos guardados; devuelve cuántos se guardaron"),
            @ApiResponse(responseCode = "400", description = "Lista vacía, con códigos en blanco o con más de 500 códigos"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
            @ApiResponse(responseCode = "409", description = "Alguno de los códigos ya existe")
    })
    public ResponseEntity<CodigosCargadosResponse> cargarCodigos(
            @Parameter(description = "ID del producto", example = "1") @PathVariable Long id,
            @Validated @RequestBody CargarCodigosRequest request) {
        int guardados = codigoVirtualService.guardarCodigos(id, request.codigos());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CodigosCargadosResponse(guardados));
    }
}