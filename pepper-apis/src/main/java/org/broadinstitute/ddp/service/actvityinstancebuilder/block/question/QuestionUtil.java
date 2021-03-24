package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;

public class QuestionUtil {

    public static boolean isReadOnly(ActivityInstanceFromActivityDefStoreBuilder.Context context, QuestionDef questionDef) {
        if (!questionDef.isWriteOnce()) {
            return false;
        }
        return context.getActivityInstanceDto().getReadonly() != null ? context.getActivityInstanceDto().getReadonly() : false;
    }
}
