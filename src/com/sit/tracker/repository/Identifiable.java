package com.sit.tracker.repository;

/**
 * Implemented by every entity that lives in a repository, so the generic
 * {@link Repository} can key objects by their own identifier.
 *
 * @param <K> identifier type (String throughout this application)
 */
public interface Identifiable<K> {
    K getId();
}
