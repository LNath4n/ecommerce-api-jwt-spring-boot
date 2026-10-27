package com.ecommerce.FerreViky.service;


import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.ClienteEditarRequest;
import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.ClientePublico;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ContrasenaInvalida;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ImagenInvalidaException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ClienteNoEncontradoException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.EmailYaExisteException;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.repository.ClienteRepository;
import com.ecommerce.FerreViky.service.StorageService.contract.StorageService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ecommerce.FerreViky.mapper.cliente.ClienteMappers;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

@AllArgsConstructor
@Service
public class ClienteService {

    private final ClienteMappers clienteMappers;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final ClienteRepository clienteRepository;
    private static final Duration VALIDEZ_FOTO = Duration.ofMinutes(15);

    /**
     * Obtiene los datos públicos de un cliente.
     *
     * <p>Busca el registro asociado al cliente proporcionado y transforma
     * la entidad encontrada en un DTO público para evitar exponer
     * directamente los datos internos del modelo.</p>
     *
     * @param cliente cliente cuya información se desea consultar
     * @return datos públicos del cliente encontrado
     * @throws ClienteNoEncontradoException si no existe un registro
     *         asociado al cliente proporcionado
     */
    public ClientePublico obtenerCliente(Cliente cliente) {
        Cliente c = clienteRepository.findById(cliente.getId())
                .orElseThrow(() ->
                        new ClienteNoEncontradoException(cliente.getEmail()));

        return aPublico(c);
    }

    /**
     * Actualiza los datos del cliente identificado por su ID.
     *
     * <p>Solo se modifican los campos que no sean {@code null} en la
     * solicitud. Los campos omitidos conservan sus valores actuales.</p>
     *
     * <p>Si se solicita un cambio de contraseña, es obligatorio
     * proporcionar la contraseña actual. La nueva contraseña se codifica
     * mediante {@link PasswordEncoder} antes de almacenarse.</p>
     *
     * <p>Cuando se cambia el correo electrónico, se comprueba que no
     * esté registrado por otro cliente.</p>
     *
     * @param cliente cliente autenticado cuyos datos se actualizarán
     * @param request datos nuevos para actualizar el perfil
     * @return datos públicos del cliente actualizado
     * @throws ClienteNoEncontradoException si no existe un cliente
     *         con el ID proporcionado
     * @throws ContrasenaInvalida si no se proporciona la contraseña
     *         actual o esta no coincide con la almacenada
     * @throws EmailYaExisteException si el nuevo correo electrónico
     *         ya pertenece a otro cliente
     */
    @Transactional
    public ClientePublico editarCliente(
            Cliente cliente,
            ClienteEditarRequest request) {

        Cliente c = clienteRepository.findById(cliente.getId())
                .orElseThrow(() ->
                        new ClienteNoEncontradoException(cliente.getEmail()));

        // Actualiza la contraseña únicamente si se solicita el cambio.
        if (request.passwordNueva() != null) {
            if (request.passwordVieja() == null
                    || !passwordEncoder.matches(
                    request.passwordVieja(), c.getPassword())) {
                throw new ContrasenaInvalida();
            }

            c.setPassword(passwordEncoder.encode(request.passwordNueva()));
        }

        // Evita asignar un correo que ya pertenezca a otro cliente.
        String emailNuevo = normalizarEmail(request.email());

        if (emailNuevo != null
                && !emailNuevo.isBlank()
                && !emailNuevo.equals(c.getEmail())) {

            if (clienteRepository.existsByEmail(emailNuevo)) {
                throw new EmailYaExisteException(emailNuevo);
            }

            c.setEmail(emailNuevo);
        }

        // Actualiza los datos personales enviados en la solicitud.
        if (request.nombre() != null && !request.nombre().isBlank()) {
            c.setNombre(request.nombre());
        }

        if (request.telefono() != null) {
            c.setTelefono(request.telefono());
        }

        return aPublico(c);
    }

    /**
     * Desactiva la cuenta de un cliente sin eliminar su registro.
     *
     * <p>Busca el registro correspondiente y cambia su estado a
     * {@code activo = false}. Al ejecutarse dentro de una transacción,
     * el cambio se persiste al finalizar correctamente la operación.</p>
     *
     * @param cliente cliente cuya cuenta se desea desactivar
     * @throws ClienteNoEncontradoException si no existe un registro
     *         asociado al cliente proporcionado
     */
    @Transactional
    public void desactivarCliente(Cliente cliente) {
        Cliente c = clienteRepository.findById(cliente.getId())
                .orElseThrow(() ->
                        new ClienteNoEncontradoException(cliente.getEmail()));

        c.setActivo(false);
    }

