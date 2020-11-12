package org.broadinstitute.ddp.model.activity.definition.template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.MiscUtil;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Template {

    public static final String VELOCITY_VAR_PREFIX = "$";

    private static final Logger LOG = LoggerFactory.getLogger(Template.class);

    @NotNull
    @SerializedName("templateType")
    private TemplateType templateType;

    @SerializedName("templateCode")
    private String templateCode;

    @NotNull
    @SerializedName("templateText") 
    private String templateText;

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
            variables.add(variable);
        }
    }

    public Optional<TemplateVariable> getVariable(String name) {
        return variables.stream().filter(var -> var.getName().equals(name)).findFirst();
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String render(String languageCode) {
        Map<String, Object> variablesTxt = new HashMap<>();
        for (TemplateVariable variable : getVariables()) {
            Optional<Translation> translation = variable.getTranslation(languageCode);
            variablesTxt.put(variable.getName(), translation.isPresent() ? translation.get().getText() : null);
        }

        I18nContentRenderer renderer = new I18nContentRenderer();
        return renderer.renderToString(getTemplateText(), variablesTxt);
    }

}
