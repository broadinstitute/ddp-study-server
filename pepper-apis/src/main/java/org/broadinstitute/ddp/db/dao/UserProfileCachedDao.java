package org.broadinstitute.ddp.db.dao;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.Optional;
import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.core.Handle;

public class UserProfileCachedDao extends SQLObjectWrapper<UserProfileDao> implements UserProfileDao {
    private static final String CACHE_NAME = "UserProfile.byGuid";

    private static Cache<String, UserProfile> userProfileCache;

    private void initCache() {
        if (userProfileCache == null || userProfileCache.isClosed()) {
            userProfileCache = CacheService.getInstance().getOrCreateCache(CACHE_NAME,
                    new Duration(MINUTES, 10), UserProfileCachedDao.class);
        }
    }

    public UserProfileCachedDao(Handle handle) {
        super(handle, UserProfileDao.class);
        //  initCache();
    }

    @Override
    public UserProfileSql getUserProfileSql() {
        return new UserProfileCachedSql(getHandle());
    }

    @Override
    public void createProfile(UserProfile profile) {
        this.target.createProfile(profile);
        notifyModelUpdated(ModelChangeType.USER, profile.getUserId());
    }

    @Override
    public UserProfile updateProfile(UserProfile profile) {
        UserProfile updatedProfile = this.target.updateProfile(profile);
        notifyModelUpdated(ModelChangeType.USER, profile.getUserId());
        return updatedProfile;
    }

    @Override
    public Optional<UserProfile> findProfileByUserId(long userId) {
        return target.findProfileByUserId(userId);
    }

    @Override
    public Optional<UserProfile> findProfileByUserGuid(String userGuid) {
        return target.findProfileByUserGuid(userGuid);
        //        var profile =  userProfileCache.get(userGuid);
        //        if (profile == null) {
        //            var optionalProfile = getHandle().attach(UserProfileDao.class).findProfileByUserGuid(userGuid);
        //            if (optionalProfile.isPresent()) {
        //                userProfileCache.put(userGuid, profile);
        //            }
        //        }
        //        return Optional.ofNullable(profile);
    }
}
