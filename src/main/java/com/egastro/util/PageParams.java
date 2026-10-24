package com.egastro.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageParams {
    private PageParams() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        Sort deterministicSort = Sort.by(sort.stream()
                .filter(order -> !order.getProperty().equals("id")).toList());
        Sort.Order idOrder = sort.getOrderFor("id");
        deterministicSort = deterministicSort.and(Sort.by(idOrder == null ? Sort.Order.asc("id") : idOrder));
        return PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100), deterministicSort);
    }
}
