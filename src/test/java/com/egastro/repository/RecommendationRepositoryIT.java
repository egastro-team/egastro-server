package com.egastro.repository;

import com.egastro.entity.Product;
import com.egastro.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import java.util.*;
import static com.egastro.testData.RecommendationTestData.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=update")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "EGASTRO_POSTGRES_TESTS", matches = "true")
class RecommendationRepositoryIT {
    @Autowired private TestEntityManager em;
    @Autowired private OrderItemRepository orders;
    @Autowired private ProductRepository products;

    @Test
    void shouldAggregateQuantityAndFillNewest_When_UsingPostgres() {
        // GIVEN
        var excluded = new HashSet<>(products.findAll().stream().map(Product::getId).toList());
        excluded.add(-1L);
        var category = em.persist(newCategory());
        var user = em.persist(newUser());
        var created = new ArrayList<Product>();
        for (int index = 0; index < 7; index++) {
            var product = product(index, index != 4);
            product.setId(null);
            product.setCategory(category);
            created.add(em.persist(product));
        }
        var ids = created.stream().map(Product::getId).toList();
        excluded.add(ids.get(3)); // In the cart.
        em.persist(newOrder(user, OrderStatus.NEW, Map.of(ids.get(0), 2, ids.get(1), 4,
                ids.get(2), 4, ids.get(3), 99, ids.get(4), 99, Long.MAX_VALUE, 99)));
        em.persist(newOrder(user, OrderStatus.CONFIRMED, Map.of(ids.get(0), 3)));
        em.persist(newOrder(user, OrderStatus.COMPLETED, Map.of(ids.get(0), 1)));
        em.persist(newOrder(user, OrderStatus.CANCELLED, Map.of(ids.get(1), 99)));
        em.flush();
        em.clear();
        // WHEN
        var popular = orders.findPopularProductIds(excluded, PageRequest.of(0, 3));
        var fillExcluded = new HashSet<>(excluded);
        fillExcluded.addAll(popular);
        var fill = products.findAvailableIdsExcluding(fillExcluded, PageRequest.of(0, 2));
        var loaded = products.findAllByIdIn(popular);
        // THEN
        assertEquals(ids.subList(0, 3), popular);
        assertEquals(List.of(ids.get(6), ids.get(5)), fill);
        assertEquals(List.of(ids.get(0)), orders.findPopularProductIds(excluded, PageRequest.of(0, 1)));
        assertEquals(3, loaded.size());
        assertTrue(loaded.stream().allMatch(product -> em.getEntityManager().getEntityManagerFactory()
                .getPersistenceUnitUtil().isLoaded(product, "category")));
    }
}
