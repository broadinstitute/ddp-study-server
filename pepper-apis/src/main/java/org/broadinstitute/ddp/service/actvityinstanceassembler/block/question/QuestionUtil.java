package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question;

import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;

public class QuestionUtil {

    public static boolean isReadOnly(ActivityInstanceAssembleService.Context context, QuestionDef questionDef) {
        if (!questionDef.isWriteOnce()) {
            return false;
        }
        return context.getActivityInstanceDto().getReadonly() != null ? context.getActivityInstanceDto().getReadonly() : false;
    }
}
