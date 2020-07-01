package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UmbrellaDto;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.jdbi.v3.core.Handle;

public class JdbiUmbrellaStudyCached extends SQLObjectWrapper<JdbiUmbrellaStudy> implements JdbiUmbrellaStudy {

    private Cache<Long, StudyDto> idToStudyCache;
    private Cache<String, Long> guidToIdCache;

    public JdbiUmbrellaStudyCached(Handle handle) {
        super(handle, JdbiUmbrellaStudy.class);
        initializeCache();
    }

    private void initializeCache() {
        if (idToStudyCache == null) {
            // eternal!
            idToStudyCache = CacheService.getInstance().getOrCreateCache("idToStudyCache",
                    new Duration(),
                    ModelChangeType.UMBRELLA,
                    this.getClass());
            guidToIdCache = CacheService.getInstance().getOrCreateCache("guidToIdStudyCache", new Duration(),
                    ModelChangeType.UMBRELLA,
                    this.getClass());
            if (!isUsingNullCache()) {
                cacheAll();
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
        StudyDto study = findById(studyId);
        if (study == null) {
            return null;
        } else {
            return study.getGuid();
        }
    }

    @Override
    public List<StudyDto> findAll() {
        if (isUsingNullCache()) {
            return delegate.findAll();
        } else {
            return streamAll().collect(toList());
        }
    }

    private Stream<StudyDto> streamAll() {
        return StreamSupport.stream(idToStudyCache.spliterator(), false)
                .map(entry -> entry.getValue());
    }

    @Override
    public StudyDto findByStudyGuid(String studyGuid) {
        if (isUsingNullCache()) {
            return delegate.findByStudyGuid(studyGuid);
        } else {
            Long id = guidToIdCache.get(studyGuid);
            if (id != null) {
                return idToStudyCache.get(id);
            } else {
                return null;
            }
        }
    }

    @Override
    public StudyDto findById(long studyId) {
        if (isUsingNullCache()) {
            return delegate.findById(studyId);
        } else {
            return idToStudyCache.get(studyId);
        }
    }

    @Override
    public List<StudyDto> findByUmbrellaGuid(String umbrellaGuid) {
        if (isUsingNullCache()) {
            return delegate.findByUmbrellaGuid(umbrellaGuid);
        } else {
            JdbiUmbrella umbrellaDao = new JdbiUmbrellaCached(getHandle());
            Optional<UmbrellaDto> umbrella = umbrellaDao.findByGuid(umbrellaGuid);
            if (umbrella.isPresent()) {
                return streamAll().filter(study -> study.getUmbrellaId() == umbrella.get().getId()).collect(toList());
            } else {
                return Collections.emptyList();
            }
        }

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
        if (isUsingNullCache()) {
            return delegate.getUmbrellaGuidForStudyGuid(studyGuid);
        } else {
            StudyDto study = findByStudyGuid(studyGuid);
            if (study != null) {
                return new JdbiUmbrellaCached(getHandle())
                        .findByGuid(study.getGuid())
                        .map(umbrella -> umbrella.getGuid()).orElse(null);
            } else {
                return null;
            }
        }
    }

    @Override
    public int updateShareLocationForStudy(boolean shareLocation, String guid) {
        int val = delegate.updateShareLocationForStudy(shareLocation, guid);
        if (val > 0) {
            notifyModelUpdated(ModelChangeType.UMBRELLA, findByStudyGuid(guid).getId());
        }
        return val;
    }

    @Override
    public int updateOlcPrecisionForStudy(OLCPrecision precision, String guid) {
        int val = delegate.updateOlcPrecisionForStudy(precision, guid);
        if (val > 0) {
            notifyModelUpdated(ModelChangeType.UMBRELLA, findByStudyGuid(guid).getUmbrellaId());
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

    private void cacheAll() {
        List<StudyDto> allDtos = delegate.findAll();
        allDtos.forEach(studyDto -> {
            idToStudyCache.put(studyDto.getId(), studyDto);
            guidToIdCache.put(studyDto.getGuid(), studyDto.getId());

        });
    }
}
