package org.broadinstitute.ddp.studybuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiFormTypeActivityInstanceStatusType;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.study.ActivityMappingType;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityBuilder.class);

    private Gson gson;
    private GsonPojoValidator validator;
    private Path dirPath;
    private Config cfg;
    private Config varsCfg;
    private StudyDto studyDto;
    private long adminUserId;

    // Helper to find id based on study and activity code.
    public static long findActivityId(Handle handle, long studyId, String activityCode) {
        return handle.attach(JdbiActivity.class)
                .findIdByStudyIdAndCode(studyId, activityCode)
                .orElseThrow(() -> new DDPException("Could not find id for activity " + activityCode + " and study id " + studyId));
    }

    public ActivityBuilder(Path dirPath, Config cfg, Config varsCfg, StudyDto studyDto, long adminUserId) {
        this.gson = GsonUtil.standardGson();
        this.validator = new GsonPojoValidator();
        this.dirPath = dirPath;
        this.cfg = cfg;
        this.varsCfg = varsCfg;
        this.studyDto = studyDto;
        this.adminUserId = adminUserId;
    }

    void run(Handle handle) {
        insertActivities(handle);
        insertActivityStatusIcons(handle);
    }

    void runSingle(Handle handle, String activityCode) {
        Long id = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(studyDto.getId(), activityCode).orElse(null);
        if (id != null) {
            LOG.warn("Activity {} already exists with id={}", activityCode, id);
            return;
        }

        Instant timestamp = ConfigUtil.getInstantIfPresent(cfg, "activityTimestamp");
        boolean found = false;

        for (Config activityCfg : cfg.getConfigList("activities")) {
            Config definition;
            try {
                definition = readDefinitionConfig(activityCfg.getString("filepath"));
            } catch (DDPException ignored) {
                continue;   // Try other definition files.
            }
            if (activityCode.equals(definition.getString("activityCode"))) {
                LOG.info("Using configuration for activityCode={} with filepath={}", activityCode, activityCfg.getString("filepath"));
                ActivityDef def = gson.fromJson(ConfigUtil.toJson(definition), ActivityDef.class);
                validateDefinition(def);
                insertActivity(handle, def, timestamp);
                insertActivityMappings(handle, activityCfg, def);
                found = true;
                break;
            }
        }

        if (!found) {
            LOG.error("Unable to find configuration for activityCode={}", activityCode);
        }
    }

    private void insertActivities(Handle handle) {
        Instant timestamp = ConfigUtil.getInstantIfPresent(cfg, "activityTimestamp");
        for (Config activityCfg : cfg.getConfigList("activities")) {
            Config definitionCfg = readDefinitionConfig(activityCfg.getString("filepath"));
            ActivityDef def = gson.fromJson(ConfigUtil.toJson(definitionCfg), ActivityDef.class);
            validateDefinition(def);
            long activityRevisionId = insertActivity(handle, def, timestamp).getRevId();
            insertActivityMappings(handle, activityCfg, def);
            insertActivityValidations(handle, activityCfg, def, activityRevisionId);
        }
    }

    public ActivityVersionDto insertActivity(Handle handle, Config definition, Instant timestamp) {
        ActivityDef def = gson.fromJson(ConfigUtil.toJson(definition), ActivityDef.class);
        validateDefinition(def);
        return insertActivity(handle, def, timestamp);
    }

    public ActivityVersionDto insertActivity(Handle handle, ActivityDef def, Instant timestamp) {
        long startMillis = (timestamp == null) ? Instant.now().toEpochMilli() : timestamp.toEpochMilli();
        String reason = String.format("Create activity with studyGuid=%s activityCode=%s versionTag=%s",
                def.getStudyGuid(), def.getActivityCode(), def.getVersionTag());
        RevisionMetadata meta = new RevisionMetadata(startMillis, adminUserId, reason);

        if (def.getActivityType() == ActivityType.FORMS) {
            return insertFormActivity(handle, (FormActivityDef) def, meta);
        } else {
            throw new DDPException("Unsupported activity type " + def.getActivityType());
        }
    }

    private ActivityVersionDto insertFormActivity(Handle handle, FormActivityDef def, RevisionMetadata meta) {
        ActivityDao activityDao = handle.attach(ActivityDao.class);

        ActivityVersionDto versionDto;
        if (def.getFormType() == FormType.CONSENT) {
            versionDto = activityDao.insertConsent((ConsentActivityDef) def, meta);
        } else {
            versionDto = activityDao.insertActivity(def, meta);
        }

        LOG.info("Created activity with id={}, code={}, versionTag={}, revisionId={}, revisionStart={}",
                def.getActivityId(), def.getActivityCode(), def.getVersionTag(), versionDto.getRevId(),
                Instant.ofEpochMilli(versionDto.getRevStart()).toString());
        return versionDto;
    }

    private void insertActivityMappings(Handle handle, Config activityCfg, ActivityDef def) {
        var activityDao = handle.attach(ActivityDao.class);
        for (Config mappingCfg : activityCfg.getConfigList("mappings")) {
            var type = ActivityMappingType.valueOf(mappingCfg.getString("type"));
            String stableId = ConfigUtil.getStrIfPresent(mappingCfg, "stableId");
            activityDao.insertActivityMapping(studyDto.getGuid(), type, def.getActivityId(), stableId);
            LOG.info("Added activity mapping for {} with type={}, activityId={}, subStableId={}",
                    def.getActivityCode(), type, def.getActivityId(), stableId);
        }
    }

    private void insertActivityValidations(Handle handle, Config activityCfg, ActivityDef def, long activityRevisionId) {
        if (activityCfg.hasPath("validations")) {
            insertValidations(handle, def.getActivityId(), def.getActivityCode(), activityRevisionId,
                    List.copyOf(activityCfg.getConfigList("validations")));
        }
    }

    public void insertValidations(Handle handle, long activityId, String activityCode, long revisionId, List<Config> validations) {
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        for (Config validationCfg : validations) {
            Template errorMessageTemplate = BuilderUtils.parseTemplate(validationCfg, "messageTemplate");
            if (errorMessageTemplate == null) {
                throw new DDPException("Validation error message template is required");
            }
            String errors = BuilderUtils.validateTemplate(errorMessageTemplate);
            if (errors != null) {
                throw new DDPException(String.format(
                        "Validation error message template for activity %d has the following validation errors: %s",
                        activityId, errors));
            }

            String precondition = ConfigUtil.getStrIfPresent(validationCfg, "precondition");
            String expression = validationCfg.getString("expression");
            ActivityValidationDto dto = new ActivityValidationDto(
                    activityId,
                    null,
                    precondition,
                    expression,
                    errorMessageTemplate
            );
            List<String> stableIds = validationCfg.getStringList("stableIds");
            dto.addAffectedFields(stableIds);
            jdbiActivity.insertValidation(dto, adminUserId, studyDto.getId(), revisionId);
            LOG.info("Added activity validations for {}, activityId={}, expression={}, affectedQuestionStableIds={}",
                    activityCode, activityId, expression, stableIds);
        }
    }

    private void insertActivityStatusIcons(Handle handle) {
        JdbiFormTypeActivityInstanceStatusType jdbiStatusIcon = handle.attach(JdbiFormTypeActivityInstanceStatusType.class);

        String reason = String.format("Create activity status icons for study=%s", studyDto.getGuid());
        long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), adminUserId, reason);

        for (Config iconCfg : cfg.getConfigList("activityStatusIcons")) {
            File file = dirPath.resolve(iconCfg.getString("filepath")).toFile();
            if (!file.exists()) {
                throw new DDPException("Activity status icon file is missing: " + file);
            }

            byte[] iconBytes;
            try (FileInputStream input = new FileInputStream(file)) {
                iconBytes = IOUtils.toByteArray(input);
            } catch (IOException e) {
                throw new DDPException(e);
            }

            InstanceStatusType statusType = InstanceStatusType.valueOf(iconCfg.getString("statusType"));
            for (FormType formType : FormType.values()) {
                long iconId = jdbiStatusIcon.insert(studyDto.getId(), formType, statusType, iconBytes, revId);
                LOG.info("Added activity status icon with id={}, formType={}, statusType={}, revisionId={}",
                        iconId, formType, statusType, revId);
            }
        }
    }

    public Config readDefinitionConfig(String filepath) {
        File file = dirPath.resolve(filepath).toFile();
        if (!file.exists()) {
            throw new DDPException("Activity definition file is missing: " + file);
        }

        Config definition = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        if (definition.isEmpty()) {
            throw new DDPException("Activity definition file is empty: " + file);
        }
        return definition;
    }

    private void validateDefinition(ActivityDef def) {
        List<JsonValidationError> errors = validator.validateAsJson(def);
        if (!errors.isEmpty()) {
            String msg = errors.stream()
                    .map(JsonValidationError::toDisplayMessage)
                    .collect(Collectors.joining(", "));
            throw new DDPException(String.format(
                    "Activity definition with code=%s and versionTag=%s has validation errors: %s",
                    def.getActivityCode(), def.getVersionTag(), msg));
        }
    }
}
