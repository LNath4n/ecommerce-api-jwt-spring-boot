package com.ecommerce.FerreViky.dto.validation.NoNulls;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoNullCharsValidator.class)
public @interface NoNullChars {
    String message() default "Contiene caracteres de control no permitidos";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
