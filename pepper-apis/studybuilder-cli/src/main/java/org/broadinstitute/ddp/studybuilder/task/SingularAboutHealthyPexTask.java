package org.broadinstitute.ddp.studybuilder.task.singular;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Updates the PEX shownExpr for 2 questions in the ABOUT_HEALTHY
 * activity for Singular.
 * 
 */
@Slf4j
public class SingularAboutHealthyPexTask implements CustomTask  {
    private static class Constants {
        private static final String PATCH_PATH = "patches/ddp-8539-about-healthy-pex.conf";

        // Keypaths of specific data in the study configuration file
        private static final String KEY_PATH_STUDY_GUID = "study.guid";
    }

    @Value
    private class ChangeSet {
        private final String author;
        private final String description;
        private final String study;
        private final String activity;
        private final String versionTag;
        private final List<Change> content;

        public Change changeWithId(String stableId) {
            return content.stream()
                    .filter((change) -> change.getStableId().equals(stableId))
                    .findFirst()
                    .orElseThrow();

        }
    }

    @Value
    private class Change {
        private final String stableId;
        private final Map<String, String> expected;
        private final Map<String, String> updated;
    }

    private Config studyConfig;
    private ChangeSet changes;

    private JdbiUmbrellaStudy studySql;
    private JdbiActivity activitySql;
    private JdbiActivityVersion activityVersionSql;
    private FormActivityDao activityDao;
    private QuestionDao questionDao;
    private JdbiExpression expressionSql;

    @Override
    public void init(Path configPath, Config studyConfig, Config varsConfig) {
        var patchPath = configPath.getParent().resolve(Constants.PATCH_PATH);
        final Config config;
        try {
            config = ConfigFactory.parseFile(patchPath.toFile()).resolveWith(varsConfig);
        } catch (ConfigException configException) {
            var message = String.format("failed to load patch data file expected at '%s'", patchPath);
            throw new DDPException(message, configException);
        }

        this.changes = ConfigBeanFactory.create(config, ChangeSet.class);
        this.studyConfig = studyConfig;
    }

    @Override
    public void run(Handle handle) {
        var taskName = this.getClass().getSimpleName();
        log.info("TASK:: {}", taskName);

        daoSetup(handle);

        var studyGuid = studyConfig.getString(Constants.KEY_PATH_STUDY_GUID);

        if (!changes.getStudy().equals(studyGuid)) {
            log.warn("skipping patch {}: targeted study is '{}', but was run against '{}'",
                    taskName,
                    changes.getStudy(),
                    studyGuid);
            return;
        }

        var studyDto = Optional.ofNullable(studySql.findByStudyGuid(studyGuid)).orElseThrow(() -> {
            return new DDPException(String.format("failed to locate the study '%s'", studyGuid));
        });

        var activityCode = changes.getActivity();
        var activity = activitySql.findActivityByStudyGuidAndCode(studyGuid, activityCode).orElseThrow(() -> {
            return new DDPException(String.format("failed to locate the activity '%s' in study '%s'",
                    activityCode,
                    studyGuid));
        });

        var activityVersion = activityVersionSql.findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, changes.getVersionTag()).orElseThrow(() -> {
            return new DDPException(String.format("failed to locate the version tag '%s' for the activity '%s' in study '%s'",
                    changes.getVersionTag(),
                    activityCode,
                    studyGuid));
        });

        var currentActivityDef = activityDao.findDefByDtoAndVersion(activity, activityVersion);

        var questionsToUpdate = changes.getContent().stream()
            .map((change) -> change.getStableId())
            .collect(Collectors.toSet());

        var blocks = findBlocksForQuestions(currentActivityDef, questionsToUpdate);
        
        blocks.stream()
            .filter((block) -> changes.changeWithId(block.getQuestion().getStableId()) != null)
            .forEach((block) -> {
                var existingExpression = block.getShownExprId();
                var expression = expressionSql.getById(existingExpression);

            });

        daoCleanup();

        throw new DDPException("a wrench in the gears");
    }

    private void daoSetup(Handle handle) {
        this.studySql = handle.attach(JdbiUmbrellaStudy.class);
        this.activitySql = handle.attach(JdbiActivity.class);
        this.activityVersionSql = handle.attach(JdbiActivityVersion.class);
        this.expressionSql = handle.attach(JdbiExpression.class);
        this.activityDao = handle.attach(FormActivityDao.class);
        this.questionDao = handle.attach(QuestionDao.class);
    }

    private void daoCleanup() {
        this.studySql = null;
        this.activitySql = null;
        this.questionDao = null;
        this.activityVersionSql = null;
    }

    private List<QuestionBlockDef> findBlocksForQuestions(FormActivityDef activityDefinition, Set<String> stableIds) {
        return activityDefinition.getAllSections().stream()
            .map((section) -> section.getBlocks())
            .flatMap(List::stream)
            .filter((block) -> block.getBlockType() == BlockType.QUESTION)
            .map((block) -> QuestionBlockDef.class.cast(block))
            .filter((questionBlock) -> stableIds.contains(questionBlock.getQuestion().getStableId()))
            .collect(Collectors.toList());
    }
}
