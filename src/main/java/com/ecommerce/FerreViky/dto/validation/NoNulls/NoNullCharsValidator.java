package com.ecommerce.FerreViky.dto.validation.NoNulls;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;
public class NoNullCharsValidator implements ConstraintValidator<NoNullChars, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true;
        return value.chars().noneMatch(c -> c == 0 || (Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r'));
    }
}