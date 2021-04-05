package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static org.broadinstitute.ddp.model.activity.types.ActivityType.FORMS;

import java.util.Optional;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.FormBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.ValidationRuleCreator;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A builder providing a creation of {@link ActivityInstance} an alternative way:
 * instead of building it by fetching all data from DB
 * it gets study data from {@link ActivityDefStore} and then fetches instance data
 * from DB: answers, validation messages.. Btw, validation messages saved then to
 * {@link ActivityDefStore} cache.
 *
 * <p>Main method of this class is
 * {@link #buildActivityInstance(Handle, String, String, String, String, ContentStyle, String)}
 * In this method executed the following steps:
 * <ul>
 *     <li>find {@link FormResponse} by activityInstanceGuid (this object creates answers list);</li>
 *     <li>find activity data in {@link ActivityDefStore} by study guid and activity ID;</li>
 *     <li>if data is found then:<br>
 *        - run the process of {@link ActivityInstance} building.
 *     </li>
 * </ul>
 *
 * <p><b>Activity instance building steps:</b>
 * <ul>
 *     <li>create {@link FormInstance} (via constructor, setting data from {@link FormResponse});</li>
 *     <li>iterate through {@link FormActivityDef#getAllSections()} and add sections to the {@link FormInstance};</li>
 *     <li>in each {@link FormSectionDef} iterate through {@link FormSectionDef#getBlocks()} and add
 *       blocks to an added {@link FormInstance} section;</li>
 *     <li>in each {@link FormBlockDef} iterate through {@link FormBlockDef#getQuestions()} and add
 *       questions to an added {@link FormInstance} block;</li>
 *     <li>for each of added question find validations and add to the question;</li>
 *     <li>for each of added question find answers and add to the question.</li>
 * </ul>
 *
 * <p>For each of {@link ActivityInstance} elements a creator is implemented.
 * <br> Creators hierarchy:
 * <pre>
 *   {@link FormInstanceCreator}
 *     {@link FormSectionCreator}
 *       {@link FormBlockCreator}
 *         {@link SectionIconCreator}
 *         {@link QuestionCreator}
 *           {@link ValidationRuleCreator}
 * </pre>
 *
 * <p>NOTE: it is defined a class {@link AIBuilderContext} which used to pass the basic parameters to each
 * creator (so it's no need to pass multiple parameters to each creator constructor -
 * only one parameter {@link AIBuilderContext} is passed.
 * Also it holds a reference to {@link AICreatorsFactory} which creates all Creator-objects
 * providing {@link ActivityInstance} building.
 */
public class ActivityInstanceFromDefinitionBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityInstanceFromDefinitionBuilder.class);

    /**
     * Build {@link ActivityInstance} from {@link ActivityDefStore} + some instance data (like
     * answers) are fetched from DB.
     */
    public Optional<ActivityInstance> buildActivityInstance(
            Handle handle,
            String userGuid,
            String operatorGuid,
            String studyGuid,
            String instanceGuid,
            ContentStyle style,
            String isoLangCode
    ) {
        LOG.info("Start ActivityInstance building from definition (ActivityDefStore). StudyGuid={}, instanceGuid={}",
                studyGuid, instanceGuid);
        var formResponse = ActivityInstanceUtil.getFormResponse(handle, instanceGuid);
        if (formResponse.isEmpty()) {
            LOG.warn("Error reading form activity by guid=" + instanceGuid);
            return Optional.empty();
        } else {
            FormActivityDef formActivityDef = ActivityInstanceUtil.getActivityDef(
                    handle,
                    ActivityDefStore.getInstance(),
                    studyGuid,
                    formResponse.get().getActivityId(),
                    instanceGuid,
                    formResponse.get().getCreatedAt());
            if (formActivityDef.getActivityType() == FORMS) {
                var activityInstance = new FormInstanceCreator().createFormInstance(
                        new AIBuilderContext(
                                handle,
                                userGuid,
                                operatorGuid,
                                isoLangCode,
                                style,
                                formActivityDef,
                                formResponse.get())
                );
                LOG.info("ActivityInstance built from definition SUCCESSFULLY.");
                return Optional.of(activityInstance);
            } else {
                throw new DDPException("Wrong activity type " + formActivityDef.getActivityType() + ". "
                        + "Only activity of type " + FORMS + " is supported by ActivityInstanceFromDefinitionBuilder");
            }
        }
    }
}
