package org.broadinstitute.ddp.transformers;

import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.util.GsonUtil;

/**
 * Customize GSON Json generation for AnswerRow. Adapter will make sure that {@code AnswerRow} gets
 * serialized as an JSON array that includes the its values and not as an JSON Object
 */
public class AnswerRowAdapter extends TypeAdapter<AnswerRow> {
    private Gson gson;

    public AnswerRowAdapter() {
        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void write(JsonWriter out, AnswerRow answerRow) throws IOException {
        out.beginArray();
        for (Answer answer : answerRow.getValues()) {
            if (answer == null) {
                out.nullValue();
            } else {
                gson.toJson(answer, answer.getClass(), out);
            }
        }
        out.endArray();

    }

    @Override
    public AnswerRow read(JsonReader in) throws IOException {
        throw new UnsupportedOperationException("We can write but not read CompositeAnswerForValidation!!!");
    }
}
