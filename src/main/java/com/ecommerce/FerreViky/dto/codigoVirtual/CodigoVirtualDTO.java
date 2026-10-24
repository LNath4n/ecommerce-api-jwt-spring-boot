package com.ecommerce.FerreViky.dto.codigoVirtual;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CodigoVirtualDTO {

    @Schema(description = "Códigos a cargar para un producto")
    public record CargarCodigosRequest(
            @Schema(description = "Lista de códigos en texto plano (se cifran al guardar)",
                    example = "[\"STEAM-AAAA-0001\", \"STEAM-AAAA-0002\"]")
            @NotEmpty(message = "Debes enviar al menos un código")
            @Size(max = 500, message = "Máximo 500 códigos por carga")
            List<@NotBlank String> codigos
    ) {}

    @Schema(description = "Resultado de la carga de códigos")
    public record CodigosCargadosResponse(
            @Schema(description = "Cuántos códigos se guardaron", example = "50")
            int guardados
    ) {}
}