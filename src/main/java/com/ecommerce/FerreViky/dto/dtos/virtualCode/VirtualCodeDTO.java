package com.ecommerce.FerreViky.dto.dtos.virtualCode;

import com.ecommerce.FerreViky.dto.validation.NoNulls.NoNullChars;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
public class VirtualCodeDTO {

    @Schema(description = "Codes to load for a product")
    public record UploadProductCodesRequest(
            @Schema(
                    description = "List of plain-text codes (encrypted on save)",
                    example = "[\"STEAM-AAAA-0001\", \"STEAM-AAAA-0002\"]"
            )
            @NotEmpty(message = "At least one code is required")
            @Size(max = 500, message = "A maximum of 500 codes per upload is allowed")
            List<
                    @NotBlank(message = "Codes cannot be blank")
                    @Pattern(
                            regexp = "^\\s*\\S(?:.{3,48}\\S)\\s*$",
                            message = "Each code must have between 5 and 50 characters after trimming"
                    )
                    @NoNullChars
                            String
                    > codes
    ) {}

    @Schema(description = "Result of the code upload")
    public record UploadProductCodesResponse(
            @Schema(description = "Number of codes saved", example = "50")
            int savedCount
    ) {}
}