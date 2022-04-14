package org.broadinstitute.ddp.service.actvityinstancebuilder.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.MailingAddressComponent;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderFactory;
import org.jdbi.v3.core.Handle;

/**
 * Aggregates objects which needs on all steps of {@link ActivityInstance} building.
 * {@link AIBuilderContext} created at the beginning of the building process.
 */
public class AIBuilderContext {

    private List<AIBuildStep> passedBuildSteps = new ArrayList<>();
    private AIBuildStep buildStep;
    private AIBuildStep failedStep;
    private String failedMessage;

    private final AIBuilderFactory aiBuilderFactory;
    private final Handle handle;
    private final AIBuilderParams params;

    private final LanguageDto languageDto;

    private FormActivityDef formActivityDef;
    private FormResponse formResponse;
    private FormInstance formInstance;

    private Long previousInstanceId;

    private final PexInterpreter interpreter = new TreeWalkInterpreter();
    private final I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();
    private final Map<String, Object> rendererInitialContext = new HashMap<>();

    private Map<Long, Template> templates = new HashMap<>();

    private Map<String, String> activitySnapshots = new HashMap<>();

    /**
     * Reference to MailingAddressComponent which built in formInstance (if not exist, then null).
     * Currently we assume that form can contain only one MailingAddressComponent.
     */
    private MailingAddressComponent mailingAddressComponent;


    public AIBuilderContext(AIBuilderFactory aiBuilderFactory, Handle handle, AIBuilderParams params) {
        this.aiBuilderFactory = aiBuilderFactory;
        this.handle = handle;
        this.params = params;
        this.languageDto = LanguageStore.get(params.getIsoLangCode());
    }

    public AIBuildStep getBuildStep() {
        return buildStep;
    }

    public void setBuildStep(AIBuildStep buildStep) {
        this.buildStep = buildStep;
        passedBuildSteps.add(buildStep);
    }

    public List<AIBuildStep> getPassedBuildSteps() {
        return passedBuildSteps;
    }

    public boolean isBuildStepPassed(AIBuildStep buildStep) {
        return passedBuildSteps.contains(buildStep);
    }

    public AIBuildStep getFailedStep() {
        return failedStep;
    }

    public void setFailedStep(AIBuildStep failedStep) {
        this.failedStep = failedStep;
    }

    public String getFailedMessage() {
        return failedMessage;
    }

    public void setFailedMessage(String failedMessage) {
        this.failedMessage = failedMessage;
    }

    public AIBuilderParams getParams() {
        return params;
    }

    public AIBuilderFactory getAIBuilderFactory() {
        return aiBuilderFactory;
    }

    public Handle getHandle() {
        return handle;
    }

    public String getUserGuid() {
        return params.getUserGuid();
    }

    public String getOperatorGuid() {
        return params.getOperatorGuid();
    }

    public String getStudyGuid() {
        return params.getStudyGuid();
    }

    public String getInstanceGuid() {
        return params.getInstanceGuid();
    }

    public String getIsoLangCode() {
        return params.getIsoLangCode();
    }

    public ContentStyle getStyle() {
        return params.getStyle();
    }

    public LanguageDto getLanguageDto() {
        return languageDto;
    }

    public FormActivityDef getFormActivityDef() {
        return formActivityDef;
    }

    public AIBuilderContext setFormActivityDef(FormActivityDef formActivityDef) {
        this.formActivityDef = formActivityDef;
        return this;
    }

    public FormResponse getFormResponse() {
        return formResponse;
    }

    public AIBuilderContext setFormResponse(FormResponse formResponse) {
        this.formResponse = formResponse;
        return this;
    }

    public FormInstance getFormInstance() {
        return formInstance;
    }

    public AIBuilderContext setFormInstance(FormInstance formInstance) {
        this.formInstance = formInstance;
        return this;
    }

    public PexInterpreter getInterpreter() {
        return interpreter;
    }

    public I18nContentRenderer getI18nContentRenderer() {
        return i18nContentRenderer;
    }

    public Map<String, Object> getRendererInitialContext() {
        return rendererInitialContext;
    }

    public Map<Long, Template> getTemplates() {
        return templates;
    }

    public Long getPreviousInstanceId() {
        return previousInstanceId;
    }

    public AIBuilderContext setPreviousInstanceId(Long previousInstanceId) {
        this.previousInstanceId = previousInstanceId;
        return this;
    }

    public Map<String, String> getActivitySnapshots() {
        return activitySnapshots;
    }

    public MailingAddressComponent getMailingAddressComponent() {
        return mailingAddressComponent;
    }

    public void setMailingAddressComponent(MailingAddressComponent mailingAddressComponent) {
        this.mailingAddressComponent = mailingAddressComponent;
    }
}
