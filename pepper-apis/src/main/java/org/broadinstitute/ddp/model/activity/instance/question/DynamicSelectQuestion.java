package org.broadinstitute.ddp.model.activity.instance.question;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.instance.answer.DynamicSelectAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

public class DynamicSelectQuestion extends Question<DynamicSelectAnswer> {

    @NotNull
    @SerializedName("sourceStableIds")
    private List<String> sourceStableIds;

    public DynamicSelectQuestion(String stableId, long promptTemplateId, boolean isRestricted, boolean isDeprecated,
                                 Boolean readonly, Long tooltipTemplateId, Long additionalInfoHeaderTemplateId,
                                 Long additionalInfoFooterTemplateId, List<DynamicSelectAnswer> answers,
                                 List<Rule<DynamicSelectAnswer>> validations, List<String> sourceStableIds) {
        super(QuestionType.DYNAMIC_SELECT,
                stableId,
                promptTemplateId,
                isRestricted,
                isDeprecated,
                readonly,
                tooltipTemplateId,
                additionalInfoHeaderTemplateId,
                additionalInfoFooterTemplateId,
                answers,
                validations);
        this.sourceStableIds = sourceStableIds;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        super.registerTemplateIds(registry);
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        super.applyRenderedTemplates(rendered, style);
    }

    public List<String> getSourceQuestions() {
        return sourceStableIds;
    }

    public static class Serializer implements JsonSerializer<DynamicSelectQuestion> {
        private static Gson gson = new GsonBuilder().create();

        @Override
        public JsonElement serialize(DynamicSelectQuestion src, Type typeOfSrc, JsonSerializationContext context) {
            return gson.toJsonTree(src);
        }
    }

}
