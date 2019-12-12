package org.broadinstitute.ddp.export.json.structured;

import java.time.LocalDate;
import java.time.YearMonth;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class DateQuestionRecord extends QuestionRecord {

    @SerializedName("date")
    private String date;
    @SerializedName("dateFields")
    private DateValue dateFields;

    public DateQuestionRecord(String stableId, DateValue answer) {
        super(QuestionType.DATE, stableId);
        this.dateFields = answer;
        if (answer != null) {
            String str = answer.asLocalDate().map(LocalDate::toString).orElse(null);
            str = (str != null ? str : answer.asYearMonth().map(YearMonth::toString).orElse(null));
            str = (str != null ? str : answer.getYear() == null ? null : String.format("%04d", answer.getYear()));
            this.date = str;
        }
    }
}
