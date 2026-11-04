package com.ecommerce.FerreViky.dto.validation.sinHtml;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class SinHtmlValidator implements ConstraintValidator<SinHtml, String> {

    private static final Pattern HTML = Pattern.compile("[<>]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        return value == null || !HTML.matcher(value).find();
    }
}