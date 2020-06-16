package org.broadinstitute.ddp.cache;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;


public class CacheService {
    private static CacheService instance;
    private CacheManager cacheManager;
    private Map<ModelChangeType, Collection<String>> modelChangeTypeToCacheName = new ConcurrentHashMap<>();
    private Map<String, IdToCacheKeyMapper<?>> cacheNameToCacheKeyMapper = new ConcurrentHashMap<>();
    private Map<String, IdToCacheKeyCollectionMapper<?>> cacheNameToCacheKeyCollectionMapper = new ConcurrentHashMap<>();

    public static CacheService getInstance() {
        if (instance != null) {
            return instance;
        } else {
            synchronized(CacheService.class) {
                if (instance == null) {
                    instance = new CacheService();
                }
            }
            return instance;
        }
    }

    private CacheService() {
        cacheManager = buildCacheManager();
    }

    private CacheManager buildCacheManager() {
        URL resourceUrl = CacheService.class.getResource("/redisson-jcache.yaml");

        URI redissonConfigUri = null;
        try {
            redissonConfigUri = resourceUrl.toURI();
        } catch (URISyntaxException e) {
            throw new DDPException(e);
        }

        return Caching.getCachingProvider().getCacheManager(redissonConfigUri, null);
    }

    public <K, V> Cache<K, V> getOrCreateCache(String cacheName, Duration entryDuration, IdToCacheKeyMapper<K> mapper, ModelChangeType evictionModelChangeType, Object cacheParentObject) {
        Cache existingCache = cacheManager.getCache(cacheName);
        if (existingCache == null || existingCache.isClosed()) {
            synchronized (cacheParentObject) {
                Cache cache = _getOrCreateCache(cacheName, entryDuration);
                cacheNameToCacheKeyMapper.put(cacheName, mapper);
                updateChangeTypeToCacheName(evictionModelChangeType, cacheName);
                return cache;
            }
        } else {
            return existingCache;
        }
    }

    public <K, V> Cache<K, V> getOrCreateCache(String cacheName, Duration entryDuration, IdToCacheKeyCollectionMapper<K> mapper, ModelChangeType evictionModelChangeType, Object cacheParentObject) {
        Cache existingCache = cacheManager.getCache(cacheName);
        if (existingCache == null || existingCache.isClosed()) {
            synchronized (cacheParentObject) {
                Cache cache = _getOrCreateCache(cacheName, entryDuration);
                cacheNameToCacheKeyCollectionMapper.put(cacheName, mapper);
                updateChangeTypeToCacheName(evictionModelChangeType, cacheName);
                return cache;
            }
        } else {
            return existingCache;
        }
    }

    public <K, V> Cache<K ,V> getOrCreateCache(String cacheName, Duration entryDuration, Object cacheParentObject) {
        synchronized (cacheParentObject) {
            return _getOrCreateCache(cacheName, entryDuration);
        }
    }

    private <K, V> Cache<K ,V> _getOrCreateCache(String cacheName, Duration entryDuration) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null || cache.isClosed()) {
            var cacheConfig = new MutableConfiguration<K, V>()
                    .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(entryDuration))
                    .setStatisticsEnabled(true);
            cache = cacheManager.createCache(cacheName, cacheConfig);
        }
        return cache;
    }

    public void destroyCache(String name) {
        cacheManager.destroyCache(name);
    }
    private void updateChangeTypeToCacheName(ModelChangeType evictionModelChangeType, String cacheName) {
        Collection<String> existingCacheNames = modelChangeTypeToCacheName.get(evictionModelChangeType);
        List<String> newListOfCacheNames;
        if (existingCacheNames == null) {
            newListOfCacheNames = List.of(cacheName);
        } else {
            newListOfCacheNames = new ArrayList<>(existingCacheNames);
            newListOfCacheNames.add(cacheName);
        }
        modelChangeTypeToCacheName.put(evictionModelChangeType, newListOfCacheNames);
    }

    public void modelUpdated(ModelChangeType changeType, Handle handle, long id) {
        findCacheByEventType(changeType).forEach(cache -> {
            IdToCacheKeyMapper<?> mapper = cacheNameToCacheKeyMapper.get(cache.getName());
            if (mapper != null) {
                Object cacheKey = cacheNameToCacheKeyMapper.get(cache.getName()).mapToKey(id, handle);
                cache.remove(cacheKey);
            } else {
                IdToCacheKeyCollectionMapper<?> keyCollectionMapper = cacheNameToCacheKeyCollectionMapper.get(cache.getName());

                if (keyCollectionMapper != null) {
                    Set<?> keys = keyCollectionMapper.mapToKeys(id, handle);
                    cache.removeAll(keys);
                }
            }
        });
    }

    public Collection<Cache> findCacheByEventType(ModelChangeType changeType) {
        return modelChangeTypeToCacheName.getOrDefault(changeType, Collections.EMPTY_LIST);
    }
}