    /**
     * Activa la cuenta de un cliente sin modificar su registro.
     *
     * <p>Busca el registro correspondiente y cambia su estado a
     * {@code activo = true}. Al ejecutarse dentro de una transacción,
     * el cambio se persiste al finalizar correctamente la operación.</p>
     *
     * @param cliente cliente cuya cuenta se desea activar
     * @throws ClienteNoEncontradoException si no existe un registro
     *         asociado al cliente proporcionado
     */
    @Transactional
    public void activarCliente(Cliente cliente) {
        Cliente c = clienteRepository.findById(cliente.getId())
                .orElseThrow(() ->
                        new ClienteNoEncontradoException(cliente.getEmail()));

        c.setActivo(true);
    }


    /**
     * Sube (o reemplaza) la foto de perfil del cliente.
     *
     * <p>Sube la nueva imagen a la carpeta privada de perfiles, guarda su key
     * en la BD y, solo si la transacción se confirma, borra la foto anterior
     * del bucket. Si la transacción falla, se conserva la foto anterior y se
     * elimina la recién subida.</p>
     *
     * @param cliente cliente autenticado dueño de la foto
     * @param file    imagen a subir (solo JPG o PNG)
     * @return datos públicos del cliente con su foto actualizada
     * @throws ClienteNoEncontradoException si no existe un registro
     *         asociado al cliente proporcionado
     * @throws ImagenInvalidaException si el archivo no es una imagen válida,
     *         el formato no está permitido o excede las dimensiones máximas
     * @throws IOException si falla la lectura del archivo
     */
    @Transactional
    public ClientePublico subirFotoDelCliente(Cliente cliente, MultipartFile file) throws IOException {
        Cliente c = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new ClienteNoEncontradoException(cliente.getEmail()));

        String keyAnterior = c.getImagenKey();
        c.setImagenKey(storageService.upload(file, StorageService.Carpeta.PERFILES));

        storageService.deleteAfterCommit(keyAnterior);

        return aPublico(c);
    }

    /**
     * Elimina la foto de perfil del cliente.
     *
     * <p>Pone la key de la imagen en {@code null} y, solo si la transacción se
     * confirma, borra el archivo del bucket. Si el cliente no tenía foto, la
     * operación no tiene efecto.</p>
     *
     * @param cliente cliente autenticado dueño de la foto
     * @return datos públicos del cliente sin foto
     * @throws ClienteNoEncontradoException si no existe un registro
     *         asociado al cliente proporcionado
     */
    @Transactional
    public ClientePublico quitarFotoDelCliente(Cliente cliente) {
        Cliente c = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new ClienteNoEncontradoException(cliente.getEmail()));

        String key = c.getImagenKey();
        c.setImagenKey(null);
        storageService.deleteAfterCommit(key);   // borra de S3 solo si la BD confirma

        return aPublico(c);
    }

    /**
     * Convierte un cliente en su representación pública, incluyendo la URL
     * firmada de su foto de perfil.
     *
     * <p>La foto vive en una carpeta privada del bucket, por lo que se genera
     * una URL prefirmada de lectura con vigencia de {@link #VALIDEZ_FOTO}.
     * Si el cliente no tiene foto, el campo {@code fotoUrl} del DTO será
     * {@code null}.</p>
     *
     * <p>La URL generada expira, por lo que no debe almacenarse: debe
     * solicitarse de nuevo cada vez que se consulte el perfil.</p>
     *
     * @param c entidad del cliente a convertir
     * @return datos públicos del cliente con la URL firmada de su foto
     *         (o {@code null} si no tiene)
     */
    private ClientePublico aPublico(Cliente c) {
        String fotoUrl = storageService.getPresignedUrl(c.getImagenKey(), VALIDEZ_FOTO);
        return clienteMappers.devolverClientePublico(c, fotoUrl);
    }


    /**
     * Normaliza un correo: quita espacios y lo pasa a minúsculas.
     * Devuelve {@code null} si el correo es {@code null}.
     */
    private String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
