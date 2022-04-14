package org.broadinstitute.ddp.model.activity.instance.question;

import static org.broadinstitute.ddp.util.CollectionMiscUtil.consumeNonNulls;
import static org.broadinstitute.ddp.util.TemplateRenderUtil.applyRenderedTemplate;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.MiscUtil;

public class TextQuestion extends Question<TextAnswer> {

    @NotNull
    @SerializedName("inputType")
    private TextInputType inputType;

    @NotNull
    @SerializedName("suggestionType")
    private SuggestionType suggestionType = SuggestionType.NONE;

    @SerializedName("placeholderText")
    private String placeholderText;

    @SerializedName("confirmPlaceholderText")
    private String confirmPlaceholderText;

    @SerializedName("suggestions")
    private List<String> suggestions;

    @SerializedName("confirmEntry")
    private boolean confirmEntry;

    @SerializedName("confirmPrompt")
    private String confirmPrompt;

    @SerializedName("mismatchMessage")
    private String mismatchMessage;

    private transient Long placeholderTemplateId;
    private transient Long confirmPlaceholderTemplateId;
    private transient Long confirmPromptTemplateId;
    private transient Long mismatchMessageTemplateId;

    public TextQuestion(String stableId, long promptTemplateId, Long placeholderTemplateId, Long confirmPlaceholderTemplateId,
                        boolean isRestricted, boolean isDeprecated, Boolean readonly, Long tooltipTemplateId,
                        Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId, List<TextAnswer> answers,
                        List<Rule<TextAnswer>> validations, TextInputType inputType, SuggestionType suggestionType,
                        List<String> suggestions, boolean confirmEntry,
                        Long confirmPromptTemplateId, Long mismatchMessageTemplateId) {
        super(QuestionType.TEXT,
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

        this.inputType = MiscUtil.checkNonNull(inputType, "inputType");
        this.placeholderTemplateId = placeholderTemplateId;
        this.confirmPlaceholderTemplateId = confirmPlaceholderTemplateId;
        this.confirmEntry = confirmEntry;
        this.confirmPromptTemplateId = confirmPromptTemplateId;
        this.mismatchMessageTemplateId = mismatchMessageTemplateId;
        this.suggestionType = Optional.ofNullable(suggestionType).orElse(SuggestionType.NONE);

        if (CollectionUtils.isNotEmpty(suggestions)) {
            this.suggestions = suggestions;
        }
    }

    public TextQuestion(String stableId, long promptTemplateId, Long placeholderTemplateId,
                        List<TextAnswer> answers, List<Rule<TextAnswer>> validations, TextInputType inputType) {
        this(stableId,
                promptTemplateId,
                placeholderTemplateId,
                null,
                false,
                false,
                false,
                null,
                null,
                null,
                answers,
                validations,
                inputType,
                null,
                null,
                false,
                null,
                null);
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        super.registerTemplateIds(registry);
        // only generate the placeholder template id if it's present
        consumeNonNulls(registry,
                placeholderTemplateId,
                confirmPlaceholderTemplateId,
                confirmPromptTemplateId,
                mismatchMessageTemplateId);
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        super.applyRenderedTemplates(rendered, style);
        placeholderText = applyRenderedTemplate("placeholder",
                placeholderTemplateId, placeholderText, rendered, style);
        confirmPlaceholderText = applyRenderedTemplate("confirm placeholder",
                confirmPlaceholderTemplateId, confirmPlaceholderText, rendered, style);
        confirmPrompt = applyRenderedTemplate("confirm prompt",
                confirmPromptTemplateId, confirmPrompt, rendered, style);
        mismatchMessage = applyRenderedTemplate("mismatch message",
                mismatchMessageTemplateId, mismatchMessage, rendered, style);
    }

    public TextInputType getInputType() {
        return inputType;
    }

    public SuggestionType getSuggestionType() {
        return suggestionType;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public static class Serializer implements JsonSerializer<TextQuestion> {
        private static Gson gson = new GsonBuilder().create();

        @Override
        public JsonElement serialize(TextQuestion src, Type typeOfSrc, JsonSerializationContext context) {
            return gson.toJsonTree(src);
        }
    }

}
