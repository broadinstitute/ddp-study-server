package org.broadinstitute.ddp.model.statistics;

import com.google.gson.annotations.SerializedName;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StatisticsConfiguration {
    private final transient long studyId;
    @SerializedName("type")
    private final StatisticsType type;
    @SerializedName("questionStableId")
    private final String questionStableId;
    @SerializedName("answerValue")
    private final String answerValue;

    @JdbiConstructor
    public StatisticsConfiguration(@ColumnName("umbrella_study_id") long studyId,
                                   @ColumnName("statistics_type_code") StatisticsType statisticsType,
                                   @ColumnName("question_stable_id") String questionStableId,
                                   @ColumnName("answer_value") String answerValue) {
        this.studyId = studyId;
        this.type = statisticsType;
        this.questionStableId = questionStableId;
        this.answerValue = answerValue;
    }

    public long getStudyId() {
        return studyId;
    }

    public StatisticsType getType() {
        return type;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public String getAnswerValue() {
        return answerValue;
    }
}
