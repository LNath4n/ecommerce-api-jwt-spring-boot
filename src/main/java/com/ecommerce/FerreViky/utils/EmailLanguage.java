package com.ecommerce.FerreViky.utils;

import java.util.Locale;

public enum EmailLanguage {
    ES, EN;

    /** Spanish if the header starts with "es"; English for anything else, including no header. */
    public static EmailLanguage from(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return EN;
        String first = acceptLanguage.split(",")[0].trim().toLowerCase(Locale.ROOT);
        return first.startsWith("es") ? ES : EN;
    }
}