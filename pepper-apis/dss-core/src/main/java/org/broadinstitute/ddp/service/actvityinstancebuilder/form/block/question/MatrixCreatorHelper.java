package org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question;

import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixGroup;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixOption;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixRow;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;


public class MatrixCreatorHelper {

    public MatrixGroup createMatrixGroup(AIBuilderContext ctx, MatrixGroupDef matrixGroupDef) {
        return new MatrixGroup(matrixGroupDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(ctx, matrixGroupDef.getNameTemplate())
        );
    }
    
    public MatrixOption createMatrixOption(AIBuilderContext ctx, MatrixOptionDef matrixOptionDef) {
        return new MatrixOption(
                matrixOptionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(ctx, matrixOptionDef.getOptionLabelTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(ctx, matrixOptionDef.getTooltipTemplate()),
                matrixOptionDef.getGroupStableId(),
                matrixOptionDef.isExclusive());
    }

    public MatrixRow createMatrixQuestionRow(AIBuilderContext ctx, MatrixRowDef matrixQuestionRowDef) {
        return new MatrixRow(
                matrixQuestionRowDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(ctx, matrixQuestionRowDef.getRowLabelTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(ctx, matrixQuestionRowDef.getTooltipTemplate()));
    }
}
