package org.broadinstitute.ddp.analytics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.brsanthu.googleanalytics.GoogleAnalyticsConfig;
import com.brsanthu.googleanalytics.request.EventHit;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.study.StudySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleAnalyticsMetricsTracker {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleAnalyticsMetricsTracker.class);
    private static final Integer DEFAULT_BATCH_SIZE = 10;
    public static Map<String, GoogleAnalytics> studyAnalyticsTrackers = new HashMap<>();
    private static Set<String> noAnalyticsTokenStudies = new HashSet<>(); //studyGuid with NO analytics token

    private static GoogleAnalytics getMetricTracker(String studyGuid) {
        if (!studyAnalyticsTrackers.containsKey(studyGuid) && !noAnalyticsTokenStudies.contains(studyGuid)) {
            initStudyMetricTracker(studyGuid);
        }
        return studyAnalyticsTrackers.get(studyGuid);
    }

    private static synchronized void initStudyMetricTracker(String studyGuid) {
        if (!studyAnalyticsTrackers.containsKey(studyGuid)) {
            StudySettings studySettings = getStudySettingByStudyGuid(studyGuid);
            String studyTrackingId = studySettings == null ? null : studySettings.getAnalyticsToken();
            if (StringUtils.isEmpty(studyTrackingId)) {
                LOG.error("NO analytics token found for study : {} . skipping sending analytics. ", studyGuid);
                noAnalyticsTokenStudies.add(studyGuid);
                return;
            }

            GoogleAnalytics metricTracker = GoogleAnalytics.builder()
                    .withConfig(new GoogleAnalyticsConfig().setBatchingEnabled(true).setBatchSize(DEFAULT_BATCH_SIZE))
                    .withTrackingId(studyTrackingId)
                    .build();
            studyAnalyticsTrackers.put(studyGuid, metricTracker);
            LOG.info("Initialized GA Metrics Tracker for study GUID: {} ", studyGuid);
        }
    }

    public static void sendEventMetrics(String studyGuid, EventHit eventHit) {
        //if (noAnalyticsTokenStudies.contains(studyGuid) || getStudySettingByStudyGuid(studyGuid).isAnalyticsEnabled()) {
        if (noAnalyticsTokenStudies.contains(studyGuid)) {
            return;
        }

        GoogleAnalytics metricTracker = getMetricTracker(studyGuid);
        if (metricTracker != null) {
            metricTracker.event().eventCategory(eventHit.eventCategory())
                    .eventAction(eventHit.eventAction())
                    .eventLabel(eventHit.eventLabel())
                    .eventValue(eventHit.eventValue())
                    .sendAsync();
        }
    }

    public static StudySettings getStudySettingByStudyGuid(String studyGuid) {
        //todo: revisit after Redis caching to use cached Study, StudySettings and get rid of StudySettingsStore
        Optional<StudySettings> settingsOpt = StudySettingsStore.getInstance().getStudySettings(studyGuid);
        return settingsOpt == null ? null : settingsOpt.isPresent() ? settingsOpt.get() : null;
    }


    private static class StudySettingsStore {
        private static StudySettingsStore instance;
        private static Object lockVar1 = "lock";
        private static Map<String, Optional<StudySettings>> studySettings = new HashMap<>();

        private static StudySettingsStore getInstance() {
            if (instance == null) {
                synchronized (lockVar1) {
                    if (instance == null) {
                        instance = new StudySettingsStore();
                        loadAllStudySettings();
                    }
                }
            }
            return instance;
        }

        private static synchronized void loadAllStudySettings() {
            studySettings = TransactionWrapper.withTxn(handle -> {
                JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
                StudyDao studyDao = handle.attach(StudyDao.class);
                List<StudyDto> studyDtos = jdbiUmbrellaStudy.findAll();
                Map<String, Optional<StudySettings>> settingsMap = new HashMap<>();
                for (StudyDto studyDto : studyDtos) {
                    //load StudySettings
                    Optional<StudySettings> studySettings = studyDao.findSettings(studyDto.getId());
                    if (studySettings.isPresent()) {
                        settingsMap.put(studyDto.getGuid(), studySettings);
                    }
                }
                LOG.info("Loaded StudySettings for {} studies.", settingsMap.size());
                return settingsMap;
            });
        }

        private Optional<StudySettings> getStudySettings(String studyGuid) {
            return studySettings.get(studyGuid);
        }
    }

}
