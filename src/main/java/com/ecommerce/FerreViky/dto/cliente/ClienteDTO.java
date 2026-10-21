package com.ecommerce.FerreViky.dto.cliente;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ClienteDTO {

    @Schema(description = "Respuesta al registrar un nuevo cliente")
    public record CreacionDeClienteRespuestaDto(
            @Schema(description = "ID generado para el cliente", example = "42")
            Long id,

            @Schema(description = "Email registrado", example = "juan@gmail.com")
            String email
    ) {}

    @Schema(description = "Credenciales usadas tanto para login como para registro")
    public record LoginClienteDto(
            @Schema(description = "Email del cliente", example = "juan@gmail.com")
            @NotBlank String email,

            @Schema(description = "Contraseña en texto plano", example = "secreto123")
            @NotBlank String password
    ) {}

    @Schema(description = "Token de acceso del cliente")
    public record AuthResponse(
            @Schema(description = "Token JWT de autenticación")
            String token
    ) {}

    @Schema(description = "Información pública del cliente, sin incluir sus credenciales")
    public record ClientePublico(

            @Schema(description = "Correo electrónico del cliente", example = "juan@gmail.com")
            @Email(message = "El correo electrónico debe tener un formato válido")
            String email,

            @Schema(description = "Nombre del cliente", example = "Juan Pérez")
            String nombre,

            @Schema(description = "Teléfono de contacto del cliente", example = "2461234567")
            String telefono,
            @Schema(description = "Url de la foto del cliente")

            String fotoUrl
    ) {}

    @Schema(description = "Datos enviados para actualizar la información del cliente")
    public record ClienteEditarRequest(

            @Schema(
                    description = "Nuevo correo electrónico del cliente, si desea cambiarlo",
                    example = "nuevo@gmail.com"
            )
            @Email(message = "El correo electrónico debe tener un formato válido")
            String email,

            @Schema(
                    description = "Contraseña actual, necesaria para cambiar la contraseña",
                    example = "secreto123"
            )
            String passwordVieja,

            @Schema(
                    description = "Nueva contraseña del cliente",
                    example = "NuevaClave123"
            )
            @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
            String passwordNueva,

            @Schema(
                    description = "Nuevo nombre del cliente",
                    example = "Juan Pérez"
            )
            @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
            String nombre,

            @Schema(
                    description = "Nuevo teléfono de contacto",
                    example = "2461234567"
            )
            @Pattern(
                    regexp = "[0-9]{10}",
                    message = "El teléfono debe contener exactamente 10 dígitos"
            )
            String telefono
    ) {}
}