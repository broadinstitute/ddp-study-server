package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.TemplateHandler.addAndRenderTemplate;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.service.actvityinstancebuilder.Context;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

public class PicklistCreatorHelper {

    public PicklistGroup createPicklistOption(Context ctx, PicklistGroupDef picklistGroupDef) {
        return new PicklistGroup(
                picklistGroupDef.getStableId(),
                addAndRenderTemplate(ctx, picklistGroupDef.getNameTemplate())
        );
    }

    public PicklistOption createPicklistOption(Context ctx, PicklistOptionDef picklistOptionDef) {
        return createPicklistOption(ctx, picklistOptionDef, false);
    }

    public PicklistOption createPicklistOption(Context ctx, PicklistOptionDef picklistOptionDef, boolean isNested) {
        return new PicklistOption(
                picklistOptionDef.getStableId(),
                addAndRenderTemplate(ctx, picklistOptionDef.getOptionLabelTemplate()),
                addAndRenderTemplate(ctx, picklistOptionDef.getTooltipTemplate()),
                addAndRenderTemplate(ctx, picklistOptionDef.getDetailLabelTemplate()),
                picklistOptionDef.isDetailsAllowed(),
                picklistOptionDef.isExclusive(),
                isNested ? null : addAndRenderTemplate(ctx, picklistOptionDef.getNestedOptionsLabelTemplate()),
                isNested ? null :
                    CollectionMiscUtil.createListFromAnotherList(picklistOptionDef.getNestedOptions(),
                        (nestedPicklistOptionDef) -> createPicklistOption(ctx, nestedPicklistOptionDef, true))
        );
    }
}
