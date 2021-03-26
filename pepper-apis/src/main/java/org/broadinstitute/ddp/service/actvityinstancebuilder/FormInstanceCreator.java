package org.broadinstitute.ddp.service.actvityinstancebuilder;


import static org.broadinstitute.ddp.util.TemplateRenderUtil.toPlainText;
import static org.broadinstitute.ddp.util.TranslationUtil.extractOptionalActivityTranslation;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;

/**
 * Creates {@link FormInstance}
 */
public class FormInstanceCreator extends AbstractCreator {

    public FormInstanceCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public FormInstance createFormInstance() {
        var formInstance = constructFormInstance();
        addChildren(formInstance);
        formInstance.setDisplayNumbers();
        renderContent(formInstance, context.getRenderedTemplates()::get, context.getStyle());
        updateBlockStatuses(formInstance);
        return formInstance;
    }

    private FormInstance constructFormInstance() {
        var formActivityDef = context.getFormActivityDef();
        var formResponse = context.getFormResponse();

        var title = extractOptionalActivityTranslation(formActivityDef.getTranslatedTitles(), context.getIsoLangCode());
        var subtitle = extractOptionalActivityTranslation(formActivityDef.getTranslatedSubtitles(), context.getIsoLangCode());

        boolean readonly = ActivityInstanceUtil.isReadonly(
                formActivityDef.getEditTimeoutSec(),
                formResponse.getCreatedAt(),
                formResponse.getLatestStatus().getType().name(),
                formActivityDef.isWriteOnce(),
                formResponse.getReadonly());

        var formInstance = new FormInstance(
                formResponse.getParticipantId(),
                formResponse.getId(),
                formResponse.getActivityId(),
                formResponse.getActivityCode(),
                formActivityDef.getFormType(),
                formResponse.getGuid(),
                title,
                subtitle,
                formResponse.getLatestStatus() != null ? formResponse.getLatestStatus().getType().name() : null,
                readonly,
                formActivityDef.getListStyleHint(),
                renderTemplateIfDefined(formActivityDef.getReadonlyHintTemplate()),
                formActivityDef.getIntroduction() != null ? formActivityDef.getIntroduction().getSectionId() : null,
                formActivityDef.getClosing() != null ? formActivityDef.getClosing().getSectionId() : null,
                formResponse.getCreatedAt(),
                formResponse.getFirstCompletedAt(),
                renderTemplateIfDefined(formActivityDef.getLastUpdatedTextTemplate()),
                formActivityDef.getLastUpdated(),
                formActivityDef.canDeleteInstances(),
                formActivityDef.isFollowup(),
                formResponse.getHidden(),
                formActivityDef.isExcludeFromDisplay(),
                formResponse.getSectionIndex()
        );
        return formInstance;
    }

    private void addChildren(FormInstance formInstance) {
        var formActivityDef = context.getFormActivityDef();
        var formSectionCreator = context.getFormSectionCreator();
        formInstance.setIntroduction(formSectionCreator.createSection(formActivityDef.getIntroduction()));
        formInstance.setClosing(formSectionCreator.createSection(formActivityDef.getClosing()));
        formActivityDef.getSections().forEach(s -> {
            formInstance.getBodySections().add(formSectionCreator.createSection(s));
        });
    }

    private void updateBlockStatuses(FormInstance formInstance) {
        formInstance.updateBlockStatuses(
                context.getHandle(),
                context.getInterpreter(),
                context.getUserGuid(),
                context.getOperatorGuid(),
                context.getFormResponse().getGuid(),
                null);
    }

    private void renderContent(FormInstance formInstance, Renderable.Provider<String> rendered, ContentStyle style) {
        formInstance.getAllSections().forEach(s -> s.applyRenderedTemplates(rendered, style));
        formInstance.setReadonlyHint(toPlainText(formInstance.getReadonlyHintTemplateId(), rendered, style));
        formInstance.setActivityDefinitionLastUpdatedText(toPlainText(formInstance.getLastUpdatedTextTemplateId(), rendered, style));
    }
}
