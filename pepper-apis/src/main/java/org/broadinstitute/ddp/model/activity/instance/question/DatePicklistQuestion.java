package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;

public class DatePicklistQuestion extends DateQuestion {

    @SerializedName("useMonthNames")
    private Boolean useMonthNames;

    @SerializedName("startYear")
    private Integer startYear;

    @SerializedName("endYear")
    private Integer endYear;

    @SerializedName("firstSelectedYear")
    private Integer firstSelectedYear;

    /**
     * Instantiates DatePicklistQuestion object.
     */
    public DatePicklistQuestion(String stableId, long promptTemplateId,
                                boolean isRestricted, boolean isDeprecated, Tooltip tooltip,
                                Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
                                List<DateAnswer> answers, List<Rule<DateAnswer>> validations,
                                DateRenderMode renderMode, Boolean displayCalendar, List<DateFieldType> fields, Long placeholderTemplateId,
                                Boolean useMonthNames, Integer startYear, Integer endYear, Integer firstSelectedYear) {
        super(stableId,
                promptTemplateId,
                isRestricted,
                isDeprecated,
                tooltip,
                additionalInfoHeaderTemplateId,
                additionalInfoFooterTemplateId,
                answers,
                validations,
                renderMode,
                displayCalendar,
                fields,
                placeholderTemplateId);

        this.useMonthNames = useMonthNames;
        this.startYear = startYear;
        this.endYear = endYear;
        this.firstSelectedYear = firstSelectedYear;
    }

    public Boolean getUseMonthNames() {
        return useMonthNames;
    }

    public Integer getStartYear() {
        return startYear;
    }

    public Integer getEndYear() {
        return endYear;
    }

    public Integer getFirstSelectedYear() {
        return firstSelectedYear;
    }
}
