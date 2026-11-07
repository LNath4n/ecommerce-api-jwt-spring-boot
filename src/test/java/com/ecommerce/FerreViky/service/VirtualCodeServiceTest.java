package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.virtualcode.VirtualCodeExceptions.DuplicateCodesException;
import com.ecommerce.FerreViky.exceptions.product.ProductExceptions.ProductNotFoundException;
import com.ecommerce.FerreViky.models.VirtualCode;
import com.ecommerce.FerreViky.models.VirtualCode.CodeStatus;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository;
import com.ecommerce.FerreViky.repository.ProductRepository;
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
    @Mock private VirtualCodeRepository virtualCodeRepository;
    @Mock private ProductRepository productRepository;

    @Captor
    private ArgumentCaptor<List<VirtualCode>> codesCaptor;


    //  addCodesToProduct
    @Test
    @DisplayName("When adding codes to an existing product, it returns how many were saved")
    void addCodesToProduct_productExists_returnsSavedCount() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        int resultado = virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001", "STEAM-0002"));

        // Assert
        assertEquals(2, resultado);
        verify(virtualCodeRepository).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("When adding codes to a nonexistent product, it throws ProductNotFoundException")
    void addCodesToProduct_productNotFound_throwsProductNotFound() {
        // Arrange
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductNotFoundException.class,
                () -> virtualCodeService.addCodesToProduct(99L, List.of("STEAM-0001")));
        verify(virtualCodeRepository, never()).saveAllAndFlush(any());
    }


    @Test
    @DisplayName("When receiving only nulls, empty strings or blanks, it throws IllegalArgumentException")
    void addCodesToProduct_onlyBlanksAndNulls_throwsIllegalArgument() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
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
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        List<String> sucia = Arrays.asList(" STEAM-0001 ", "STEAM-0001", "STEAM-0002", null, "");

        // Act
        int resultado = virtualCodeService.addCodesToProduct(1L, sucia);

        // Assert
        verify(virtualCodeRepository).saveAllAndFlush(codesCaptor.capture());
        List<String> textosGuardados = codesCaptor.getValue().stream()
                .map(VirtualCode::getCode)
                .toList();

        assertEquals(2, resultado);
        assertEquals(List.of("STEAM-0001", "STEAM-0002"), textosGuardados);
    }


    @Test
    @DisplayName("New codes are created as AVAILABLE and linked to the product")
    void addCodesToProduct_newCodes_areAvailableAndLinkedToProduct() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001", "STEAM-0002"));

        // Assert
        verify(virtualCodeRepository).saveAllAndFlush(codesCaptor.capture());
        List<VirtualCode> guardados = codesCaptor.getValue();

        assertEquals(2, guardados.size());
        guardados.forEach(cv -> {
            assertEquals(CodeStatus.AVAILABLE, cv.getStatus());
            assertSame(p, cv.getProduct());
        });
    }

    @Test
    @DisplayName("It first validates that the product exists and then saves the codes")
    void addCodesToProduct_order_looksUpProductBeforeSaving() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
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
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        int resultado = virtualCodeService.addCodeToProduct(1L, "STEAM-0001");

        // Assert
        verify(virtualCodeRepository).saveAllAndFlush(codesCaptor.capture());
        assertEquals(1, resultado);
        assertEquals(1, codesCaptor.getValue().size());
        assertEquals("STEAM-0001", codesCaptor.getValue().get(0).getCode());
    }


    //  NEW TESTS 

    //  addCodesToProduct (new)
    @Test
    @DisplayName("When some codes already exist, it throws DuplicateCodesException and saves nothing")
    void addCodesToProduct_existingCodes_throwsDuplicateCodes() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(virtualCodeRepository.findExistingCodes(List.of("STEAM-0001", "STEAM-0002")))
                .thenReturn(List.of("STEAM-0001"));

        // Act + Assert
        assertThrows(DuplicateCodesException.class,
                () -> virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001", "STEAM-0002")));
        verify(virtualCodeRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("The duplicate check receives the normalized codes (trimmed and without repeats)")
    void addCodesToProduct_dirtyList_checksDuplicatesWithNormalizedCodes() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        List<String> sucia = Arrays.asList(" STEAM-0001 ", "STEAM-0001", "STEAM-0002", null, "");

        // Act
        virtualCodeService.addCodesToProduct(1L, sucia);

        // Assert
        verify(virtualCodeRepository).findExistingCodes(List.of("STEAM-0001", "STEAM-0002"));
    }

    @Test
    @DisplayName("When the database rejects the codes for integrity (concurrent insert), it throws DuplicateCodesException")
    void addCodesToProduct_dataIntegrity_throwsDuplicateCodes() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(virtualCodeRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("unique code"));

        // Act + Assert
        assertThrows(DuplicateCodesException.class,
                () -> virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001")));
    }

    @Test
    @DisplayName("When there are no valid codes, it throws IllegalArgumentException with a message and does not query duplicates")
    void addCodesToProduct_onlyBlanksAndNulls_doesNotCheckDuplicates() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        List<String> basura = Arrays.asList(null, "", "   ");

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> virtualCodeService.addCodesToProduct(1L, basura));

        // Assert
        assertEquals("No valid codes were provided.", ex.getMessage());
        verify(virtualCodeRepository, never()).findExistingCodes(any());
    }

    @Test
    @DisplayName("When the product does not exist, the code repository is not touched at all")
    void addCodesToProduct_productNotFound_doesNotTouchCodeRepository() {
        // Arrange
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ProductNotFoundException.class,
                () -> virtualCodeService.addCodesToProduct(99L, List.of("STEAM-0001")));
        verifyNoInteractions(virtualCodeRepository);
    }

    @Test
    @DisplayName("The steps run in order: look up product, check duplicates, save codes")
    void addCodesToProduct_order_checksDuplicatesBeforeSaving() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        virtualCodeService.addCodesToProduct(1L, List.of("STEAM-0001"));

        // Assert
        InOrder inOrder = inOrder(productRepository, virtualCodeRepository);
        inOrder.verify(productRepository).findById(1L);
        inOrder.verify(virtualCodeRepository).findExistingCodes(List.of("STEAM-0001"));
        inOrder.verify(virtualCodeRepository).saveAllAndFlush(anyList());
    }

    //  addCodeToProduct (new)
    @Test
    @DisplayName("When the single code is null, addCodeToProduct throws IllegalArgumentException")
    void addCodeToProduct_nullCode_throwsIllegalArgument() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act + Assert
        assertThrows(IllegalArgumentException.class,
                () -> virtualCodeService.addCodeToProduct(1L, null));
        verify(virtualCodeRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("When the single code already exists, addCodeToProduct throws DuplicateCodesException")
    void addCodeToProduct_existingCode_throwsDuplicateCodes() {
        // Arrange
        Product p = generarProductoFake(prod -> prod.setId(1L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(virtualCodeRepository.findExistingCodes(Collections.singletonList("STEAM-0001")))
                .thenReturn(List.of("STEAM-0001"));

        // Act + Assert
        assertThrows(DuplicateCodesException.class,
                () -> virtualCodeService.addCodeToProduct(1L, "STEAM-0001"));
    }
}