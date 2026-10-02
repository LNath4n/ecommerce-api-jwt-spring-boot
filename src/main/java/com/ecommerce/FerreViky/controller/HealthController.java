package com.ecommerce.FerreViky.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
@RequestMapping("/health")
@Tag(name = "Health", description = "Consulta el estado de el API")
public class HealthController {

    @GetMapping
    public ResponseEntity<String> obtenerVersion(){
        return ResponseEntity.ok("Version 2.0.0");
    }
}
