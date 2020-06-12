package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.MiscUtil;

public class DateQuestion extends Question<DateAnswer> {

    @NotNull
    @SerializedName("renderMode")
    private DateRenderMode renderMode;

    @SerializedName("displayCalendar")
    private boolean displayCalendar;

    @NotEmpty
    @SerializedName("fields")
    private List<DateFieldType> fields;

    @SerializedName("placeholderText")
    private String placeholderText;

    protected transient Long placeholderTemplateId;

    public DateQuestion(String stableId, long promptTemplateId,
                        boolean isRestricted, boolean isDeprecated, Tooltip tooltip,
                        @Nullable Long additionalInfoHeaderTemplateId, @Nullable Long additionalInfoFooterTemplateId,
                        List<DateAnswer> answers, List<Rule<DateAnswer>> validations,
                        DateRenderMode renderMode, boolean displayCalendar, List<DateFieldType> fields, Long placeholderTemplateId) {
        super(QuestionType.DATE,
                stableId,
                promptTemplateId,
                isRestricted,
                isDeprecated,
                tooltip,
                additionalInfoHeaderTemplateId,
                additionalInfoFooterTemplateId,
                answers,
                validations);

        this.renderMode = MiscUtil.checkNonNull(renderMode, "renderMode");
        this.displayCalendar = displayCalendar;
        this.placeholderTemplateId = placeholderTemplateId;
        if (fields != null && !fields.isEmpty()) {
            this.fields = fields;
        } else {
            throw new IllegalArgumentException("date fields cannot be null or empty");
        }
    }

    public DateQuestion(String stableId, long promptTemplateId,
                        List<DateAnswer> answers, List<Rule<DateAnswer>> validations,
                        DateRenderMode renderMode, boolean displayCalendar, List<DateFieldType> fields) {
        this(stableId, promptTemplateId, false, false, null, null, null, answers, validations, renderMode, displayCalendar, fields, null);
    }

    public DateRenderMode getRenderMode() {
        return renderMode;
    }

    public boolean getDisplayCalendar() {
        return displayCalendar;
    }

    public List<DateFieldType> getFields() {
        return fields;
    }

    public boolean isSpecifiedFieldsPresent(DateAnswer answer) {
        DateValue value = answer.getValue();
        for (DateFieldType field : fields) {
            if ((field == DateFieldType.YEAR && value.getYear() == null)
                    || (field == DateFieldType.MONTH && value.getMonth() == null)
                    || (field == DateFieldType.DAY && value.getDay() == null)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasRequiredFields(DateAnswer answer) {
        DateValue value = answer.getValue();
        boolean hasFieldRule = false;
        for (var rule : validations) {
            if (rule.getRuleType() == RuleType.YEAR_REQUIRED) {
                hasFieldRule = true;
                if (value.getYear() == null) {
                    return false;
                }
            } else if (rule.getRuleType() == RuleType.MONTH_REQUIRED) {
                hasFieldRule = true;
                if (value.getMonth() == null) {
                    return false;
                }
            } else if (rule.getRuleType() == RuleType.DAY_REQUIRED) {
                hasFieldRule = true;
                if (value.getDay() == null) {
                    return false;
                }
            }
        }
        // There are field rules and date has value for those rules, so return true.
        return hasFieldRule;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        super.registerTemplateIds(registry);
        if (placeholderTemplateId != null) {
            registry.accept(placeholderTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        super.applyRenderedTemplates(rendered, style);
        if (placeholderTemplateId != null) {
            placeholderText = rendered.get(placeholderTemplateId);
            if (placeholderText == null) {
                throw new NoSuchElementException("No rendered template found for placeholder with id " + placeholderTemplateId);
            }
            if (style == ContentStyle.BASIC) {
                placeholderText = HtmlConverter.getPlainText(placeholderText);
            }
        }
    }
}
