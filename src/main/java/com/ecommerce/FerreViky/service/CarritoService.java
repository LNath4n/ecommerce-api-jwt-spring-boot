package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CarritoDeClienteNoEncontrado;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CarritoNoEncontrado;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CantidadExcedidaException;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CantidadNoValida;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.ProductoNoEnCarritoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.mapper.carrito.CarritoMappers;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.CarritoProducto;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

@Service
@AllArgsConstructor
public class CarritoService {

    private final ProductoRepository productoRepository;
    private final CarritoRepository carritoRepository;

    /**
     * Valida que el producto exista y que haya stock suficiente para la cantidad solicitada.
     *
     * @param idProducto ID del producto a validar
     * @param cantidad   cantidad solicitada
     * @return producto encontrado y validado
     * @throws ProductoNoEncontradoException si el producto no existe
     * @throws CantidadNoValida             si la cantidad es menor o igual a 0
     * @throws CantidadExcedidaException    si la cantidad supera el stock disponible
     */
    private Producto validarProductoYStock(Long idProducto, int cantidad) {
        Producto p = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        if (cantidad <= 0)
            throw new CantidadNoValida(cantidad);

        if (cantidad > p.getStock())
            throw new CantidadExcedidaException(p.getStock(), cantidad, p.getDescripcion());

        return p;
    }

    /**
     * Obtiene el carrito activo del cliente usando una query que carga los productos en el mismo JOIN,
     * evitando el problema de N+1 queries. Si el cliente no tiene carrito, crea uno nuevo y lo persiste.
     *
     * @param c cliente dueño del carrito
     * @return carrito existente (con productos cargados) o uno recién creado y persistido
     */
    private Carrito obtenerOCrearCarrito(Cliente c) {
        return carritoRepository.findByClienteConProductos(c)
                .orElseGet(() -> {
                    Carrito nuevo = new Carrito();
                    nuevo.setCliente(c);
                    nuevo.setFechaCreacion(LocalDateTime.now());
                    nuevo.setProductos(new ArrayList<>());
                    return carritoRepository.save(nuevo);
                });
    }

    /**
     * Agrega un producto al carrito del cliente autenticado, o actualiza su cantidad si ya existe.
     * <p>
     * Lógica de actualización:
     * <ul>
     *   <li>Si el producto ya está en el carrito, suma {@code dto.cantidad()} a la cantidad actual.</li>
     *   <li>Si la nueva cantidad total supera el stock, lanza {@link CantidadExcedidaException}.</li>
     *   <li>Si el producto no está en el carrito, se agrega como nuevo ítem.</li>
     * </ul>
     * El cliente se recibe directamente desde el {@code @AuthenticationPrincipal} del controlador,
     * por lo que no se consulta de nuevo a la BD.
     *
     * @param dto     datos de la operación: {@code idProducto} y {@code cantidad} a agregar
     * @param cliente cliente autenticado dueño del carrito
     * @throws ProductoNoEncontradoException si el producto no existe
     * @throws CantidadNoValida             si la cantidad solicitada es ≤ 0
     * @throws CantidadExcedidaException    si la cantidad total supera el stock disponible
     */
    @Transactional
    public void agregarOActualizar(AgregarCarrito dto, Cliente cliente) {
        Producto p = validarProductoYStock(dto.idProducto(), dto.cantidad());
        Carrito car = obtenerOCrearCarrito(cliente);

        Optional<CarritoProducto> itemExistente = car.getProductos().stream()
                .filter(cp -> cp.getProducto().getId().equals(p.getId()))
                .findFirst();

        if (itemExistente.isPresent()) {
            CarritoProducto item = itemExistente.get();
            int nuevaCantidad = item.getCantidad() + dto.cantidad();

            if (nuevaCantidad > p.getStock())
                throw new CantidadExcedidaException(
                        p.getStock(),
                        nuevaCantidad,
                        p.getDescripcion()
                );

            item.setCantidad(nuevaCantidad);
        } else {
            CarritoProducto nuevoItem = new CarritoProducto();
            nuevoItem.setCarrito(car);
            nuevoItem.setProducto(p);
            nuevoItem.setCantidad(dto.cantidad());
            car.getProductos().add(nuevoItem);
        }

        carritoRepository.save(car);
    }

