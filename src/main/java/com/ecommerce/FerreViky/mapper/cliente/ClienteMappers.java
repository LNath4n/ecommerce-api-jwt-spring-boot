package com.ecommerce.FerreViky.mapper.cliente;

import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.ClientePublico;
import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.CreacionDeClienteRespuestaDto;
import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.LoginClienteDto;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.models.Rol;
import org.springframework.stereotype.Component;

/**
 * Mappers para convertir entre DTOs de cliente y la entidad {@link Cliente}.
 */
@Component
public class ClienteMappers {

    /**
     * Convierte el DTO de login/registro a una entidad {@link Cliente}.
     *
     * <p>Intencionalmente solo mapea email y password para no exponer
     * ni persistir campos que el cliente no deba controlar (e.g. id, roles).
     */
    public Cliente DtoLoginACliente(LoginClienteDto loginClienteDto) {
        Cliente cliente = new Cliente();
        cliente.setEmail(loginClienteDto.email());
        cliente.setPassword(loginClienteDto.password());
        cliente.setRol(Rol.USER);
        return cliente;
    }

    /**
     * Convierte una entidad {@link Cliente} al DTO de respuesta post-creación,
     * exponiendo únicamente el id y email asignados.
     *
     * @param cliente entidad ya persistida con id generado
     * @return DTO con los datos mínimos de confirmación al frontend
     */
    public CreacionDeClienteRespuestaDto ClienteACreacion(Cliente cliente) {
        return new CreacionDeClienteRespuestaDto(
                cliente.getId(), cliente.getEmail()
        );
    }

    public ClientePublico devolverClientePublico(Cliente cliente){

        return new ClientePublico(cliente.getEmail(),cliente.getNombre(),cliente.getTelefono(), cliente.getImagenKey());
    }
}