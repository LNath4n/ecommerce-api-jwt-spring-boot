package com.ecommerce.FerreViky.dto.validation.sinHtml;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SinHtmlValidator.class)
public @interface SinHtml {
    String message() default "No se permite contenido HTML";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}