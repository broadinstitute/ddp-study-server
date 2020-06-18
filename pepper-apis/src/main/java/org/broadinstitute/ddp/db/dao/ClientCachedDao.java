package org.broadinstitute.ddp.db.dao;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.security.StudyClientConfiguration;
import org.jdbi.v3.core.Handle;

public class ClientCachedDao extends SQLObjectWrapper<ClientDao> implements ClientDao {
    private static final String CACHE_NAME = "StudyClientConfigCache.byStudyClientConfigCacheKey";

    private class StudyClientConfigCacheKey implements Serializable {
        String auth0ClientId;
        String auth0Domain;

        StudyClientConfigCacheKey(String auth0ClientId, String auth0Domain) {
            this.auth0ClientId = auth0ClientId;
            this.auth0Domain = auth0Domain;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            StudyClientConfigCacheKey that = (StudyClientConfigCacheKey) o;

            if (auth0ClientId != null ? !auth0ClientId.equals(that.auth0ClientId) : that.auth0ClientId != null) {
                return false;
            }
            return auth0Domain != null ? auth0Domain.equals(that.auth0Domain) : that.auth0Domain == null;
        }

        @Override
        public int hashCode() {
            int result = auth0ClientId != null ? auth0ClientId.hashCode() : 0;
            result = 31 * result + (auth0Domain != null ? auth0Domain.hashCode() : 0);
            return result;
        }
    }

    private static Cache<StudyClientConfigCacheKey, StudyClientConfiguration> CLIENT_STUDY_CONFIG_CACHE;

    private static void initCache() {
        if (CLIENT_STUDY_CONFIG_CACHE == null || CLIENT_STUDY_CONFIG_CACHE.isClosed()) {
            CLIENT_STUDY_CONFIG_CACHE = CacheService.getInstance().getOrCreateCache(CACHE_NAME, new Duration(MINUTES, 10),
                    ClientCachedDao.class);
        }
    }

    public ClientCachedDao(Handle handle) {
        super(handle, ClientDao.class);
        initCache();
    }

    @Override
    public JdbiClient getClientDao() {
        return delegate.getClientDao();
    }

    @Override
    public JdbiUmbrellaStudy getUmbrellaStudyDao() {
        return delegate.getUmbrellaStudyDao();
    }

    @Override
    public JdbiClientUmbrellaStudy getClientUmbrellaStudyDao() {
        return delegate.getClientUmbrellaStudyDao();
    }

    @Override
    public List<ClientDto> findAllPermittedClientsForStudy(long studyId) {
        return delegate.findAllPermittedClientsForStudy(studyId);
    }

    @Override
    public long registerClient(String auth0ClientId, String auth0ClientSecret, Collection<String> studyGuidsToAccess,
                               String encryptionKey, Long auth0TenantId) {
        return delegate.registerClient(auth0ClientId, auth0ClientSecret, studyGuidsToAccess, encryptionKey, auth0TenantId);
    }

    @Override
    public StudyClientConfiguration getConfiguration(String auth0ClientId, String auth0Domain) {
        var key = new StudyClientConfigCacheKey(auth0ClientId, auth0Domain);
        var config = CLIENT_STUDY_CONFIG_CACHE.get(key);
        if (config == null) {
            config = delegate.getConfiguration(auth0ClientId, auth0Domain);
            CLIENT_STUDY_CONFIG_CACHE.put(key, config);
        }
        return config;
    }

    @Override
    public Long getClientIdByAuth0ClientAndDomain(String auth0ClientId, String auth0Domain) {
        return delegate.getClientIdByAuth0ClientAndDomain(auth0ClientId, auth0Domain);
    }

    @Override
    public int deleteByAuth0ClientIdAndAuth0TenantId(String auth0ClientId, long auth0TenantId) {
        return delegate.deleteByAuth0ClientIdAndAuth0TenantId(auth0ClientId, auth0TenantId);
    }

    @Override
    public boolean isAuth0ClientActive(String auth0ClientId, String auth0Domain) {
        return delegate.isAuth0ClientActive(auth0ClientId, auth0Domain);
    }
}
