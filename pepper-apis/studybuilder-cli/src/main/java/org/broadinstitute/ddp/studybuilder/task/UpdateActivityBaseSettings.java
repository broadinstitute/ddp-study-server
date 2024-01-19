package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Functions;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;

/**
 * General task to update basic activity configuration such as display order, max instances per user, and other flags.
 * This will also update activity naming details such as name, title, subtitle, and description. The activity definition
 * will be read to lookup the latest text to update to. If new text in a new language is added, those will be inserted
 * as well. This task will do this for all activities in the study.
 */
@Slf4j
public class UpdateActivityBaseSettings implements CustomTask {
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
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);

        for (Config activityCfg : studyCfg.getConfigList("activities")) {
            Config definition = activityBuilder.readDefinitionConfig(activityCfg.getString("filepath"));
            updateActivityBaseSettings(handle, studyDto, jdbiActVersion, definition);

            //nested activities
            if (activityCfg.hasPath("nestedActivities")) {
                List<String> nestedPaths = activityCfg.hasPath("nestedActivities")
                        ? activityCfg.getStringList("nestedActivities")
                        : Collections.emptyList();
                for (var nestedPath : nestedPaths) {
                    Config nestedConf = activityBuilder.readDefinitionConfig(nestedPath);
                    //compare and update
                    updateActivityBaseSettings(handle, studyDto, jdbiActVersion, nestedConf);
                }
            }
        }
    }

    private void updateActivityBaseSettings(Handle handle, StudyDto studyDto, JdbiActivityVersion jdbiActVersion, Config activityCfg) {
        String activityCode = activityCfg.getString("activityCode");
        String versionTag = activityCfg.getString("versionTag");

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto versionDto = jdbiActVersion.findByActivityIdAndVersionTag(activityId, versionTag).orElseThrow();
        log.info("Working on activity {} version {} (revisionId={})...", activityCode, versionTag, versionDto.getRevId());

        compareBasicSettings(handle, activityCfg, activityId);
        compareNamingDetails(handle, activityCfg, activityId, versionDto);
        compareStatusSummaries(handle, activityCfg, activityId);
    }

    private void compareBasicSettings(Handle handle, Config definition, long activityId) {
        var jdbiActivity = handle.attach(JdbiActivity.class);
        ActivityDto currentDto = jdbiActivity.queryActivityById(activityId);
        ActivityDto latestDto = new ActivityDto(
                currentDto.getActivityId(),
                currentDto.getActivityTypeId(),
                currentDto.getStudyId(),
                currentDto.getActivityCode(),
                currentDto.getParentActivityId(),
                currentDto.getParentActivityCode(),
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
                definition.getBoolean("hideExistingInstancesOnCreation"),
                ConfigUtil.getBoolOrElse(definition, "createOnParentCreation", false),
                ConfigUtil.getBoolOrElse(definition, "canDeleteInstances", false),
                ConfigUtil.getBoolIfPresent(definition, "canDeleteFirstInstance"),
                ConfigUtil.getBoolOrElse(definition, "showActivityStatus", false));
        if (!currentDto.equals(latestDto)) {
            if (currentDto.canDeleteInstances() != latestDto.canDeleteInstances()) {
                throw new UnsupportedOperationException("Updating `canDeleteInstances` setting is currently not supported"
                        + " to prevent accidental updates of this property and allowing undesired deletion of data");
            }
            jdbiActivity.updateActivity(
                    latestDto.getActivityId(),
                    latestDto.getDisplayOrder(),
                    latestDto.writeOnce(),
                    latestDto.instantiateUponRegistration(),
                    latestDto.getMaxInstancesPerUser(),
                    latestDto.getEditTimeoutSec(),
                    latestDto.isOnDemandTriggerAllowed(),
                    latestDto.shouldExcludeFromDisplay(),
                    latestDto.isUnauthenticatedAllowed(),
                    latestDto.isFollowup(),
                    latestDto.shouldExcludeStatusIconFromDisplay(),
                    latestDto.hideExistingInstancesOnCreation(),
                    latestDto.isCreateOnParentCreation(),
                    latestDto.canDeleteInstances(),
                    latestDto.canDeleteFirstInstance());
            log.info("Updated basic settings");
        } else {
            log.info("No changes to basic settings");
        }
    }

    public void compareNamingDetails(Handle handle, Config definition, long activityId, ActivityVersionDto versionDto) {
        var activityI18nDao = handle.attach(ActivityI18nDao.class);
        Map<String, ActivityI18nDetail> currentDetails = activityI18nDao
                .findDetailsByActivityIdAndTimestamp(activityId, versionDto.getRevStart())
                .stream()
                .collect(Collectors.toMap(ActivityI18nDetail::getIsoLangCode, Functions.identity()));
        Map<String, ActivityI18nDetail> latestDetails =
                buildLatestNamingDetails(activityId, versionDto.getRevId(), definition, currentDetails);

        List<ActivityI18nDetail> updatedDetails = new ArrayList<>();
        for (String language : currentDetails.keySet()) {
            ActivityI18nDetail current = currentDetails.get(language);
            ActivityI18nDetail latest = latestDetails.remove(language);
            if (!current.equals(latest)) {
                updatedDetails.add(latest);
            }
        }

        activityI18nDao.updateDetails(updatedDetails);
        log.info("Updated naming details for {} languages: {}", updatedDetails.size(),
                updatedDetails.stream().map(ActivityI18nDetail::getIsoLangCode).collect(Collectors.toList()));

        // Only new naming details are left, if any.
        List<ActivityI18nDetail> newDetails = List.copyOf(latestDetails.values());
        activityI18nDao.insertDetails(newDetails);
        log.info("Created naming details for {} languages: {}", newDetails.size(),
                newDetails.stream().map(ActivityI18nDetail::getIsoLangCode).collect(Collectors.toList()));
    }

    private Map<String, ActivityI18nDetail> buildLatestNamingDetails(long activityId, long revisionId, Config definition,
                                                                     Map<String, ActivityI18nDetail> currentDetails) {
        Map<String, String> names = collectTranslatedText(definition, "translatedNames");
        Map<String, String> secondNames = collectTranslatedText(definition, "translatedSecondNames");
        Map<String, String> titles = collectTranslatedText(definition, "translatedTitles");
        Map<String, String> subtitles = collectTranslatedText(definition, "translatedSubtitles");
        Map<String, String> descriptions = collectTranslatedText(definition, "translatedDescriptions");
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
                    descriptions.getOrDefault(language, null),
                    current == null ? revisionId : current.getRevisionId());
            details.put(language, current == null ? latest : mergeNamingDetails(current, latest));
        }
        return details;
    }

    private Map<String, String> collectTranslatedText(Config definition, String key) {
        Map<String, String> container = new HashMap<>();
        for (Config textCfg : definition.getConfigList(key)) {
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
                latest.getDescription(),
                current.getRevisionId());
    }

    protected void compareStatusSummaries(Handle handle, Config definition, long activityId) {
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
        log.info("Updated {} status summaries: {}", updatedSummaries.size(),
                updatedSummaries.stream().map(this::statusSummaryKey).collect(Collectors.toList()));

        // Only new status summaries are left, if any.
        List<SummaryTranslation> newSummaries = List.copyOf(latestSummaries.values());
        activityI18nDao.insertSummaries(activityId, newSummaries);
        log.info("Created {} status summaries: {}", newSummaries.size(),
                newSummaries.stream().map(this::statusSummaryKey).collect(Collectors.toList()));
    }

    private String statusSummaryKey(SummaryTranslation summary) {
        return String.format("%d-%s-%s",
                summary.getActivityId(),
                summary.getStatusType().name(),
                summary.getLanguageCode());
    }

    private Map<String, SummaryTranslation> buildLatestStatusSummaries(long activityId, Config definition,
                                                                       Map<String, SummaryTranslation> currentSummaries) {
        Map<String, SummaryTranslation> summaries = new HashMap<>();
        for (Config summaryCfg : definition.getConfigList("translatedSummaries")) {
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
