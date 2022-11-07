package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.TabularBlockDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public class SingularAOMAddParticipantUpdates implements CustomTask {
    private static final String PATCH_PATH = "patches/add-participant-aom-location.conf";

    // Keypaths of specific data in the study configuration file
    private static final String KEY_PATH_STUDY_GUID = "study.guid";

    /*
     * 
     *  Types
     * 
     */

    public interface SqlHelper extends SqlObject {

        /**
         * Increments the display order all subsequent ordered blocks following blocK-id
         *  by the given offset.
         */
        @SqlUpdate("UPDATE form_section__block AS fsb "
                +  "INNER JOIN form_section__block AS fsb2 ON fsb2.block_id = :blockId "
                +  "SET fsb.display_order = fsb.display_order + :offset "
                +  "WHERE fsb.form_section_id = :sectionId "
                +  "  AND fsb.revision_id = :revisionId "
                +  "  AND fsb.display_order > fsb2.display_order")
        void offsetDisplayOrder(@Bind long blockId,
                                @Bind long sectionId,
                                @Bind long revisionId,
                                @Bind long offset);
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static enum Metadata {
        DESCRIPTION("description"),
        STUDY("study"),
        ACTIVITY_VALIDATIONS("activityValidations"),
        QUESTIONS("questions");

        public final String key;
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static enum Content {
        ACTIVITY("activity"),
        VERSION_TAG("versionTag"),
        VALIDATIONS("validations"),
        BLOCKS("blocks"),
        INSERT_AFTER("insert-after-stableid");

        public final String key;
    }

    /*
     * 
     * Instance implementation
     * 
     */

    private Path studyConfigRoot;
    private Config studyConfig;
    private Config patchConfig;
    private Config varsConfig;

    private final Gson gson = GsonUtil.standardGson();
    private final GsonPojoValidator gsonValidator = new GsonPojoValidator();

    @Override
    public void init(Path configPath, Config studyConfig, Config varsConfig) {
        final var patchPath = configPath.getParent().resolve(PATCH_PATH);
        final Config patchConfig;
        try {
            patchConfig = ConfigFactory.parseFile(patchPath.toFile()).resolveWith(varsConfig);
        } catch (ConfigException configException) {
            throw failedToLoadPatchError(patchPath.toString(), configException);
        }

        final var studyGuid = studyConfig.getString(KEY_PATH_STUDY_GUID);
        final var targetStudy = patchConfig.getString(Metadata.STUDY.key);
        if (!targetStudy.equals(studyGuid)) {
            throw studyNotSupportedError(taskName(), targetStudy, studyGuid);
        }

        this.studyConfigRoot = configPath.getParent();
        this.patchConfig = patchConfig;
        this.studyConfig = studyConfig;
        this.varsConfig = varsConfig;
    }

    @Override
    public void run(Handle handle) {
        final var targetStudy = patchConfig.getString(Metadata.STUDY.key);


        log.info("TASK:: {}", taskName());
        log.info("Running patch {} against [study:{}]\n\tdescription = {}",
                taskName(),
                targetStudy,
                patchConfig.getString(Metadata.DESCRIPTION.key));

        if (shouldRun(handle) == false) {
            log.info("patch will be skipped.");
            log.info("TASK:: {} completed successfully.", taskName());
            return;
        }

        patchConfig.getConfigList(Metadata.QUESTIONS.key)
                .forEach((blockPatch) -> {
                    final var targetActivity = blockPatch.getString(Content.ACTIVITY.key);
                    final var versionTag = blockPatch.getString(Content.VERSION_TAG.key);
                    final var insertAfter = blockPatch.getString(Content.INSERT_AFTER.key);
                    final var formBlocks = blockPatch.getConfigList(Content.BLOCKS.key);

                    log.info("Inserting {} blocks into [activity:{},version:{}] after [question:{}]",
                            formBlocks.size(),
                            targetActivity,
                            versionTag,
                            insertAfter);
                    processStudyQuestionBlockPatch(handle, targetStudy, targetActivity, versionTag, insertAfter, formBlocks);
                });

        
        final var activityValidationsActions = patchConfig.getConfigList(Metadata.ACTIVITY_VALIDATIONS.key);
        log.info("inserting {} activity validations", activityValidationsActions.size());
        activityValidationsActions.forEach((validationPatch) -> {
            insertValidations(handle,
                    targetStudy,
                    validationPatch.getString(Content.ACTIVITY.key),
                    validationPatch.getConfigList(Content.VALIDATIONS.key));
        });
    }

    private boolean shouldRun(Handle handle) {
        final var patchConfig = this.patchConfig;
        final var targetStudy = getTargetStudyGuid();

        final var patchQuestionIds = patchConfig.getConfigList(Metadata.QUESTIONS.key).stream()
                .map(config -> config.getConfigList(Content.BLOCKS.key))
                .flatMap(List::stream)
                .map(config -> config.getString("question.stableId"))
                .collect(Collectors.toUnmodifiableSet());

        final var jdbiQuestion = handle.attach(JdbiQuestion.class);
        final var existingStableIds = patchQuestionIds.stream()
                .map(stableId -> jdbiQuestion.findLatestDtoByStudyGuidAndQuestionStableId(targetStudy, stableId))
                .map(maybeQuestion -> maybeQuestion.map(QuestionDto::getStableId))
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());

        final var stableIdsToInsert = new HashSet<>(patchQuestionIds);
        stableIdsToInsert.removeAll(existingStableIds);

        if (stableIdsToInsert.isEmpty()) {
            /*
             * All of the questions we expected to insert already exist in the study. Assume
             *  that the necessary modifications have been made and indicate to the caller that
             *  it should not continue.
             * This is a non-fatal case, and to be expected if a study has been re-deployed with
             *  an updated configuration.
             */
            log.info("stable ids [{}] already exist in the target [study:{}]",
                    String.join(", ", patchQuestionIds),
                    targetStudy);
            return false;
        } else if (stableIdsToInsert.equals(patchQuestionIds) == false) {
            /* 
             * If the code flows here, the patch is in a situation where a partial set
             *  of the expected stable ids were found in the target study. This is an exceptional
             *  case as it's potentially indicative of something being grossly out of sync between
             *  the study configuration and the applied patch. This situation needs developer intervention.
             */
            var message = "Expected to insert questions with stable ids [%s], but found existing questions with stable ids [%s]";
            message = String.format(message, String.join(", ", patchQuestionIds), String.join(", ", existingStableIds));
            log.error("study configuration is inconsistent with patch content. {}", message);
            throw new DDPException(message);
        } else {
            return true;
        }
    }

    private void processStudyQuestionBlockPatch(@NonNull Handle handle,
                                                @NonNull String studyGuid,
                                                @NonNull String activityCode,
                                                @NonNull String versionTag,
                                                @NonNull String insertAfterStableId,
                                                @NonNull List<? extends Config> formBlockDefs) {
        assert StringUtils.isNotBlank(studyGuid);
        assert formBlockDefs.isEmpty() == false;

        final var daoStudy = handle.attach(JdbiUmbrellaStudy.class);
        final var daoActivity = handle.attach(JdbiActivity.class);
        final var daoActivityVersion = handle.attach(JdbiActivityVersion.class);
        final var daoForm = handle.attach(FormActivityDao.class);
        final var daoSectionBlock = handle.attach(SectionBlockDao.class);
        final var sqlHelper = handle.attach(SqlHelper.class);

        final var studyDto = Optional.ofNullable(daoStudy.findByStudyGuid(studyGuid))
                .orElseThrow(() -> studyNotFoundError(studyGuid));

        final var activity = daoActivity.findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .orElseThrow(() -> activityNotFoundError(activityCode, studyGuid));

        final var activityVersion = daoActivityVersion.findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag)
                .orElseThrow(() -> activityVersionNotFoundError(versionTag, activityCode, studyGuid));

        final var currentActivityDef = daoForm.findDefByDtoAndVersion(activity, activityVersion);

        final var indexBlock = findBlocksForQuestions(currentActivityDef, List.of(insertAfterStableId)).stream()
                .findFirst()
                .orElseThrow(() -> questionNotFoundError(insertAfterStableId, activityCode, versionTag, studyGuid));

        final var section = sectionForBlock(currentActivityDef, indexBlock.getBlockGuid())
                .orElseThrow(() -> noSectionForBlockError(indexBlock.getBlockGuid(),
                        insertAfterStableId,
                        activityCode,
                        versionTag,
                        studyGuid));

        final var indexBlockMembership = daoSectionBlock.getJdbiFormSectionBlock()
                .getActiveMembershipByBlockId(indexBlock.getBlockId())
                .orElseThrow(() -> blockMissingSectionMembershipError(section.getSectionCode(),
                        indexBlock.getBlockGuid(),
                        activityCode,
                        versionTag,
                        studyGuid));

        sqlHelper.offsetDisplayOrder(indexBlock.getBlockId(),
                section.getSectionId(),
                activityVersion.getRevId(),
                SectionBlockDao.DISPLAY_ORDER_GAP * formBlockDefs.size());

        var displayOrder = indexBlockMembership.getDisplayOrder() + SectionBlockDao.DISPLAY_ORDER_GAP;
        for (final var formBlockConfig : formBlockDefs) {
            final var formBlockDef = createBlockDef(formBlockConfig);

            daoSectionBlock.insertBlockForSection(activity.getActivityId(),
                    section.getSectionId(),
                    displayOrder,
                    formBlockDef,
                    activityVersion.getRevId());

            displayOrder += SectionBlockDao.DISPLAY_ORDER_GAP;
        }
    }

    private void insertValidations(@NonNull Handle handle,
                                    @NonNull String studyGuid,
                                    @NonNull String activityCode,
                                    @NonNull List<? extends Config> validations) {
        assert StringUtils.isNotBlank(studyGuid);
        assert StringUtils.isNotBlank(activityCode);
        assert validations.isEmpty() == false;

        final var jdbiActivity = handle.attach(JdbiActivity.class);
        final var jdbiStudy = handle.attach(JdbiUmbrellaStudy.class);
        final var jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);
        final var jdbiRevision = handle.attach(JdbiRevision.class);

        final var studyDto = jdbiStudy.findByStudyGuid(studyGuid);
        final var activity = jdbiActivity.findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .orElseThrow(() -> activityNotFoundError(activityCode, studyGuid));

        final var adminUserGuid = studyConfig.getString("adminUser.guid");
        final var adminUser = getUser(handle, adminUserGuid).orElseThrow(() -> studyAdminUserNotFoundError(adminUserGuid, studyGuid));

        // Derived from the InsertActivityValidations.run(Handle) method
        final var initialRevision = jdbiActivityVersion.findAllVersionsInAscendingOrder(activity.getActivityId())
                    .stream()
                    .findFirst()
                    .map(revision -> jdbiRevision.copyStart(revision.getRevId()))
                    .orElseThrow(() -> activeRevisionNotFoundError(activityCode, studyGuid));

        final var activityBuilder = new ActivityBuilder(studyConfigRoot, studyConfig, varsConfig, studyDto, adminUser.getUserId());
        activityBuilder.insertValidations(handle,
                activity.getActivityId(),
                activity.getActivityCode(),
                initialRevision,
                List.copyOf(validations));  /* 
                                             * Using List.copyOf in order to work around typing issues.
                                             *  `ActivityBuilder.insertValidations` expect a List<Config>, but Typesafe
                                             *  returns a List<? extends Config>.
                                            */
        
        log.info("Added activity {} validations for [activity:{},id:{}]",
                validations.size(),
                activity.getActivityCode(),
                activity.getActivityId());
    }

    private Optional<UserDto> getUser(@NonNull Handle handle, String userGuid) {
        assert studyConfig != null;

        final var userSql = handle.attach(JdbiUser.class);

        return Optional.ofNullable(userSql.findByUserGuid(userGuid));
    }

    private String getTargetStudyGuid() {
        return this.patchConfig.getString(Metadata.STUDY.key);
    }

    private Optional<FormSectionDef> sectionForBlock(FormActivityDef activityDefinition, String blockGuid) {
        return activityDefinition.getAllSections().stream()
            // A block guid should belong to one, and only one, section
            .filter((section) -> section.getBlocks().stream().anyMatch((block) -> block.getBlockGuid().equals(blockGuid)))
            .findFirst();
    }

    private List<QuestionBlockDef> findBlocksForQuestions(FormActivityDef activityDefinition, Collection<String> stableIds) {
        final var stableIdSet = new HashSet<>(stableIds);

        return activityDefinition.getAllSections().stream()
            .map((section) -> section.getBlocks())
            .flatMap(List::stream)
            .map((block) -> {
                // Relies on container blocks not being themselves nested.
                // If that assumption changes, this code may fail to find
                // specific questions if they are nested too deep.
                switch (block.getBlockType()) {
                    case QUESTION:
                        return (List<FormBlockDef>)(List.of(block));
                    case CONDITIONAL:
                        var conditionalBlock = ConditionalBlockDef.class.cast(block);
                        return (List<FormBlockDef>)conditionalBlock.getNested();
                    case GROUP:
                        var groupBlock = GroupBlockDef.class.cast(block);
                        return (List<FormBlockDef>)groupBlock.getNested();
                    case TABULAR:
                        var tabularBlock = TabularBlockDef.class.cast(block);
                        return tabularBlock.getBlocks();
                    default:
                        return new ArrayList<FormBlockDef>();
                }
            })
            .flatMap(List::stream)
            .filter((block) -> block.getBlockType() == BlockType.QUESTION)
            .map((block) -> QuestionBlockDef.class.cast(block))
            .filter((questionBlock) -> stableIdSet.contains(questionBlock.getQuestion().getStableId()))
            .collect(Collectors.toList());
    }

    private FormBlockDef createBlockDef(Config blockConfig) {
        final var blockJson = ConfigUtil.toJson(blockConfig);
        final var blockDef = gson.fromJson(blockJson, FormBlockDef.class);
        final var validationErrors = gsonValidator.validateAsJson(blockDef);

        if (validationErrors.isEmpty()) {
            // No errors found!
            // Just return the object
            return blockDef;
        }

        final var message = validationErrors.stream()
                .map(JsonValidationError::toDisplayMessage)
                .collect(Collectors.joining(", "));

        final DDPException validationException;
        switch (blockDef.getBlockType()) {
            case QUESTION:
                validationException = new DDPException(String.format(
                        "Question definition with stableId=%s has validation errors: %s",
                        blockDef.getQuestions().findFirst().get().getStableId(),
                        message));
                break;
            default:
                validationException = new DDPException(String.format(
                    "%s block definition with has validation errors: %s",
                    blockDef.getBlockType(),
                    message));
        }

        throw validationException;
    }

    /*
     * 
     * ## Exception message functions
     * 
     */

    private DDPException failedToLoadPatchError(String path, Exception cause) {
        return new DDPException(String.format("failed to load patch data located at '%s'", path), cause);
    }

    private DDPException studyAdminUserNotFoundError(String userGuid, String studyGuid) {
        return new DDPException(String.format("failed to find admin [user:%s] for [study:%s]", userGuid, studyGuid));
    }

    private DDPException studyNotSupportedError(String taskName, String patchStudyGuid, String configStudyGuid) {
        return new DDPException(String.format("Patch %s targets study '%s', but is being run against '%s'",
                taskName,
                patchStudyGuid,
                configStudyGuid));
    }

    private DDPException studyNotFoundError(String studyGuid) {
        return new DDPException(String.format("failed to locate the study [study:%s]'", studyGuid));
    }

    private DDPException activityNotFoundError(String activityCode, String studyGuid) {
        return new DDPException(String.format("failed to find [activity:%s] in [study:%s]", activityCode, studyGuid));
    }

    private DDPException activeRevisionNotFoundError(String activityCode, String studyGuid) {
        return new DDPException(String.format("failed to find an active revision for [activity:%s] in [study:%s]",
                activityCode,
                studyGuid));
    }

    private DDPException activityVersionNotFoundError(String versionTag, String activityCode, String studyGuid) {
        return new DDPException(String.format("failed to find [activity:%s,version:%s] in [study:%s]",
                activityCode,
                versionTag,
                studyGuid));
    }

    private DDPException questionNotFoundError(String stableId, String activityCode, String versionTag, String studyGuid) {
        return new DDPException(String.format("No [question:%s] found in [activity:%s,version:%s] ([study:%s])",
                stableId,
                activityCode,
                versionTag,
                studyGuid));
    }

    private DDPException noSectionForBlockError(String blockGuid,
            String stableId,
            String activityCode,
            String versionTag,
            String studyGuid) {
        return new DDPException(String.format(
                "no section for [block:%s] with [question:%s] in [activity:%s,version:%s] ([study:%s])",
                blockGuid,
                stableId,
                activityCode,
                versionTag,
                studyGuid));
    }

    private DDPException blockMissingSectionMembershipError(String sectionCode,
            String blockGuid,
            String activityCode,
            String versionTag,
            String studyGuid) {
        return new DDPException(String.format(
                "a membership record in [section:%s] was not found for [block:%s] in [activity:%s,version:%s] ([study:%s])",
                sectionCode,
                blockGuid,
                activityCode,
                versionTag,
                studyGuid));
    }

    /*
     * 
     * Helper functions for getting information about the patch itself
     * 
     */

    private String taskName() {
        return this.getClass().getSimpleName();
    }
}
