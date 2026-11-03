package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CantidadExcedidaException;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CantidadNoValida;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CarritoDeClienteNoEncontrado;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CarritoNoEncontrado;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.ConflictoConcurrenteException;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.ProductoNoEnCarritoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.mapper.carrito.CarritoMappers;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.models.CodigoVirtual.EstadoCodigo;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.models.ProductoEnCarrito;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import com.ecommerce.FerreViky.service.storageService.contract.StorageService;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Servicio del carrito de compras: agregar, actualizar y quitar productos, y
 * consultar el carrito de un cliente.
 * <p>
 * El stock de un producto es la cantidad de códigos virtuales en estado
 * {@link EstadoCodigo#DISPONIBLE}.
 */
@Service
@AllArgsConstructor
public class CarritoService {

    private static final String MENSAJE_CONFLICTO =
            "Se está procesando otra operación en tu carrito, intenta de nuevo";

    private final CarritoRepository carritoRepository;
    private final ProductoRepository productoRepository;
    private final CodigoVirtualRepository codigoVirtualRepository;
    private final StorageService storageService;

    /**
     * Agrega un producto al carrito del cliente, o suma a su cantidad si ya está.
     *
     * <p>Si el producto ya está en el carrito, se suma {@code dto.cantidad()} a la
     * cantidad actual; si no, se agrega como ítem nuevo. En ambos casos el stock se
     * valida contra la cantidad total resultante.</p>
     *
     * <p>El carrito se bloquea hasta terminar la transacción para serializar
     * peticiones simultáneas del mismo cliente. El cliente llega directo del
     * {@code @AuthenticationPrincipal}, así que no se consulta de nuevo.</p>
     *
     * @param dto     {@code idProducto} y {@code cantidad} a agregar
     * @param cliente cliente autenticado dueño del carrito
     * @throws ProductoNoEncontradoException si el producto no existe o está inactivo
     * @throws CantidadNoValida              si la cantidad es menor o igual a 0
     * @throws CantidadExcedidaException     si la cantidad total supera el stock
     * @throws ConflictoConcurrenteException si otra operación modificó el carrito al mismo tiempo
     */
    @Transactional
    public void agregarOActualizar(AgregarCarrito dto, Cliente cliente) {
        Producto producto = obtenerProductoActivo(dto.idProducto(), dto.cantidad());
        Carrito carrito = obtenerOCrearCarritoBloqueado(cliente);

        Optional<ProductoEnCarrito> itemExistente = buscarItem(carrito, producto.getId());

        int cantidadActual = itemExistente.map(ProductoEnCarrito::getCantidad).orElse(0);
        int nuevaCantidad = Math.addExact(cantidadActual, dto.cantidad());

        validarStock(producto, cantidadActual, dto.cantidad());

        if (itemExistente.isPresent()) {
            itemExistente.get().setCantidad(nuevaCantidad);
        } else {
            ProductoEnCarrito nuevoItem = new ProductoEnCarrito();
            nuevoItem.setCarrito(carrito);
            nuevoItem.setProducto(producto);
            nuevoItem.setCantidad(nuevaCantidad);
            carrito.getProductos().add(nuevoItem);
        }

        try {
            carritoRepository.saveAndFlush(carrito);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoConcurrenteException(MENSAJE_CONFLICTO);
        }
    }

    /**
     * Establece la cantidad exacta de un producto que ya está en el carrito.
     *
     * <p>A diferencia de {@link #agregarOActualizar}, que suma, este método
     * <b>reemplaza</b> la cantidad actual. Si la cantidad es {@code 0}, la línea se
     * elimina del carrito (requiere {@code orphanRemoval = true} en la relación
     * {@code Carrito → ProductoEnCarrito}).</p>
     *
     * @param dto     id del producto y nueva cantidad (≥ 0)
     * @param cliente cliente autenticado dueño del carrito
     * @throws CarritoDeClienteNoEncontrado si el cliente no tiene carrito
     * @throws ProductoNoEnCarritoException si el producto no está en el carrito
     * @throws CantidadExcedidaException    si la cantidad supera el stock disponible
     */
    @Transactional
    public void actualizarCantidad(ActualizarCantidad dto, Cliente cliente) {
        Carrito carrito = obtenerCarritoParaActualizar(cliente);

        ProductoEnCarrito item = buscarItem(carrito, dto.idProducto())
                .orElseThrow(() -> new ProductoNoEnCarritoException(dto.idProducto()));

        if (dto.cantidad() == 0) {
            carrito.getProductos().remove(item);
        } else {
            validarStockExacto(item.getProducto(), dto.cantidad());
            item.setCantidad(dto.cantidad());
        }

        carritoRepository.save(carrito);
    }

    /**
     * Elimina por completo un producto del carrito, sin importar su cantidad.
     *
     * <p>Requiere {@code orphanRemoval = true} en la relación
     * {@code Carrito → ProductoEnCarrito} para que la fila se borre de la base de
     * datos.</p>
     *
     * @param idProducto id del producto a quitar
     * @param cliente    cliente autenticado dueño del carrito
     * @throws CarritoDeClienteNoEncontrado si el cliente no tiene carrito
     * @throws ProductoNoEnCarritoException si el producto no está en el carrito
     */
    @Transactional
    public void eliminarProducto(Long idProducto, Cliente cliente) {
        Carrito carrito = obtenerCarritoParaActualizar(cliente);

        ProductoEnCarrito item = buscarItem(carrito, idProducto)
                .orElseThrow(() -> new ProductoNoEnCarritoException(idProducto));

        carrito.getProductos().remove(item);
    }

    /**
     * Retorna el carrito del cliente autenticado mapeado a DTO.
     *
     * <p>Carga los productos en un solo query ({@code JOIN FETCH}) y corre en una
     * transacción de solo lectura.</p>
     *
     * @param cliente cliente autenticado
     * @return {@link CarritoResponseDTO} con el carrito y sus productos
     * @throws CarritoNoEncontrado si el cliente no tiene carrito
     */
    @Transactional(readOnly = true)
    public CarritoResponseDTO obtenerCarritoPorCliente(Cliente cliente) {
        Carrito carrito = carritoRepository.findByClienteConProductos(cliente)
                .orElseThrow(() -> new CarritoNoEncontrado(cliente.getId()));

        return toResponse(carrito);
    }

    /**
     * Retorna el carrito de un cliente a partir de su ID.
     *
     * <p>Pensado para consultas administrativas donde no se tiene la entidad
     * {@link Cliente}. La transacción de solo lectura mantiene abierta la sesión JPA
     * mientras se arma la respuesta.</p>
     *
     * @param id ID del cliente
     * @return {@link CarritoResponseDTO} con el carrito y sus productos
     * @throws CarritoNoEncontrado si no existe carrito para ese cliente
     */
    @Transactional(readOnly = true)
    public CarritoResponseDTO obtenerCarritoPorId(Long id) {
        Carrito carrito = carritoRepository.findByClienteId(id)
                .orElseThrow(() -> new CarritoNoEncontrado(id));

        return toResponse(carrito);
    }

    /**
     * Obtiene el carrito del cliente bloqueándolo ({@code FOR UPDATE}) hasta que
     * termine la transacción, y lo crea si no existe.
     * <p>
     * Si otro request lo crea al mismo tiempo, la restricción de unicidad lo detecta.
     * En Postgres la transacción queda abortada tras ese error, por lo que no se
     * debe consultar nada más dentro de ella.
     *
     * @param cliente cliente dueño del carrito
     * @return el carrito bloqueado, existente o recién creado
     * @throws ConflictoConcurrenteException si otro request creó el carrito primero
     */
    private Carrito obtenerOCrearCarritoBloqueado(Cliente cliente) {
        return carritoRepository.findByClienteParaActualizar(cliente)
                .orElseGet(() -> {
                    Carrito nuevo = new Carrito();
                    nuevo.setCliente(cliente);
                    nuevo.setFechaCreacion(LocalDateTime.now());
                    nuevo.setProductos(new ArrayList<>());
                    try {
                        return carritoRepository.saveAndFlush(nuevo);
                    } catch (DataIntegrityViolationException e) {
                        throw new ConflictoConcurrenteException(MENSAJE_CONFLICTO);
                    }
                });
    }

    /**
     * Obtiene el carrito del cliente bloqueado para modificarlo.
     *
     * @param cliente cliente dueño del carrito
     * @return el carrito bloqueado
     * @throws CarritoDeClienteNoEncontrado si el cliente no tiene carrito
     */
    private Carrito obtenerCarritoParaActualizar(Cliente cliente) {
        return carritoRepository.findByClienteParaActualizar(cliente)
                .orElseThrow(() -> new CarritoDeClienteNoEncontrado(cliente.getId()));
    }

    /**
     * Busca la línea de un producto dentro del carrito.
     *
     * @param carrito    carrito donde buscar
     * @param idProducto id del producto
     * @return la línea del carrito, o vacío si el producto no está
     */
    private Optional<ProductoEnCarrito> buscarItem(Carrito carrito, Long idProducto) {
        return carrito.getProductos().stream()
                .filter(item -> item.getProducto().getId().equals(idProducto))
                .findFirst();
    }

    /**
     * Busca el producto y valida que exista, esté activo y que la cantidad
     * solicitada sea positiva.
     *
     * <p>El stock se valida aparte con {@link #validarStock(Producto, long, long)}, porque
     * al sumar a una línea existente hay que comparar contra la cantidad total.</p>
     *
     * @param idProducto ID del producto
     * @param cantidad   cantidad solicitada
     * @return el producto encontrado
     * @throws ProductoNoEncontradoException si el producto no existe o está inactivo
     * @throws CantidadNoValida              si la cantidad es menor o igual a 0
     */
    private Producto obtenerProductoActivo(Long idProducto, int cantidad) {
        Producto producto = productoRepository.findById(idProducto)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        if (cantidad <= 0)
            throw new CantidadNoValida(cantidad);

        return producto;
    }

    /**
     * Verifica que haya stock suficiente para <b>sumar</b> unidades a un producto
     * que ya puede estar en el carrito.
     *
     * <p>El stock es la cantidad de códigos virtuales en estado
     * {@link EstadoCodigo#DISPONIBLE}. Se compara contra {@code cantidadActual +
     * cantidadSolicitada}.</p>
     *
     * @param producto          producto cuyo stock se valida
     * @param cantidadActual    unidades que el cliente ya tiene en el carrito (0 si no está)
     * @param cantidadSolicitada unidades que se quieren agregar
     * @throws CantidadExcedidaException si el total resultante supera los códigos disponibles
     */
    private void validarStock(Producto producto, long cantidadActual, long cantidadSolicitada) {
        long disponibles = codigoVirtualRepository
                .countByProductoIdAndEstado(producto.getId(), EstadoCodigo.DISPONIBLE);

        if (cantidadActual + cantidadSolicitada > disponibles)
            throw new CantidadExcedidaException(
                    producto.getNombre(), disponibles, cantidadActual, cantidadSolicitada);
    }

    /**
     * Verifica que haya stock suficiente para <b>fijar</b> una cantidad exacta de
     * un producto en el carrito, reemplazando la actual.
     *
     * @param producto       producto cuyo stock se valida
     * @param cantidadDeseada cantidad total que el cliente quiere tener
     * @throws CantidadExcedidaException si la cantidad deseada supera los códigos disponibles
     */
    private void validarStockExacto(Producto producto, long cantidadDeseada) {
        long disponibles = codigoVirtualRepository
                .countByProductoIdAndEstado(producto.getId(), EstadoCodigo.DISPONIBLE);

        if (cantidadDeseada > disponibles)
            throw new CantidadExcedidaException(
                    producto.getNombre(), disponibles, cantidadDeseada);
    }

    /**
     * Elimina todos los productos del carrito del cliente.
     *
     * <p>El carrito en sí se conserva (vacío); requiere {@code orphanRemoval = true}
     * en la relación {@code Carrito → ProductoEnCarrito} para borrar las filas.</p>
     *
     * @param cliente cliente autenticado dueño del carrito
     * @throws CarritoDeClienteNoEncontrado si el cliente no tiene carrito
     */
    @Transactional
    public void vaciarCarrito(Cliente cliente) {
        Carrito carrito = obtenerCarritoParaActualizar(cliente);
        carrito.getProductos().clear();
    }

    /**
     * Convierte un carrito a DTO resolviendo la URL pública de la imagen de cada
     * producto.
     *
     * @param carrito carrito a convertir
     * @return DTO del carrito con {@code imagenUrl} en cada línea
     */
    private CarritoResponseDTO toResponse(Carrito carrito) {
        return CarritoMappers.toCarritoResponseDTO(
                carrito, producto -> storageService.getPublicUrl(producto.getImagenKey()));
    }
}