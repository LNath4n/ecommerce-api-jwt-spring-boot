package com.ecommerce.FerreViky.dto.dtos.codigoVirtual;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
public class CodigoVirtualDTO {

    @Schema(description = "Codes to load for a product")
    public record CargarCodigosRequest(
            @Schema(
                    description = "List of plain-text codes (encrypted on save)",
                    example = "[\"STEAM-AAAA-0001\", \"STEAM-AAAA-0002\"]"
            )
            @NotEmpty(message = "At least one code is required")
            @Size(max = 500, message = "A maximum of 500 codes per upload is allowed")
            List<
                    @NotBlank(message = "Codes cannot be blank")
                    @Size(min = 5, max = 50,
                            message = "Each code must be between 5 and 50 characters")
                            String
                    > codigos
    ) {}

    @Schema(description = "Result of the code upload")
    public record CodigosCargadosResponse(
            @Schema(description = "Number of codes saved", example = "50")
            int guardados
    ) {}
}