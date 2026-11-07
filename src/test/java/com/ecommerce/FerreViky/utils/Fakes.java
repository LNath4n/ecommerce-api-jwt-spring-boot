package com.ecommerce.FerreViky.utils;

import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.UpdateCartItemQuantityRequest;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.AddCartItemRequest;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.CartItemResponse;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.CartResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.PublicProductResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.ProductRequest;
import com.ecommerce.FerreViky.models.*;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository.ProductStock;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Test fixtures located under {@code src/test/java}.
 * Only test data belongs here; do not add mocks or assertions.
 * Each fake accepts a {@code Consumer} to customize only what differs.
 */
public final class Fakes {

    private static final Faker faker = new Faker();

    private Fakes() {}

    
    // Cliente
    
    public static Customer generarClienteFake() {
        return generarClienteFake(cli -> {});
    }

    public static Customer generarClienteFake(Consumer<Customer> cambios) {
        Customer c = new Customer();
        c.setId(faker.number().numberBetween(1L, 100_000L));
        c.setEmail(faker.internet().safeEmailAddress());
        c.setActive(true);
        c.setName("Nathan");
        c.setPassword("UnSaludo");
        c.setPhone("5551234567");

        cambios.accept(c);
        return c;
    }

    
    // Producto
    
    public static Product generarProductoFake() {
        return generarProductoFake(p -> {});
    }

    public static Product generarProductoFake(Consumer<Product> cambios) {
        Product p = new Product();
        Category c = new Category();
        c.setId(faker.number().numberBetween(1L, 100_000L));
        c.setName("VideoJuegos");

        p.setId(faker.number().numberBetween(1L, 100_000L));
        p.setSku("STEAM-MX-200");
        p.setName("Steam Gift Card $200 MXN");
        p.setSlug("steam-gift-card-200-mxn");
        p.setDescription("Tarjeta de regalo para la tienda de Steam");
        p.setPlatform("Steam");
        p.setType(Product.ProductType.GIFT_CARD);
        p.setRegion("MX");
        p.setFaceValue(new BigDecimal("200.00"));
        p.setCurrency("MXN");
        p.setSalePrice(new BigDecimal("210.00"));
        p.setCostPrice(new BigDecimal("190.00"));
        p.setRedemptionInstructions("Canjea el código en Steam > Canjear una tarjeta");
        p.setImageKey("productos/abc.png");
        p.setActive(true);
        p.setCreatedAt(LocalDateTime.now());
        p.setCategory(c);

        cambios.accept(p);
        return p;
    }

    
    // PublicProductResponse esperado (record -> builder) (Inmutables xsd)
    
    public static class PublicoBuilder {
        public Long id;
        public String slug;
        public String sku;
        public String name;
        public String description;
        public String platform;
        public Product.ProductType type;
        public String region;
        public BigDecimal faceValue;
        public String currency;
        public BigDecimal salePrice;
        public String redemptionInstructions;
        public String imageUrl;
        public Integer stock;

        public Long views = 0L;
        public Double averageRating = 0.0;
        public Long totalReviews = 0L;

        public PublicProductResponse build() {
            return new PublicProductResponse(id, slug, sku, name, description, platform, type,
                    region, faceValue, currency, salePrice, redemptionInstructions, imageUrl, stock,
                    views, averageRating, totalReviews);
        }
    }

    public static PublicProductResponse esperadoPublico(Product p, String imageUrl, int stock) {
        return esperadoPublico(p, imageUrl, stock, b -> {});
    }

    public static PublicProductResponse esperadoPublico(
            Product p, String imageUrl, int stock, Consumer<PublicoBuilder> cambios) {

        PublicoBuilder b = new PublicoBuilder();
        b.id = p.getId();
        b.slug = p.getSlug();
        b.sku = p.getSku();
        b.name = p.getName();
        b.description = p.getDescription();
        b.platform = p.getPlatform();
        b.type = p.getType();
        b.region = p.getRegion();
        b.faceValue = p.getFaceValue();
        b.currency = p.getCurrency();
        b.salePrice = p.getSalePrice();
        b.redemptionInstructions = p.getRedemptionInstructions();
        b.imageUrl = imageUrl;
        b.stock = stock;

        cambios.accept(b);
        return b.build();
    }

    
    // ProductRequest (record -> builder con valores por defecto)
    
    public static class RequestBuilder {
        public String sku = "STEAM-MX-200";
        public String name = "Steam Gift Card $200 MXN";
        public String description = "Tarjeta de regalo para la tienda de Steam";
        public String platform = "Steam";
        public Product.ProductType type = Product.ProductType.GIFT_CARD;
        public String region = "MX";
        public BigDecimal faceValue = new BigDecimal("200.00");
        public String currency = "MXN";
        public BigDecimal salePrice = new BigDecimal("210.00");
        public BigDecimal costPrice = new BigDecimal("190.00");
        public String redemptionInstructions = "Canjea el código en Steam > Canjear una tarjeta";
        public Boolean active = true;
        public Long categoryId = null;   // sin categoria por defecto

