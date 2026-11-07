package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.codigoVirtual.CodigosVirtualesExceptions.CodigosDuplicadosException;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.ecommerce.FerreViky.utils.Fakes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VirtualCodeService - Unit tests")
class VirtualCodeServiceTest {

    @InjectMocks
    private VirtualCodeService virtualCodeService;
    @Mock private CodigoVirtualRepository virtualCodeRepository;
    @Mock private ProductoRepository productRepository;

    @Captor
    private ArgumentCaptor<List<CodigoVirtual>> codesCaptor;


    //  addCodesToProduct
    @Test
    @DisplayName("When adding codes to an existing product, it returns how many were saved")
    void addCodesToProduct_productExists_returnsSavedCount() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        int resultado = virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001", "STEAM-0002"));

        // Assert
        assertEquals(2, resultado);
        verify(virtualCodeRepository).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("When adding codes to a nonexistent product, it throws ProductoNoEncontradoException")
    void addCodesToProduct_productNotFound_throwsProductNotFound() {
        // Arrange
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> virtualCodeService.addCodesToProduct(99L, List.of("STEAM-0001")));
        verify(virtualCodeRepository, never()).saveAllAndFlush(any());
    }


    @Test
    @DisplayName("When receiving only nulls, empty strings or blanks, it throws IllegalArgumentException")
    void addCodesToProduct_onlyBlanksAndNulls_throwsIllegalArgument() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        List<String> basura = Arrays.asList(null, "", "   ");

        // Act + Assert
        assertThrows(IllegalArgumentException.class,
                () -> virtualCodeService.addCodesToProduct(1L, basura));
        verify(virtualCodeRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("When saving, it trims spaces and removes nulls, empty strings and duplicates before calling saveAllAndFlush")
    void addCodesToProduct_dirtyList_savesOnlyCleanAndUniqueCodes() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        List<String> sucia = Arrays.asList(" STEAM-0001 ", "STEAM-0001", "STEAM-0002", null, "");

        // Act
        int resultado = virtualCodeService.addCodesToProduct(1L, sucia);

        // Assert
        verify(virtualCodeRepository).saveAllAndFlush(codesCaptor.capture());
        List<String> textosGuardados = codesCaptor.getValue().stream()
                .map(CodigoVirtual::getCodigo)
                .toList();

        assertEquals(2, resultado);
        assertEquals(List.of("STEAM-0001", "STEAM-0002"), textosGuardados);
    }


    @Test
    @DisplayName("New codes are created as DISPONIBLE and linked to the product")
    void addCodesToProduct_newCodes_areAvailableAndLinkedToProduct() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001", "STEAM-0002"));

        // Assert
        verify(virtualCodeRepository).saveAllAndFlush(codesCaptor.capture());
        List<CodigoVirtual> guardados = codesCaptor.getValue();

        assertEquals(2, guardados.size());
        guardados.forEach(cv -> {
            assertEquals(EstadoCodigo.DISPONIBLE, cv.getEstado());
            assertSame(p, cv.getProducto());
        });
    }

    @Test
    @DisplayName("It first validates that the product exists and then saves the codes")
    void addCodesToProduct_order_looksUpProductBeforeSaving() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001"));

        // Assert
        InOrder inOrder = inOrder(productRepository, virtualCodeRepository);
        inOrder.verify(productRepository).findById(1L);
        inOrder.verify(virtualCodeRepository).saveAllAndFlush(anyList());
    }

    //  addCodeToProduct
    @Test
    @DisplayName("The addCodeToProduct shortcut saves exactly one code")
    void addCodeToProduct_shortcut_savesSingleCode() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        int resultado = virtualCodeService.addCodeToProduct(1L, "STEAM-0001");

        // Assert
        verify(virtualCodeRepository).saveAllAndFlush(codesCaptor.capture());
        assertEquals(1, resultado);
        assertEquals(1, codesCaptor.getValue().size());
        assertEquals("STEAM-0001", codesCaptor.getValue().get(0).getCodigo());
    }


    //  NEW TESTS 

    //  addCodesToProduct (new)
    @Test
    @DisplayName("When some codes already exist, it throws CodigosDuplicadosException and saves nothing")
    void addCodesToProduct_existingCodes_throwsDuplicateCodes() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(virtualCodeRepository.findCodigosExistentes(List.of("STEAM-0001", "STEAM-0002")))
                .thenReturn(List.of("STEAM-0001"));

        // Act + Assert
        assertThrows(CodigosDuplicadosException.class,
                () -> virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001", "STEAM-0002")));
        verify(virtualCodeRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("The duplicate check receives the normalized codes (trimmed and without repeats)")
    void addCodesToProduct_dirtyList_checksDuplicatesWithNormalizedCodes() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        List<String> sucia = Arrays.asList(" STEAM-0001 ", "STEAM-0001", "STEAM-0002", null, "");

        // Act
        virtualCodeService.addCodesToProduct(1L, sucia);

        // Assert
        verify(virtualCodeRepository).findCodigosExistentes(List.of("STEAM-0001", "STEAM-0002"));
    }

    @Test
    @DisplayName("When the database rejects the codes for integrity (concurrent insert), it throws CodigosDuplicadosException")
    void addCodesToProduct_dataIntegrity_throwsDuplicateCodes() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(virtualCodeRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("unique code"));

        // Act + Assert
        assertThrows(CodigosDuplicadosException.class,
                () -> virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001")));
    }

    @Test
    @DisplayName("When there are no valid codes, it throws IllegalArgumentException with a message and does not query duplicates")
    void addCodesToProduct_onlyBlanksAndNulls_doesNotCheckDuplicates() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        List<String> basura = Arrays.asList(null, "", "   ");

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> virtualCodeService.addCodesToProduct(1L, basura));

        // Assert
        assertEquals("No valid codes were provided.", ex.getMessage());
        verify(virtualCodeRepository, never()).findCodigosExistentes(any());
    }

    @Test
    @DisplayName("When the product does not exist, the code repository is not touched at all")
    void addCodesToProduct_productNotFound_doesNotTouchCodeRepository() {
        // Arrange
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductoNoEncontradoException.class,
                () -> virtualCodeService.addCodesToProduct(99L, List.of("STEAM-0001")));
        verifyNoInteractions(virtualCodeRepository);
    }

    @Test
    @DisplayName("The steps run in order: look up product, check duplicates, save codes")
    void addCodesToProduct_order_checksDuplicatesBeforeSaving() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001"));

        // Assert
        InOrder inOrder = inOrder(productRepository, virtualCodeRepository);
        inOrder.verify(productRepository).findById(1L);
        inOrder.verify(virtualCodeRepository).findCodigosExistentes(List.of("STEAM-0001"));
        inOrder.verify(virtualCodeRepository).saveAllAndFlush(anyList());
    }

    //  addCodeToProduct (new)
    @Test
    @DisplayName("When the single code is null, addCodeToProduct throws IllegalArgumentException")
    void addCodeToProduct_nullCode_throwsIllegalArgument() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act + Assert
        assertThrows(IllegalArgumentException.class,
                () -> virtualCodeService.addCodeToProduct(1L, null));
        verify(virtualCodeRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("When the single code already exists, addCodeToProduct throws CodigosDuplicadosException")
    void addCodeToProduct_existingCode_throwsDuplicateCodes() {
        // Arrange
        Producto p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(virtualCodeRepository.findCodigosExistentes(Collections.singletonList("STEAM-0001")))
                .thenReturn(List.of("STEAM-0001"));

        // Act + Assert
        assertThrows(CodigosDuplicadosException.class,
                () -> virtualCodeService.addCodeToProduct(1L, "STEAM-0001"));
    }
}