package com.egastro.mapper;

import com.egastro.dto.response.ProductResponse;
import com.egastro.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    public ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getUnitOfMeasure(), product.getImageUrl(), product.isAvailable(),
                product.getCategory().getId(), product.getCategory().getName());
    }
}
