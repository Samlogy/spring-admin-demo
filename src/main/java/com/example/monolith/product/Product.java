package com.example.monolith.product;

import java.math.BigDecimal;

/** Example business entity. */
public record Product(Long id, String name, BigDecimal price) {

    Product withId(Long newId) {
        return new Product(newId, name, price);
    }
}
