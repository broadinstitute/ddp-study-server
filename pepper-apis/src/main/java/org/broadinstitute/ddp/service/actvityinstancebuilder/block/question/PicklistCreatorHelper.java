package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.service.actvityinstancebuilder.AbstractCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

public class PicklistCreatorHelper extends AbstractCreator {

    public PicklistCreatorHelper(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public PicklistGroup createPicklistOption(PicklistGroupDef picklistGroupDef) {
        return new PicklistGroup(
                picklistGroupDef.getStableId(),
                renderTemplateIfDefined(picklistGroupDef.getNameTemplate())
        );
    }

    public PicklistOption createPicklistOption(PicklistOptionDef picklistOptionDef) {
        return createPicklistOption(picklistOptionDef, false);
    }

    public PicklistOption createPicklistOption(PicklistOptionDef picklistOptionDef, boolean isNested) {
        return new PicklistOption(
                picklistOptionDef.getStableId(),
                renderTemplateIfDefined(picklistOptionDef.getOptionLabelTemplate()),
                renderTemplateIfDefined(picklistOptionDef.getTooltipTemplate()),
                renderTemplateIfDefined(picklistOptionDef.getDetailLabelTemplate()),
                picklistOptionDef.isDetailsAllowed(),
                picklistOptionDef.isExclusive(),
                isNested ? null : renderTemplateIfDefined(picklistOptionDef.getNestedOptionsLabelTemplate()),
                isNested ? null :
                        CollectionMiscUtil.createListFromAnotherList(picklistOptionDef.getNestedOptions(),
                                (nestedPicklistOptionDef) -> createPicklistOption(nestedPicklistOptionDef, true))
        );
    }
}
