package org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.util.CollectionMiscUtil;


public class PicklistCreatorHelper {

    public PicklistGroup createPicklistGroup(AIBuilderContext ctx, PicklistGroupDef picklistGroupDef) {
        return new PicklistGroup(
                picklistGroupDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, picklistGroupDef.getNameTemplate())
        );
    }

    public PicklistOption createPicklistOption(AIBuilderContext ctx, PicklistOptionDef picklistOptionDef,
                                               List<PicklistGroupDef> picklistGroupDefs) {
        return createPicklistOption(ctx, picklistOptionDef, picklistGroupDefs, false);
    }

    public PicklistOption createPicklistOption(AIBuilderContext ctx, PicklistOptionDef picklistOptionDef,
                                               List<PicklistGroupDef> picklistGroupDefs, boolean isNested) {
        PicklistOption picklistOption = new PicklistOption(
                picklistOptionDef.getStableId(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, picklistOptionDef.getOptionLabelTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, picklistOptionDef.getTooltipTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, picklistOptionDef.getDetailLabelTemplate()),
                picklistOptionDef.isDetailsAllowed(),
                picklistOptionDef.isExclusive(),
                picklistOptionDef.isDefault(),
                isNested ? null : ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, picklistOptionDef.getNestedOptionsLabelTemplate()),
                isNested ? null :
                    CollectionMiscUtil.createListFromAnotherList(picklistOptionDef.getNestedOptions(),
                        (nestedPicklistOptionDef) -> createPicklistOption(ctx, nestedPicklistOptionDef,  picklistGroupDefs, true))
        );

        setGroupStableIdIfOptionBelongsToGroup(picklistGroupDefs, picklistOption);

        return picklistOption;
    }

    private void setGroupStableIdIfOptionBelongsToGroup(List<PicklistGroupDef> picklistGroupDefs, PicklistOption picklistOption) {
        if (picklistGroupDefs != null) {
            for (PicklistGroupDef groupDef : picklistGroupDefs) {
                for (PicklistOptionDef optionDef : groupDef.getOptions()) {
                    if (StringUtils.equals(optionDef.getStableId(), picklistOption.getStableId())) {
                        picklistOption.setGroupStableId(groupDef.getStableId());
                    }
                }
            }
        }
    }
}
