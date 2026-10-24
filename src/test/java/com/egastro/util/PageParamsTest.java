package com.egastro.util;

import com.egastro.dto.response.PageResponse;
import com.egastro.enums.ProductSort;
import com.egastro.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.domain.Sort;

import static com.egastro.testData.ResponseTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class PageParamsTest {
    @Test
    void shouldAppendId_When_RequestedSortHasNoId() {
        // GIVEN
        var sort = Sort.by("name");

        // WHEN
        var result = PageParams.of(0, 20, sort);

        // THEN
        assertEquals(Sort.by("name").and(Sort.by("id")), result.getSort());
    }

    @Test
    void shouldMoveIdToEndAndPreserveDirection_When_IdAlreadyInSort() {
        // GIVEN
        var sort = Sort.by(Sort.Order.desc("id"), Sort.Order.asc("name"));

        // WHEN
        var result = PageParams.of(0, 20, sort);

        // THEN
        assertEquals(Sort.by(Sort.Order.asc("name"), Sort.Order.desc("id")), result.getSort());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#paginationBounds")
    void shouldClampPagination_When_ParametersOutsideBounds(int page, int size, int expectedPage, int expectedSize) {
        // GIVEN
        var sort = Sort.by("id");

        // WHEN
        var result = PageParams.of(page, size, sort);

        // THEN
        assertEquals(expectedPage, result.getPageNumber());
        assertEquals(expectedSize, result.getPageSize());
        assertEquals(sort, result.getSort());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#productSorts")
    void shouldParseSortAndEndWithId_When_KnownSortRequested(ProductSort sort) {
        // GIVEN
        String parameter = sort.getRequestValue();

        // WHEN
        var result = ProductSort.fromParam(parameter);

        // THEN
        assertSame(sort, result);
        assertEquals("id", result.getSort().toList().getLast().getProperty());
        assertEquals(Sort.Direction.ASC, result.getSort().toList().getLast().getDirection());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#unsupportedSortValues")
    void shouldRejectSort_When_RequestValueNotSupported(String value) {
        // GIVEN
        String parameter = value;

        // WHEN
        var error = assertThrows(ApiException.class, () -> ProductSort.fromParam(parameter));

        // THEN
        assertEquals("Validation failed", error.getMessage());
        assertEquals("sort", error.getFieldErrors().getFirst().field());
    }

    @Test
    void shouldUseNameAscending_When_SortOmitted() {
        // GIVEN
        String parameter = null;

        // WHEN
        var result = ProductSort.fromParam(parameter);

        // THEN
        assertSame(ProductSort.NAME_ASC, result);
    }

    @Test
    void shouldPreservePageMetadata_When_CreatingPageResponse() {
        // GIVEN
        var page = textPage();

        // WHEN
        var result = PageResponse.from(page);

        // THEN
        assertEquals(textPageResponse(), result);
    }
}
