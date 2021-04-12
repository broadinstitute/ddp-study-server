package org.broadinstitute.ddp.service.actvityinstancebuilder.context;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.jdbi.v3.core.Handle;

/**
 * Aggregates objects which needs on all steps of {@link ActivityInstance} building.
 * {@link AIBuilderContext} created at the beginning of the building process.
 *
 * <p>Contains a reference to {@link AICreatorsFactory} where created all instances
 * of Creator-classes used to create {@link ActivityInstance}
 */
public class AIBuilderContext {

    private final Handle handle;
    private final String userGuid;
    private final String operatorGuid;
    private final long langCodeId;
    private final String isoLangCode;
    private final FormActivityDef formActivityDef;
    private final FormResponse formResponse;

    private final AIBuilderExtraParams aiBuilderExtraParams;
    private final AIBuilderCustomizationFlags aiBuilderCustomizationFlags;

    private final PexInterpreter interpreter = new TreeWalkInterpreter();
    private final I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();
    private final Map<String, Object> rendererInitialContext = new HashMap<>();

    private final Long previousInstanceId;

    private Map<Long, String> renderedTemplates = new HashMap<>();

    private final AICreatorsFactory creatorsFactory;

    public AIBuilderContext(
            Handle handle,
            String userGuid,
            String operatorGuid,
            String isoLangCode,
            FormActivityDef formActivityDef,
            FormResponse formResponse,
            AIBuilderExtraParams aiBuilderExtraParams,
            AIBuilderCustomizationFlags aiBuilderCustomizationFlags) {
        this.handle = handle;
        this.userGuid = userGuid;
        this.operatorGuid = operatorGuid == null ? userGuid : operatorGuid;
        this.isoLangCode = isoLangCode;
        this.langCodeId = LanguageStore.get(isoLangCode).getId();
        this.formActivityDef = formActivityDef;
        this.formResponse = formResponse;

        this.aiBuilderExtraParams = aiBuilderExtraParams == null ? AIBuilderExtraParams.create() : aiBuilderExtraParams;
        this.aiBuilderCustomizationFlags = aiBuilderCustomizationFlags;

        this.previousInstanceId = ActivityInstanceUtil.getPreviousInstanceId(handle, formResponse.getId());

        creatorsFactory = new AICreatorsFactory();
    }

    public Handle getHandle() {
        return handle;
    }

    public String getIsoLangCode() {
        return isoLangCode;
    }

    public long getLangCodeId() {
        return langCodeId;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getOperatorGuid() {
        return operatorGuid;
    }

    public FormActivityDef getFormActivityDef() {
        return formActivityDef;
    }

    public FormResponse getFormResponse() {
        return formResponse;
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

    public Map<Long, String> getRenderedTemplates() {
        return renderedTemplates;
    }

    public Long getPreviousInstanceId() {
        return previousInstanceId;
    }

    public AICreatorsFactory creators() {
        return creatorsFactory;
    }

    public AIBuilderExtraParams getAiBuilderExtraParams() {
        return aiBuilderExtraParams;
    }

    public AIBuilderCustomizationFlags getAiBuilderCustomizationParams() {
        return aiBuilderCustomizationFlags;
    }
}
