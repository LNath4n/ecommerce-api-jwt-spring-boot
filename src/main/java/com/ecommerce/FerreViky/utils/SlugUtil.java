package com.ecommerce.FerreViky.utils;

import java.text.Normalizer;

public class SlugUtil {

    public static String slugify(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")        // quita acentos
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")   // todo lo raro -> guion
                .replaceAll("(^-|-$)", "");      // sin guiones al inicio/fin
    }
}

