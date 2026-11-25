package com.sit.tracker.repository;

import java.util.List;
import java.util.Optional;

/**
 * Minimal persistence abstraction. The whole application talks to entities
 * through this interface only, so swapping the in-memory demo store for a
 * JDBC/JPA-backed implementation later means writing one new class — no
 * service or UI code changes.
 *
 * @param <K> identifier type
 * @param <T> entity type
 */
public interface Repository<K, T extends Identifiable<K>> {

    /** Insert or update the entity, keyed by {@link Identifiable#getId()}. */
    T save(T entity);

    Optional<T> findById(K id);

    /**
     * Snapshot of all entities in insertion order.
     */
    List<T> findAll();

    boolean deleteById(K id);

    long count();
}
