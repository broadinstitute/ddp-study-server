package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static org.broadinstitute.ddp.model.activity.types.ActivityType.FORMS;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.BUILD_FORM_CHILDREN;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.BUILD_FORM_INSTANCE;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.CHECK_PARAMS;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.CREATE_RENDERER_CONTEXT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.END_BUILD;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.INIT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.POPULATE_SNAPSHOTTED_ADDRESS;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.READ_ACTIVITY_DEF;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.READ_FORM_INSTANCE;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.RENDER_CONTENT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.RENDER_FORM_TITLES;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.SET_DISPLAY_NUMBERS;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.START_BUILD;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep.UPDATE_BLOCK_STATUSES;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.RendererInitialContextCreator.RenderContextSource;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuildStep;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderParams;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormInstanceCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormSectionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.SectionIconCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.FormBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.QuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question.ValidationRuleCreator;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.jdbi.v3.core.Handle;

/**
 * A builder providing a creation of {@link ActivityInstance} (of type {@link FormInstance}).
 * Instead of building it by fetching all data from DB
 * it gets study data from {@link ActivityDefStore} and fetches instance data
 * from DB: answers, validation messages.. Btw, validation messages saved then to
 * {@link ActivityDefStore} cache for further reusing.
 *
 * <p>NOTE: it needs to use {@link AIBuilderFactory#createAIBuilder(AIBuilderFactory, Handle, AIBuilderParams)}
 * or {@link AIBuilderFactory#createAIBuilder(Handle, AIBuilderParams)} in order to create an
 * instance of {@link ActivityInstanceFromDefinitionBuilder}.
 *
 * <p><b>AI builder usage example:</b>
 * <pre>
 *     var activityInstance = AIBuilderFactory.createAIBuilder(handle,
 *        createParams(userGuid,studyGuid,instanceGuid).setStyle(style))
 *        .checkParams()
 *          .readFormInstanceData()
 *          .readActivityDef()
 *        .startBuild()
 *          .buildFormInstance()
 *          .buildFormChildren()
 *          .renderFormTitles()
 *          .renderContent()
 *        .endBuild()
 *          .getContext()
 *          .getFormInstance();
 * </pre>
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
 */
@Slf4j
public class ActivityInstanceFromDefinitionBuilder {
    private final AIBuilderContext context;

    ActivityInstanceFromDefinitionBuilder(AIBuilderFactory aiBuilderFactory, Handle handle, AIBuilderParams params) {
        context = new AIBuilderContext(aiBuilderFactory, handle, params);
        context.setBuildStep(INIT);
    }

    public AIBuilderContext getContext() {
        return context;
    }

