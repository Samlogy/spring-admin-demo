package com.example.monolith.product;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory store (no database needed for the example; data is lost on restart). */
@Repository
public class ProductRepository {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public List<Product> findAll() {
        return products.values().stream().sorted(Comparator.comparing(Product::id)).toList();
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id));
    }

    public Product save(Product product) {
        Product saved = product.id() == null ? product.withId(sequence.incrementAndGet()) : product;
        products.put(saved.id(), saved);
        return saved;
    }

    public boolean deleteById(Long id) {
        return products.remove(id) != null;
    }
}
