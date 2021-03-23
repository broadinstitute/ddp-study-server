package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates {@link PicklistOption}
 */
public class PicklistOptionCreator extends ElementCreator {

    public PicklistOptionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public PicklistOption createPicklistOption(PicklistOptionDef picklistOptionDef) {
        return createPicklistOption(picklistOptionDef, false);
    }

    public PicklistOption createPicklistOption(PicklistOptionDef picklistOptionDef, boolean isNested) {
        return new PicklistOption(
                picklistOptionDef.getStableId(),
                getTemplateId(picklistOptionDef.getOptionLabelTemplate()),
                getTemplateId(picklistOptionDef.getTooltipTemplate()),
                getTemplateId(picklistOptionDef.getDetailLabelTemplate()),
                picklistOptionDef.isDetailsAllowed(),
                picklistOptionDef.isExclusive(),
                isNested ? null : (getTemplateId(picklistOptionDef.getNestedOptionsLabelTemplate())),
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
