package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;


/**
 * Creates {@link PicklistGroup}
 */
public class PicklistGroupCreator extends ElementCreator {

    public PicklistGroupCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public PicklistGroup createPicklistOption(PicklistGroupDef picklistGroupDef) {
        return new PicklistGroup(
             picklistGroupDef.getStableId(),
                renderTemplateIfDefined(picklistGroupDef.getNameTemplate())
        );
    }
}
