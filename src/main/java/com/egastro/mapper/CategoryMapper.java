package com.egastro.mapper;

import com.egastro.dto.response.CategoryResponse;
import com.egastro.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {
    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
