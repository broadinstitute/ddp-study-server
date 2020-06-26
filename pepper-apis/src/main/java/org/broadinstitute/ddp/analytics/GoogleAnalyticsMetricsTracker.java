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
    private static Map<String, GoogleAnalytics> studyAnalyticsTrackers = new HashMap<>();
    private static Set<String> noAnalyticsTokenStudies = new HashSet<>(); //studyGuid with NO analytics token
    private static volatile GoogleAnalyticsMetricsTracker instance;
    private static Object lockGA = new Object();

    private GoogleAnalyticsMetricsTracker() {
        Map<String, StudySettings> allStudySettings = StudySettingsStore.getInstance().getAllStudySettings();
        allStudySettings.forEach((studyGuid, studySettings) -> initStudyMetricTracker(studyGuid, studySettings));
    }

    public static GoogleAnalyticsMetricsTracker getInstance() {
        if (instance == null) {
            synchronized (lockGA) {
                if (instance == null) {
                    instance = new GoogleAnalyticsMetricsTracker();
                }
            }
        }
        return instance;
    }

    private GoogleAnalytics getMetricTracker(String studyGuid) {
        if (!studyAnalyticsTrackers.containsKey(studyGuid) && !noAnalyticsTokenStudies.contains(studyGuid)) {
            StudySettings studySettings = getStudySettingByStudyGuid(studyGuid);
            initStudyMetricTracker(studyGuid, studySettings);
        }
        return studyAnalyticsTrackers.get(studyGuid);
    }

    private synchronized void initStudyMetricTracker(String studyGuid, StudySettings studySettings) {
        if (!studyAnalyticsTrackers.containsKey(studyGuid)) {
            if (studySettings == null || !studySettings.isAnalyticsEnabled()) {
                noAnalyticsTokenStudies.add(studyGuid);
                return;
            } else if (StringUtils.isEmpty(studySettings.getAnalyticsToken())) {
                LOG.error("NO analytics token found for study : {} . skipping sending analytics. ", studyGuid);
                noAnalyticsTokenStudies.add(studyGuid);
                return;
            }

            GoogleAnalytics metricTracker = GoogleAnalytics.builder()
                    .withConfig(new GoogleAnalyticsConfig().setBatchingEnabled(true).setBatchSize(DEFAULT_BATCH_SIZE))
                    .withTrackingId(studySettings.getAnalyticsToken())
                    .build();
            studyAnalyticsTrackers.put(studyGuid, metricTracker);
            LOG.info("Initialized GA Metrics Tracker for study GUID: {} ", studyGuid);
        }
    }

    private void sendEventMetrics(String studyGuid, EventHit eventHit) {
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

    private StudySettings getStudySettingByStudyGuid(String studyGuid) {
        //todo: revisit after Redis caching to use cached Study, StudySettings and get rid of StudySettingsStore
        return StudySettingsStore.getInstance().getStudySettings(studyGuid);
    }

    public void sendAnalyticsMetrics(String studyGuid, String category, String action, String label,
                                     String labelContent, int value) {
        StudySettings studySettings = getStudySettingByStudyGuid(studyGuid);
        if (studySettings != null && studySettings.isAnalyticsEnabled()) {
            String gaEventLabel = String.join(":", label,
                    studyGuid);
            if (labelContent != null) {
                gaEventLabel = String.join(":", gaEventLabel, labelContent);
            }
            EventHit eventHit = new EventHit(category, action, gaEventLabel, value);
            sendEventMetrics(studyGuid, eventHit);
        }
    }

    public void flushOutMetrics() {
        //lookup all Metrics Trackers and flush out any pending events
        LOG.info("Flushing out all pending GA events");
        for (GoogleAnalytics tracker : studyAnalyticsTrackers.values()) {
            tracker.flush();
        }
    }

    private static class StudySettingsStore {
        private static StudySettingsStore instance;
        private static Object lockVar1 = new Object();
        private static Map<String, StudySettings> studySettings = new HashMap<>();

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
                Map<String, StudySettings> settingsMap = new HashMap<>();
                for (StudyDto studyDto : studyDtos) {
                    //load StudySettings
                    Optional<StudySettings> studySettings = studyDao.findSettings(studyDto.getId());
                    if (studySettings.isPresent()) {
                        settingsMap.put(studyDto.getGuid(), studySettings.get());
                    }
                }
                LOG.info("Loaded StudySettings for {} studies.", settingsMap.size());
                return settingsMap;
            });
        }

        private StudySettings getStudySettings(String studyGuid) {
            return studySettings.get(studyGuid);
        }

        private Map<String, StudySettings> getAllStudySettings() {
            return studySettings;
        }

    }

}
