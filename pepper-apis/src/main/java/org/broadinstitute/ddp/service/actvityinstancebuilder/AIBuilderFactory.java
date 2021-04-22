package org.broadinstitute.ddp.service.actvityinstancebuilder;

import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderParams;
import org.broadinstitute.ddp.service.actvityinstancebuilder.service.AIContentRendererService;
import org.broadinstitute.ddp.service.actvityinstancebuilder.service.SetDisplayNumbersService;
import org.broadinstitute.ddp.service.actvityinstancebuilder.service.TemplateRenderService;
import org.broadinstitute.ddp.service.actvityinstancebuilder.service.UpdateBlockStatusesService;
import org.broadinstitute.ddp.service.actvityinstancebuilder.service.ValidationRuleService;
import org.jdbi.v3.core.Handle;


/**
 * AI builder main factory: it contains references to other factories
 * (constructing all components of ActivityInstance builder module).
 * Also it provides {@link ActivityInstanceFromDefinitionBuilder} creation - via static methods:
 * <ul>
 * <li>{@link #createAIBuilder(Handle, AIBuilderParams)} - this version utilizes a default implementation of
 *    the factory class - {@link AIBuilderFactory}</li>
 * <li>{@link #createAIBuilder(AIBuilderFactory, Handle, AIBuilderParams)} - this method allows to specify
 *    custom version of factory class (rewriting any parts of AI builder code).</li>
 * </ul>
 */
public class AIBuilderFactory {

    /**
     * ActivityInstance builder creator method.
     * In this version of creator method used a default {@link AIBuilderFactory}.
     *
     * @param handle jdbi Handle
     * @param params object with parameters
     * @return created AI builder
     */
    public static ActivityInstanceFromDefinitionBuilder createAIBuilder(
            Handle handle, AIBuilderParams params) {
        return new ActivityInstanceFromDefinitionBuilder(createAIBuilderFactory(), handle, params);
    }

    /**
     * ActivityInstance builder creator method.
     * In this version it is possible to specify custom version of {@link AIBuilderFactory}.
     *
     * @param aiBuilderFactory custom {@link AIBuilderFactory}
     * @param handle jdbi Handle
     * @param params object with parameters
     * @return created AI builder
     */
    public static ActivityInstanceFromDefinitionBuilder createAIBuilder(
            AIBuilderFactory aiBuilderFactory, Handle handle, AIBuilderParams params) {
        return new ActivityInstanceFromDefinitionBuilder(aiBuilderFactory, handle, params);
    }

    /**
     * Creates default {@link AIBuilderFactory}
     */
    public static AIBuilderFactory createAIBuilderFactory() {
        return new AIBuilderFactory();
    }


    private AICreatorsFactory aiCreatorsFactory = new AICreatorsFactory();

    private AIContentRendererService aiContentRendererService = new AIContentRendererService();
    private TemplateRenderService templateRenderService = new TemplateRenderService();
    private UpdateBlockStatusesService updateBlockStatusesService = new UpdateBlockStatusesService();
    private SetDisplayNumbersService setDisplayNumbersService = new SetDisplayNumbersService();
    private ValidationRuleService validationRuleService = new ValidationRuleService();


    private AIBuilderFactory() {
    }

    public AICreatorsFactory getAICreatorsFactory() {
        return aiCreatorsFactory;
    }

    public AIBuilderFactory setAICreatorsFactory(AICreatorsFactory aiCreatorsFactory) {
        this.aiCreatorsFactory = aiCreatorsFactory;
        return this;
    }

    public AIContentRendererService getAIContentRendererFactory() {
        return aiContentRendererService;
    }

    public AIBuilderFactory setAIContentRendererFactory(AIContentRendererService aiContentRendererService) {
        this.aiContentRendererService = aiContentRendererService;
        return this;
    }

    public TemplateRenderService getTemplateRenderFactory() {
        return templateRenderService;
    }

    public AIBuilderFactory setTemplateRenderFactory(TemplateRenderService templateRenderService) {
        this.templateRenderService = templateRenderService;
        return this;
    }

    public UpdateBlockStatusesService getUpdateBlockStatusFactory() {
        return updateBlockStatusesService;
    }

    public AIBuilderFactory setUpdateBlockStatusFactory(UpdateBlockStatusesService updateBlockStatusesService) {
        this.updateBlockStatusesService = updateBlockStatusesService;
        return this;
    }

    public SetDisplayNumbersService getSetDisplayNumbersFactory() {
        return setDisplayNumbersService;
    }

    public AIBuilderFactory setSetDisplayNumbersFactory(SetDisplayNumbersService setDisplayNumbersService) {
        this.setDisplayNumbersService = setDisplayNumbersService;
        return this;
    }

    public ValidationRuleService getValidationRuleService() {
        return validationRuleService;
    }

    public AIBuilderFactory setValidationRuleService(ValidationRuleService validationRuleService) {
        this.validationRuleService = validationRuleService;
        return this;
    }
}
