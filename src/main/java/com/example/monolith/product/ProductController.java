package com.example.monolith.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/** Example CRUD API: /api/products. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Product> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        validate(product);
        Product created = repository.save(product.withId(null));
        log.info("Product created: {}", created);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @RequestBody Product product) {
        validate(product);
        repository.findById(id).orElseThrow(() -> notFound(id));
        Product updated = repository.save(product.withId(id));
        log.info("Product updated: {}", updated);
        return updated;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.deleteById(id)) {
            throw notFound(id);
        }
        log.info("Product deleted: {}", id);
        return ResponseEntity.noContent().build();
    }

    private static void validate(Product product) {
        if (product.name() == null || product.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (product.price() == null || product.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price must be >= 0");
        }
    }

    private static ResponseStatusException notFound(Long id) {
        log.debug("Product {} not found", id);
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product " + id + " not found");
    }
}
