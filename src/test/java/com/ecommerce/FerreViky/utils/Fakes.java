package com.ecommerce.FerreViky.utils;

import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.CarritoProductoDTO;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.models.*;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository.StockProducto;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Datos falsos para pruebas. Vive en src/test/java.
 * Regla: aqui solo van datos, nunca mocks ni asserts.
 * Truco: cada fake acepta un Consumer para cambiar solo lo que sea "diferente".
 */
public final class Fakes {

    private static final Faker faker = new Faker();

    private Fakes() {}

    
    // Cliente
    
    public static Cliente generarClienteFake() {
        return generarClienteFake(cli -> {});
    }

    public static Cliente generarClienteFake(Consumer<Cliente> cambios) {
        Cliente c = new Cliente();
        c.setId(faker.number().numberBetween(1L, 100_000L));
        c.setEmail(faker.internet().safeEmailAddress());
        c.setActivo(true);
        c.setNombre("Nathan");
        c.setPassword("UnSaludo");
        c.setTelefono("5551234567");

        cambios.accept(c);
        return c;
    }

    
    // Producto
    
    public static Producto generarProductoFake() {
        return generarProductoFake(p -> {});
    }

    public static Producto generarProductoFake(Consumer<Producto> cambios) {
        Producto p = new Producto();
        Categoria c = new Categoria();
        c.setId(faker.number().numberBetween(1L, 100_000L));
        c.setNombre("VideoJuegos");

        p.setId(faker.number().numberBetween(1L, 100_000L));
        p.setSku("STEAM-MX-200");
        p.setNombre("Steam Gift Card $200 MXN");
        p.setSlug("steam-gift-card-200-mxn");
        p.setDescripcion("Tarjeta de regalo para la tienda de Steam");
        p.setPlataforma("Steam");
        p.setTipo(Producto.TipoProducto.GIFT_CARD);
        p.setRegion("MX");
        p.setValorNominal(new BigDecimal("200.00"));
        p.setMoneda("MXN");
        p.setPrecioVenta(new BigDecimal("210.00"));
        p.setPrecioCosto(new BigDecimal("190.00"));
        p.setInstruccionesCanje("Canjea el código en Steam > Canjear una tarjeta");
        p.setImagenKey("productos/abc.png");
        p.setActivo(true);
        p.setFechaCreacion(LocalDateTime.now());
        p.setCategoria(c);

        cambios.accept(p);
        return p;
    }

    
    // ProductoPublicoResponse esperado (record -> builder) (Inmutables xsd)
    
    public static class PublicoBuilder {
        public Long id;
        public String slug;
        public String sku;
        public String nombre;
        public String descripcion;
        public String plataforma;
        public Producto.TipoProducto tipo;
        public String region;
        public BigDecimal valorNominal;
        public String moneda;
        public BigDecimal precioVenta;
        public String instruccionesCanje;
        public String imagenUrl;
        public Integer stock;

        public ProductoPublicoResponse build() {
            return new ProductoPublicoResponse(id, slug, sku, nombre, descripcion, plataforma, tipo,
                    region, valorNominal, moneda, precioVenta, instruccionesCanje, imagenUrl, stock);
        }
    }

    public static ProductoPublicoResponse esperadoPublico(Producto p, String imagenUrl, int stock) {
        return esperadoPublico(p, imagenUrl, stock, b -> {});
    }

    public static ProductoPublicoResponse esperadoPublico(
            Producto p, String imagenUrl, int stock, Consumer<PublicoBuilder> cambios) {

        PublicoBuilder b = new PublicoBuilder();
        b.id = p.getId();
        b.slug = p.getSlug();
        b.sku = p.getSku();
        b.nombre = p.getNombre();
        b.descripcion = p.getDescripcion();
        b.plataforma = p.getPlataforma();
        b.tipo = p.getTipo();
        b.region = p.getRegion();
        b.valorNominal = p.getValorNominal();
        b.moneda = p.getMoneda();
        b.precioVenta = p.getPrecioVenta();
        b.instruccionesCanje = p.getInstruccionesCanje();
        b.imagenUrl = imagenUrl;
        b.stock = stock;

        cambios.accept(b);
        return b.build();
    }

    
    // ProductoRequest (record -> builder con valores por defecto)
    
    public static class RequestBuilder {
        public String sku = "STEAM-MX-200";
        public String nombre = "Steam Gift Card $200 MXN";
        public String descripcion = "Tarjeta de regalo para la tienda de Steam";
        public String plataforma = "Steam";
        public Producto.TipoProducto tipo = Producto.TipoProducto.GIFT_CARD;
        public String region = "MX";
        public BigDecimal valorNominal = new BigDecimal("200.00");
        public String moneda = "MXN";
        public BigDecimal precioVenta = new BigDecimal("210.00");
        public BigDecimal precioCosto = new BigDecimal("190.00");
        public String instruccionesCanje = "Canjea el código en Steam > Canjear una tarjeta";
        public Boolean activo = true;
        public Long categoriaId = null;   // sin categoria por defecto

