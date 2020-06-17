package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class CompositeQuestionDef extends QuestionDef {

    @NotEmpty
    private final List<@Valid @NotNull QuestionDef> children;
    private final OrientationType childOrientation;
    private final boolean allowMultiple;
    private final boolean unwrapOnExport;
    @Valid
    private final Template additionalItemTemplate;
    @Valid
    private Template addButtonTemplate;

    public static Builder builder() {
        return new Builder();
    }

    public CompositeQuestionDef(String stableId, boolean isRestricted, Template promptTemplate,
                                Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                                List<RuleDef> validations, List<QuestionDef> children, OrientationType childOrientation,
                                boolean allowMultiple, boolean unwrapOnExport, Template addButtonTemplate,
                                Template additionalItemTemplate, boolean hideNumber) {
        super(QuestionType.COMPOSITE,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber);
        this.children = children;
        this.childOrientation = childOrientation;
        this.allowMultiple = allowMultiple;
        this.unwrapOnExport = unwrapOnExport;
        this.addButtonTemplate = addButtonTemplate;
        this.additionalItemTemplate = additionalItemTemplate;
        if (allowMultiple && unwrapOnExport) {
            throw new IllegalArgumentException("Having both allowMultiple and unwrapOnExport is currently not supported");
        }
    }

    public List<QuestionDef> getChildren() {
        return children;
    }

    public OrientationType getChildOrientation() {
        return childOrientation;
    }

    public boolean isAllowMultiple() {
        return allowMultiple;
    }

    public boolean isUnwrapOnExport() {
        return unwrapOnExport;
    }

    public Template getAddButtonTemplate() {
        return addButtonTemplate;
    }

    public Template getAdditionalItemTemplate() {
        return additionalItemTemplate;
    }

    public boolean shouldUnwrapChildQuestions() {
        return unwrapOnExport && !allowMultiple;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private boolean allowMultiple = false;
        private boolean unwrapOnExport = false;
        private Template addButtonTemplate;
        private List<QuestionDef> children;
        private OrientationType childOrientation;
        private Template additionalItemTemplate;

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setAllowMultiple(boolean allowMultiple) {
            this.allowMultiple = allowMultiple;
            return this;
        }

        public Builder setUnwrapOnExport(boolean unwrapOnExport) {
            this.unwrapOnExport = unwrapOnExport;
            return this;
        }

        public Builder setAddButtonTemplate(Template buttonTemplate) {
            this.addButtonTemplate = buttonTemplate;
            return this;
        }

        public Builder setAdditionalItemTemplate(Template additionalItemTemplate) {
            this.additionalItemTemplate = additionalItemTemplate;
            return this;
        }

        public Builder addChildrenQuestions(List<QuestionDef> childQuestions) {
            this.children = new ArrayList<>(childQuestions);
            return this;
        }

        public Builder addChildrenQuestions(QuestionDef... childQuestions) {
            return this.addChildrenQuestions(Arrays.asList(childQuestions));
        }

        public Builder setChildOrientation(OrientationType childOrientation) {
            this.childOrientation = childOrientation;
            return this;
        }

        public CompositeQuestionDef build() {
            CompositeQuestionDef question = new CompositeQuestionDef(stableId,
                                                                        isRestricted,
                                                                        prompt,
                                                                        getAdditionalInfoHeader(),
                                                                        getAdditionalInfoFooter(),
                                                                        validations,
                                                                        children,
                                                                        childOrientation,
                                                                        allowMultiple,
                                                                        unwrapOnExport,
                                                                        addButtonTemplate,
                                                                        additionalItemTemplate,
                                                                        hideNumber);
            configure(question);
            return question;
        }
    }
}
