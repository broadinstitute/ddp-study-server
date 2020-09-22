package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.base.Functions;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General task to help update activity naming details such as name, title, subtitle, and description. The activity
 * definition will be read to lookup the latest text to update to. If new text in a new language is added, those will be
 * inserted as well. This task will do this for all activities in the study.
 */
public class UpdateActivityNamingDetails implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateActivityNamingDetails.class);

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

            var activityI18nDao = handle.attach(ActivityI18nDao.class);
            Map<String, ActivityI18nDetail> currentDetails = activityI18nDao
                    .findDetailsByActivityId(activityId)
                    .stream()
                    .collect(Collectors.toMap(ActivityI18nDetail::getIsoLangCode, Functions.identity()));

            Map<String, String> names = collectTranslatedText(definition, "translatedNames");
            Map<String, String> titles = collectTranslatedText(definition, "translatedTitles");
            Map<String, String> subtitles = collectTranslatedText(definition, "translatedSubtitles");
            Map<String, String> descriptions = collectTranslatedText(definition, "translatedDescriptions");

            var newDetails = buildDetailsForUpdate(currentDetails, names, titles, subtitles, descriptions);
            activityI18nDao.updateDetails(newDetails);
            LOG.info("Updated for {} languages: {}", newDetails.size(),
                    newDetails.stream().map(ActivityI18nDetail::getIsoLangCode).collect(Collectors.toList()));

            newDetails = buildDetailsForInsert(activityId, currentDetails, names, titles, subtitles, descriptions);
            activityI18nDao.insertDetails(newDetails);
            LOG.info("Created for {} languages: {}", newDetails.size(),
                    newDetails.stream().map(ActivityI18nDetail::getIsoLangCode).collect(Collectors.toList()));
        }
    }

    private Map<String, String> collectTranslatedText(Config activityCfg, String key) {
        Map<String, String> container = new HashMap<>();
        for (Config textCfg : activityCfg.getConfigList(key)) {
            container.put(textCfg.getString("language"), textCfg.getString("text"));
        }
        return container;
    }

    private List<ActivityI18nDetail> buildDetailsForUpdate(
            Map<String, ActivityI18nDetail> currentDetails,
            Map<String, String> names,
            Map<String, String> titles,
            Map<String, String> subtitles,
            Map<String, String> descriptions) {
        List<ActivityI18nDetail> newDetails = new ArrayList<>();
        for (String language : currentDetails.keySet()) {
            ActivityI18nDetail current = currentDetails.get(language);
            ActivityI18nDetail latest = new ActivityI18nDetail(
                    current.getId(),
                    current.getActivityId(),
                    current.getLangCodeId(),
                    current.getIsoLangCode(),
                    names.get(language),
                    titles.getOrDefault(language, null),
                    subtitles.getOrDefault(language, null),
                    descriptions.getOrDefault(language, null));
            if (isAnyTextDifferent(current, latest)) {
                newDetails.add(latest);
            }
        }
        return newDetails;
    }

    private List<ActivityI18nDetail> buildDetailsForInsert(
            long activityId,
            Map<String, ActivityI18nDetail> currentDetails,
            Map<String, String> names,
            Map<String, String> titles,
            Map<String, String> subtitles,
            Map<String, String> descriptions) {
        List<ActivityI18nDetail> newDetails = new ArrayList<>();
        // Adding something (like a title) without adding a name in that language doesn't make sense,
        // so keying off of the name is the way to go, and name is also required.
        for (String language : names.keySet()) {
            if (!currentDetails.containsKey(language)) {
                newDetails.add(new ActivityI18nDetail(
                        activityId,
                        language,
                        names.get(language),
                        titles.getOrDefault(language, null),
                        subtitles.getOrDefault(language, null),
                        descriptions.getOrDefault(language, null)));
            }
        }
        return newDetails;
    }

    private boolean isAnyTextDifferent(ActivityI18nDetail current, ActivityI18nDetail latest) {
        return hash(current) != hash(latest);
    }

    private int hash(ActivityI18nDetail detail) {
        return Objects.hash(detail.getName(), detail.getTitle(), detail.getSubtitle(), detail.getDescription());
    }
}
