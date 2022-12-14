package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
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
import org.broadinstitute.ddp.db.dao.JdbiPicklistOption;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dto.PicklistGroupDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public final class PancanAddAdditionalDiagnosis implements CustomTask {
    private static final String PATCH_CONF_NAME = "add-additional-diagnosis.conf";

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static enum Metadata {
        DESCRIPTION("description"),
        STUDY("study"),
        EXPECTED_OPTIONS("expected-options"),
        TARGET_GROUP_SID("target-group-sid"),
        INSERT_AFTER_SID("insert-after-sid"),
        NEW_OPTIONS("options");

        @Getter
        private final String key;
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
                + "    qsc.stable_id"
                + "  FROM question AS q"
                + "    JOIN question_stable_code AS qsc ON qsc.question_stable_code_id = q.question_stable_code_id"
                + " WHERE q.question_id = :questionId")
        public String fetchQuestionStableIdById(@Bind("questionId") long questionId);

        @SqlUpdate("INSERT INTO picklist_grouped_option (picklist_group_id, picklist_option_id)"
                + " VALUES (:groupId, :optionId)")
        public void linkOptionToGroupId(@Bind("optionId") long optionId,
                @Bind("groupId") long groupId);
    }
    
    private Path configurationRoot;
    private Config studyConfiguration;
    private Config patchConfiguration;
    
    private final Gson gson = GsonUtil.standardGson();

    protected Path getConfigurationRoot() {
        return configurationRoot;
    }

    protected Path getPatchRoot() {
        assert configurationRoot != null;
        return getConfigurationRoot().resolve("patches");
    }

    protected Path getPatchConfigurationPath() {
        return getPatchRoot().resolve(PATCH_CONF_NAME);
    }

    protected String getConfigurationStudy() {
        assert studyConfiguration != null;
        return studyConfiguration.getString("study.guid");
    }

    protected String getTargetStudy() {
        assert patchConfiguration != null;
        return patchConfiguration.getString(Metadata.STUDY.getKey());
    }

    protected String getTaskName() {
        return this.getClass().getSimpleName();
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
        log.info("TASK::{}", getTaskName());
        log.info("description: {}", patchConfiguration.getString(Metadata.DESCRIPTION.getKey()));

        final var jdbiPatchHelper = handle.attach(SqlHelper.class);
        final var jdbiPicklistQuestion = handle.attach(PicklistQuestionDao.class);
        final var jdbiPicklistOption = handle.attach(JdbiPicklistOption.class);

        final var picklistGroupName = patchConfiguration.getString(Metadata.TARGET_GROUP_SID.getKey());
        final var containingQuestionIds = jdbiPatchHelper.fetchPicklistGroupsByName(getTargetStudy(), picklistGroupName)
                .stream()
                .map((group) -> jdbiPatchHelper.fetchQuestionIdForGroup(group.getId()))
                .collect(Collectors.toList());

        final var nowTimestamp = Instant.now().toEpochMilli();
        final var questionToGroupObjects = jdbiPicklistQuestion.findOrderedGroupAndOptionDtos(containingQuestionIds, nowTimestamp);

        for (final var questionOptionDetails : questionToGroupObjects.entrySet()) {
            /*
            * Deserializing the new PicklistOptionDefs inside of the loop (and repeating the serialize/deserialize every loop)
            *   because PicklistQuestionDao.insertOption(long, PicklistOptionDef, int, long) modifies the PicklistOptionDef
            *   passed in the 2nd parameter. This is a risky behavior to refactor, so my workaround for now is to reload the entire
            *   list of new PicklistOptionDefs on every loop.
            */
            final var newOptionDefs = patchConfiguration.getConfigList(Metadata.NEW_OPTIONS.getKey())
                    .stream()
                    .map((optionDefConfig) -> gson.fromJson(ConfigUtil.toJson(optionDefConfig), PicklistOptionDef.class))
                    .collect(Collectors.toList());
            
            final var questionId = questionOptionDetails.getKey();
            final var questionStableId = jdbiPatchHelper.fetchQuestionStableIdById(questionId);
            log.info("Updating picklist options for [study:{}, question:{}]", getTargetStudy(), questionStableId);

            // The picklist option group
            final var optionGroup = questionOptionDetails.getValue().getGroups()
                    .stream()
                    .filter((group) -> picklistGroupName.equals(group.getStableId()))
                    .findFirst()
                    .orElseThrow(() -> picklistGroupNotFound(getTargetStudy(), questionStableId, picklistGroupName));

            // The picklist options contained within the previous group
            final var groupOptions = questionOptionDetails.getValue().getGroupIdToOptions().get(optionGroup.getId());

            // Check to see if all the options expected to be in the group are there (see `expected-options` in the patch configuration).
            // This check disregards the ordering of the stable ids.
            final var existingPicklistOptionStableIds = groupOptions.stream()
                    .map(PicklistOptionDto::getStableId)
                    .collect(Collectors.toSet());
            
            final var expectedOptions = new HashSet<String>(patchConfiguration.getStringList(Metadata.EXPECTED_OPTIONS.getKey()));
            if (!existingPicklistOptionStableIds.equals(expectedOptions)) {
                throw studyHasBeenModified(getTargetStudy(),
                        questionStableId,
                        expectedOptions,
                        existingPicklistOptionStableIds);
            }

            // This is the option after which all the new should be inserted (in display order).
            // See the `insert-after-sid` option in the patch config file to fine tune this if changes are needed.
            final var insertAfterOptionStableId = patchConfiguration.getString(Metadata.INSERT_AFTER_SID.getKey());
            final var targetOption = groupOptions
                    .stream()
                    .filter((option) -> option.getStableId().equals(insertAfterOptionStableId))
                    .findFirst()
                    .orElseThrow(() -> picklistOptionNotFound(getTargetStudy(), questionStableId, insertAfterOptionStableId));

            var displayOrder = targetOption.getDisplayOrder() + PicklistQuestionDao.DISPLAY_ORDER_GAP;
            for (final var option : newOptionDefs) {
                log.info("Inserting picklist option {} for [study:{}, question:{}] at display order {}",
                        option.getStableId(),
                        getTargetStudy(),
                        questionStableId,
                        displayOrder);

                final var optionId = jdbiPicklistQuestion.insertOption(questionId,
                        option,
                        displayOrder,
                        optionGroup.getRevisionId());
                
                log.info("Linking option {}({}) to group {}({})",
                        option.getStableId(),
                        optionId,
                        optionGroup.getStableId(),
                        optionGroup.getId());
                jdbiPatchHelper.linkOptionToGroupId(optionId, optionGroup.getId());

                displayOrder += PicklistQuestionDao.DISPLAY_ORDER_GAP;
            }

            /*
             * Go back to all the PicklistOptionDtos we originally fetched, and figure out
             * which ones need to have their display order offset (each option should be
             * advanced by numberOfInsertedOptions * DISPLAY_ORDER_GAP)
             */
            final var optionsToUpdate = groupOptions.subList(groupOptions.indexOf(targetOption) + 1, groupOptions.size());
            for (final var option : optionsToUpdate) {
                log.info("Changing display order for picklist option {}({}) from {} to {}",
                        option.getStableId(),
                        option.getId(),
                        option.getDisplayOrder(),
                        displayOrder);
                option.setDisplayOrder(displayOrder);
                jdbiPicklistOption.update(option);
                displayOrder += PicklistQuestionDao.DISPLAY_ORDER_GAP;
            }
        }

        log.info("TASK::{} completed successfully", getTaskName());
    }

    private DDPException studyHasBeenModified(String study,
            String questionStableId,
            Iterable<String> expectedOptionStableIds,
            Iterable<String> currentOptionStableIds) {
        return new DDPException(String.format("Mismatch in expected options for [study:%s, question:%s]. "
                + "Fetched picklist options [%s], but expected picklist options [%s]. The study has been modified, and"
                + " the patch's `expected-options` value must be updated to match the current state of the study.",
                study,
                questionStableId,
                String.join(", ", expectedOptionStableIds),
                String.join(", ", currentOptionStableIds)));
    }

    private DDPException picklistOptionNotFound(String study, String question, String option) {
        return new DDPException(String.format("unable to find picklist option %s in [study:%s, question:%s]",
                option,
                study,
                question));
    }

    private DDPException picklistGroupNotFound(String study, String question, String groupName) {
        return new DDPException(String.format("unable to find picklist group %s in [study:%s, question:%s]",
                groupName,
                study,
                question));
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
