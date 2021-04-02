package org.broadinstitute.ddp.service.actvityinstancebuilder;

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
 * {@link Context} created at the beginning of the building process.
 */
public class Context {

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
    private final Map<String, Object> rendererInitialContext;

    private final Long previousInstanceId;

    private Map<Long, String> renderedTemplates = new HashMap<>();
    private Map<String, String> commonSnapshot;
    private Map<String, String> snapshot;

    private final CreatorFactory creatorFactory;

    public Context(
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

        this.rendererInitialContext = TemplateHandler.createRendererInitialContext(this);

        this.previousInstanceId = ActivityInstanceUtil.getPreviousInstanceId(handle, formResponse.getId());

        creatorFactory = new CreatorFactory();
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

    public Map<String, String> getCommonSnapshot() {
        return commonSnapshot;
    }

    public void setCommonSnapshot(Map<String, String> commonSnapshot) {
        this.commonSnapshot = commonSnapshot;
    }

    public Map<String, String> getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Map<String, String> snapshot) {
        this.snapshot = snapshot;
    }

    public CreatorFactory creators() {
        return creatorFactory;
    }
}