        public ProductoRequest build() {
            return new ProductoRequest(sku, nombre, descripcion, plataforma, tipo, region,
                    valorNominal, moneda, precioVenta, precioCosto, instruccionesCanje,
                    activo, categoriaId);
        }
    }

    public static ProductoRequest generarRequestFake() {
        return generarRequestFake(b -> {});
    }

    public static ProductoRequest generarRequestFake(Consumer<RequestBuilder> cambios) {
        RequestBuilder b = new RequestBuilder();
        cambios.accept(b);
        return b.build();
    }

    
    // Proyeccion de stock (contarPorProducto)
    
    public static StockProducto stockFake(Long productoId, Long total) {
        return new StockProducto() {
            public Long getProductoId() { return productoId; }
            public Long getTotal() { return total; }
        };
    }

    
    // Carrito y lineas
    
    public static Carrito generarCarritoFake() {
        return generarCarritoFake(car -> {});
    }


    public static Carrito generarCarritoFake(Consumer<Carrito> cambios) {
        Carrito car = new Carrito();
        car.setId(faker.number().numberBetween(1L, 100_000L));
        car.setCliente(generarClienteFake());
        car.setProductos(new ArrayList<>());   // si queda null, el stream y el add truenan
        car.setFechaCreacion(LocalDateTime.now());

        cambios.accept(car);
        return car;
    }

    // Linea del carrito: la enlaza en ambos sentidos y la agrega al carrito
    public static ProductoEnCarrito agregarItem(Carrito carrito, Producto producto, int cantidad) {
        ProductoEnCarrito item = new ProductoEnCarrito();
        item.setId(faker.number().numberBetween(1L, 100_000L));
        item.setCarrito(carrito);
        item.setProducto(producto);
        item.setCantidad(cantidad);
        carrito.getProductos().add(item);
        return item;
    }

    
    // DTOs de entrada del carrito
    
    public static AgregarCarrito agregarDto(Long idProducto, int cantidad) {
        return new AgregarCarrito(idProducto, cantidad);
    }

    public static ActualizarCantidad actualizarDto(Long idProducto, int cantidad) {
        return new ActualizarCantidad(idProducto, cantidad);
    }

    
    // CarritoResponseDTO esperado (record -> builder)
    
    public static class CarritoBuilder {
        public Long id;
        public List<CarritoProductoDTO> productos;
        public LocalDateTime fechaCreacion;
        public BigDecimal subtotal;

        public CarritoResponseDTO build() {
            return new CarritoResponseDTO(productos, fechaCreacion, subtotal);
        }
    }

    // Linea esperada: sale del producto y la cantidad; la url la decides tu
    public static CarritoProductoDTO esperadaLinea(Producto p, int cantidad, String imagenUrl) {
        return new CarritoProductoDTO(
                p.getId(), p.getSlug(), p.getNombre(), p.getSku(), p.getPlataforma(),
                cantidad, p.getPrecioVenta(), imagenUrl);
    }

    /**
     * Carrito esperado: una linea por cada item del carrito (mismo orden) y
     * subtotal = suma de precioVenta * cantidad.
     *
     * @param urlDe como resolver la url de cada producto (debe coincidir con tus mocks de storageService)
     */
    public static CarritoResponseDTO esperadoCarrito(Carrito car, Function<Producto, String> urlDe) {
        return esperadoCarrito(car, urlDe, b -> {});
    }

    public static CarritoResponseDTO esperadoCarrito(
            Carrito car, Function<Producto, String> urlDe, Consumer<CarritoBuilder> cambios) {

        List<CarritoProductoDTO> lineas = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ProductoEnCarrito cp : car.getProductos()) {
            Producto p = cp.getProducto();
            lineas.add(esperadaLinea(p, cp.getCantidad(), urlDe.apply(p)));
            subtotal = subtotal.add(p.getPrecioVenta().multiply(BigDecimal.valueOf(cp.getCantidad())));
        }

        CarritoBuilder b = new CarritoBuilder();
        b.id = car.getId();
        b.productos = lineas;
        b.fechaCreacion = car.getFechaCreacion();
        b.subtotal = subtotal;

        cambios.accept(b);
        return b.build();
    }

    // CodigoVirtual
    public static CodigoVirtual generarCodigoFake(Producto producto, String texto) {
        return generarCodigoFake(producto, texto, cv -> {});
    }

    public static CodigoVirtual generarCodigoFake(Producto producto, String texto, Consumer<CodigoVirtual> cambios) {
        CodigoVirtual cv = new CodigoVirtual();
        cv.setId(faker.number().numberBetween(1L, 100_000L));
        cv.setProducto(producto);
        cv.setCodigo(texto);
        cv.setEstado(CodigoVirtual.EstadoCodigo.DISPONIBLE);
        cambios.accept(cv);
        return cv;
    }
}