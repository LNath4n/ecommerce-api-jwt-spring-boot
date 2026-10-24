package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions.ProductoNoEncontradoException;
import com.ecommerce.FerreViky.models.CodigoVirtual;
import com.ecommerce.FerreViky.models.CodigoVirtual.EstadoCodigo;
import com.ecommerce.FerreViky.models.Producto;
import com.ecommerce.FerreViky.repository.CodigoVirtualRepository;
import com.ecommerce.FerreViky.repository.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.ecommerce.FerreViky.utils.Fakes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodigoVirtual Service - Pruebas unitarias")
class CodigoVirtualServiceTest {

    @InjectMocks
    private CodigoVirtualService codigoVirtualService;
    @Mock private CodigoVirtualRepository codigoVirtualRepository;
    @Mock private ProductoRepository productoRepository;

    @Captor
    private ArgumentCaptor<List<CodigoVirtual>> codigosCaptor;


    @Test
    @DisplayName("Al guardar códigos de un producto existente devuelve cuántos se guardaron")
    void guardarCodigos_productoExiste_devuelveCantidadGuardada() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        int resultado = codigoVirtualService.guardarCodigos(1L, List.of("STEAM-0001", "STEAM-0002"));

        // Assert
        assertEquals(2, resultado);
        verify(codigoVirtualRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Al guardar códigos de un producto inexistente lanza ProductoNoEncontradoException")
    void guardarCodigos_productoNoExiste_lanzaProductoNoEncontrado() {
        // Arrange
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> codigoVirtualService.guardarCodigos(99L, List.of("STEAM-0001")));
        verify(codigoVirtualRepository, never()).saveAll(any());
    }


    @Test
    @DisplayName("Al recibir solo nulos, vacíos o espacios lanza IllegalArgumentException")
    void guardarCodigos_soloVaciosYNulos_lanzaIllegalArgument() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));
        List<String> basura = Arrays.asList(null, "", "   ");

        // Act + Assert
        assertThrows(IllegalArgumentException.class,
                () -> codigoVirtualService.guardarCodigos(1L, basura));
        verify(codigoVirtualRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Al guardar limpia espacios, nulos, vacíos y repetidos antes de llamar a saveAll")
    void guardarCodigos_listaSucia_guardaSoloLosLimpiosYUnicos() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));
        List<String> sucia = Arrays.asList(" STEAM-0001 ", "STEAM-0001", "STEAM-0002", null, "");

        // Act
        int resultado = codigoVirtualService.guardarCodigos(1L, sucia);

        // Assert
        verify(codigoVirtualRepository).saveAll(codigosCaptor.capture());
        List<String> textosGuardados = codigosCaptor.getValue().stream()
                .map(CodigoVirtual::getCodigo)
                .toList();

        assertEquals(2, resultado);
        assertEquals(List.of("STEAM-0001", "STEAM-0002"), textosGuardados);
    }


    @Test
    @DisplayName("Los códigos nuevos nacen DISPONIBLE y ligados al producto")
    void guardarCodigos_codigosNuevos_nacenDisponiblesYLigadosAlProducto() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        codigoVirtualService.guardarCodigos(1L, List.of("STEAM-0001", "STEAM-0002"));

        // Assert
        verify(codigoVirtualRepository).saveAll(codigosCaptor.capture());
        List<CodigoVirtual> guardados = codigosCaptor.getValue();

        assertEquals(2, guardados.size());
        guardados.forEach(cv -> {
            assertEquals(EstadoCodigo.DISPONIBLE, cv.getEstado());
            assertSame(p, cv.getProducto());
        });
    }

    @Test
    @DisplayName("Primero valida que el producto exista y después guarda los códigos")
    void guardarCodigos_orden_buscaProductoAntesDeGuardar() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        codigoVirtualService.guardarCodigos(1L, List.of("STEAM-0001"));

        // Assert
        InOrder inOrder = inOrder(productoRepository, codigoVirtualRepository);
        inOrder.verify(productoRepository).findById(1L);
        inOrder.verify(codigoVirtualRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("El atajo guardarCodigo guarda exactamente un código")
    void guardarCodigo_atajo_guardaUnSoloCodigo() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        int resultado = codigoVirtualService.guardarCodigo(1L, "STEAM-0001");

        // Assert
        verify(codigoVirtualRepository).saveAll(codigosCaptor.capture());
        assertEquals(1, resultado);
        assertEquals(1, codigosCaptor.getValue().size());
        assertEquals("STEAM-0001", codigosCaptor.getValue().get(0).getCodigo());
    }
}