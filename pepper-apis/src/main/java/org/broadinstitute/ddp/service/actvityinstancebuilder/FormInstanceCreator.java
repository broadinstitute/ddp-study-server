package org.broadinstitute.ddp.service.actvityinstancebuilder;


import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;

import static org.broadinstitute.ddp.util.TranslationUtil.extractOptionalActivityTranslation;

/**
 * Creates {@link FormInstance}
 */
public class FormInstanceCreator extends ElementCreator {

    public FormInstanceCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public FormInstance createFormInstance() {
        FormInstance formInstance = constructFormInstance();
        addChildren(formInstance);
        updateBlockStatuses(formInstance);
        formInstance.setDisplayNumbers();
        render(formInstance);
        return formInstance;
    }

    private FormInstance constructFormInstance() {
        ActivityInstanceDto instanceDto = context.getActivityInstanceDto();
        FormActivityDef formActivityDef = context.getFormActivityDef();

        String title = extractOptionalActivityTranslation(formActivityDef.getTranslatedTitles(), context.getIsoLangCode());
        String subtitle = extractOptionalActivityTranslation(formActivityDef.getTranslatedSubtitles(), context.getIsoLangCode());

        boolean readonly = ActivityInstanceUtil.isReadonly(
                context.getActivityInstanceDto().getEditTimeoutSec(),
                context.getActivityInstanceDto().getCreatedAtMillis(),
                context.getActivityInstanceDto().getStatusType().name(),
                formActivityDef.isWriteOnce(),
                context.getActivityInstanceDto().getReadonly());

        FormInstance formInstance = new FormInstance(
                instanceDto.getParticipantId(),
                instanceDto.getId(),
                instanceDto.getActivityId(),
                instanceDto.getActivityCode(),
                formActivityDef.getFormType(),
                instanceDto.getGuid(),
                title,
                subtitle,
                instanceDto.getStatusType() != null ? instanceDto.getStatusType().name() : null,
                readonly,
                formActivityDef.getListStyleHint(),
                getTemplateId(formActivityDef.getReadonlyHintTemplate()),
                formActivityDef.getIntroduction() != null ? formActivityDef.getIntroduction().getSectionId() : null,
                formActivityDef.getClosing() != null ? formActivityDef.getClosing().getSectionId() : null,
                instanceDto.getCreatedAtMillis(),
                instanceDto.getFirstCompletedAt(),
                getTemplateId(formActivityDef.getLastUpdatedTextTemplate()),
                formActivityDef.getLastUpdated(),
                formActivityDef.canDeleteInstances(),
                formActivityDef.isFollowup(),
                instanceDto.isHidden(),
                formActivityDef.isExcludeFromDisplay(),
                instanceDto.getSectionIndex()
        );
        return formInstance;
    }

    private void addChildren(FormInstance formInstance) {
        FormActivityDef formActivityDef = context.getFormActivityDef();
        FormSectionCreator formSectionCreator = new FormSectionCreator(context);
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
                context.getActivityInstanceDto().getGuid(),
                null);
    }

    private void render(FormInstance formInstance) {
        formInstance.setReadonlyHint(renderTemplate(context.getFormActivityDef().getReadonlyHintTemplate()));
        formInstance.setActivityDefinitionLastUpdatedText(renderTemplate(context.getFormActivityDef().getLastUpdatedTextTemplate()));
    }

    private String renderTemplate(Template template) {
        String renderedString = template != null ? template.render(context.getIsoLangCode()) : null;
        if (renderedString != null) {
            if (context.getStyle() == ContentStyle.BASIC) {
                renderedString = HtmlConverter.getPlainText(renderedString);
            }
        }
        return renderedString;
    }
}
