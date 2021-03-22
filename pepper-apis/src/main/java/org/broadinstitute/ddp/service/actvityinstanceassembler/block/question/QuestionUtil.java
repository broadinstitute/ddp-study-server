package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question;

import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;

public class QuestionUtil {

    public static Long getPromptTemplateId(QuestionDef questionDef) {
        return questionDef.getPromptTemplate() != null ? questionDef.getPromptTemplate().getTemplateId() : null;
    }

    public static Long getTooltipTemplateId(QuestionDef questionDef) {
        return questionDef.getTooltipTemplate() != null ? questionDef.getTooltipTemplate().getTemplateId() : null;
    }

    public static Long getAdditionalInfoHeaderTemplateId(QuestionDef questionDef) {
        return questionDef.getAdditionalInfoHeaderTemplate() != null
                ? questionDef.getAdditionalInfoHeaderTemplate().getTemplateId() : null;
    }

    public static Long getAdditionalInfoFooterTemplateId(QuestionDef questionDef) {
        return questionDef.getAdditionalInfoFooterTemplate() != null
                ? questionDef.getAdditionalInfoFooterTemplate().getTemplateId() : null;
    }

    public static boolean isReadOnly(ActivityInstanceAssembleService.Context context, QuestionDef questionDef) {
        if (!questionDef.isWriteOnce()) {
            return false;
        }
        return context.getActivityInstanceDto().getReadonly() != null
                ? context.getActivityInstanceDto().getReadonly() : false;
    }
}
