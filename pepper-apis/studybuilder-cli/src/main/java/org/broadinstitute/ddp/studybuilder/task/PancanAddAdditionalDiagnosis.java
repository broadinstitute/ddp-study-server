package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.broadinstitute.ddp.db.dao.JdbiPicklistGroup;
import org.broadinstitute.ddp.db.dao.JdbiPicklistOption;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.PicklistGroupDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

@Slf4j
public final class PancanAddAdditionalDiagnosis implements CustomTask {
    private static final String PATCH_CONF_NAME = "add-additional-diagnosis.conf";

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static enum Metadata {
        DESCRIPTION("description"),
        STUDY("study"),
        OPTION_GROUP("option-group");

        @Getter
        private final String key;
    }

    @FunctionalInterface
    private static interface DeleteAction {
        public void delete(Handle handle);
    }

    private static interface SqlHelper extends SqlObject {
        
        @SqlQuery("SELECT"
                + "    pg.picklist_group_id AS picklist_group_id,"
                + "    pg.group_stable_id AS group_stable_id,"
                + "    pg.name_template_id AS name_template_id,"
                + "    pg.display_order AS display_order,"
                + "    pg.revision_id AS revision_id,"
                + "    revision.start_date AS revision_start_timestamp,"
                + "    revision.end_date AS revision_end_timestamp"
                + "  FROM picklist_group AS pg"
                + "  JOIN picklist_question AS pq ON pq.question_id = pg.picklist_question_id"
                + "  JOIN question AS q ON q.question_id = pq.question_id"
                + "  JOIN study_activity AS sa ON q.study_activity_id = sa.study_activity_id"
                + "  JOIN umbrella_study AS us ON sa.study_id = us.umbrella_study_id"
                + "  JOIN revision ON revision.revision_id = pg.revision_id"
                + "  WHERE group_stable_id = :groupStableId"
                + "    AND us.guid = :studyGuid")
        @RegisterConstructorMapper(PicklistGroupDto.class)
        public List<PicklistGroupDto> fetchPicklistGroupsByName(
                @Bind("studyGuid") String studyGuid, 
                @Bind("groupStableId") String groupStableId);

        @SqlQuery("SELECT"
                + "    po.picklist_option_id AS picklist_option_id,"
                + "    po.picklist_option_stable_id AS picklist_option_stable_id,"
                + "    po.value AS value,"
                + "    po.option_label_template_id AS option_label_template_id,"
                + "    po.tooltip_template_id AS tooltip_template_id,"
                + "    po.detail_label_template_id AS detail_label_template_id,"
                + "    po.allow_details AS allow_details,"
                + "    po.is_exclusive AS is_exclusive,"
                + "    po.is_default AS is_default,"
                + "    po.display_order AS display_order,"
                + "    po.nested_options_template_id AS nested_options_template_id,"
                + "    revision.revision_id AS revision_id,"
                + "    revision.start_date AS revision_start_timestamp,"
                + "    revision.end_date AS revision_end_timestamp"
                + "  FROM picklist_option AS po"
                + "    JOIN picklist_grouped_option AS pgo ON pgo.picklist_option_id = po.picklist_option_id"
                + "    JOIN picklist_group AS pg ON pg.picklist_group_id = pgo.picklist_group_id"
                + "    JOIN picklist_question AS pq ON pq.question_id = po.picklist_question_id"
                + "    JOIN revision ON revision.revision_id = po.revision_id"
                + "  WHERE"
                + "    pg.picklist_group_id = :groupId"
                + "  ORDER BY"
                + "    po.display_order ASC")
        @RegisterConstructorMapper(PicklistOptionDto.class)
        public List<PicklistOptionDto> fetchPicklistOptionsByGroup(@Bind("groupId") long groupId);

        @SqlQuery("SELECT"
                + "    pg.picklist_question_id"
                + "  FROM picklist_group AS pg"
                + "  WHERE pg.picklist_group_id = :groupId")
        public long fetchQuestionIdForGroup(@Bind("groupId") long groupId);

        @SqlQuery("SELECT"
                + "    po.picklist_question_id"
                + "  FROM picklist_option AS po"
                + "  WHERE po.picklist_option_id = :groupId")
        public long fetchQuestionIdForOption(@Bind("optionId") long optionId);
    }
    
    private Path configurationRoot;
    private Config studyConfiguration;
    private Config patchConfiguration;
    
    private final Gson gson = GsonUtil.standardGson();

    protected Path getConfigurationRoot() {
        return configurationRoot;
    }

    protected Path getPatchRoot() {
        return getConfigurationRoot().resolve("patches");
    }

    protected Path getPatchConfigurationPath() {
        return getPatchRoot().resolve(PATCH_CONF_NAME);
    }

    protected String getConfigurationStudy() {
        return studyConfiguration.getString("study.guid");
    }

    protected String getTargetStudy() {
        assert patchConfiguration != null;
        return patchConfiguration.getString(Metadata.STUDY.getKey());
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.configurationRoot = cfgPath.getParent();
        this.studyConfiguration = studyCfg;

        final var patchConfigurationPath = getPatchConfigurationPath();
        try {
            var patchConfiguration = ConfigFactory.parseFile(patchConfigurationPath.toFile());
            final var resolveOptions = ConfigResolveOptions.defaults().setAllowUnresolved(true);
            patchConfiguration = patchConfiguration.resolve(resolveOptions);
            this.patchConfiguration = patchConfiguration.resolveWith(varsCfg);
        } catch (ConfigException configException) {
            throw failedToLoadPatchError(patchConfigurationPath.toString(), configException);
        }

        if (!getTargetStudy().equals(getConfigurationStudy())) {
            throw invalidStudyError(getConfigurationStudy(), getTargetStudy());
        }
    }

    @Override
    public void run(Handle handle) {
        final var optionGroupConfig = patchConfiguration.getConfig(Metadata.OPTION_GROUP.getKey());
        final var optionGroupDef = gson.fromJson(ConfigUtil.toJson(optionGroupConfig), PicklistGroupDef.class);

        final var jdbiPatchHelper = handle.attach(SqlHelper.class);

        final var nowTimestamp = Instant.now().toEpochMilli();
        final var existingGroups = jdbiPatchHelper.fetchPicklistGroupsByName(getTargetStudy(), optionGroupDef.getStableId())
                .stream()
                .filter((group) -> group.getRevisionStartTimestamp() <= nowTimestamp)
                .filter((group) -> (group.getRevisionEndTimestamp() == null) || (group.getRevisionEndTimestamp() > nowTimestamp))
                .collect(Collectors.toList());

        for (final var group : existingGroups) {

        }

        return;
    }

    private DDPException invalidStudyError(String configuredStudy, String targetStudy) {
        return new DDPException(String.format("patch targets the %s study, and can not be run against study %s",
                targetStudy,
                configuredStudy));
    }

    private DDPException failedToLoadPatchError(String path, Exception cause) {
        return new DDPException(String.format("failed to load patch data located at '%s'", path), cause);
    }
}
