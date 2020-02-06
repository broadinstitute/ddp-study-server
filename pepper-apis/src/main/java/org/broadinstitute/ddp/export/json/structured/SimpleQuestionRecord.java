package org.broadinstitute.ddp.export.json.structured;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class SimpleQuestionRecord extends QuestionRecord {

    @SerializedName("answer")
    private Object answer;
    @SerializedName("history")
    private List<Map<String, String>> history;

    public SimpleQuestionRecord(QuestionType questionType, String stableId, Object answer, List<Answer> history) {
        super(questionType, stableId);
        this.answer = answer;
        this.history = history.stream()
                .map(ans -> {
                    Map<String, String> hist = new HashMap<>();
                    hist.put("answer", ans.getValue().toString());
                    hist.put("updatedAt", Instant.ofEpochMilli(ans.getUpdatedAt()).toString());
                    return hist;
                })
                .collect(Collectors.toList());
    }
}
