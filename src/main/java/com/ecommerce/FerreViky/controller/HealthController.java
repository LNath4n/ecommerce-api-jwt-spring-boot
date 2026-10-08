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
@Tag(name = "Health", description = "Consulta el estado de el API")
public class HealthController {
    @GetMapping
    @Operation(summary = "Obtener versión", description = "Retorna la versión actual de la API")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Versión obtenida exitosamente")
    })
    public ResponseEntity<String> obtenerVersion() {
        return ResponseEntity.ok("Version 2.1.0 : Se agrego Editar/Crear/Borrar producto");
    }
}
