package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.Jwt.JwtService;
import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.LoginClienteDto;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions;
import com.ecommerce.FerreViky.mapper.cliente.ClienteMappers;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.ClienteRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class AuthService {

    private final ClienteRepository clienteRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final ClienteMappers clienteMappers;
    private final CarritoRepository carritoRepository;

    /**
     * Autentica al cliente con sus credenciales y genera un token JWT.
     * <p>
     * Delega la validación al {@link AuthenticationManager}, que lanza una excepción
     * automáticamente si las credenciales son incorrectas, por lo que no es necesario
     * verificarlo manualmente después.
     *
     * @param dto DTO con email y contraseña del cliente
     * @return {@link AuthResponse} con el JWT generado
     * @throws org.springframework.security.core.AuthenticationException si las credenciales son inválidas
     */
    public AuthResponse login(LoginClienteDto dto) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(dto.email(), dto.password()));
        Cliente user = clienteRepository.findByEmail(dto.email()).orElseThrow(ClienteExceptions.CredencialesInvalidasException::new);
        String token = jwtService.getToken(user);
        return new AuthResponse(token);
    }

    /**
     * Registra un nuevo cliente y crea su carrito vacío en la misma transacción.
     * <p>
     * El flujo es:
     * <ol>
     *   <li>Verifica que el email no esté ya registrado.</li>
     *   <li>Mapea el DTO a entidad {@link Cliente} y encripta la contraseña.</li>
     *   <li>Persiste el cliente.</li>
     *   <li>Crea y persiste un {@link Carrito} vinculado al cliente.</li>
     *   <li>Genera y retorna un JWT para que el cliente pueda operar sin un login adicional.</li>
     * </ol>
     * Si la creación del carrito falla, toda la transacción se revierte gracias a {@code @Transactional}.
     *
     * @param dto DTO con email y contraseña del nuevo cliente
     * @return {@link AuthResponse} con el JWT del cliente recién registrado
     * @throws ClienteExceptions.EmailYaExisteException          si el email ya está registrado
     * @throws CarritoExceptions.ErrorAlCrearCarritoException     si ocurre un error al persistir el carrito
     */
    @Transactional
    public AuthResponse guardarCliente(LoginClienteDto dto) {
        if (clienteRepository.existsByEmail(dto.email())) {
            throw new ClienteExceptions.EmailYaExisteException(dto.email());
        }

        Cliente cliente = clienteMappers.DtoLoginACliente(dto);
        cliente.setPassword(passwordEncoder.encode(dto.password()));
        cliente = clienteRepository.save(cliente);

        Carrito carrito = new Carrito();
        carrito.setCliente(cliente);
        carrito.setFechaCreacion(LocalDateTime.now());
        try {
            carritoRepository.save(carrito);
        } catch (Exception e) {
            throw new CarritoExceptions.ErrorAlCrearCarritoException(cliente.getId(), e);
        }

        return new AuthResponse(jwtService.getToken(cliente));
    }
}