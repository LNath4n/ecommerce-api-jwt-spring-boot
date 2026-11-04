package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.ClienteEditarRequest;
import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.ClientePublico;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/cliente")
@AllArgsConstructor
@CrossOrigin("*")
@Tag(
        name = "Cliente",
        description = "Operaciones de consulta, edición y desactivación de la cuenta del cliente autenticado"
)
@SecurityRequirement(name = "bearerAuth")
public class ClienteController {

    private final ClienteService clienteService;


    @Operation(
            summary = "Consultar perfil del cliente",
            description = "Obtiene los datos públicos de la cuenta asociada al usuario autenticado mediante JWT."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cliente obtenido exitosamente",
                    content = @Content(
                            schema = @Schema(implementation = ClientePublico.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuario no autenticado o credenciales inválidas",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "El cliente no existe",
                    content = @Content
            )
    })
    @GetMapping
    public ResponseEntity<ClientePublico> obtenerCliente(
            @AuthenticationPrincipal Cliente cliente) {

        return ResponseEntity.ok(clienteService.obtenerCliente(cliente));
    }


    @Operation(
            summary = "Editar perfil del cliente",
            description = "Actualiza los datos del cliente autenticado. Para cambiar la contraseña se requiere la contraseña actual."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cliente actualizado exitosamente",
                    content = @Content(
                            schema = @Schema(implementation = ClientePublico.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuario no autenticado o contraseña actual incorrecta",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "El cliente no existe",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "El correo electrónico ya está registrado",
                    content = @Content
            )
    })
    @PutMapping
    public ResponseEntity<ClientePublico> editarCliente(
            @AuthenticationPrincipal Cliente cliente,
            @Valid @RequestBody ClienteEditarRequest request) {

        return ResponseEntity.ok(
                clienteService.editarCliente(cliente, request)
        );
    }

    @Operation(
            summary = "Desactivar cuenta del cliente",
            description = "Desactiva la cuenta del cliente autenticado sin eliminar físicamente su registro."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cliente desactivado exitosamente",
                    content = @Content(
                            schema = @Schema(
                                    type = "string",
                                    example = "Cliente desactivado exitosamente"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuario no autenticado o credenciales inválidas",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "El cliente no existe",
                    content = @Content
            )
    })
    @DeleteMapping
    public ResponseEntity<String> borrarCliente(
            @AuthenticationPrincipal Cliente cliente) {

        clienteService.desactivarCliente(cliente);
        return ResponseEntity.ok("Cliente desactivado exitosamente");
    }


    @Operation(
            summary = "Subir foto de perfil",
            description = "Sube la foto del cliente autenticado (campo multipart 'file'). Solo se aceptan JPG y PNG, máximo 5MB. Si ya tenía una, la reemplaza y borra la anterior del bucket"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Foto subida; devuelve el perfil actualizado"),
            @ApiResponse(responseCode = "400", description = "El archivo no es una imagen válida o el formato no está permitido (solo JPG y PNG)"),
            @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
            @ApiResponse(responseCode = "404", description = "El cliente no existe"),
            @ApiResponse(responseCode = "413", description = "La imagen supera el tamaño máximo permitido")
    })
    @PostMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClientePublico> subirFoto(
            @AuthenticationPrincipal Cliente cliente,
            @RequestParam("file") MultipartFile file) throws IOException {

        return ResponseEntity.ok(clienteService.subirFotoDelCliente(cliente, file));
    }


    @Operation(summary = "Quitar foto de perfil",
            description = "Elimina la foto del cliente autenticado. Si no tenía, no hace nada")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Foto eliminada; devuelve el perfil actualizado"),
            @ApiResponse(responseCode = "401", description = "Usuario no autenticado"),
            @ApiResponse(responseCode = "404", description = "El cliente no existe")
    })
    @DeleteMapping("/foto")
    public ResponseEntity<ClientePublico> quitarFoto(@AuthenticationPrincipal Cliente cliente) {
        return ResponseEntity.ok(clienteService.quitarFotoDelCliente(cliente));
    }


}
