package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CantidadExcedidaException;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CantidadNoValida;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CarritoDeClienteNoEncontrado;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.CarritoNoEncontrado;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.ProductoNoEnCarritoException;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.mapper.carrito.CarritoMappers;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.ProductoEnCarrito;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.models.CodigoVirtual.EstadoCodigo;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import com.ecommerce.FerreViky.service.storageService.contract.StorageService;
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
    private final CodigoVirtualRepository codigoVirtualRepository;
    private final StorageService storageService;

    /**
     * Busca el producto y valida que exista, esté activo y que la cantidad
     * solicitada sea positiva.
     *
     * <p>La validación de stock se hace aparte con
     * {@link #validarStock(Producto, long)}, porque al sumar a una línea
     * existente hay que comparar contra la cantidad total resultante.</p>
     *
     * @param idProducto ID del producto a validar
     * @param cantidad   cantidad solicitada
     * @return producto encontrado y validado
     * @throws ProductoNoEncontradoException si el producto no existe o está
     *         inactivo
     * @throws CantidadNoValida              si la cantidad es menor o igual
     *         a 0
     */
    private Producto obtenerProductoActivo(Long idProducto, int cantidad) {
        Producto p = productoRepository.findById(idProducto)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new ProductoNoEncontradoException(idProducto));

        if (cantidad <= 0)
            throw new CantidadNoValida(cantidad);

        return p;
    }

    /**
     * Verifica que existan suficientes códigos disponibles del producto.
     *
     * <p>El stock corresponde a la cantidad de códigos virtuales del producto
     * cuyo estado es {@link EstadoCodigo#DISPONIBLE}.</p>
     *
     * @param p             producto cuyo stock se valida
     * @param cantidadTotal cantidad total que se desea tener en el carrito
     * @throws CantidadExcedidaException si se piden más códigos de los
     *         disponibles
     */
    private void validarStock(Producto p, long cantidadTotal) {
        long disponibles = codigoVirtualRepository
                .countByProductoIdAndEstado(p.getId(), EstadoCodigo.DISPONIBLE);

        if (cantidadTotal > disponibles)
            throw new CantidadExcedidaException(disponibles, cantidadTotal, p.getNombre());
    }

    /**
     * Obtiene el carrito activo del cliente usando una query que carga los
     * productos en el mismo JOIN, evitando el problema de N+1 queries. Si el
     * cliente no tiene carrito, crea uno nuevo y lo persiste.
     *
     * @param c cliente dueño del carrito
     * @return carrito existente (con productos cargados) o uno recién creado
     *         y persistido
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
     * Agrega un producto al carrito del cliente autenticado, o actualiza su
     * cantidad si ya existe.
     *
     * <p>Lógica de actualización:</p>
     * <ul>
     *   <li>Si el producto ya está en el carrito, suma {@code dto.cantidad()}
     *       a la cantidad actual.</li>
     *   <li>Si el producto no está en el carrito, se agrega como nuevo
     *       ítem.</li>
     *   <li>En ambos casos, el stock se valida contra la cantidad total
     *       resultante; si la supera, lanza
     *       {@link CantidadExcedidaException}.</li>
     * </ul>
     *
     * <p>El cliente se recibe directamente desde el
     * {@code @AuthenticationPrincipal} del controlador, por lo que no se
     * consulta de nuevo a la BD.</p>
     *
     * @param dto     datos de la operación: {@code idProducto} y
     *                {@code cantidad} a agregar
     * @param cliente cliente autenticado dueño del carrito
     * @throws ProductoNoEncontradoException si el producto no existe o está
     *         inactivo
     * @throws CantidadNoValida              si la cantidad solicitada es
     *         menor o igual a 0
     * @throws CantidadExcedidaException     si la cantidad total supera el
     *         stock disponible
     */
    @Transactional
    public void agregarOActualizar(AgregarCarrito dto, Cliente cliente) {
        Producto p = obtenerProductoActivo(dto.idProducto(), dto.cantidad());
        Carrito car = obtenerOCrearCarrito(cliente);

        Optional<ProductoEnCarrito> itemExistente = car.getProductos().stream()
                .filter(cp -> cp.getProducto().getId().equals(p.getId()))
                .findFirst();//Operacion terminal que regresa el primero

        int cantidadActual = itemExistente.map(ProductoEnCarrito::getCantidad).orElse(0);
        int nuevaCantidad = Math.addExact(cantidadActual, dto.cantidad());

        validarStock(p, nuevaCantidad);

        if (itemExistente.isPresent()) {
            itemExistente.get().setCantidad(nuevaCantidad);
        } else {
            ProductoEnCarrito nuevoItem = new ProductoEnCarrito();
            nuevoItem.setCarrito(car);
            nuevoItem.setProducto(p);
            nuevoItem.setCantidad(dto.cantidad());
            car.getProductos().add(nuevoItem);
        }

        carritoRepository.save(car);
    }

    /**
     * Establece la cantidad exacta de un producto que ya está en el carrito
     * del cliente.
     *
     * <p>A diferencia de {@link #agregarOActualizar}, que suma, este método
     * <b>reemplaza</b> la cantidad actual por la indicada en el DTO. Si la
     * cantidad es {@code 0}, la línea se elimina del carrito (requiere
     * {@code orphanRemoval = true} en la relación
     * {@code Carrito → CarritoProducto}).</p>
     *
     * @param dto     datos con el id del producto y la nueva cantidad (≥ 0)
     * @param cliente cliente autenticado dueño del carrito
     * @throws CarritoDeClienteNoEncontrado si el cliente no tiene carrito
     * @throws ProductoNoEnCarritoException si el producto no está en el
     *         carrito
     * @throws CantidadExcedidaException    si la cantidad solicitada supera
     *         el stock disponible
     */
    @Transactional
    public void actualizarCantidad(ActualizarCantidad dto, Cliente cliente) {
        Carrito car = carritoRepository.findByClienteConProductos(cliente)
                .orElseThrow(() -> new CarritoDeClienteNoEncontrado(cliente.getId()));

        ProductoEnCarrito item = car.getProductos().stream()
                .filter(cp -> cp.getProducto().getId().equals(dto.idProducto()))
                .findFirst()
                .orElseThrow(() -> new ProductoNoEnCarritoException(dto.idProducto()));

        if (dto.cantidad() == 0) {
            car.getProductos().remove(item);
        } else {
            validarStock(item.getProducto(), dto.cantidad());
            item.setCantidad(dto.cantidad());
        }

        carritoRepository.save(car);
    }

    /**
     * Elimina por completo un producto del carrito del cliente, sin importar
     * su cantidad.
     *
     * <p>Requiere {@code orphanRemoval = true} en la relación
     * {@code Carrito → CarritoProducto} para que la fila se borre de la base
     * de datos.</p>
     *
     * @param idProducto id del producto a quitar del carrito
     * @param cliente    cliente autenticado dueño del carrito
     * @throws CarritoDeClienteNoEncontrado si el cliente no tiene carrito
     * @throws ProductoNoEnCarritoException si el producto no está en el
     *         carrito
     */
    @Transactional
    public void eliminarProducto(Long idProducto, Cliente cliente) {
        Carrito car = carritoRepository.findByClienteConProductos(cliente)
                .orElseThrow(() -> new CarritoDeClienteNoEncontrado(cliente.getId()));

        ProductoEnCarrito item = car.getProductos().stream()
                .filter(cp -> cp.getProducto().getId().equals(idProducto))
                .findFirst()
                .orElseThrow(() -> new ProductoNoEnCarritoException(idProducto));

        car.getProductos().remove(item);
    }

    /**
     * Retorna el carrito completo del cliente autenticado, mapeado a DTO de
     * respuesta.
     *
     * <p>Usa {@code findByClienteConProductos} para cargar los productos en
     * un solo query (JOIN FETCH). Se ejecuta en una transacción de solo
     * lectura, por lo que no modifica datos.</p>
     *
     * @param cliente cliente autenticado del que se quiere obtener el carrito
     * @return {@link CarritoResponseDTO} con el carrito y sus productos
     * @throws CarritoNoEncontrado si el cliente no tiene un carrito
     *         registrado
     */
    @Transactional(readOnly = true)
    public CarritoResponseDTO obtenerCarritoPorCliente(Cliente cliente) {
        Carrito carrito = carritoRepository.findByClienteConProductos(cliente)
                .orElseThrow(() -> new CarritoNoEncontrado(cliente.getId()));

        return toResponse(carrito);
    }

    /**
     * Obtiene el carrito asociado a un cliente por su ID.
     *
     * <p>A diferencia de {@link #obtenerCarritoPorCliente(Cliente)}, este
     * método recibe solo el ID, útil para consultas administrativas donde no
     * se dispone de la entidad {@link Cliente} completa. Se ejecuta en una
     * transacción de solo lectura, por lo que mantiene abierta la sesión JPA
     * mientras se arma la respuesta.</p>
     *
     * @param id ID del cliente cuyo carrito se quiere consultar
     * @return {@link CarritoResponseDTO} con el carrito y sus productos
     * @throws CarritoNoEncontrado si no existe carrito para ese ID de cliente
     */
    @Transactional(readOnly = true)
    public CarritoResponseDTO obtenerCarritoPorId(Long id) {
        Carrito carrito = carritoRepository.findByClienteId(id)
                .orElseThrow(() -> new CarritoNoEncontrado(id));

        return toResponse(carrito);
    }

    /**
     * Convierte un carrito a DTO resolviendo la URL de imagen de cada
     * producto.
     *
     * @param carrito carrito a convertir
     * @return DTO del carrito con {@code imagenUrl} en cada línea
     */
    private CarritoResponseDTO toResponse(Carrito carrito) {
        return CarritoMappers.toCarritoResponseDTO(
                carrito, p -> storageService.getPublicUrl(p.getImagenKey()));
    }
}