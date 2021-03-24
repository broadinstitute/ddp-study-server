package org.broadinstitute.ddp.service.actvityinstancebuilder;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.FormBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.ValidationRuleCreator;
import org.jdbi.v3.core.Handle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.broadinstitute.ddp.model.activity.types.ActivityType.FORMS;

/**
 * A builder providing a creation of {@link ActivityInstance} an alternative way:
 * instead of building it by fetching all data from DB
 * it gets most of study data from {@link ActivityDefStore} and then fetch rest
 * of data from DB (answers, validation messages).
 *
 * <p>Main method of this class is
 * {@link #buildActivityInstance(Handle, String, ContentStyle, String, String, String, ActivityInstanceDto)}
 * In this method executed the following steps:
 * <ul>
 *     <li>find activity data in {@link ActivityDefStore} by study guid and activity instance dto;</li>
 *     <li>if data is found then:<br>
 *        - get answers from DB;<br>
 *        - run the process of {@link ActivityInstance} building.
 *     </li>
 * </ul>
 *
 * <p><b>Activity instance building steps:</b>
 * <ul>
 *     <li>create {@link FormInstance} (via constructor, setting data from {@link ActivityInstanceDto});</li>
 *     <li>iterate through {@link FormActivityDef#getAllSections()} and add sections to the {@link FormInstance};</li>
 *     <li>in each {@link FormSectionDef} iterate through {@link FormSectionDef#getBlocks()} and add
 *       blocks to an added {@link FormInstance} section;</li>
 *     <li>in each {@link FormBlockDef} iterate through {@link FormBlockDef#getQuestions()} and add
 *       questions to an added {@link FormInstance} block;</li>
 *     <li>for each of added question find validations and add to the question;</li>
 *     <li>for each of added question find answers and add to the question.</li>
 * </ul>
 *
 * <p>For each element type a creator is implemented (an instance of interface {@link ElementCreator}).
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
 * {@link ElementCreator} (so it's no need to pass multiple parameters to each creator constructor -
 * only one parameter {@link Context} is passed.
 */
public class ActivityInstanceFromActivityDefStoreBuilder {

    public Optional<ActivityInstance> buildActivityInstance(
            Handle handle,
            String isoLangCode,
            ContentStyle style,
            String studyGuid,
            String userGuid,
            String operatorGuid,
            ActivityInstanceDto instanceDto) {
        Optional<FormActivityDef> formActivityDef = ActivityDefStore.getInstance().findActivityDef(handle, studyGuid, instanceDto);
        if (formActivityDef.isPresent() && formActivityDef.get().getActivityType() == FORMS) {
            List<Answer> answers = getAnswers(handle, instanceDto.getGuid());
            ActivityInstance activityInstance = new FormInstanceCreator(
                    new Context(handle, isoLangCode, style, userGuid, operatorGuid, formActivityDef.get(), instanceDto, answers)
            ).createFormInstance();
            return Optional.of(activityInstance);
        }
        return Optional.empty();
    }

    private List<Answer> getAnswers(Handle handle, String activityInstGuid) {
        FormResponse formResponse = handle.attach(ActivityInstanceDao.class)
                .findFormResponseWithAnswersByInstanceGuid(activityInstGuid)
                .orElse(null);
        return formResponse != null ? formResponse.getAnswers() : null;
    }

    /**
     * Aggregates objects which needs on all steps of {@link ActivityInstance} building.
     */
    public static class Context {
        private final Handle handle;
        private final String isoLangCode;
        private final  ContentStyle style;
        private final String userGuid;
        private final String operatorGuid;
        private final long langCodeId;
        private final FormActivityDef formActivityDef;
        private final ActivityInstanceDto activityInstanceDto;
        private final List<Answer> answers;

        private final PexInterpreter interpreter = new TreeWalkInterpreter();
        private final I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();

        private Map<Long, String> renderedTemplates = new HashMap<>();

        public Context(
                Handle handle,
                String isoLangCode,
                ContentStyle style,
                String userGuid,
                String operatorGuid,
                FormActivityDef formActivityDef,
                ActivityInstanceDto activityInstanceDto,
                List<Answer> answers) {
            this.handle = handle;
            this.isoLangCode = isoLangCode;
            this.style = style;
            this.userGuid = userGuid;
            this.operatorGuid = operatorGuid;
            this.langCodeId = LanguageStore.get(isoLangCode).getId();
            this.formActivityDef = formActivityDef;
            this.activityInstanceDto = activityInstanceDto;
            this.answers = answers;
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

        public ActivityInstanceDto getActivityInstanceDto() {
            return activityInstanceDto;
        }

        public List<Answer> getAnswers() {
            return answers;
        }

        public PexInterpreter getInterpreter() {
            return interpreter;
        }

        public I18nContentRenderer getI18nContentRenderer() {
            return i18nContentRenderer;
        }

        public Map<Long, String> getRenderedTemplates() {
            return renderedTemplates;
        }
    }
}
