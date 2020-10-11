package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.util.TestRedisConnection;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbiUmbrellaStudyCached extends SQLObjectWrapper<JdbiUmbrellaStudy> implements JdbiUmbrellaStudy {
    private static final Logger LOG = LoggerFactory.getLogger(JdbiUmbrellaStudyCached.class);

    private static RLocalCachedMap<Long, StudyDto> idToStudyCache;
    private static RLocalCachedMap<String, Long> studyGuidToIdCache;

    public JdbiUmbrellaStudyCached(Handle handle) {
        super(handle, JdbiUmbrellaStudy.class);
        initializeCache();
    }

    private void initializeCache() {
        if (idToStudyCache == null) {
            synchronized (this.getClass()) {
                if (idToStudyCache == null) {
                    idToStudyCache = CacheService.getInstance().getOrCreateLocalCache("idToStudyCache", 1000);
                    studyGuidToIdCache = CacheService.getInstance().getOrCreateLocalCache("studyGuidToIdCache", 1000);
                }
            }
        }
    }

    @Override
    public JdbiOLCPrecision getJdbiOLCPrecision() {
        return delegate.getJdbiOLCPrecision();
    }

    public boolean isUsingNullCache() {
        return isNullCache(idToStudyCache);
    }

    @Override
    public String findUmbrellaGuidForStudyId(long studyId) {
        return delegate.findUmbrellaGuidForStudyId(studyId);
    }

    @Override
    public List<StudyDto> findAll() {
        return delegate.findAll();
    }

    @Override
    public StudyDto findByStudyGuid(String studyGuid) {
        if (isUsingNullCache()) {
            return delegate.findByStudyGuid(studyGuid);
        } else {
            StudyDto dto;
            Long id = null;
            try {
                id = studyGuidToIdCache.get(studyGuid);
            } catch (RedisException e) {
                LOG.warn("Failed to retrieve value from Redis cache: " + studyGuidToIdCache.getName() + " key lookedup:" + studyGuid
                                + "Will try to retrieve from database", e);
                TestRedisConnection.doTest();
            }
            if (id == null) {
                dto = delegate.findByStudyGuid(studyGuid);
                addToCacheAsync(dto);
            } else {
                dto = findById(id);
            }
            return dto;
        }
    }

    @Override
    public StudyDto findById(long studyId) {
        if (isUsingNullCache()) {
            return delegate.findById(studyId);
        } else {
            StudyDto dto = idToStudyCache.get(studyId);
            try {
                dto = idToStudyCache.get(studyId);
            } catch (RedisException e) {
                LOG.warn("Failed to retrieve value from Redis cache: " + idToStudyCache.getName() + " key lookedup:" + studyId, e);
                TestRedisConnection.doTest();
            }
            if (dto == null) {
                dto = delegate.findById(studyId);
                addToCacheAsync(dto);
            }
            return dto;
        }
    }

    @Override
    public List<StudyDto> findByUmbrellaGuid(String umbrellaGuid) {
        return delegate.findByUmbrellaGuid(umbrellaGuid);
    }

    @Override
    public StudyDto findByDomainAndStudyGuid(String auth0Domain, String studyGuid) {
        return delegate.findByDomainAndStudyGuid(auth0Domain, studyGuid);
    }

    @Override
    public Optional<Long> getIdByGuid(String studyGuid) {
        return Optional.ofNullable(findByStudyGuid(studyGuid)).map(studyDto -> studyDto.getId());
    }

    @Override
    public String findGuidByStudyId(long id) {
        if (isUsingNullCache()) {
            return delegate.findGuidByStudyId(id);
        } else {
            var study = findById(id);
            if (study == null) {
                return null;
            } else {
                return study.getGuid();
            }
        }
    }

    @Override
    public String getUmbrellaGuidForStudyGuid(String studyGuid) {
        return delegate.getUmbrellaGuidForStudyGuid(studyGuid);
    }

    @Override
    public int updateShareLocationForStudy(boolean shareLocation, String guid) {
        int val = delegate.updateShareLocationForStudy(shareLocation, guid);
        if (val > 0) {
            StudyDto dto = findByStudyGuid(guid);
            if (dto != null) {
                notifyModelUpdated(ModelChangeType.UMBRELLA, dto.getId());
                removeFromCache(dto);
            }
        }
        return val;
    }

    private void removeFromCache(StudyDto dto) {
        if (dto != null) {
            try {
                idToStudyCache.removeAsync(dto.getId());
                studyGuidToIdCache.removeAsync(dto.getGuid());
            } catch (RedisException e) {
                LOG.warn("Failed to remove values from Redis caches", e);
                TestRedisConnection.doTest();
            }
        }
    }

    @Override
    public int updateOlcPrecisionForStudy(OLCPrecision precision, String guid) {
        int val = delegate.updateOlcPrecisionForStudy(precision, guid);
        if (val > 0) {
            StudyDto dto = findByStudyGuid(guid);
            if (dto != null) {
                notifyModelUpdated(ModelChangeType.UMBRELLA, dto.getUmbrellaId());
                removeFromCache(dto);
            }
        }
        return val;
    }

    @Override
    public String getIrbPasswordUsingStudyGuid(String studyGuid) {
        if (isUsingNullCache()) {
            return delegate.getIrbPasswordUsingStudyGuid(studyGuid);
        } else {
            var study = findByStudyGuid(studyGuid);
            if (study != null) {
                return study.getIrbPassword();
            } else {
                return null;
            }
        }
    }

    @Override
    public long insert(String studyName, String studyGuid, long umbrellaId, String webBaseUrl, long auth0TenantId, String irbPassword,
                       Long precisionId, boolean shareLocation, String studyEmail, String recaptchaSiteKey) {
        long studyId = delegate.insert(studyName, studyGuid, umbrellaId, webBaseUrl, auth0TenantId, irbPassword, precisionId, shareLocation,
                studyEmail, recaptchaSiteKey);
        notifyModelUpdated(ModelChangeType.UMBRELLA, umbrellaId);
        return studyId;
    }

    @Override
    public long insert(String studyName, String studyGuid, long umbrellaId, String webBaseUrl, long auth0TenantId, OLCPrecision precision,
                       boolean shareLocation, String studyEmail, String recaptchaSiteKey) {
        long studyId = delegate.insert(studyName, studyGuid, umbrellaId, webBaseUrl, auth0TenantId, precision, shareLocation,
                studyEmail, recaptchaSiteKey);
        notifyModelUpdated(ModelChangeType.UMBRELLA, umbrellaId);
        return studyId;
    }

    @Override
    public int updateIrbPasswordByGuid(String irbPassword, String studyGuid) {
        int modified = delegate.updateIrbPasswordByGuid(irbPassword, studyGuid);
        if (modified > 0) {
            notifyModelUpdated(ModelChangeType.UMBRELLA, findByStudyGuid(studyGuid).getUmbrellaId());
        }
        return modified;
    }

    @Override
    public boolean updateWebBaseUrl(String studyGuid, String webBaseUrl) {
        boolean modified = delegate.updateWebBaseUrl(studyGuid, webBaseUrl);
        if (modified) {
            notifyModelUpdated(ModelChangeType.UMBRELLA, findByStudyGuid(studyGuid).getUmbrellaId());
        }
        return modified;
    }

    private void addToCacheAsync(StudyDto dto) {
        if (dto != null) {
            try {
                idToStudyCache.put(dto.getId(), dto);
                studyGuidToIdCache.put(dto.getGuid(), dto.getId());
            } catch (RedisException e) {
                LOG.warn("Failed to store value to Redis cache", e);
                TestRedisConnection.doTest();
            }
        }
    }
}
