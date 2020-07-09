package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class DateQuestionDef extends QuestionDef {

    @NotNull
    @SerializedName("renderMode")
    private DateRenderMode renderMode;

    @SerializedName("displayCalendar")
    private boolean displayCalendar;

    @NotEmpty
    @SerializedName("fields")
    private List<@NotNull DateFieldType> fields;

    @Valid
    @SerializedName("picklistConfig")
    private DatePicklistDef picklistDef;

    @Valid
    @SerializedName("placeholderTemplate")
    private Template placeholderTemplate;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(DateRenderMode renderMode, String stableId, Template prompt) {
        return new Builder()
                .setRenderMode(renderMode)
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    /**
     * Instantiate DateQuestionDef object.
     */
    public DateQuestionDef(String stableId, boolean isRestricted, Template promptTemplate,
                           Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                           List<RuleDef> validations, DateRenderMode renderMode, boolean displayCalendar,
                           List<DateFieldType> fields, DatePicklistDef picklistDef, boolean hideNumber,
                           Template placeholderTemplate) {
        super(QuestionType.DATE,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber);
        
        this.renderMode = MiscUtil.checkNonNull(renderMode, "renderMode");
        this.displayCalendar = displayCalendar;
        this.picklistDef = picklistDef;
        this.placeholderTemplate = placeholderTemplate;
        if (fields != null && !fields.isEmpty()) {
            this.fields = fields;
        } else {
            throw new IllegalArgumentException("Need at least one date field");
        }
    }

    public DateQuestionDef(String stableId, boolean isRestricted, Template promptTemplate,
                           Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                           List<RuleDef> validations, DateRenderMode renderMode, boolean displayCalendar,
                           List<DateFieldType> fields, DatePicklistDef picklistDef,
                           boolean hideNumber) {
        this(stableId, isRestricted, promptTemplate, additionalInfoHeaderTemplate, additionalInfoFooterTemplate,
                validations, renderMode, displayCalendar, fields, picklistDef, hideNumber, null);
    }

    public DateRenderMode getRenderMode() {
        return renderMode;
    }

    public boolean isDisplayCalendar() {
        return displayCalendar;
    }

    public List<DateFieldType> getFields() {
        return fields;
    }

    public DatePicklistDef getPicklistDef() {
        return picklistDef;
    }

    public Template getPlaceholderTemplate() {
        return placeholderTemplate;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private DateRenderMode renderMode;

        private boolean displayCalendar = false;
        private DatePicklistDef picklistDef = null;
        private List<DateFieldType> fields = new ArrayList<>();
        private Template placeholderTemplate = null;

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setRenderMode(DateRenderMode renderMode) {
            this.renderMode = renderMode;
            return this;
        }

        public Builder setDisplayCalendar(boolean displayCalendar) {
            this.displayCalendar = displayCalendar;
            return this;
        }

        public Builder setPicklistDef(DatePicklistDef picklistDef) {
            this.picklistDef = picklistDef;
            return this;
        }

        public Builder setPlaceholderTemplate(Template placeholderTemplate) {
            this.placeholderTemplate = placeholderTemplate;
            return this;
        }

        public Builder addFields(DateFieldType... fields) {
            Collections.addAll(this.fields, fields);
            return this;
        }

        public Builder addFields(Collection<DateFieldType> fields) {
            this.fields.addAll(fields);
            return this;
        }

        public Builder clearFields() {
            this.fields.clear();
            return this;
        }

        public DateQuestionDef build() {
            DateQuestionDef question = new DateQuestionDef(stableId,
                                                            isRestricted,
                                                            prompt,
                                                            getAdditionalInfoHeader(),
                                                            getAdditionalInfoFooter(),
                                                            validations,
                                                            renderMode,
                                                            displayCalendar,
                                                            fields,
                                                            picklistDef,
                                                            hideNumber,
                                                            placeholderTemplate);
            configure(question);
            return question;
        }
    }
}
