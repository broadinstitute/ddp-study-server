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

import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigManager;
import org.jdbi.v3.core.Handle;


public class CacheService {
    private static volatile CacheService INSTANCE;
    private CacheManager cacheManager;
    private Map<ModelChangeType, Collection<String>> modelChangeTypeToCacheName = new ConcurrentHashMap<>();
    private Map<String, IdToCacheKeyMapper<?>> cacheNameToCacheKeyMapper = new ConcurrentHashMap<>();
    private Map<String, IdToCacheKeyCollectionMapper<?>> cacheNameToCacheKeyCollectionMapper = new ConcurrentHashMap<>();

    public static CacheService getInstance() {
        if (INSTANCE != null) {
            return INSTANCE;
        } else {
            synchronized (CacheService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CacheService();
                }
            }
            return INSTANCE;
        }
    }

    private CacheService() {
        String configFileName = ConfigManager.getInstance().getConfig().getString(ConfigFile.JCACHE_CONFIGURATION_FILE);
        cacheManager = buildCacheManager(configFileName);
    }

    private CacheManager buildCacheManager(String configFileName) {
        URL resourceUrl = CacheService.class.getResource(configFileName);
        if (resourceUrl == null) {
            throw new DDPException("Could not find JCache config file with name:" + configFileName);
        }

        URI redissonConfigUri;
        try {
            redissonConfigUri = resourceUrl.toURI();
        } catch (URISyntaxException e) {
            throw new DDPException(e);
        }

        return Caching.getCachingProvider().getCacheManager(redissonConfigUri, null);
    }

    public <K, V> Cache<K, V> getOrCreateCache(String cacheName, Duration entryDuration, IdToCacheKeyMapper<K> mapper,
                                               ModelChangeType evictionModelChangeType, Object cacheParentObject) {
        return _getOrCreateCache(cacheName, entryDuration, mapper, evictionModelChangeType, cacheParentObject);
    }

    public <K, V> Cache<K, V> getOrCreateCache(String cacheName, Duration entryDuration, IdToCacheKeyCollectionMapper<K> mapper,
                                               ModelChangeType evictionModelChangeType, Object cacheParentObject) {
        return _getOrCreateCache(cacheName, entryDuration, mapper, evictionModelChangeType, cacheParentObject);
    }

    public <K, V> Cache<K, V> getOrCreateCache(String cacheName, Duration entryDuration, Object cacheParentObject) {
        synchronized (cacheParentObject) {
            return _getOrCreateCache(cacheName, entryDuration);
        }
    }

    private <K, V> Cache<K, V> _getOrCreateCache(String cacheName, Duration entryDuration, Object mapper,
                                                 ModelChangeType evictionModelChangeType,
                                                 Object cacheParentObject) {
        Cache existingCache = cacheManager.getCache(cacheName);
        if (existingCache == null || existingCache.isClosed()) {
            synchronized (cacheParentObject) {
                Cache<K, V> cache = _getOrCreateCache(cacheName, entryDuration);
                if (mapper instanceof IdToCacheKeyCollectionMapper) {
                    cacheNameToCacheKeyCollectionMapper.put(cacheName, (IdToCacheKeyCollectionMapper) mapper);
                } else if (mapper instanceof IdToCacheKeyMapper) {
                    cacheNameToCacheKeyMapper.put(cacheName, (IdToCacheKeyMapper) mapper);
                } else {
                    throw new DDPException("Unrecognized mapper type");
                }
                updateChangeTypeToCacheName(evictionModelChangeType, cacheName);
                return cache;
            }
        } else {
            return (Cache<K, V>) existingCache;
        }
    }

    private <K, V> Cache<K, V> _getOrCreateCache(String cacheName, Duration entryDuration) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null || cache.isClosed()) {
            var cacheConfig = new MutableConfiguration<K, V>()
                    .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(entryDuration))
                    .setStatisticsEnabled(true);
            cache = cacheManager.createCache(cacheName, cacheConfig);
        }
        return (Cache<K, V>) cache;
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
        findCacheNameByEventType(changeType).forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                IdToCacheKeyMapper<?> mapper = cacheNameToCacheKeyMapper.get(cacheName);
                if (mapper != null) {
                    Object cacheKey = cacheNameToCacheKeyMapper.get(cacheName).mapToKey(id, handle);
                    if (cacheKey != null) {
                        cache.remove(cacheKey);
                    }
                } else {
                    IdToCacheKeyCollectionMapper<?> keyCollectionMapper = cacheNameToCacheKeyCollectionMapper.get(cacheName);

                    if (keyCollectionMapper != null) {
                        Set<?> keys = keyCollectionMapper.mapToKeys(id, handle);
                        if (keys != null) {
                            cache.removeAll(keys);
                        }
                    }
                }
            }
        });
    }

    private Collection<String> findCacheNameByEventType(ModelChangeType changeType) {
        return modelChangeTypeToCacheName.getOrDefault(changeType, Collections.emptyList());
    }
}
