package com.islandwarfare.storage;

/**
 * Abstraction over the persistence backend so YAML can be swapped for
 * SQLite (or any other backend) later without touching the rest of the
 * plugin. See {@link YamlStorageService} for the default implementation.
 */
public interface StorageService {

    /** Loads all clans, islands and raid phase state into their managers. */
    void loadAll();

    /** Persists all clans, islands and raid phase state to disk. */
    void saveAll();

    void init();

    void shutdown();
}
