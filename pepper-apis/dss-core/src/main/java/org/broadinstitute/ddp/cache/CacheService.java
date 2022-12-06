package org.broadinstitute.ddp.cache;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ApplicationProperty;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.redisson.Redisson;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;
import org.redisson.codec.FstCodec;
import org.redisson.config.Config;
import org.redisson.jcache.JCacheManager;
import org.redisson.jcache.configuration.RedissonConfiguration;

@Slf4j
public class CacheService {
    private static final String LOCAL_CACHE_PREFIX = "LOCAL_CACHE:";
    private static volatile CacheService INSTANCE;

    // Redisson config details
    private RedissonClient redissonClient;
    private Config redissonConfig;

    private CacheManager cacheManager;
    private Map<ModelChangeType, Collection<String>> modelChangeTypeToCacheName = new ConcurrentHashMap<>();
    private Map<String, IdToCacheKeyMapper<?>> cacheNameToCacheKeyMapper = new ConcurrentHashMap<>();
    private Map<String, IdToCacheKeyCollectionMapper<?>> cacheNameToCacheKeyCollectionMapper = new ConcurrentHashMap<>();
    private Set<String> clearAllOnChangeCacheNames = ConcurrentHashMap.newKeySet();
    private boolean resetCaches = false;

    private static boolean getCachingDisabled() {
        return Optional.ofNullable(System.getProperty(ApplicationProperty.JCACHE_DISABLED.getPropertyName()))
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private static boolean useJcacheConfig() {
        final var applicationConfig = ConfigManager.getInstance().getConfig();
        assert applicationConfig != null;

        if (applicationConfig.hasPath(ConfigFile.JCACHE_CONFIGURATION_FILE)) {
            return true;
        } else {
            return false;
        }
    }

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

    /** 
     * Create the cache service using either the configuration located at the `redisson` key
     * in the application configuration, or the on-disk configuration file identified by the
     * path in the `jcacheConfigurationFile` in the application configuration.
     * 
     * <p>If present, the URI specified by `jcacheConfigurationFile` will be the preferred
     * means of configuration.
     */
    private CacheService() {
        if (useJcacheConfig()) {
            initFromJcacheConfig();
        } else {
            initFromApplicationConfig();
        }
    }

    private void initFromJcacheConfig() {
        final var dssConfig = ConfigManager.getInstance().getConfig();
        assert dssConfig != null;

        if (getCachingDisabled() == true) {
            log.info("CacheService is disabled.");
            cacheManager = new NullCacheManager();
            return;
        }

        if (!dssConfig.hasPath(ConfigFile.JCACHE_CONFIGURATION_FILE)) {
            log.warn("Configuration file not set for key {}. Redisson JCache is disabled.",
                    ConfigFile.JCACHE_CONFIGURATION_FILE);
            cacheManager = new NullCacheManager();
            return;
        }

        final var redissonConfigFileName = dssConfig.getString(ConfigFile.JCACHE_CONFIGURATION_FILE);
        final var redissonConfigPath = Paths.get(redissonConfigFileName).normalize().toAbsolutePath();

        if (!Files.exists(redissonConfigPath)) {
            throw new DDPException("Path for configuration file: " + redissonConfigPath + " could not be found");
        }
        
        final Config redissonConfig;
        try {
            redissonConfig = Config.fromYAML(redissonConfigPath.toFile());
        } catch (IOException ioe) {
            throw new DDPException("failed to load the JCache config from the application configuration.", ioe);
        }
    
        this.redissonConfig = redissonConfig;
        this.redissonClient = Redisson.create(redissonConfig);
        this.cacheManager = buildCacheManager(redissonConfigPath.toUri());
    }

    private void initFromApplicationConfig() {
        final var dssConfig = ConfigManager.getInstance().getConfig();
        assert dssConfig != null;

        if (getCachingDisabled() == true) {
            log.info("CacheService is disabled.");
            this.cacheManager = new NullCacheManager();
            return;
        }

        if (!dssConfig.hasPath(ConfigFile.REDISSON_CONFIG)) {
            log.warn("Configuration file not set: " + ConfigFile.REDISSON_CONFIG + "JCache is not enabled");
            this.cacheManager = new NullCacheManager();
            return;
        }

        final var dssRedissonConfig = dssConfig.getConfig(ConfigFile.REDISSON_CONFIG);

        if (!dssRedissonConfig.hasPath(ConfigFile.Redisson.JCACHE_CONFIG)) {
            log.warn("A redisson config was not found at keypath '{}.{}'. See org.redisson.config.Config for the expected format",
                    ConfigFile.REDISSON_CONFIG,
                    ConfigFile.Redisson.JCACHE_CONFIG);
            this.cacheManager = new NullCacheManager();
            return;
        }

        final var jcacheConfig = dssRedissonConfig.getConfig(ConfigFile.Redisson.JCACHE_CONFIG);
        
        /*
         * The mixing of `toJson` and `fromYAML` is intentional. `org.redisson.config.Config.fromJSON` is deprecated
         * and YAML is a superset of JSON (any valid JSON is also a valid YAML document).
         */
        final Config redissonConfig;
        try {
            redissonConfig = Config.fromYAML(ConfigUtil.toJson(jcacheConfig));
        } catch (IOException ioe) {
            throw new DDPException("failed to load the JCache config from the application configuration.", ioe);
        }

        final var redisson = (Redisson) Redisson.create(redissonConfig);

        this.redissonConfig = redissonConfig;
        this.redissonClient = redisson;
        this.cacheManager = Caching.getCachingProvider().getCacheManager();
    }

    private CacheManager buildCacheManager(URI redissonConfigUri) {
        return Caching.getCachingProvider().getCacheManager(redissonConfigUri, null);
    }

    private <K, V> Configuration<K, V> getCacheConfiguration(Duration entryLifetime) {
        final var jcacheConfig = new MutableConfiguration<K, V>()
                .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(entryLifetime))
                .setStatisticsEnabled(true);
        return RedissonConfiguration.fromConfig(this.redissonConfig, jcacheConfig);
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
        Cache<K, V> existingCache = cacheManager.getCache(cacheName);
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
        var cache = cacheManager.<K, V>getCache(cacheName);
        if (cache == null || cache.isClosed()) {
            final var cacheConfig = this.<K, V>getCacheConfiguration(entryDuration);
            cache = cacheManager.createCache(cacheName, cacheConfig);
            if (resetCaches) {
                cache.clear();
                log.info("Cleared redis cache {}", cacheName);
            }
        }
        return cache;
    }

