package org.broadinstitute.ddp.export.json.structured;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class DateQuestionRecord extends QuestionRecord {

    @SerializedName("date")
    private String date;
    @SerializedName("dateFields")
    private DateValue dateFields;
    @SerializedName("dateHistory")
    private List<Map<String, Object>> history;

    public DateQuestionRecord(String stableId, DateValue answer, List<Answer> history) {
        super(QuestionType.DATE, stableId);
        this.dateFields = answer;
        if (answer != null) {
            String str = answer.asLocalDate().map(LocalDate::toString).orElse(null);
            str = (str != null ? str : answer.asYearMonth().map(YearMonth::toString).orElse(null));
            str = (str != null ? str : answer.getYear() == null ? null : String.format("%04d", answer.getYear()));
            this.date = str;
        }
        this.history = history.stream()
                .map(ans -> {
                    Map<String, Object> hist = new HashMap<>();
                    hist.put("dateFields", ((DateAnswer) ans).getValue());
                    hist.put("updatedAt", Instant.ofEpochMilli(ans.getUpdatedAt()).toString());
                    return hist;
                })
                .collect(Collectors.toList());
    }
}
