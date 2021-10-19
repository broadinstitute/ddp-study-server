package org.broadinstitute.ddp.model.activity.definition.template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.MiscUtil;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class Template {

    public static final String VELOCITY_VAR_PREFIX = "$";

    @NotNull
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
        return templateType;
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
        return variables;
    }

    public void addVariable(TemplateVariable variable) {
        if (variable != null) {
            if (variables == null) {
                variables = new ArrayList<>();
            }
            variables.add(variable);
        }
    }

    public Optional<TemplateVariable> getVariable(String name) {
        if (variables == null) {
            return null;
        } else {
            return variables.stream().filter(var -> var.getName().equals(name)).findFirst();
        }
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
        return TemplateUtil.render(getTemplateText(), getVariables(), languageCode, useDefaultsForDdpMethods);
    }

    public String render(String languageCode, I18nContentRenderer renderer, Map<String, Object> initialContext) {
        return TemplateUtil.render(getTemplateText(), getVariables(), languageCode, renderer, initialContext);
    }

    public String renderWithDefaultValues(String languageCode) {
        return TemplateUtil.renderWithDefaultValues(getTemplateText(), getVariables(), languageCode);
    }
}
