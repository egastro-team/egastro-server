package com.egastro.service;

import com.egastro.dto.request.CategoryRequest;
import com.egastro.entity.Category;
import com.egastro.exception.ApiException;
import com.egastro.mapper.CategoryMapper;
import com.egastro.repository.CategoryRepository;
import com.egastro.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static com.egastro.testData.TestFixtures.*;
import static com.egastro.support.TestAssertions.assertApiError;
import static com.egastro.testData.RequestTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock private CategoryRepository repository;
    @Mock private ProductRepository products;
    @Spy private CategoryMapper mapper;
    @InjectMocks private CategoryService service;

    @Test
    void shouldMapSortedCategories_When_ListRequested() {
        // GIVEN
        var sort = Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id"));
        when(repository.findAll(sort)).thenReturn(List.of(category()));

        // WHEN
        var result = service.list();

        // THEN
        assertEquals("Dairy", result.getFirst().name());
        verify(repository, times(1)).findAll(sort);
    }

    @Test
    void shouldReturnEmptyList_When_NoCategoriesExist() {
        // GIVEN
        when(repository.findAll(any(Sort.class))).thenReturn(List.of());

        // WHEN
        var result = service.list();

        // THEN
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSaveTrimmedName_When_CreatingCategory() {
        // GIVEN
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        var result = service.create(categoryRequest(" Dairy "));

        // THEN
        assertEquals("Dairy", result.name());
        var captor = ArgumentCaptor.forClass(Category.class);
        verify(repository, times(1)).save(captor.capture());
        assertEquals("Dairy", captor.getValue().getName());
    }

    @Test
    void shouldRejectCreate_When_NameDuplicatesExistingCategory() {
        // GIVEN
        when(repository.existsByNameIgnoreCase("Dairy")).thenReturn(true);

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.create(categoryRequest("Dairy")));

        // THEN
        assertApiError(error, CONFLICT, "A category with this name already exists");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldReplaceName_When_UpdateDoesNotConflict() {
        // GIVEN
        var category = category();
        when(repository.findById(2L)).thenReturn(Optional.of(category));
        when(repository.save(category)).thenReturn(category);

        // WHEN
        var result = service.update(2L, categoryRequest("Drinks"));

        // THEN
        assertEquals("Drinks", result.name());
        verify(repository, times(1)).existsByNameIgnoreCaseAndIdNot("Drinks", 2L);
    }

    @Test
    void shouldKeepOriginalName_When_UpdateConflicts() {
        // GIVEN
        var category = category();
        when(repository.findById(2L)).thenReturn(Optional.of(category));
        when(repository.existsByNameIgnoreCaseAndIdNot("Drinks", 2L)).thenReturn(true);

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.update(2L, categoryRequest("Drinks")));

        // THEN
        assertApiError(error, CONFLICT, "A category with this name already exists");
        assertEquals("Dairy", category.getName());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#booleanValues")
    void shouldRejectMissingCategory_When_UpdatingOrDeleting(boolean update) {
        // GIVEN
        when(repository.findById(2L)).thenReturn(Optional.empty());

        // WHEN
        var error = assertThrows(ApiException.class, () -> {
            if (update) service.update(2L, categoryRequest("Dairy"));
            else service.delete(2L);
        });

        // THEN
        assertApiError(error, NOT_FOUND, "Category not found");
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void shouldDeleteCategory_When_NoProductsReferenceIt() {
        // GIVEN
        when(repository.findById(2L)).thenReturn(Optional.of(category()));

        // WHEN
        service.delete(2L);

        // THEN
        verify(repository, times(1)).deleteById(2L);
    }

    @Test
    void shouldRejectDelete_When_CategoryContainsProducts() {
        // GIVEN
        when(repository.findById(2L)).thenReturn(Optional.of(category()));
        when(products.existsByCategoryId(2L)).thenReturn(true);

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.delete(2L));

        // THEN
        assertApiError(error, CONFLICT, "Category contains products and cannot be deleted");
        verify(repository, never()).deleteById(anyLong());
    }
}