    public ActivityInstanceFromDefinitionBuilder checkParams() {
        checkStep(INIT, CHECK_PARAMS);

        if (context.getParams().getUserGuid() == null
                || context.getParams().getStudyGuid() == null
                || context.getParams().getInstanceGuid() == null
                || context.getParams().getIsoLangCode() == null) {
            throw new IllegalArgumentException("None of these parameters should be null: userGuid, studyGuid, instanceGuid, isoLangCode");
        }
        if (context.getParams().getOperatorGuid() == null) {
            context.getParams().setOperatorGuid(context.getParams().getUserGuid());
        }

        LanguageDto languageDto = LanguageStore.get(context.getParams().getIsoLangCode());
        if (languageDto == null) {
            context.setFailedMessage("Unknown language code: " + context.getParams().getIsoLangCode());
            context.setFailedStep(CHECK_PARAMS);
        }

        context.setBuildStep(CHECK_PARAMS);
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder readFormInstanceData() {
        return readFormInstanceData(null);
    }

    public ActivityInstanceFromDefinitionBuilder readFormInstanceData(FormResponse existingFormResponse) {
        if (checkStep(CHECK_PARAMS, READ_FORM_INSTANCE)) {

            var formResponse = existingFormResponse != null
                    ? Optional.of(existingFormResponse) :
                    ActivityInstanceUtil.getFormResponse(context.getHandle(), context.getInstanceGuid());
            if (formResponse.isPresent()) {
                context.setFormResponse(formResponse.get());
                readPreviousInstanceId(formResponse);
            } else {
                context.setFailedMessage("Form instance data fetching failed");
                context.setFailedStep(READ_FORM_INSTANCE);
            }

            context.setBuildStep(READ_FORM_INSTANCE);
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder readActivityDef() {
        return readActivityDef(null);
    }

    public ActivityInstanceFromDefinitionBuilder readActivityDef(FormActivityDef existingFormActivityDef) {
        if (checkStep(READ_FORM_INSTANCE, READ_ACTIVITY_DEF)) {

            if (context.getFormResponse() == null) {
                context.setFailedMessage("FormResponse data not fetched");
                context.setFailedStep(READ_ACTIVITY_DEF);
            } else {
                FormActivityDef formActivityDef = existingFormActivityDef != null ? existingFormActivityDef :
                        ActivityInstanceUtil.getActivityDef(
                                context.getHandle(),
                                ActivityDefStore.getInstance(),
                                context.getStudyGuid(),
                                context.getFormResponse().getActivityId(),
                                context.getInstanceGuid(),
                                context.getFormResponse().getCreatedAt());
                context.setFormActivityDef(formActivityDef);

                context.setBuildStep(READ_ACTIVITY_DEF);
            }
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder createRendererContext(RenderContextSource renderContextSource) {
        if (checkStep(READ_ACTIVITY_DEF, CREATE_RENDERER_CONTEXT)) {

            context.getAIBuilderFactory().getTemplateRenderHelper().createRendererInitialContext(context, renderContextSource);

            context.setBuildStep(CREATE_RENDERER_CONTEXT);
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder startBuild() {
        if (checkStep(CREATE_RENDERER_CONTEXT, START_BUILD)) {

            log.debug("Start ActivityInstance building from definition (ActivityDefStore). StudyGuid={}, instanceGuid={}",
                    context.getStudyGuid(), context.getInstanceGuid());

            context.setBuildStep(START_BUILD);
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder buildFormInstance() {
        if (checkStep(START_BUILD, BUILD_FORM_INSTANCE)) {

            if (context.getFormActivityDef().getActivityType() != FORMS) {
                context.setFailedMessage("Cannot build ActivityInstance of type other than FORMS");
                context.setFailedStep(BUILD_FORM_INSTANCE);
            } else {
                var formInstance = context.getAIBuilderFactory().getFormInstanceCreator().createFormInstance(context);
                context.setFormInstance(formInstance);
                context.setBuildStep(BUILD_FORM_INSTANCE);
            }
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder buildFormChildren() {
        if (checkStep(BUILD_FORM_INSTANCE, BUILD_FORM_CHILDREN)) {

            context.getAIBuilderFactory().getFormInstanceCreator().addChildren(context);

            context.setBuildStep(BUILD_FORM_CHILDREN);
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder renderFormTitles() {
        if (checkStep(BUILD_FORM_INSTANCE, RENDER_FORM_TITLES)) {

            context.getAIBuilderFactory().getFormInstanceCreator().renderTitleAndSubtitle(context);

            context.setBuildStep(RENDER_FORM_TITLES);
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder renderContent() {
        if (checkStep(BUILD_FORM_CHILDREN, RENDER_CONTENT)) {
            if (context.getParams().getStyle() == null) {
                throw new IllegalStateException("RenderContent() cannot be executed because ContentStyle parameter is not set");
            }
            if (context.getParams().isDisableTemplatesRendering()) {
                context.setFailedMessage("Cannot render content because templates rendering is disabled by parameter"
                        + " disableTemplatesRendering");
                context.setFailedStep(RENDER_CONTENT);
            } else {
                context.getAIBuilderFactory().getFormInstanceCreator().renderContent(context);

                context.setBuildStep(RENDER_CONTENT);
            }
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder updateBlockStatuses() {
        if (checkStep(BUILD_FORM_CHILDREN, UPDATE_BLOCK_STATUSES)) {

            context.getAIBuilderFactory().getFormInstanceCreator().updateBlockStatuses(context);

            context.setBuildStep(UPDATE_BLOCK_STATUSES);
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder setDisplayNumbers() {
        if (checkStep(BUILD_FORM_CHILDREN, SET_DISPLAY_NUMBERS)) {

            context.getAIBuilderFactory().getFormInstanceCreatorHelper().setDisplayNumbers(context.getFormInstance());

            context.setBuildStep(SET_DISPLAY_NUMBERS);
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder populateSnapshottedAddress() {
        if (checkStep(BUILD_FORM_CHILDREN, POPULATE_SNAPSHOTTED_ADDRESS)) {

            context.getAIBuilderFactory().getFormInstanceCreatorHelper().populateSnapshottedAddress(
                    context.getMailingAddressComponent(), context.getActivitySnapshots());

            context.setBuildStep(POPULATE_SNAPSHOTTED_ADDRESS);
        }
        return this;
    }

    public ActivityInstanceFromDefinitionBuilder endBuild() {
        if (checkStep(START_BUILD, END_BUILD)) {

            log.debug("ActivityInstance built from definition SUCCESSFULLY.");

            context.setBuildStep(END_BUILD);
        }
        return this;
    }

    private AIBuildStep getBuildStep() {
        return context.getBuildStep();
    }

    private boolean checkStep(AIBuildStep expectedMinimalStep, AIBuildStep nextStep) {
        if (context.getFailedStep() != null) {
            return false;
        }
        if (getBuildStep().ordinal() < expectedMinimalStep.ordinal()) {
            throw new IllegalStateException("Incorrect last step before step=" + nextStep + " of ActivityInstance building: "
                    + "last step=" + getBuildStep() + ", but required lastStep=" + expectedMinimalStep);
        }
        return true;
    }

    private void readPreviousInstanceId(Optional<FormResponse> formResponse) {
        if (context.getParams().isReadPreviousInstanceId()) {
            context.setPreviousInstanceId(
                    ActivityInstanceUtil.getPreviousInstanceId(context.getHandle(), formResponse.get().getId())
            );
        }
    }
}