        public ProductRequest build() {
            return new ProductRequest(sku, name, description, platform, type, region,
                    faceValue, currency, salePrice, costPrice, redemptionInstructions,
                    active, categoryId);
        }
    }

    public static ProductRequest generarRequestFake() {
        return generarRequestFake(b -> {});
    }

    public static ProductRequest generarRequestFake(Consumer<RequestBuilder> cambios) {
        RequestBuilder b = new RequestBuilder();
        cambios.accept(b);
        return b.build();
    }

    
    // Stock projection (findStockByProductIdsAndStatus)
    
    public static ProductStock stockFake(Long productId, Long total) {
        return new ProductStock() {
            public Long getProductId() { return productId; }
            public Long getTotal() { return total; }
        };
    }

    
    // Carrito y lineas
    
    public static Cart generarCarritoFake() {
        return generarCarritoFake(car -> {});
    }


    public static Cart generarCarritoFake(Consumer<Cart> cambios) {
        Cart car = new Cart();
        car.setId(faker.number().numberBetween(1L, 100_000L));
        car.setCustomer(generarClienteFake());
        car.setItems(new ArrayList<>());   // si queda null, el stream y el add truenan
        car.setCreatedAt(LocalDateTime.now());

        cambios.accept(car);
        return car;
    }

    // Linea del carrito: la enlaza en ambos sentidos y la agrega al carrito
    public static ItemInCart agregarItem(Cart cart, Product product, int cantidad) {
        ItemInCart item = new ItemInCart();
        item.setId(faker.number().numberBetween(1L, 100_000L));
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(cantidad);
        cart.getItems().add(item);
        return item;
    }

    
    // DTOs de entrada del carrito
    
    public static AddCartItemRequest agregarDto(Long idProducto, int cantidad) {
        return new AddCartItemRequest(idProducto, cantidad);
    }

    public static UpdateCartItemQuantityRequest actualizarDto(Long idProducto, int cantidad) {
        return new UpdateCartItemQuantityRequest(idProducto, cantidad);
    }

    
    // CartResponse esperado (record -> builder)
    
    public static class CarritoBuilder {
        public Long id;
        public List<CartItemResponse> items;
        public LocalDateTime createdAt;
        public BigDecimal subtotal;

        public CartResponse build() {
            return new CartResponse(items, createdAt, subtotal);
        }
    }

    // Linea esperada: sale del producto y la cantidad; la url la decides tu
    public static CartItemResponse esperadaLinea(Product p, int cantidad, String imageUrl) {
        return new CartItemResponse(
                p.getId(), p.getSlug(), p.getName(), p.getSku(), p.getPlatform(),
                cantidad, p.getSalePrice(), imageUrl);
    }

    /**
     * Builds an expected cart with one line per cart item in the same order and
     * a subtotal equal to the sum of sale price multiplied by quantity.
     *
     * @param urlDe resolves each product URL and must match the storage service mocks
     */
    public static CartResponse esperadoCarrito(Cart car, Function<Product, String> urlDe) {
        return esperadoCarrito(car, urlDe, b -> {});
    }

    public static CartResponse esperadoCarrito(
            Cart car, Function<Product, String> urlDe, Consumer<CarritoBuilder> cambios) {

        List<CartItemResponse> lineas = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemInCart cp : car.getItems()) {
            Product p = cp.getProduct();
            lineas.add(esperadaLinea(p, cp.getQuantity(), urlDe.apply(p)));
            subtotal = subtotal.add(p.getSalePrice().multiply(BigDecimal.valueOf(cp.getQuantity())));
        }

        CarritoBuilder b = new CarritoBuilder();
        b.id = car.getId();
        b.items = lineas;
        b.createdAt = car.getCreatedAt();
        b.subtotal = subtotal;

        cambios.accept(b);
        return b.build();
    }

    // CodigoVirtual
    public static VirtualCode generarCodigoFake(Product product, String texto) {
        return generarCodigoFake(product, texto, cv -> {});
    }

    public static VirtualCode generarCodigoFake(Product product, String texto, Consumer<VirtualCode> cambios) {
        VirtualCode cv = new VirtualCode();
        cv.setId(faker.number().numberBetween(1L, 100_000L));
        cv.setProduct(product);
        cv.setCode(texto);
        cv.setStatus(VirtualCode.CodeStatus.AVAILABLE);
        cambios.accept(cv);
        return cv;
    }
}