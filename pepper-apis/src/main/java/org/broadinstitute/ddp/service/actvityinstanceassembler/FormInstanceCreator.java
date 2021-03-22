package org.broadinstitute.ddp.service.actvityinstanceassembler;


import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;

import java.util.Optional;

/**
 * Creates {@link FormInstance}
 */
public class FormInstanceCreator extends ElementCreator {

    public FormInstanceCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public FormInstance createFormInstance() {
        ActivityI18nDetail activityI18nDetail = getActivityI18nDetail();
        FormInstance formInstance = constructFormInstance(activityI18nDetail);
        addChildren(formInstance);
        updateBlockStatuses(formInstance);
        return formInstance;
    }

    private FormInstance constructFormInstance(ActivityI18nDetail activityI18nDetail) {
        ActivityInstanceDto instanceDto = context.getActivityInstanceDto();
        FormActivityDef formActivityDef = context.getFormActivityDef();

        FormInstance formInstance = new FormInstance(
                instanceDto.getParticipantId(),
                instanceDto.getId(),
                instanceDto.getActivityId(),
                instanceDto.getActivityCode(),
                formActivityDef.getFormType(),
                instanceDto.getGuid(),
                activityI18nDetail != null ? activityI18nDetail.getTitle() : null,
                activityI18nDetail != null ? activityI18nDetail.getSubtitle() : null,
                instanceDto.getStatusType() != null ? instanceDto.getStatusType().name() : null,
                instanceDto.getReadonly(),
                formActivityDef.getListStyleHint(),
                formActivityDef.getReadonlyHintTemplate() != null ? formActivityDef.getReadonlyHintTemplate().getTemplateId() : null,
                formActivityDef.getIntroduction() != null ? formActivityDef.getIntroduction().getSectionId() : null,
                formActivityDef.getClosing() != null ? formActivityDef.getClosing().getSectionId() : null,
                instanceDto.getCreatedAtMillis(),
                instanceDto.getFirstCompletedAt(),
                formActivityDef.getLastUpdatedTextTemplate() != null ? formActivityDef.getLastUpdatedTextTemplate().getTemplateId() : null,
                formActivityDef.getLastUpdated(),
                formActivityDef.canDeleteInstances(),
                formActivityDef.isFollowup(),
                instanceDto.isHidden(),
                formActivityDef.isExcludeFromDisplay(),
                instanceDto.getSectionIndex()
        );
        return formInstance;
    }

    private ActivityI18nDetail getActivityI18nDetail() {
        Optional<ActivityI18nDetail> activityI18nDetail =
                context.getHandle().attach(ActivityI18nDao.class).findDetailByLanguageCodeIdAndActivityId(
                        context.getLangCodeId(), context.getActivityInstanceDto().getActivityId());
        return activityI18nDetail.get();
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

    private void render(FormInstance formInstance, FormActivityDef formActivityDef) {
        // TODO
    }
}
