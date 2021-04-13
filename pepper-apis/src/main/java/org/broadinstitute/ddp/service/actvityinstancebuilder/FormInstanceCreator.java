package org.broadinstitute.ddp.service.actvityinstancebuilder;


import static org.broadinstitute.ddp.service.actvityinstancebuilder.util.TemplateHandler.addAndRenderTemplate;

import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;

/**
 * Creates {@link FormInstance}
 */
public class FormInstanceCreator {

    public FormInstance createFormInstance(AIBuilderContext ctx) {
        var formActivityDef = ctx.getFormActivityDef();
        var formResponse = ctx.getFormResponse();

        boolean readonly = ActivityInstanceUtil.isReadonly(
                formActivityDef.getEditTimeoutSec(),
                formResponse.getCreatedAt(),
                formResponse.getLatestStatus().getType().name(),
                formActivityDef.isWriteOnce(),
                formResponse.getReadonly());

        boolean isFirstInstance = ctx.getPreviousInstanceId() == null;
        boolean canDelete = ActivityInstanceUtil.computeCanDelete(
                formActivityDef.canDeleteInstances(),
                formActivityDef.getCanDeleteFirstInstance(),
                isFirstInstance);

        var formInstance = new FormInstance(
                formResponse.getParticipantId(),
                formResponse.getId(),
                formResponse.getActivityId(),
                formResponse.getActivityCode(),
                formActivityDef.getFormType(),
                formResponse.getGuid(),
                null,       // 'title' is rendered and assigned after FormInstance creation completed
                null,     // 'subTitle' is rendered and assigned after FormInstance creation completed
                formResponse.getLatestStatus() != null ? formResponse.getLatestStatus().getType().name() : null,
                readonly,
                formActivityDef.getListStyleHint(),
                addAndRenderTemplate(ctx, formActivityDef.getReadonlyHintTemplate()),
                formActivityDef.getIntroduction() != null ? formActivityDef.getIntroduction().getSectionId() : null,
                formActivityDef.getClosing() != null ? formActivityDef.getClosing().getSectionId() : null,
                formResponse.getCreatedAt(),
                formResponse.getFirstCompletedAt(),
                addAndRenderTemplate(ctx, formActivityDef.getLastUpdatedTextTemplate()),
                formActivityDef.getLastUpdated(),
                canDelete,
                formActivityDef.isFollowup(),
                formResponse.getHidden(),
                formActivityDef.isExcludeFromDisplay(),
                formResponse.getSectionIndex()
        );

        return formInstance;
    }
}
