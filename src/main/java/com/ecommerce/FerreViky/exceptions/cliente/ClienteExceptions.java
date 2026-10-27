package com.ecommerce.FerreViky.exceptions.cliente;
public class ClienteExceptions {

    public static class EmailYaExisteException extends RuntimeException {
        public EmailYaExisteException(String email) {
            super("El usuario con email " + email + " ya existe");
        }
    }

    public static class ClienteNoEncontradoException extends RuntimeException {
        public ClienteNoEncontradoException(String email) {
            super("No se encontró cliente con email " + email);
        }

        public ClienteNoEncontradoException(Long id) {
            super("No se encontró cliente con id " + id);
        }
    }

    public static class CredencialesInvalidasException extends RuntimeException {
        public CredencialesInvalidasException() {
            super("Credenciales Incorrectas");
        }
    }

    public static class ContrasenaInvalida extends RuntimeException {
        public ContrasenaInvalida() {
            super("La contrasena proporcionada es invalida");
        }
    }

    public static class ImagenInvalidaException extends RuntimeException {
        public ImagenInvalidaException(String mensaje) {
            super(mensaje);
        }
    }

    public static class CorreoYaEsTuyo extends RuntimeException {
        public CorreoYaEsTuyo(String mensaje) {
            super(mensaje);
        }
    }


}