    /**
     * Establece la cantidad exacta de un producto que ya está en el carrito del cliente.
     * <p>
     * A diferencia de {@link #agregarOActualizar}, que suma, este método <b>reemplaza</b>
     * la cantidad actual por la indicada en el DTO. Si la cantidad es {@code 0},
     * la línea se elimina del carrito (requiere {@code orphanRemoval = true}
     * en la relación {@code Carrito → CarritoProducto}).
     *
     * @param dto     datos con el id del producto y la nueva cantidad (≥ 0)
     * @param cliente cliente autenticado dueño del carrito
     * @throws CarritoDeClienteNoEncontrado si el cliente no tiene carrito
     * @throws ProductoNoEnCarritoException si el producto no está en el carrito
     * @throws CantidadExcedidaException    si la cantidad solicitada supera el stock disponible
     */
    @Transactional
    public void actualizarCantidad(ActualizarCantidad dto, Cliente cliente) {
        Carrito car = carritoRepository.findByClienteConProductos(cliente)
                .orElseThrow(() -> new CarritoDeClienteNoEncontrado(cliente.getId()));

        CarritoProducto item = car.getProductos().stream()
                .filter(cp -> cp.getProducto().getId().equals(dto.idProducto()))
                .findFirst()
                .orElseThrow(() -> new ProductoNoEnCarritoException(dto.idProducto()));

        if (dto.cantidad() == 0) {
            car.getProductos().remove(item);
        } else {
            Producto p = item.getProducto();

            if (dto.cantidad() > p.getStock())
                throw new CantidadExcedidaException(
                        p.getStock(),
                        dto.cantidad(),
                        p.getDescripcion()
                );

            item.setCantidad(dto.cantidad());
        }

        carritoRepository.save(car);
    }

    /**
     * Elimina por completo un producto del carrito del cliente, sin importar su cantidad.
     * <p>
     * Requiere {@code orphanRemoval = true} en la relación {@code Carrito → CarritoProducto}
     * para que la fila se borre de la base de datos.
     *
     * @param idProducto id del producto a quitar del carrito
     * @param cliente    cliente autenticado dueño del carrito
     * @throws CarritoDeClienteNoEncontrado si el cliente no tiene carrito
     * @throws ProductoNoEnCarritoException si el producto no está en el carrito
     */
    @Transactional
    public void eliminarProducto(Long idProducto, Cliente cliente) {
        Carrito car = carritoRepository.findByClienteConProductos(cliente)
                .orElseThrow(() -> new CarritoDeClienteNoEncontrado(cliente.getId()));

        CarritoProducto item = car.getProductos().stream()
                .filter(cp -> cp.getProducto().getId().equals(idProducto))
                .findFirst()
                .orElseThrow(() -> new ProductoNoEnCarritoException(idProducto));

        car.getProductos().remove(item);
    }

    /**
     * Retorna el carrito completo del cliente autenticado, mapeado a DTO de respuesta.
     * <p>
     * Usa {@code findByClienteConProductos} para cargar los productos en un solo query (JOIN FETCH),
     * evitando lazy loading fuera de la sesión JPA.
     *
     * @param cliente cliente autenticado del que se quiere obtener el carrito
     * @return {@link CarritoResponseDTO} con el carrito y sus productos
     * @throws CarritoNoEncontrado si el cliente no tiene un carrito registrado
     */
    public CarritoResponseDTO obtenerCarritoPorCliente(Cliente cliente) {
        Carrito carrito = carritoRepository.findByClienteConProductos(cliente)
                .orElseThrow(() -> new CarritoNoEncontrado(cliente.getId()));

        return CarritoMappers.toCarritoResponseDTO(carrito);
    }

    /**
     * Obtiene el carrito asociado a un cliente por su ID.
     * <p>
     * A diferencia de {@link #obtenerCarritoPorCliente(Cliente)}, este método
     * recibe solo el ID, útil para consultas administrativas donde no se dispone
     * de la entidad {@link Cliente} completa.
     *
     * @param id ID del cliente cuyo carrito se quiere consultar
     * @return {@link CarritoResponseDTO} con el carrito y sus productos
     * @throws CarritoNoEncontrado si no existe carrito para ese ID de cliente
     */
    public CarritoResponseDTO obtenerCarritoPorId(Long id) {
        Carrito carrito = carritoRepository.findByClienteId(id)
                .orElseThrow(() -> new CarritoNoEncontrado(id));

        return CarritoMappers.toCarritoResponseDTO(carrito);
    }
}