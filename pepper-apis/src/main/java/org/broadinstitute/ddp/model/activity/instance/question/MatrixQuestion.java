package org.broadinstitute.ddp.model.activity.instance.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.function.Consumer;

public final class MatrixQuestion extends Question<MatrixAnswer> {

    @NotNull
    @SerializedName("selectMode")
    private MatrixSelectMode selectMode;

    @SerializedName("groups")
    private List<MatrixGroup> groups;

    @NotEmpty
    @SerializedName("matrixOptions")
    private List<MatrixOption> matrixOptions;

    @NotEmpty
    @SerializedName("matrixQuestions")
    private List<MatrixRow> matrixQuestions;

    public MatrixQuestion(String stableId, long promptTemplateId, boolean isRestricted,
                          boolean isDeprecated, Boolean readonly, Long tooltipTemplateId, Long additionalInfoHeaderTemplateId,
                          Long additionalInfoFooterTemplateId, List<MatrixAnswer> answers, List<Rule<MatrixAnswer>> validations,
                          MatrixSelectMode selectMode, List<MatrixGroup> groups, List<MatrixOption> matrixOptions,
                          List<MatrixRow> matrixQuestions) {
        super(QuestionType.MATRIX, stableId, promptTemplateId, isRestricted, isDeprecated, readonly, tooltipTemplateId,
                additionalInfoHeaderTemplateId, additionalInfoFooterTemplateId, answers, validations);
        this.selectMode = selectMode;
        this.groups = groups;
        this.matrixOptions = matrixOptions;
        this.matrixQuestions = matrixQuestions;
    }
    
    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        super.registerTemplateIds(registry);

        for (MatrixGroup group : groups) {
            group.registerTemplateIds(registry);
        }

        for (MatrixOption option : matrixOptions) {
            option.registerTemplateIds(registry);
        }

        for (MatrixRow question : matrixQuestions) {
            question.registerTemplateIds(registry);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        super.applyRenderedTemplates(rendered, style);

        for (MatrixGroup group : groups) {
            group.applyRenderedTemplates(rendered, style);
        }

        for (MatrixOption option : matrixOptions) {
            option.applyRenderedTemplates(rendered, style);
        }

        for (MatrixRow question : matrixQuestions) {
            question.applyRenderedTemplates(rendered, style);
        }
    }
}
