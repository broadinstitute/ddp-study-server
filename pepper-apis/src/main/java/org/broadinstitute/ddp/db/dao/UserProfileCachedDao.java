package org.broadinstitute.ddp.db.dao;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.Optional;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheManagerFactory;
import org.broadinstitute.ddp.cache.SingleItemCacheLoader;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;

public class CachedUserProfileDao implements UserProfileDao {
    private static final String CACHE_NAME = "UserProfile.byGuid";

    private final UserProfileDao target;
    private static Cache<String, UserProfile> userProfileCache;

    private void initCache() {
        if (userProfileCache == null) {
            CacheManager cacheManager = CacheManagerFactory.getCacheManager();
            synchronized (CachedUserProfileDao.class) {
                userProfileCache = cacheManager.getCache(CACHE_NAME);
                if (userProfileCache == null) {
                    SingleItemCacheLoader<String, UserProfile> loader = (guid) -> {
                        var optProfile = target.findProfileByUserGuid(guid);
                        return optProfile.isPresent() ? optProfile.get() : null;
                    };

                    var guidToProfileConfig = new MutableConfiguration<String, UserProfile>()
                            .setReadThrough(true)
                            .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(MINUTES, 10)))
                            .setStatisticsEnabled(true)
                            .setCacheLoaderFactory(
                                    new FactoryBuilder.SingletonFactory<>(loader));

                    userProfileCache = cacheManager.createCache(CACHE_NAME, guidToProfileConfig);
                }
            }
        }
    }

    public CachedUserProfileDao(Handle handle) {
        this.target = handle.attach(UserProfileDao.class);
        initCache();
    }


    @Override
    public UserProfileSql getUserProfileSql() {
        return target.getUserProfileSql();
    }

    @Override
    public void createProfile(UserProfile profile) {
        this.target.createProfile(profile);
    }

    @Override
    public UserProfile updateProfile(UserProfile profile) {
        Optional<User> user = getHandle().attach(UserDao.class).findUserById(profile.getUserId());
        return user.isPresent() ? userProfileCache.getAndReplace(user.get().getGuid(), profile) : null;
    }

    @Override
    public Optional<UserProfile> findProfileByUserId(long userId) {
        Optional<User> user = getHandle().attach(UserDao.class).findUserById(userId);
        return user.isPresent() ? Optional.ofNullable(userProfileCache.get(user.get().getGuid())) : Optional.empty();
    }

    @Override
    public Optional<UserProfile> findProfileByUserGuid(String userGuid) {
        return Optional.ofNullable(userProfileCache.get(userGuid));
    }

    @Override
    public Handle getHandle() {
        return target.getHandle();
    }

    @Override
    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
        return target.withHandle(callback);
    }

    @Override
    public <X extends Exception> void useHandle(HandleConsumer<X> consumer) throws X {
        target.useHandle(consumer);
    }
}
