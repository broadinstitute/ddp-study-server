package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.json.activity.ActivityInstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.route.GetActivityInstanceStatusTypeListRoute;
import org.broadinstitute.ddp.util.TestRedisConnection;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JbdiActivityInstanceStatusTypeCached extends SQLObjectWrapper<JdbiActivityInstanceStatusType>
        implements JdbiActivityInstanceStatusType {
    private static final Logger LOG = LoggerFactory.getLogger(GetActivityInstanceStatusTypeListRoute.class);
    private static RLocalCachedMap<String, List<ActivityInstanceStatusType>> languageCodeToActivityInstanceStatusType;

    public JbdiActivityInstanceStatusTypeCached(Handle handle) {
        super(handle, JdbiActivityInstanceStatusType.class);
        initializeCache();
    }

    private void initializeCache() {
        if (languageCodeToActivityInstanceStatusType == null) {
            synchronized (JbdiActivityInstanceStatusTypeCached.class) {
                if (languageCodeToActivityInstanceStatusType == null) {
                    languageCodeToActivityInstanceStatusType = CacheService.getInstance()
                            .getOrCreateLocalCache("languageCodeToActivityInstanceStatusType", 50);
                }
            }
        }
    }

    @Override
    public long getStatusTypeId(InstanceStatusType statusType) {
        return delegate.getStatusTypeId(statusType);
    }

    @Override
    public List<ActivityInstanceStatusType> getActivityInstanceStatusTypes(String isoLanguageCode) {
        if (isUsingNullCache()) {
            return delegate.getActivityInstanceStatusTypes(isoLanguageCode);
        } else {
            List<ActivityInstanceStatusType> statusTypes = null;
            try {
                statusTypes = languageCodeToActivityInstanceStatusType.get(isoLanguageCode);
            } catch (RedisException e) {
                LOG.warn("Failed to retrieve value from Redis cache: " + languageCodeToActivityInstanceStatusType.getName()
                        + " key lookedup: " + isoLanguageCode + " Will try to retrieve from database", e);
                TestRedisConnection.doTest();
            }
            if (statusTypes == null) {
                statusTypes = delegate.getActivityInstanceStatusTypes(isoLanguageCode);
                languageCodeToActivityInstanceStatusType.putAsync(isoLanguageCode, statusTypes);
            }
            return statusTypes;
        }
    }

    private boolean isUsingNullCache() {
        return isNullCache(languageCodeToActivityInstanceStatusType);
    }
}
