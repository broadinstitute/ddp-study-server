package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Functions;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General task to update basic activity configuration such as display order, max instances per user, and other flags.
 * This will also update activity naming details such as name, title, subtitle, and description. The activity definition
 * will be read to lookup the latest text to update to. If new text in a new language is added, those will be inserted
 * as well. This task will do this for all activities in the study.
 */
public class UpdateActivityBaseSettings implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateActivityBaseSettings.class);

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        User admin = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, admin.getId());

        for (Config activityCfg : studyCfg.getConfigList("activities")) {
            Config definition = activityBuilder.readDefinitionConfig(activityCfg.getString("filepath"));
            String activityCode = definition.getString("activityCode");
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
            LOG.info("Working on activity {}...", activityCode);

            compareBasicSettings(handle, definition, activityId);
            compareNamingDetails(handle, definition, activityId);
            compareStatusSummaries(handle, definition, activityId);
        }
    }

    private void compareBasicSettings(Handle handle, Config definition, long activityId) {
        var jdbiActivity = handle.attach(JdbiActivity.class);
        ActivityDto currentDto = jdbiActivity.queryActivityById(activityId);
        ActivityDto latestDto = new ActivityDto(
                currentDto.getActivityId(),
                currentDto.getActivityTypeId(),
                currentDto.getStudyId(),
                currentDto.getActivityCode(),
                definition.getInt("displayOrder"),
                definition.getBoolean("writeOnce"),
                false,  // instantiate_upon_registration not supported anymore!
                ConfigUtil.getIntIfPresent(definition, "maxInstancesPerUser"),
                ConfigUtil.getLongIfPresent(definition, "editTimeoutSec"),
                definition.getBoolean("allowOndemandTrigger"),
                definition.getBoolean("excludeFromDisplay"),
                definition.getBoolean("allowUnauthenticated"),
                definition.getBoolean("isFollowup"),
                definition.getBoolean("excludeStatusIconFromDisplay"),
                definition.getBoolean("hideExistingInstancesOnCreation"));
        if (!currentDto.equals(latestDto)) {
            jdbiActivity.updateActivity(
                    latestDto.getActivityId(),
                    latestDto.getDisplayOrder(),
                    latestDto.isWriteOnce(),
                    latestDto.isInstantiateUponRegistration(),
                    latestDto.getMaxInstancesPerUser(),
                    latestDto.getEditTimeoutSec(),
                    latestDto.isOndemandTriggerAllowed(),
                    latestDto.shouldExcludeFromDisplay(),
                    latestDto.isUnauthenticatedAllowed(),
                    latestDto.isFollowup(),
                    latestDto.shouldExcludeStatusIconFromDisplay(),
                    latestDto.isHideExistingInstancesOnCreation());
            LOG.info("Updated basic settings");
        } else {
            LOG.info("No changes to basic settings");
        }
    }

    private void compareNamingDetails(Handle handle, Config definition, long activityId) {
        var activityI18nDao = handle.attach(ActivityI18nDao.class);
        Map<String, ActivityI18nDetail> currentDetails = activityI18nDao
                .findDetailsByActivityId(activityId)
                .stream()
                .collect(Collectors.toMap(ActivityI18nDetail::getIsoLangCode, Functions.identity()));
        Map<String, ActivityI18nDetail> latestDetails = buildLatestNamingDetails(activityId, definition, currentDetails);

        List<ActivityI18nDetail> updatedDetails = new ArrayList<>();
        for (String language : currentDetails.keySet()) {
            ActivityI18nDetail current = currentDetails.get(language);
            ActivityI18nDetail latest = latestDetails.remove(language);
            if (!current.equals(latest)) {
                updatedDetails.add(latest);
            }
        }

        activityI18nDao.updateDetails(updatedDetails);
        LOG.info("Updated naming details for {} languages: {}", updatedDetails.size(),
                updatedDetails.stream().map(ActivityI18nDetail::getIsoLangCode).collect(Collectors.toList()));

        // Only new naming details are left, if any.
        List<ActivityI18nDetail> newDetails = List.copyOf(latestDetails.values());
        activityI18nDao.insertDetails(newDetails);
        LOG.info("Created naming details for {} languages: {}", newDetails.size(),
                newDetails.stream().map(ActivityI18nDetail::getIsoLangCode).collect(Collectors.toList()));
    }

    private Map<String, ActivityI18nDetail> buildLatestNamingDetails(long activityId, Config activityCfg,
                                                                     Map<String, ActivityI18nDetail> currentDetails) {
        Map<String, String> names = collectTranslatedText(activityCfg, "translatedNames");
        Map<String, String> secondNames = collectTranslatedText(activityCfg, "translatedSecondNames");
        Map<String, String> titles = collectTranslatedText(activityCfg, "translatedTitles");
        Map<String, String> subtitles = collectTranslatedText(activityCfg, "translatedSubtitles");
        Map<String, String> descriptions = collectTranslatedText(activityCfg, "translatedDescriptions");
        Map<String, ActivityI18nDetail> details = new HashMap<>();
        // Adding something (like a title) without adding a name in that language doesn't make sense,
        // so keying off of the name is the way to go, and name is also required.
        for (String language : names.keySet()) {
            ActivityI18nDetail current = currentDetails.get(language);
            ActivityI18nDetail latest = new ActivityI18nDetail(
                    activityId,
                    language,
                    names.get(language),
                    secondNames.getOrDefault(language, null),
                    titles.getOrDefault(language, null),
                    subtitles.getOrDefault(language, null),
                    descriptions.getOrDefault(language, null));
            details.put(language, current == null ? latest : mergeNamingDetails(current, latest));
        }
        return details;
    }

    private Map<String, String> collectTranslatedText(Config activityCfg, String key) {
        Map<String, String> container = new HashMap<>();
        for (Config textCfg : activityCfg.getConfigList(key)) {
            container.put(textCfg.getString("language"), textCfg.getString("text"));
        }
        return container;
    }

    private ActivityI18nDetail mergeNamingDetails(ActivityI18nDetail current, ActivityI18nDetail latest) {
        return new ActivityI18nDetail(
                current.getId(),
                current.getActivityId(),
                current.getLangCodeId(),
                current.getIsoLangCode(),
                latest.getName(),
                latest.getSecondName(),
                latest.getTitle(),
                latest.getSubtitle(),
                latest.getDescription());
    }

    private void compareStatusSummaries(Handle handle, Config definition, long activityId) {
        var activityI18nDao = handle.attach(ActivityI18nDao.class);
        Map<String, SummaryTranslation> currentSummaries = activityI18nDao
                .findSummariesByActivityId(activityId)
                .stream()
                .collect(Collectors.toMap(this::statusSummaryKey, Functions.identity()));
        Map<String, SummaryTranslation> latestSummaries = buildLatestStatusSummaries(activityId, definition, currentSummaries);

        List<SummaryTranslation> updatedSummaries = new ArrayList<>();
        for (String key : currentSummaries.keySet()) {
            SummaryTranslation current = currentSummaries.get(key);
            SummaryTranslation latest = latestSummaries.remove(key);
            if (!current.equals(latest)) {
                updatedSummaries.add(latest);
            }
        }

        activityI18nDao.updateSummaries(updatedSummaries);
        LOG.info("Updated {} status summaries: {}", updatedSummaries.size(),
                updatedSummaries.stream().map(this::statusSummaryKey).collect(Collectors.toList()));

        // Only new status summaries are left, if any.
        List<SummaryTranslation> newSummaries = List.copyOf(latestSummaries.values());
        activityI18nDao.insertSummaries(activityId, newSummaries);
        LOG.info("Created {} status summaries: {}", newSummaries.size(),
                newSummaries.stream().map(this::statusSummaryKey).collect(Collectors.toList()));
    }

    private String statusSummaryKey(SummaryTranslation summary) {
        return String.format("%d-%s-%s",
                summary.getActivityId(),
                summary.getStatusType().name(),
                summary.getLanguageCode());
    }

    private Map<String, SummaryTranslation> buildLatestStatusSummaries(long activityId, Config activityCfg,
                                                                       Map<String, SummaryTranslation> currentSummaries) {
        Map<String, SummaryTranslation> summaries = new HashMap<>();
        for (Config summaryCfg : activityCfg.getConfigList("translatedSummaries")) {
            var statusType = InstanceStatusType.valueOf(summaryCfg.getString("statusCode"));
            var language = summaryCfg.getString("language");
            var text = summaryCfg.getString("text");
            var latest = new SummaryTranslation(-1, activityId, statusType, language, text);
            String key = statusSummaryKey(latest);
            SummaryTranslation current = currentSummaries.get(key);
            summaries.put(key, current == null ? latest : mergeStatusSummary(current, latest));
        }
        return summaries;
    }

    private SummaryTranslation mergeStatusSummary(SummaryTranslation current, SummaryTranslation latest) {
        return new SummaryTranslation(
                current.getId().get(),
                current.getActivityId(),
                current.getStatusType(),
                current.getLanguageCode(),
                latest.getText());
    }
}
