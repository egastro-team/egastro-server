package com.egastro.enums;

import com.egastro.exception.ApiException;
import lombok.Getter;
import org.springframework.data.domain.Sort;

import java.util.Arrays;

@Getter
public enum ProductSort {
    NAME_ASC("name_asc", Sort.Order.asc("name").ignoreCase()),
    NAME_DESC("name_desc", Sort.Order.desc("name").ignoreCase()),
    PRICE_ASC("price_asc", Sort.Order.asc("price")),
    PRICE_DESC("price_desc", Sort.Order.desc("price"));

    private final String requestValue;
    private final Sort sort;

    ProductSort(String requestValue, Sort.Order order) {
        this.requestValue = requestValue;
        this.sort = Sort.by(order, Sort.Order.asc("id"));
    }

    public static ProductSort fromParam(String value) {
        if (value == null) {
            return NAME_ASC;
        }
        return Arrays.stream(values())
                .filter(option -> option.requestValue.equals(value))
                .findFirst()
                .orElseThrow(() -> ApiException.validation("sort", "Invalid product sort value"));
    }
}
