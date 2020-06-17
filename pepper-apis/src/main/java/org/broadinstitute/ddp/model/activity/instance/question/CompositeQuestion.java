package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class CompositeQuestion extends Question<CompositeAnswer> {

    private boolean allowMultiple;
    private transient boolean unwrapOnExport;
    private String addButtonText;
    private transient Long addButtonTextTemplateId;
    private String additionalItemText;
    private transient Long additionalItemTextTemplateId;
    private List<Question> children = new ArrayList<>();
    private OrientationType childOrientation;

    public CompositeQuestion(String stableId, long promptTemplateId,
                            boolean isRestricted, boolean isDeprecated, Long tooltipTemplateId,
                            Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
                            List<Rule<CompositeAnswer>> validations, boolean allowMultiple, boolean unwrapOnExport,
                            Long addButtonTextTemplateId, Long additionalItemTextTemplateId,
                            List<Question> childQuestions, OrientationType childOrientation, List<CompositeAnswer> answers) {
        super(QuestionType.COMPOSITE,
                stableId,
                promptTemplateId,
                isRestricted,
                isDeprecated,
                tooltipTemplateId,
                additionalInfoHeaderTemplateId,
                additionalInfoFooterTemplateId,
                answers,
                validations);
        this.allowMultiple = allowMultiple;
        this.unwrapOnExport = unwrapOnExport;
        this.additionalItemTextTemplateId = additionalItemTextTemplateId;
        this.addButtonTextTemplateId = addButtonTextTemplateId;
        this.children.addAll(childQuestions);
        this.childOrientation = childOrientation;
        this.answers = answers;
    }

    public CompositeQuestion(String stableId, long promptTemplateId,
            List<Rule<CompositeAnswer>> validations, boolean allowMultiple, Long addButtonTextTemplateId,
            Long additionalItemTextTemplateId,
            List<Question> childQuestions, List<CompositeAnswer> answers) {
        this(stableId,
                promptTemplateId,
                false,
                false,
                null,
                null,
                null,
                validations,
                allowMultiple,
                false,
                addButtonTextTemplateId,
                additionalItemTextTemplateId,
                childQuestions,
                null,
                answers);
    }

    public boolean isAllowMultiple() {
        return allowMultiple;
    }

    public void setAllowMultiple(boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }

    public boolean isUnwrapOnExport() {
        return unwrapOnExport;
    }

    public void setUnwrapOnExport(boolean unwrapOnExport) {
        this.unwrapOnExport = unwrapOnExport;
    }

    public String getAddButtonText() {
        return addButtonText;
    }

    public void setAddButtonText(String addButtonText) {
        this.addButtonText = addButtonText;
    }

    public String getAdditionalItemText() {
        return additionalItemText;
    }

    public void setAdditionalItemText(String additionalItemText) {
        this.additionalItemText = additionalItemText;
    }

    public List<Question> getChildren() {
        return children;
    }

    public void setChildren(List<Question> children) {
        this.children = children;
    }

    public OrientationType getChildOrientation() {
        return childOrientation;
    }

    public Long getAddButtonTextTemplateId() {
        return addButtonTextTemplateId;
    }

    public Long getAdditionalItemTextTemplateId() {
        return additionalItemTextTemplateId;
    }

    public boolean shouldUnwrapChildQuestions() {
        return unwrapOnExport && !allowMultiple;
    }

    public List<Question> getUnwrappedChildQuestions() {
        // Realistically, there should be one answer with one row, but we use streams here to cover for the empty case.
        List<Answer> allChildAnswers = answers.stream()
                .flatMap(answer -> answer.getValue().stream())
                .flatMap(row -> row.getValues().stream())
                .collect(Collectors.toList());
        return children.stream()
                .peek(child -> child.setAnswers(allChildAnswers.stream()
                        .filter(childAnswer -> childAnswer != null && childAnswer.getQuestionStableId().equals(child.getStableId()))
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        super.registerTemplateIds(registry);
        if (addButtonTextTemplateId != null) {
            registry.accept(addButtonTextTemplateId);
        }
        if (additionalItemTextTemplateId != null) {
            registry.accept(additionalItemTextTemplateId);
        }
        children.forEach(child -> child.registerTemplateIds(registry));
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        super.applyRenderedTemplates(rendered, style);

        if (addButtonTextTemplateId != null) {
            addButtonText = rendered.get(addButtonTextTemplateId);
            if (addButtonText == null) {
                throw new NoSuchElementException("No rendered template found for addButtonText " + addButtonTextTemplateId);
            }
        }

        if (additionalItemTextTemplateId != null) {
            additionalItemText = rendered.get(additionalItemTextTemplateId);
            if (additionalItemText == null) {
                throw new NoSuchElementException("No rendered template found for prompt with additionalItemText "
                        + additionalItemTextTemplateId);
            }
        }

        if (style == ContentStyle.BASIC) {
            addButtonText = HtmlConverter.getPlainText(addButtonText);
            additionalItemText = HtmlConverter.getPlainText(additionalItemText);
        }

        children.forEach(child -> child.applyRenderedTemplates(rendered, style));
    }

    @Override
    public boolean passesDeferredValidations() {
        // First check for complete on parent
        if (!super.passesDeferredValidations()) {
            return false;
        }

        // If parent appears to be complete, check the children
        for (Question child : children) {
            // If any child is required, there should be an answer.
            boolean isChildRequired = child.isRequired();
            if (isChildRequired && answers.isEmpty()) {
                return false;
            }

            // If child question is required, then all the rows need to pass for that child,
            // and there should be at least one row that passes (aka the first row).
            for (CompositeAnswer answer : answers) {
                int numPassed = 0;
                for (AnswerRow row : answer.getValue()) {
                    // Realistically, there should only be one child answer per row.
                    List<Answer> values = row.getValues().stream()
                            .filter(ans -> ans != null && ans.getQuestionStableId().equals(child.getStableId()))
                            .collect(Collectors.toList());
                    if (child.passesDeferredValidations(values)) {
                        numPassed += 1;
                    } else {
                        return false;
                    }
                }
                if (isChildRequired && numPassed < 1) {
                    return false;
                }
            }
        }

        return true;
    }
}
