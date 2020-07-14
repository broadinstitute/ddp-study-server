package org.broadinstitute.ddp.cache;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.redisson.Redisson;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;
import org.redisson.codec.FstCodec;
import org.redisson.config.Config;
import org.redisson.jcache.JCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CacheService {
    private static final Logger LOG = LoggerFactory.getLogger(CacheService.class);
    private static final String LOCAL_CACHE_PREFIX = "LOCAL_CACHE:";
    private static volatile CacheService INSTANCE;
    RedissonClient redissonClient;
    private CacheManager cacheManager;
    private Map<ModelChangeType, Collection<String>> modelChangeTypeToCacheName = new ConcurrentHashMap<>();
    private Map<String, IdToCacheKeyMapper<?>> cacheNameToCacheKeyMapper = new ConcurrentHashMap<>();
    private Map<String, IdToCacheKeyCollectionMapper<?>> cacheNameToCacheKeyCollectionMapper = new ConcurrentHashMap<>();
    private Set<String> clearAllOnChangeCacheNames = ConcurrentHashMap.newKeySet();
    private boolean resetCaches = false;

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
        boolean configFileSet = ConfigManager.getInstance().getConfig().hasPath(ConfigFile.JCACHE_CONFIGURATION_FILE);
        if (configFileSet) {
            String configFileName = ConfigManager.getInstance().getConfig().getString(ConfigFile.JCACHE_CONFIGURATION_FILE);
            Path redissonConfigPath = Paths.get(configFileName);
            if (!redissonConfigPath.toFile().exists()) {
                throw new DDPException("Path for configuration file: " + redissonConfigPath + " could not be found");
            }
            URI redissonConfigPathUri = redissonConfigPath.toUri();
            cacheManager = buildCacheManager(redissonConfigPathUri);
            String redisAddress = ConfigManager.getInstance().getConfig().getString(ConfigFile.REDIS_SERVER_ADDRESS);
            Config redissonConfig = new Config();
            redissonConfig.useSingleServer()
                    .setTimeout(1000000)
                    .setAddress(redisAddress);
            redissonClient = Redisson.create(redissonConfig);
        } else {
            LOG.warn("Configuration file not set: " + ConfigFile.JCACHE_CONFIGURATION_FILE + "JCache is not enabled");
            cacheManager = new NullCacheManager();
        }
    }

    private CacheManager buildCacheManager(URI redissonConfigUri) {

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

    public <K, V> Cache<K, V> getOrCreateCache(String cacheName, Duration entryDuration, ModelChangeType evictionModelChangeType,
                                               Object cacheParentObject) {
        return _getOrCreateCache(cacheName, entryDuration, null, evictionModelChangeType, cacheParentObject);
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
                } else if (mapper == null) {
                    clearAllOnChangeCacheNames.add(cacheName);
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
            if (resetCaches) {
                cache.clear();
            }
        }
        return (Cache<K, V>) cache;
    }

    // @TODO implement resetting of cache based on notifications
    public <K, V> RLocalCachedMap<K, V> getOrCreateLocalCache(String name, int size) {
        if (cacheManager instanceof NullCacheManager) {
            return null;
        } else {
            LocalCachedMapOptions cacheOptions = LocalCachedMapOptions.defaults()
                    .evictionPolicy(LocalCachedMapOptions.EvictionPolicy.LRU)
                    .syncStrategy(LocalCachedMapOptions.SyncStrategy.UPDATE)
                    .cacheSize(size);
            return redissonClient.getLocalCachedMap(LOCAL_CACHE_PREFIX + name, new FstCodec(), cacheOptions);
        }
    }

    public void resetAllCaches() {
        if (cacheManager instanceof JCacheManager) {
            cacheManager.getCacheNames().forEach(cacheName -> cacheManager.getCache(cacheName).clear());
        }
        if (!(cacheManager instanceof NullCacheManager)) {
            redissonClient.getKeys().getKeysByPattern(LOCAL_CACHE_PREFIX + "*").forEach(cacheKey ->
                    redissonClient.getLocalCachedMap(cacheKey, new FstCodec(), LocalCachedMapOptions.defaults()).delete());
        }
        resetCaches = true;
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
                if (clearAllOnChangeCacheNames.contains(cacheName)) {
                    cache.removeAll();
                }
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

    public static void main(String[] args) {
        if (args.length > 0 && args[0].startsWith("--clearcache")) {
            LOG.warn("Clearing all caches");
        }
        CacheService.getInstance().resetAllCaches();
    }
}
