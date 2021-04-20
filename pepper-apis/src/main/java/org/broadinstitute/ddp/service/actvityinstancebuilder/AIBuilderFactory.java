package org.broadinstitute.ddp.service.actvityinstancebuilder;

import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderParams;
import org.broadinstitute.ddp.service.actvityinstancebuilder.factory.AIContentRendererFactory;
import org.broadinstitute.ddp.service.actvityinstancebuilder.factory.AICreatorsFactory;
import org.broadinstitute.ddp.service.actvityinstancebuilder.factory.TemplateRenderFactory;
import org.jdbi.v3.core.Handle;


public class AIBuilderFactory {

    public static ActivityInstanceFromDefinitionBuilder createAIBuilder(
            Handle handle, AIBuilderParams params) {
        return new ActivityInstanceFromDefinitionBuilder(createAIBuilderFactory(), handle, params);
    }

    public static ActivityInstanceFromDefinitionBuilder createAIBuilder(
            AIBuilderFactory aiBuilderFactory, Handle handle, AIBuilderParams params) {
        return new ActivityInstanceFromDefinitionBuilder(aiBuilderFactory, handle, params);
    }

    public static AIBuilderFactory createAIBuilderFactory() {
        return new AIBuilderFactory();
    }

    private AICreatorsFactory aiCreatorsFactory = new AICreatorsFactory();
    private AIContentRendererFactory aiContentRendererFactory = new AIContentRendererFactory();
    private TemplateRenderFactory templateRenderFactory = new TemplateRenderFactory();

    private AIBuilderFactory() {
    }

    public AICreatorsFactory getAICreatorsFactory() {
        return aiCreatorsFactory;
    }

    public AIBuilderFactory setAICreatorsFactory(AICreatorsFactory aiCreatorsFactory) {
        this.aiCreatorsFactory = aiCreatorsFactory;
        return this;
    }

    public AIContentRendererFactory getAIContentRendererFactory() {
        return aiContentRendererFactory;
    }

    public AIBuilderFactory setAIContentRendererFactory(AIContentRendererFactory aiContentRendererFactory) {
        this.aiContentRendererFactory = aiContentRendererFactory;
        return this;
    }

    public TemplateRenderFactory getTemplateRenderFactory() {
        return templateRenderFactory;
    }

    public AIBuilderFactory setTemplateRenderFactory(TemplateRenderFactory templateRenderFactory) {
        this.templateRenderFactory = templateRenderFactory;
        return this;
    }
}
