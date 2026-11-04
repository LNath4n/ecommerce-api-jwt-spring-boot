package com.ecommerce.FerreViky.exceptions.codigoVirtual;

public class CodigosVirtualesExceptions {

    public static class CodigosDuplicadosException extends RuntimeException {
        public CodigosDuplicadosException(int cantidad) {
            super("Hay " + cantidad + " código(s) que ya existen; no se guardó ninguno");
        }
    }
}
