package org.broadinstitute.ddp.cache;

import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.spi.CachingProvider;

public class NullCacheManager implements CacheManager {
    private Map<String, Cache> nameToCache = new ConcurrentHashMap<>();

    @Override
    public CachingProvider getCachingProvider() {
        return null;
    }

    @Override
    public URI getURI() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return null;
    }

    @Override
    public Properties getProperties() {
        return null;
    }

    @Override
    public <K, V, C extends Configuration<K, V>> Cache<K, V> createCache(String cacheName, C configuration)
            throws IllegalArgumentException {
        var newCache = new NullCache(cacheName, configuration);
        nameToCache.put(cacheName, newCache);
        return newCache;
    }

    @Override
    public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType) {
        return (Cache<K, V>) nameToCache.get(cacheName);
    }

    @Override
    public <K, V> Cache<K, V> getCache(String cacheName) {
        return (Cache<K, V>) nameToCache.get(cacheName);
    }

    @Override
    public Iterable<String> getCacheNames() {
        return nameToCache.keySet();
    }

    @Override
    public void destroyCache(String cacheName) {

    }

    @Override
    public void enableManagement(String cacheName, boolean enabled) {

    }

    @Override
    public void enableStatistics(String cacheName, boolean enabled) {

    }

    @Override
    public void close() {
        this.nameToCache.values().forEach(cache -> cache.close());
    }

    @Override
    public boolean isClosed() {
        return nameToCache.values().stream().anyMatch(cache -> !cache.isClosed());
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        return (T) this;
    }
}
