package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiPicklistOption;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.Handle;

/**
 * Task for updating the IF_YOU_HAVE_HAD_ARRHYTHMIA_COMPLICATIONS (PATIENT_SURVEY)
 * and IF_YOU_HAVE_HAD_ARRHYTHMIA (ABOUT_HEALTHY) questions to mark the DO_NOT_REQUIRE
 * and DID_NOT_REQUIRED options (respectively) as exclusive.
 * 
 * <p>See https://broadinstitute.atlassian.net/browse/DDP-8576
 */
@Slf4j
public class SingularMakeNoMedicalTreatmentOptionExclusive implements CustomTask {
    private static final String PATCH_PATH = "patches";
    private static final String PATCH_CONF_NAME = "ddp-8576-select-logic.conf";
    private static final String CONFIG_STUDY_GUID = "study.guid";

    private static class Keys {
        private static class ChangeSet {
            private static final String AUTHOR = "author";
            private static final String DESCRIPTION = "description";
            private static final String STUDY = "study";
            private static final String CONTENT = "content";
        }

        private static class ChangeContent {
            private static final String ACTIVITY = "activity";
            private static final String VERSION_TAG = "versionTag";
            private static final String STABLE_ID = "stableId";
            private static final String OPTION_ID = "optionId";
            private static final String EXCLUSIVE = "exclusive";
        }
    }

    private Config patchConfig;

    private JdbiUmbrellaStudy studySql;
    private JdbiActivity activitySql;
    private JdbiActivityVersion activityVersionSql;
    private FormActivityDao activityDao;
    private JdbiPicklistOption picklistOptionSql;

    @Override
    public void init(Path configPath, Config studyConfig, Config varsConfig) {
        var patchConfPath = Paths.get(PATCH_PATH, PATCH_CONF_NAME);
        var patchPath = configPath.getParent().resolve(patchConfPath);

        if (!Files.exists(patchPath)) {
            throw new DDPException(String.format("the configuration file named '%s' cannot be found", patchConfPath));
        }

        final Config patchConfig;
        try {
            patchConfig = ConfigFactory.parseFile(patchPath.toFile()).resolveWith(varsConfig);
        } catch (Exception configException) {
            var message = String.format("failed to load patch data file expected at '%s'", patchPath);
            throw new DDPException(message, configException);
        }

        var studyGuid = studyConfig.getString(CONFIG_STUDY_GUID);
        var targetStudy = patchConfig.getString(Keys.ChangeSet.STUDY);
        if (!targetStudy.equals(studyGuid)) {
            throw new DDPException(String.format("Patch %s targets study '%s', but is being run against '%s'",
                    this.getClass().getSimpleName(),
                    targetStudy,
                    studyGuid));
        }

        this.patchConfig = patchConfig;
    }

