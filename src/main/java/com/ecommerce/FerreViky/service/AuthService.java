package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.LoginClienteDto;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.ErrorAlCrearCarritoException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.CredencialesInvalidasException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.EmailYaExisteException;
import com.ecommerce.FerreViky.jwt.JwtService;
import com.ecommerce.FerreViky.mapper.cliente.ClienteMappers;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.ClienteRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Servicio de autenticación de clientes: inicio de sesión y registro.
 * <p>
 * Ambas operaciones devuelven un JWT listo para usarse en las siguientes peticiones.
 */
@Service
@AllArgsConstructor
public class AuthService {

    private final ClienteRepository clienteRepository;
    private final CarritoRepository carritoRepository;
    private final ClienteMappers clienteMappers;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    /**
     * Autentica al cliente con sus credenciales y genera un token JWT.
     * <p>
     * La validación la hace el {@link AuthenticationManager}, que lanza una excepción
     * si las credenciales son incorrectas. El email se normaliza antes de autenticar.
     *
     * @param dto email y contraseña del cliente
     * @return {@link AuthResponse} con el JWT generado
     * @throws org.springframework.security.core.AuthenticationException si las credenciales son inválidas
     * @throws CredencialesInvalidasException                            si el cliente no existe tras autenticar
     */
    public AuthResponse login(LoginClienteDto dto) {
        String email = normalizarEmail(dto.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, dto.password())
        );

        Cliente cliente = clienteRepository.findByEmail(email)
                .orElseThrow(CredencialesInvalidasException::new);

        return new AuthResponse(jwtService.getToken(cliente));
    }

    /**
     * Registra un nuevo cliente y crea su carrito vacío en la misma transacción.
     * <p>
     * Pasos:
     * <ol>
     *   <li>Verifica que el email no esté registrado.</li>
     *   <li>Persiste el cliente con la contraseña encriptada.</li>
     *   <li>Crea y persiste su {@link Carrito}.</li>
     *   <li>Genera un JWT para que pueda operar sin iniciar sesión de nuevo.</li>
     * </ol>
     * Si cualquier paso falla, la transacción completa se revierte.
     *
     * @param dto email y contraseña del nuevo cliente
     * @return {@link AuthResponse} con el JWT del cliente recién registrado
     * @throws EmailYaExisteException       si el email ya está registrado
     * @throws ErrorAlCrearCarritoException si falla la creación del carrito
     */
    @Transactional
    public AuthResponse guardarCliente(LoginClienteDto dto) {
        String email = normalizarEmail(dto.email());

        if (clienteRepository.existsByEmail(email)) {
            throw new EmailYaExisteException(email);
        }

        Cliente cliente = persistirCliente(dto, email);
        crearCarrito(cliente);

        return new AuthResponse(jwtService.getToken(cliente));
    }

    /**
     * Construye y guarda el cliente con la contraseña encriptada.
     * <p>
     * Si dos registros con el mismo email llegan a la vez, el segundo en guardar
     * choca con la restricción de unicidad y se reporta como email ya existente.
     *
     * @param dto   datos del cliente
     * @param email email ya normalizado
     * @return el cliente persistido
     * @throws EmailYaExisteException si la base de datos rechaza el email por duplicado
     */
    private Cliente persistirCliente(LoginClienteDto dto, String email) {
        Cliente cliente = clienteMappers.DtoLoginACliente(dto);
        cliente.setEmail(email);
        cliente.setPassword(passwordEncoder.encode(dto.password()));

        try {
            return clienteRepository.saveAndFlush(cliente);
        } catch (DataIntegrityViolationException e) {
            throw new EmailYaExisteException(email);
        }
    }

    /**
     * Crea y guarda un carrito vacío asociado al cliente.
     *
     * @param cliente cliente dueño del carrito
     * @throws ErrorAlCrearCarritoException si ocurre un error al persistirlo
     */
    private void crearCarrito(Cliente cliente) {
        Carrito carrito = new Carrito();
        carrito.setCliente(cliente);
        carrito.setFechaCreacion(LocalDateTime.now());

        try {
            carritoRepository.saveAndFlush(carrito);
        } catch (Exception e) {
            throw new ErrorAlCrearCarritoException(cliente.getId(), e);
        }
    }

    /**
     * Quita espacios y pasa el email a minúsculas para comparar de forma consistente.
     *
     * @param email email recibido, puede ser {@code null}
     * @return el email normalizado, o {@code null} si era {@code null}
     */
    private String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}