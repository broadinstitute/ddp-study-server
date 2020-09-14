package org.broadinstitute.ddp.db.dao;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.model.study.StudyLanguage;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;

public class StudyLanguageCachedDao extends SQLObjectWrapper<StudyLanguageDao> implements StudyLanguageDao {
    private static RLocalCachedMap<Long, List<StudyLanguage>> studyIdToLanguageCache;

    public StudyLanguageCachedDao(Handle handle) {
        super(handle, StudyLanguageDao.class);
        initializeCacheIfNeeded();
    }

    private void initializeCacheIfNeeded() {
        if (studyIdToLanguageCache == null) {
            synchronized (this.getClass()) {
                if (studyIdToLanguageCache == null) {
                    studyIdToLanguageCache = CacheService.getInstance().getOrCreateLocalCache("studyIdToLanguageCache", 100);
                }
            }
        }
    }

    @Override
    public StudyLanguageSql getStudyLanguageSql() {
        return delegate.getStudyLanguageSql();
    }

    @Override
    public JdbiUmbrellaStudy getUmbrellaStudy() {
        return new JdbiUmbrellaStudyCached(getHandle());
    }

    @Override
    public long insert(long umbrellaStudyId, long languageCodeId) {
        studyIdToLanguageCache.remove(umbrellaStudyId);
        return delegate.insert(umbrellaStudyId, languageCodeId);
    }

    @Override
    public long insert(long umbrellaStudyId, long languageCodeId, String name) {
        studyIdToLanguageCache.remove(umbrellaStudyId);
        return delegate.insert(umbrellaStudyId, languageCodeId, name);
    }

    @Override
    public long insert(String studyGuid, String languageCode, String name) {
        studyIdToLanguageCache.clear();
        return insert(studyGuid, languageCode, name);
    }

    @Override
    public void deleteStudyLanguageById(long studyLanguageId) {
        studyIdToLanguageCache.clear();
        deleteStudyLanguageById(studyLanguageId);
    }

    @Override
    public int setAsDefaultLanguage(long umbrellaStudyId, long languageCodeId) {
        if (!isNullCache()) {
            studyIdToLanguageCache.remove(umbrellaStudyId);
        }
        return setAsDefaultLanguage(umbrellaStudyId, languageCodeId);
    }

    @Override
    public int setAsDefaultLanguage(long umbrellaStudyId, String languageCode) {
        if (!isNullCache()) {
            studyIdToLanguageCache.remove(umbrellaStudyId);
        }
        return delegate.setAsDefaultLanguage(umbrellaStudyId, languageCode);
    }

    @Override
    public List<StudyLanguage> findLanguages(long umbrellaStudyId) {
        if (isNullCache(studyIdToLanguageCache)) {
            return delegate.findLanguages(umbrellaStudyId);
        } else {
            List<StudyLanguage> result = studyIdToLanguageCache.get(umbrellaStudyId);
            if (result == null) {
                result = delegate.findLanguages(umbrellaStudyId);
                studyIdToLanguageCache.putAsync(umbrellaStudyId, result);
            }
            return result;
        }
    }

    @Override
    public List<StudyLanguage> findLanguages(String studyGuid) {
        if (isNullCache(studyIdToLanguageCache)) {
            return delegate.findLanguages(studyGuid);
        } else {
            Optional<Long> studyIdOpt = new JdbiUmbrellaStudyCached(getHandle()).getIdByGuid(studyGuid);
            if (studyIdOpt.isPresent()) {
                return findLanguages(studyIdOpt.get());
            } else {
                return Collections.emptyList();
            }
        }
    }

    private boolean isNullCache() {
        return isNullCache(studyIdToLanguageCache);
    }
}
