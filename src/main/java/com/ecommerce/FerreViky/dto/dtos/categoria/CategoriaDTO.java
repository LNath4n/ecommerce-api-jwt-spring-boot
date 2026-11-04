package com.ecommerce.FerreViky.dto.dtos.categoria;

import com.ecommerce.FerreViky.dto.validation.sinHtml.SinHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CategoriaDTO {

    @Schema(description = "Input data to create or update a category (administrator)")
    public record CategoriaRequest(
            @Schema(description = "Category name", example = "Gift Cards")
            @SinHtml
            @NotBlank(message = "Name is required")
            @Size(max = 100, message = "Name cannot exceed 100 characters")
            @Pattern(
                    regexp = "^[^\\p{Cntrl}]*[\\p{L}\\p{N}][^\\p{Cntrl}]*$",
                    message = "Name must contain at least one letter or number and no control characters"
            )
            String nombre
    ) {
    }
}
