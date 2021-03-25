package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;

/**
 * Creates {@link PicklistOption}
 */
public class PicklistOptionCreator extends ElementCreator {

    public PicklistOptionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
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
                isNested ? null : (renderTemplateIfDefined(picklistOptionDef.getNestedOptionsLabelTemplate())),
                isNested ? null : createNestedPicklistOptions(picklistOptionDef.getNestedOptions())
        );
    }

    public List<PicklistOption> createNestedPicklistOptions(List<PicklistOptionDef> nestedPicklistOptionsDef) {
        List<PicklistOption> picklistOptions = new ArrayList<>();
        if (nestedPicklistOptionsDef != null) {
            nestedPicklistOptionsDef.forEach(po -> picklistOptions.add(createPicklistOption(po, true)));
        }
        return picklistOptions;
    }
}
