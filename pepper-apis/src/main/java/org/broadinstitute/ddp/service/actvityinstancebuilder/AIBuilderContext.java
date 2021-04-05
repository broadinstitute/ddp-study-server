package org.broadinstitute.ddp.service.actvityinstancebuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.ContentStyle;
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
 */
public class AIBuilderContext {

    private final Handle handle;
    private final String userGuid;
    private final String operatorGuid;
    private final long langCodeId;
    private final String isoLangCode;
    private final ContentStyle style;
    private final FormActivityDef formActivityDef;
    private final FormResponse formResponse;

    private final PexInterpreter interpreter = new TreeWalkInterpreter();
    private final I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();
    private final Map<String, Object> rendererInitialContext = new HashMap<>();

    private final Long previousInstanceId;

    private Map<Long, String> renderedTemplates = new HashMap<>();
    private Map<String, String> commonSnapshot;
    private Map<String, String> snapshot;
    private LocalDate lastUpdatedDate;

    private final AICreatorsFactory creatorFactory;

    public AIBuilderContext(
            Handle handle,
            String userGuid,
            String operatorGuid,
            String isoLangCode,
            ContentStyle style,
            FormActivityDef formActivityDef,
            FormResponse formResponse) {
        this.handle = handle;
        this.userGuid = userGuid;
        this.operatorGuid = operatorGuid;
        this.isoLangCode = isoLangCode;
        this.style = style;
        this.langCodeId = LanguageStore.get(isoLangCode).getId();
        this.formActivityDef = formActivityDef;
        this.formResponse = formResponse;

        this.previousInstanceId = ActivityInstanceUtil.getPreviousInstanceId(handle, formResponse.getId());

        creatorFactory = new AICreatorsFactory();
    }

    public Handle getHandle() {
        return handle;
    }

    public String getIsoLangCode() {
        return isoLangCode;
    }

    public ContentStyle getStyle() {
        return style;
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

    public LocalDate getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate(LocalDate lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }

    public AICreatorsFactory creators() {
        return creatorFactory;
    }
}