    // @TODO implement resetting of cache based on notifications
    public <K, V> RLocalCachedMap<K, V> getOrCreateLocalCache(String name, int size) {
        if (cacheManager instanceof NullCacheManager) {
            return null;
        } else {
            final var cacheOptions = LocalCachedMapOptions.<K, V>defaults()
                    .evictionPolicy(LocalCachedMapOptions.EvictionPolicy.LRU)
                    .syncStrategy(LocalCachedMapOptions.SyncStrategy.UPDATE)
                    .cacheSize(size);
            return redissonClient.getLocalCachedMap(LOCAL_CACHE_PREFIX + name, new FstCodec(), cacheOptions);
        }
    }

    public void resetAllCaches() {
        if (cacheManager instanceof NullCacheManager) {
            resetCaches = true;
            return;
        }
        
        if (cacheManager instanceof JCacheManager) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                cacheManager.getCache(cacheName).clear();
                log.info("Cleared redis cache {}", cacheName);
            });
        }
        if (!(cacheManager instanceof NullCacheManager)) {
            redissonClient.getKeys().getKeysByPattern(LOCAL_CACHE_PREFIX + "*").forEach(cacheKey -> {
                redissonClient.getLocalCachedMap(cacheKey, new FstCodec(), LocalCachedMapOptions.defaults()).delete();
                log.info("Cleared local redis cache {}", cacheKey);
            });
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
            var cache = cacheManager.getCache(cacheName);
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
            log.warn("Clearing all caches");
        }
        CacheService.getInstance().resetAllCaches();
    }
}
