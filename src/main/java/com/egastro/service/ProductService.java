package com.egastro.service;

import com.egastro.dto.request.ProductRequest;
import com.egastro.dto.response.PageResponse;
import com.egastro.dto.response.ProductResponse;
import com.egastro.entity.Product;
import com.egastro.enums.ProductSort;
import com.egastro.event.ProductDeletedEvent;
import com.egastro.exception.ApiException;
import com.egastro.mapper.ProductMapper;
import com.egastro.repository.CategoryRepository;
import com.egastro.repository.ProductRepository;
import com.egastro.util.PageParams;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ApplicationEventPublisher eventPublisher;

    public PageResponse<ProductResponse> list(Long categoryId, String query, String sort, int page, int size) {
        String search = query == null ? "" : query.trim();
        if (search.length() > 100) {
            throw ApiException.validation("q", "Search must be at most 100 characters long");
        }
        var pageable = PageParams.of(page, size, ProductSort.fromParam(sort).getSort());
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw ApiException.notFound("Category not found");
        }
        String pattern = "%" + search.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return PageResponse.from(productRepository.findCatalog(categoryId, pattern, pageable)
                .map(productMapper::toResponse));
    }

    public ProductResponse get(Long id) {
        return productMapper.toResponse(requireProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        var product = new Product();
        replaceFields(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = requireProduct(id);
        replaceFields(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        requireProduct(id);
        // Synchronous listeners remove cart references within this transaction before the hard delete.
        eventPublisher.publishEvent(new ProductDeletedEvent(id));
        productRepository.deleteById(id);
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private void replaceFields(Product product, ProductRequest request) {
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> ApiException.validation("categoryId", "Category does not exist"));
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(category);
        product.setPrice(request.price().setScale(2, RoundingMode.UNNECESSARY));
        product.setUnitOfMeasure(request.unitOfMeasure());
        product.setImageUrl(request.imageUrl());
        product.setAvailable(request.available());
    }
}
