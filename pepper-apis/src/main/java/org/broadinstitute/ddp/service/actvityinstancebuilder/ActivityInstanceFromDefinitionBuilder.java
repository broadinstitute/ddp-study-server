package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static org.broadinstitute.ddp.model.activity.types.ActivityType.FORMS;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.FormBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.ValidationRuleCreator;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.broadinstitute.ddp.util.TemplateRenderUtil;
import org.jdbi.v3.core.Handle;

/**
 * A builder providing a creation of {@link ActivityInstance} an alternative way:
 * instead of building it by fetching all data from DB
 * it gets most of study data from {@link ActivityDefStore} and then fetch rest
 * of data from DB (answers, validation messages).
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
 * <p>For each element type a creator is implemented (an instance of interface {@link AbstractCreator}).
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
 * <p>NOTE: it is defined a class {@link Context} used to pass the basic parameters to each
 * {@link AbstractCreator} (so it's no need to pass multiple parameters to each creator constructor -
 * only one parameter {@link Context} is passed.
 */
public class ActivityInstanceFromDefinitionBuilder {

    public Optional<ActivityInstance> buildActivityInstance(
            Handle handle,
            String userGuid,
            String operatorGuid,
            String studyGuid,
            String instanceGuid,
            ContentStyle style,
            String isoLangCode
    ) {
        var formResponse = ActivityInstanceUtil.getFormResponse(handle, instanceGuid);
        Optional<FormActivityDef> formActivityDef = ActivityDefStore.getInstance().findActivityDef(
                handle, studyGuid, formResponse.getActivityId(), formResponse.getCreatedAt(), formResponse.getActivityCode());
        if (formActivityDef.isPresent() && formActivityDef.get().getActivityType() == FORMS) {
            var activityInstance = new FormInstanceCreator(
                    new Context(handle, userGuid, operatorGuid, isoLangCode, style, formActivityDef.get(), formResponse)
            ).createFormInstance();
            return Optional.of(activityInstance);
        }
        return Optional.empty();
    }

    /**
     * Aggregates objects which needs on all steps of {@link ActivityInstance} building.
     */
    public static class Context {

        private final Handle handle;
        private final String userGuid;
        private final String operatorGuid;
        private final long langCodeId;
        private final String isoLangCode;
        private final  ContentStyle style;
        private final FormActivityDef formActivityDef;
        private final FormResponse formResponse;

        private final FormSectionCreator formSectionCreator;
        private final SectionIconCreator sectionIconCreator;
        private final FormBlockCreator formBlockCreator;
        private final QuestionCreator questionCreator;

        private final PexInterpreter interpreter = new TreeWalkInterpreter();
        private final I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();
        private final Map<String, Object> rendererInitialContext;

        private final Long previousInstanceId;

        private Map<Long, String> renderedTemplates = new HashMap<>();

        public Context(
                Handle handle,
                String userGuid,
                String operatorGuid,
                String isoLangCode,
                ContentStyle style,
                FormActivityDef formActivityDef,
                FormResponse formResponse) {
            this.handle = handle;
            this.userGuid = userGuid;
            this.operatorGuid = operatorGuid;
            this.isoLangCode = isoLangCode;
            this.style = style;
            this.langCodeId = LanguageStore.get(isoLangCode).getId();
            this.formActivityDef = formActivityDef;
            this.formResponse = formResponse;

            this.rendererInitialContext = TemplateRenderUtil.createRendererInitialContext(handle,
                    formResponse.getParticipantId(), formResponse.getId(), formActivityDef.getLastUpdated());

            this.previousInstanceId = ActivityInstanceUtil.getPreviousInstanceId(handle, formResponse.getId());

            formSectionCreator = new FormSectionCreator(this);
            sectionIconCreator = new SectionIconCreator(this);
            formBlockCreator = new FormBlockCreator(this);
            questionCreator = new QuestionCreator(this);
        }

        public Handle getHandle() {
            return handle;
        }

        public String getIsoLangCode() {
            return isoLangCode;
        }

        public ContentStyle getStyle() {
            return style;
        }

        public long getLangCodeId() {
            return langCodeId;
        }

        public String getUserGuid() {
            return userGuid;
        }

        public String getOperatorGuid() {
            return operatorGuid;
        }

        public FormActivityDef getFormActivityDef() {
            return formActivityDef;
        }

        public FormResponse getFormResponse() {
            return formResponse;
        }

        public PexInterpreter getInterpreter() {
            return interpreter;
        }

        public I18nContentRenderer getI18nContentRenderer() {
            return i18nContentRenderer;
        }

        public Map<String, Object> getRendererInitialContext() {
            return rendererInitialContext;
        }

        public Map<Long, String> getRenderedTemplates() {
            return renderedTemplates;
        }

        public Long getPreviousInstanceId() {
            return previousInstanceId;
        }

        public FormSectionCreator getFormSectionCreator() {
            return formSectionCreator;
        }

        public SectionIconCreator getSectionIconCreator() {
            return sectionIconCreator;
        }

        public FormBlockCreator getFormBlockCreator() {
            return formBlockCreator;
        }

        public QuestionCreator getQuestionCreator() {
            return questionCreator;
        }
    }
}
