package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiActivityValidation;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiTemplate;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;

/**
 * A one-time task to fix revision data for Brain study's Prequal activity validations.
 *
 * <p>In the Brain study's `patch-log.conf` file, we see that we ran `BrainPrequalV2` before `BrainPreqalValidations`.
 * This means when we go insert activity validations, the first version of Prequal has been marked terminated, and a new
 * v2 has been created. Given how `InsertActivityValidations` was previously written, it will insert templates for an
 * activity validation using the same revision_id as the oldest activity version. This means for Brain's Prequal
 * activity, we inserted templates that are effectively terminated. This is an issue when we query activity validation
 * templates using proper time ranges.
 *
 * <p>This task fixes this issue by creating the appropriate revisioning data and updating the templates
 * with new revision_ids.
 */
@Slf4j
public class BrainFixPrequalValidations implements CustomTask {
    private static final String STUDY_GUID = "cmi-brain";
    private static final String PREQUAL_ACT_CODE = "PREQUAL";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);

        var jdbiRevision = handle.attach(JdbiRevision.class);
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);
        var jdbiActValidation = handle.attach(JdbiActivityValidation.class);
        var jdbiSubstitution = handle.attach(JdbiVariableSubstitution.class);
        var jdbiTemplate = handle.attach(JdbiTemplate.class);
        var templateDao = handle.attach(TemplateDao.class);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), PREQUAL_ACT_CODE);
        List<ActivityVersionDto> versions = jdbiActVersion.findAllVersionsInAscendingOrder(activityId);
        if (versions.isEmpty()) {
            throw new DDPException("Could not find versions for activity " + PREQUAL_ACT_CODE);
        }

        ActivityVersionDto oldestVersion = versions.get(0);
        long newRevId = jdbiRevision.copyStart(oldestVersion.getRevId());

        // Find the templates associated with the activity validations.
        List<Long> validationMessageIds = jdbiActValidation._findByActivityId(activityId)
                .stream()
                .map(ActivityValidationDto::getErrorMessageTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        log.info("Found {} activity validation message templates to update", validationMessageIds.size());

        // Find the translation text objects for each template.
        // Each variable should only have one translation for the Brain study.
        // We use the oldest version's start time for the query to ensure we find them.
        List<Long> translationIds = templateDao
                .loadTemplatesByIdsAndTimestamp(validationMessageIds, oldestVersion.getRevStart())
                .flatMap(template -> template.getVariables().stream())
                .flatMap(variable -> variable.getTranslations().stream())
                .map(translation -> translation.getId().get())
                .collect(Collectors.toList());
        log.info("Found {} template variable translations to update", translationIds.size());

        // Manually update the templates and translations to use the new revision_id.
        // The new revision_id doesn't have an end date, so it ensures templates are visible for all activity versions.
        long[] newTranslationRevIds = new long[translationIds.size()];
        Arrays.fill(newTranslationRevIds, newRevId);
        int[] updated = jdbiSubstitution.bulkUpdateRevisionIdsBySubIds(translationIds, newTranslationRevIds);
        DBUtils.checkUpdate(translationIds.size(), Arrays.stream(updated).sum());
        for (var templateId : validationMessageIds) {
            DBUtils.checkUpdate(1, jdbiTemplate.updateRevisionIdById(templateId, newRevId));
        }

        log.info("Finished fixing activity validations for " + PREQUAL_ACT_CODE);
    }
}
