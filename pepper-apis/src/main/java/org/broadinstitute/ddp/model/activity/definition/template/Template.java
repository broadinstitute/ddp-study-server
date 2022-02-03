package org.broadinstitute.ddp.model.activity.definition.template;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.I18nTemplateRenderFacade;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.MiscUtil;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class Template {

    public static final String VELOCITY_VAR_PREFIX = "$";

    @SerializedName("templateType")
    private TemplateType templateType;

    @SerializedName("templateCode")
    private String templateCode;

    @NotNull
    @SerializedName("templateText") 
    private final String templateText;

    @NotNull
    @SerializedName("variables")
    private Collection<@Valid @NotNull TemplateVariable> variables = new ArrayList<>();

    private transient Long templateId;
    private transient Long revisionId;

    public static Template text(String template) {
        return new Template(TemplateType.TEXT, null, template);
    }

    public static Template html(String template) {
        return new Template(TemplateType.HTML, null, template);
    }

    @JdbiConstructor
    public Template(
            @ColumnName("template_id") long id,
            @ColumnName("template_type") TemplateType type,
            @ColumnName("template_code") String code,
            @ColumnName("template_text") String text,
            @ColumnName("template_revision_id") long revisionId) {
        this(type, code, text);
        this.templateId = id;
        this.revisionId = revisionId;
    }

    public Template(TemplateType templateType, String templateCode, String templateText) {
        this.templateType = MiscUtil.checkNonNull(templateType, "templateType");
        this.templateCode = templateCode;
        this.templateText = MiscUtil.checkNonNull(templateText, "templateText");
    }
    
    public Optional<Long> getRevisionId() {
        return Optional.ofNullable(revisionId);
    }

    public TemplateType getTemplateType() {
        return templateType != null ? templateType : TemplateType.TEXT;
    }

    public void setTemplateType(TemplateType templateType) {
        this.templateType = templateType;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateText() {
        return templateText;
    }

    public Collection<TemplateVariable> getVariables() {
        return this.variables;
    }

    public void setVariables(Collection<TemplateVariable> variables) {
        this.variables = variables;
    }

    /**
     * It is possible that `variables` set to null: this could happen during building of
     * object {@link Template} from a JSON (config file) in a case if child element `variables[]` is not specified
     * (and we don't want to specify it trying to make template definition in config files as compact as possible).
     * It means that if we want during JSON serialization to Template object to avoid assigning variables to null we
     * need to define in config like:
     * <pre>
     * {@code
     *     "bodyTemplate": {
     *             "templateType": "HTML", "templateText": """<p class="ddp-question-prompt">$prompt *</p>"""
     *             "variables": []
     *     }
     * }
     * </pre>
     * But more compact to do like this (but this causes to set `variables` to null):
     * <pre>
     * {@code
     *  "bodyTemplate": {"templateType": "HTML", "templateText": """<p class="ddp-question-prompt">$prompt *</p>"""}
     * }
     * </pre>
     * So, it is checked if `variables` is null and if it is - an empty list is created.
     */
    public void addVariable(TemplateVariable variable) {
        if (variable != null) {
            if (variables == null) {
                variables = new ArrayList<>();
            }
            variables.add(variable);
        }
    }

    /**
     * It is possible that variables could be null (see comments to method {@link #addVariable(TemplateVariable)}
     */
    public Optional<TemplateVariable> getVariable(String name) {
        if (variables == null) {
            return Optional.empty();
        }

        return variables.stream().filter(var -> var.getName().equals(name)).findFirst();
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String render(String languageCode) {
        return render(languageCode, false);
    }

    public String render(String languageCode, boolean useDefaultsForDdpMethods) {
        return I18nTemplateRenderFacade.INSTANCE.renderTemplate(
                this, getTemplateText(), getVariables(), languageCode, useDefaultsForDdpMethods);
    }

    public String render(String languageCode, Map<String, Object> initialContext) {
        return I18nTemplateRenderFacade.INSTANCE.renderTemplate(this, languageCode, initialContext);
    }

    public String renderWithDefaultValues(String languageCode) {
        return I18nTemplateRenderFacade.INSTANCE.renderTemplateWithDefaultValues(getTemplateText(), getVariables(), languageCode);
    }
}
