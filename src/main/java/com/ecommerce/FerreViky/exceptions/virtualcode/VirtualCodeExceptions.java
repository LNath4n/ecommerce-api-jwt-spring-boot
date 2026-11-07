package com.ecommerce.FerreViky.exceptions.virtualcode;

public class VirtualCodeExceptions {

    public static class DuplicateCodesException extends RuntimeException {
        public DuplicateCodesException(int count) {
            super(count + " code(s) already exist; none were saved");
        }
    }
}
