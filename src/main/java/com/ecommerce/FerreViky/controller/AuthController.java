package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.LoginClienteDto;
import com.ecommerce.FerreViky.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
@CrossOrigin("*")
@Tag(name = "Clientes", description = "Operaciones de autenticación y registro de clientes")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Iniciar sesión",
            description = "Autentica al cliente con email y contraseña. Devuelve un JWT válido para usar en endpoints protegidos."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Autenticación exitosa. Se retorna el token JWT.",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos (validación fallida)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginClienteDto dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @Operation(
            summary = "Registrar nuevo cliente",
            description = "Crea una cuenta nueva para el cliente y le asigna un carrito vacío automáticamente. Devuelve un JWT listo para usar."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cliente registrado exitosamente.",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos (validación fallida)", content = @Content),
            @ApiResponse(responseCode = "409", description = "El email ya está registrado", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error interno al crear el carrito del cliente", content = @Content)
    })
    @PostMapping
    public ResponseEntity<AuthResponse> crearCliente(@Valid @RequestBody LoginClienteDto dto) {
        return ResponseEntity.ok(authService.guardarCliente(dto));
    }
}