    @Override
    public void run(final Handle handle) {
        final var taskName = this.getClass().getSimpleName();
        log.info("TASK:: {}", taskName);

        daoSetup(handle);

        final var studyGuid = patchConfig.getString(Keys.ChangeSet.STUDY);
        final var studyDto = Optional.ofNullable(studySql.findByStudyGuid(studyGuid)).orElseThrow(() -> {
            return new DDPException(String.format("failed to locate the study '%s'", studyGuid));
        });

        patchConfig.getConfigList(Keys.ChangeSet.CONTENT).stream()
                .forEach((config) -> {
                    final var activityCode = config.getString(Keys.ChangeContent.ACTIVITY);
                    final var versionTag = config.getString(Keys.ChangeContent.VERSION_TAG);

                    final var activityDef = getActivityDef(studyDto, activityCode, versionTag);

                    final var questionStableId = config.getString(Keys.ChangeContent.STABLE_ID);
                    final var picklistDef = getPicklistDef(activityDef, questionStableId);

                    final var optionStableId = config.getString(Keys.ChangeContent.OPTION_ID);
                    final var optionDef = picklistDef.getAllPicklistOptions().stream()
                            .filter((option) -> StringUtils.equals(optionStableId, option.getStableId()))
                            .findFirst()
                            .orElseThrow(() -> {
                                var message = String.format("failed to find option definition for [question:%s,option:%s]",
                                        questionStableId,
                                        optionStableId);
                                return new DDPException(message);
                            });

                    final var newValue = config.getBoolean(Keys.ChangeContent.EXCLUSIVE);

                    if (optionDef.isExclusive() == newValue) {
                        log.info("No change required- the value of isExclusive is already '{}' [question:{},option:{}]",
                                newValue,
                                questionStableId,
                                optionStableId);
                        return;
                    }

                    assert optionDef.getOptionId() != null;

                    final var optionDto = picklistOptionSql.findById(optionDef.getOptionId())
                            .orElseThrow(() -> {
                                return new DDPException(String.format("failed to find option with id %s", optionDef.getOptionId()));
                            });
                    optionDto.setExclusive(newValue);
                    final var rowsUpdated = picklistOptionSql.update(optionDto);
                    DBUtils.checkInsert(1, rowsUpdated);

                    log.info("Successfully set isExclusive = {} for [question:{},option:{}]",
                            newValue,
                            questionStableId,
                            optionStableId);
                });

        log.info("TASK:: {} was successfully applied", taskName);

        daoTearDown();
    }

    private void daoSetup(Handle handle) {
        this.studySql = handle.attach(JdbiUmbrellaStudy.class);
        this.activitySql = handle.attach(JdbiActivity.class);
        this.activityVersionSql = handle.attach(JdbiActivityVersion.class);
        this.activityDao = handle.attach(FormActivityDao.class);
        this.picklistOptionSql = handle.attach(JdbiPicklistOption.class);
    }

    private void daoTearDown() {
        this.studySql = null;
        this.activitySql = null;
        this.activityVersionSql = null;
        this.activityDao = null;
        this.picklistOptionSql = null;
    }

    private FormActivityDef getActivityDef(StudyDto study, String activityCode, String versionTag) {
        final var studyGuid = study.getGuid();
        final var studyId = study.getId();

        final var activity = activitySql.findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .orElseThrow(() -> {
                    return new DDPException(String.format("failed to locate the activity '%s' in study '%s'",
                            activityCode,
                            studyGuid));
                });

        final var activityVersion = activityVersionSql.findByActivityCodeAndVersionTag(studyId, activityCode, versionTag)
                .orElseThrow(() -> {
                    var message = String.format("failed to locate the version tag '%s' for the activity '%s' in study '%s'",
                            versionTag,
                            activityCode,
                            studyGuid);
                    return new DDPException(message);
                });

        return Optional.ofNullable(activityDao.findDefByDtoAndVersion(activity, activityVersion)).stream()
                    .filter((activityDef) -> activityDef.getActivityType() == ActivityType.FORMS)
                    .map(FormActivityDef.class::cast)
                    .findFirst()
                    .orElseThrow(() -> {
                        final var message = String.format("Unable to find the activity [%s:s] with code '%s' in study %s]",
                                activityCode,
                                versionTag,
                                ActivityType.FORMS,
                                studyGuid);
                        return new DDPException(message);
                    });
    }

    private PicklistQuestionDef getPicklistDef(final FormActivityDef activityDef, final String stableId) {
        return Optional.ofNullable(activityDef.getQuestionByStableId(stableId)).stream()
                .filter((questionDef) -> questionDef.getQuestionType() == QuestionType.PICKLIST)
                .map(PicklistQuestionDef.class::cast)
                .findFirst()
                .orElseThrow(() -> {
                    final var message = String.format("Unable to find a question with stable id '%s' and type '%s' in activity %s]",
                            stableId,
                            QuestionType.PICKLIST,
                            activityDef.getActivityCode());
                    return new DDPException(message);
                });
    }
    
}
