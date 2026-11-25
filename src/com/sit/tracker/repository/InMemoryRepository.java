package com.sit.tracker.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe in-memory {@link Repository} used as the demo database.
 * A {@link LinkedHashMap} preserves insertion order, which keeps listings
 * (and the audit trail) stable and readable.
 */
public class InMemoryRepository<K, T extends Identifiable<K>> implements Repository<K, T> {

    private final Map<K, T> storage = new LinkedHashMap<>();

    @Override
    public synchronized T save(T entity) {
        storage.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public synchronized Optional<T> findById(K id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public synchronized List<T> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public synchronized boolean deleteById(K id) {
        return storage.remove(id) != null;
    }

    @Override
    public synchronized long count() {
        return storage.size();
    }
}
