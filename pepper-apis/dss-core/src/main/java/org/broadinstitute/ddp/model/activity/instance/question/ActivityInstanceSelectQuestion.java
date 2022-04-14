package org.broadinstitute.ddp.model.activity.instance.question;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.instance.answer.ActivityInstanceSelectAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

public class ActivityInstanceSelectQuestion extends Question<ActivityInstanceSelectAnswer> {

    @NotNull
    @SerializedName("activityCodes")
    private final List<String> activityCodes;

    public ActivityInstanceSelectQuestion(String stableId, long promptTemplateId, boolean isRestricted, boolean isDeprecated,
                                          Boolean readonly, Long tooltipTemplateId, Long additionalInfoHeaderTemplateId,
                                          Long additionalInfoFooterTemplateId, List<ActivityInstanceSelectAnswer> answers,
                                          List<Rule<ActivityInstanceSelectAnswer>> validations, List<String> activityCodes) {
        super(QuestionType.ACTIVITY_INSTANCE_SELECT,
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
        this.activityCodes = activityCodes;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        super.registerTemplateIds(registry);
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        super.applyRenderedTemplates(rendered, style);
    }

    public List<String> getActivityCodes() {
        return activityCodes;
    }

    public static class Serializer implements JsonSerializer<ActivityInstanceSelectQuestion> {
        private static final Gson gson = new GsonBuilder().create();

        @Override
        public JsonElement serialize(ActivityInstanceSelectQuestion src, Type typeOfSrc, JsonSerializationContext context) {
            return gson.toJsonTree(src);
        }
    }

}
