package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.TabularBlockDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.pex.Expression;
import org.jdbi.v3.core.Handle;

/**
 * Updates the PEX shownExpr for 2 questions in the ABOUT_HEALTHY
 * activity for Singular.
 * 
 * <p>See the file ddp-8539-about-healthy-pex.conf and Jira ticket DDP-8539
 * for more details about the specifics of the change.
 */
@Slf4j
public class SingularAboutHealthyPexTask implements CustomTask  {
    private static class Constants {
        private static final String PATCH_PATH = "patches/ddp-8539-about-healthy-pex.conf";

        // Keypaths of specific data in the study configuration file
        private static final String KEY_PATH_STUDY_GUID = "study.guid";
    }

    private static class Keys {
        private static class ChangeSet {
            private static final String AUTHOR = "author";
            private static final String DESCRIPTION = "description";
            private static final String STUDY = "study";
            private static final String ACTIVITY = "activity";
            private static final String VERSION_TAG = "versionTag";
            private static final String CONTENT = "content";
        }

        private static class ChangeContent {
            private static final String STABLE_ID = "stableId";
            private static final String EXPECTED = "expectedShownExpression";
            private static final String UPDATED = "updatedShownExpression";
        }
    }

    private Config studyConfig;
    private Config changes;

    private JdbiUmbrellaStudy studySql;
    private JdbiActivity activitySql;
    private JdbiActivityVersion activityVersionSql;
    private FormActivityDao activityDao;
    private JdbiExpression expressionSql;

    @Override
    public void init(Path configPath, Config studyConfig, Config varsConfig) {
        var patchPath = configPath.getParent().resolve(Constants.PATCH_PATH);
        final Config patchConfig;
        try {
            patchConfig = ConfigFactory.parseFile(patchPath.toFile()).resolveWith(varsConfig);
        } catch (ConfigException configException) {
            var message = String.format("failed to load patch data file expected at '%s'", patchPath);
            throw new DDPException(message, configException);
        }

        var studyGuid = studyConfig.getString(Constants.KEY_PATH_STUDY_GUID);
        var targetStudy = patchConfig.getString(Keys.ChangeSet.STUDY);
        if (!targetStudy.equals(studyGuid)) {
            throw new DDPException(String.format("Patch %s targets study '%s', but is being run against '%s'",
                    this.getClass().getSimpleName(),
                    targetStudy,
                    studyGuid));
        }

        this.changes = patchConfig;
        this.studyConfig = studyConfig;
    }

    @Override
    public void run(Handle handle) {
        var taskName = this.getClass().getSimpleName();
        log.info("TASK:: {}", taskName);

        daoSetup(handle);

        var studyGuid = studyConfig.getString(Constants.KEY_PATH_STUDY_GUID);
        var targetActivity = changes.getString(Keys.ChangeSet.ACTIVITY);
        var targetVersion = changes.getString(Keys.ChangeSet.VERSION_TAG);

        var studyDto = Optional.ofNullable(studySql.findByStudyGuid(studyGuid)).orElseThrow(() -> {
            return new DDPException(String.format("failed to locate the study '%s'", studyGuid));
        });

        var activity = activitySql.findActivityByStudyGuidAndCode(studyGuid, targetActivity).orElseThrow(() -> {
            return new DDPException(String.format("failed to locate the activity '%s' in study '%s'",
                    targetActivity,
                    studyGuid));
        });

        var activityVersion = activityVersionSql.findByActivityCodeAndVersionTag(studyDto.getId(), targetActivity, targetVersion)
                .orElseThrow(() -> {
                    return new DDPException(String.format("failed to locate the version tag '%s' for the activity '%s' in study '%s'",
                            targetVersion,
                            targetActivity,
                            studyGuid));
                });

        var currentActivityDef = activityDao.findDefByDtoAndVersion(activity, activityVersion);

        final Map<String, Config> questionsToUpdate = changes.getConfigList(Keys.ChangeSet.CONTENT).stream()
                .collect(Collectors.toMap((change) -> change.getString(Keys.ChangeContent.STABLE_ID),
                        (change) -> change));

        var blocks = findBlocksForQuestions(currentActivityDef, questionsToUpdate.keySet());
        
        blocks.stream()
                .forEach((block) -> {
                    var stableId = block.getQuestion().getStableId();
                    var readableName = String.format("block:%s, question:%s", block.getBlockGuid(), stableId);

                    var change = questionsToUpdate.get(stableId);
                    assert change != null;

                    var expectedExpressionText = StringUtils.normalizeSpace(change.getString(Keys.ChangeContent.EXPECTED));
                    var updatedExpressionText = StringUtils.normalizeSpace(change.getString(Keys.ChangeContent.UPDATED));

                    // If there isn't a pre-existing expression, just bail. This patch
                    // doesn't have any cases where a new Expression is being created
                    var expression = expressionSql.getById(block.getShownExprId()).orElseThrow();
                    var expressionText = StringUtils.normalizeSpace(expression.getText());

                    if (Objects.equals(expressionText, updatedExpressionText)) {
                        log.info("The shown expression for [{}] was already updated, skipping...", readableName);
                        return;
                    }

                    if (!Objects.equals(expressionText, expectedExpressionText)) {
                        var message = String.format("The shown expression for [%s] does not match the expected value.", readableName);
                        log.error("{}\n\texpected: '{}'\n\tactual:   '{}'", message, expectedExpressionText, expressionText);
                        throw new DDPException(message);
                    }

                    log.info("Found expression {} for [{}].", expression.getGuid(), readableName);

                    var updateExpression = new Expression(expression.getId(), expression.getGuid(), updatedExpressionText);
                    var result = expressionSql.update(updateExpression);
                    DBUtils.checkInsert(1, result);

                    log.info("Successfully updated expression {} for [{}].", expression.getGuid(), readableName);
                });

        daoCleanup();

        log.info("TASK:: {} has completed successfully.", taskName);
    }

    private void daoSetup(Handle handle) {
        this.studySql = handle.attach(JdbiUmbrellaStudy.class);
        this.activitySql = handle.attach(JdbiActivity.class);
        this.activityVersionSql = handle.attach(JdbiActivityVersion.class);
        this.expressionSql = handle.attach(JdbiExpression.class);
        this.activityDao = handle.attach(FormActivityDao.class);
    }

    private void daoCleanup() {
        this.studySql = null;
        this.activitySql = null;
        this.activityVersionSql = null;
        this.expressionSql = null;
        this.activityDao = null;
    }

    /** 
     * Finds the set of parent blocks in an activity for a given set of question stable ids.
     * 
     * <p>This will only return blocks for which the question is an immediate child. If
     * the question is embedded in any other structure, it will not be found
     * 
     */
    private List<QuestionBlockDef> findBlocksForQuestions(FormActivityDef activityDefinition, Set<String> stableIds) {
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
            .filter((questionBlock) -> stableIds.contains(questionBlock.getQuestion().getStableId()))
            .collect(Collectors.toList());
    }
}
