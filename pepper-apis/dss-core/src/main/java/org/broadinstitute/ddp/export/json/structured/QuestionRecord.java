package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.GsonUtil;

import java.lang.reflect.Type;

public abstract class QuestionRecord {

    @SerializedName("questionType")
    private QuestionType questionType;
    @SerializedName("stableId")
    private String stableId;

    private transient Object questionAnswer;

    QuestionRecord(QuestionType questionType, String stableId) {
        this.questionType = questionType;
        this.stableId = stableId;
    }

    public static class Serializer implements JsonSerializer<QuestionRecord> {
        private static Gson gson = GsonUtil.standardGson();

        @Override
        public JsonElement serialize(QuestionRecord record, Type typeOfSrc, JsonSerializationContext context) {
            JsonElement recordElement = gson.toJsonTree(record);
            JsonObject recordObject = recordElement.getAsJsonObject();
            //check question_type and try to get answer by casting !!!
            Object questionAnswer = null;
            if (record instanceof SimpleQuestionRecord) {
                questionAnswer = ((SimpleQuestionRecord) record).getAnswer();
            } else if (record instanceof DateQuestionRecord) {
                questionAnswer = ((DateQuestionRecord) record).getDate();
            } else if (record instanceof PicklistQuestionRecord) {
                questionAnswer = ((PicklistQuestionRecord) record).getSelected();
            } else if (record instanceof CompositeQuestionRecord) {
                questionAnswer = ((CompositeQuestionRecord) record).getAnswer();
            } else if (record instanceof MatrixQuestionRecord) {
                questionAnswer = ((MatrixQuestionRecord) record).getSelected();
            }

            recordObject.add(record.stableId, gson.toJsonTree(questionAnswer));
            return gson.toJsonTree(recordObject);
        }
    }
}
