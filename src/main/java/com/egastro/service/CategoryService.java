package com.egastro.service;

import com.egastro.dto.request.CategoryRequest;
import com.egastro.dto.response.CategoryResponse;
import com.egastro.entity.Category;
import com.egastro.exception.ApiException;
import com.egastro.mapper.CategoryMapper;
import com.egastro.repository.CategoryRepository;
import com.egastro.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    private static final String DUPLICATE_NAME = "A category with this name already exists";
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    public List<CategoryResponse> list() {
        return categoryRepository.findAll(Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")))
                .stream().map(categoryMapper::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw ApiException.conflict(DUPLICATE_NAME);
        }
        var category = new Category();
        category.setName(request.name());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = requireCategory(id);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw ApiException.conflict(DUPLICATE_NAME);
        }
        category.setName(request.name());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        requireCategory(id);
        if (productRepository.existsByCategoryId(id)) {
            throw ApiException.conflict("Category contains products and cannot be deleted");
        }
        categoryRepository.deleteById(id);
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
    }
}
