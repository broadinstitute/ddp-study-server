package org.broadinstitute.ddp.service.actvityinstancebuilder.context;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;

/**
 * Extra parameters for {@link ActivityInstanceFromDefinitionBuilder}.
 * Contains parameters which are optional (could be mandatory in some cases).
 */
public class AIBuilderExtraParams {

    private UserActivityInstanceSummary instanceSummary;
    private ContentStyle style;


    public static AIBuilderExtraParams create() {
        return new AIBuilderExtraParams();
    }

    private AIBuilderExtraParams() {
    }

    public UserActivityInstanceSummary getInstanceSummary() {
        return instanceSummary;
    }

    public AIBuilderExtraParams setInstanceSummary(UserActivityInstanceSummary instanceSummary) {
        this.instanceSummary = instanceSummary;
        return this;
    }

    public ContentStyle getStyle() {
        return style;
    }

    public AIBuilderExtraParams setStyle(ContentStyle style) {
        this.style = style;
        return this;
    }
}
