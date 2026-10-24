package com.egastro.repository;

import com.egastro.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = "category")
    List<Product> findAllByIdIn(Collection<Long> ids);

    @Query("select p.id from Product p where p.available = true and p.id not in :excluded order by p.id desc")
    List<Long> findAvailableIdsExcluding(@Param("excluded") Collection<Long> excluded, Pageable pageable);

    boolean existsByCategoryId(Long categoryId);

    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Long id);

    @EntityGraph(attributePaths = "category")
    @Query(value = """
            select p from Product p
            where (:categoryId is null or p.category.id = :categoryId)
              and lower(p.name) like :pattern escape '\\'
            """, countQuery = """
            select count(p) from Product p
            where (:categoryId is null or p.category.id = :categoryId)
              and lower(p.name) like :pattern escape '\\'
            """)
    Page<Product> findCatalog(@Param("categoryId") Long categoryId,
                              @Param("pattern") String pattern, Pageable pageable);
}
