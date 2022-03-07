package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class MatrixQuestionDef extends QuestionDef {

    @NotNull
    @SerializedName("selectMode")
    private final MatrixSelectMode selectMode;

    @SerializedName("renderModal")
    private boolean renderModal;

    @Valid
    @SerializedName("modalTemplate")
    private Template modalTemplate;

    @Valid
    @SerializedName("modalTitleTemplate")
    private Template modalTitleTemplate;

    @NotNull
    @SerializedName("groups")
    private final List<@Valid @NotNull MatrixGroupDef> groups = new ArrayList<>();

    @NotNull
    @SerializedName("options")
    private final List<@Valid @NotNull MatrixOptionDef> matrixOptions = new ArrayList<>();

    @NotNull
    @SerializedName("rows")
    private final List<@Valid @NotNull MatrixRowDef> matrixRows = new ArrayList<>();

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(MatrixSelectMode selectMode, String stableId, Template prompt) {
        return new Builder()
                .setSelectMode(selectMode)
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    /**
     * Construct a picklist question definition, with one or more picklist options.
     */
    public MatrixQuestionDef(String stableId, boolean isRestricted, boolean renderModal,
                             Template promptTemplate, Template additionalInfoHeaderTemplate,
                             Template additionalInfoFooterTemplate, Template modalTemplate, Template modalTitleTemplate,
                             List<RuleDef> validations, MatrixSelectMode selectMode, List<MatrixGroupDef> groups,
                             List<MatrixOptionDef> options, List<MatrixRowDef> questions,
                             boolean hideNumber, boolean writeOnce) {
        super(QuestionType.MATRIX,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber,
                writeOnce);
        this.selectMode = MiscUtil.checkNonNull(selectMode, "selectMode");
        this.modalTemplate = modalTemplate;
        this.modalTitleTemplate = modalTitleTemplate;
        this.renderModal = renderModal;

        groups = (groups == null) ? new ArrayList<>() : groups;
        options = (options == null) ? new ArrayList<>() : options;
        questions = (questions == null) ? new ArrayList<>() : questions;

        if (options.isEmpty() || questions.isEmpty()) {
            throw new IllegalArgumentException("need to have at least one option and one question");
        }

        this.groups.addAll(groups);
        this.matrixOptions.addAll(options);
        this.matrixRows.addAll(questions);
    }

    public MatrixSelectMode getSelectMode() {
        return selectMode;
    }

    public boolean isRenderModal() {
        return renderModal;
    }

    public Template getModalTemplate() {
        return modalTemplate;
    }

    public Template getModalTitleTemplate() {
        return modalTitleTemplate;
    }

    public List<MatrixGroupDef> getGroups() {
        return groups;
    }

    public List<MatrixOptionDef> getOptions() {
        return matrixOptions;
    }

    public List<MatrixRowDef> getRows() {
        return matrixRows;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private MatrixSelectMode selectMode;
        private Template modalTemplate;
        private Template modalTitleTemplate;
        private boolean renderModal;

        private final List<MatrixGroupDef> groups = new ArrayList<>();
        private final List<MatrixOptionDef> options = new ArrayList<>();
        private final List<MatrixRowDef> rows = new ArrayList<>();

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setSelectMode(MatrixSelectMode selectMode) {
            this.selectMode = selectMode;
            return this;
        }

        public Builder setModalTemplate(Template modalTemplate) {
            this.modalTemplate = modalTemplate;
            return this;
        }

        public Builder setModalTitleTemplate(Template modalTitleTemplate) {
            this.modalTitleTemplate = modalTitleTemplate;
            return this;
        }

        public Builder setRenderModal(boolean renderModal) {
            this.renderModal = renderModal;
            return this;
        }

        public Builder addGroup(MatrixGroupDef group) {
            this.groups.add(group);
            return this;
        }

        public Builder addGroups(Collection<MatrixGroupDef> groups) {
            this.groups.addAll(groups);
            return this;
        }

        public Builder addOption(MatrixOptionDef option) {
            this.options.add(option);
            return this;
        }

        public Builder addOptions(Collection<MatrixOptionDef> options) {
            this.options.addAll(options);
            return this;
        }

        public Builder addRow(MatrixRowDef question) {
            this.rows.add(question);
            return this;
        }

        public Builder addRows(Collection<MatrixRowDef> matrixRawDefs) {
            this.rows.addAll(matrixRawDefs);
            return this;
        }

        public MatrixQuestionDef build() {
            MatrixQuestionDef question = new MatrixQuestionDef(stableId,
                    isRestricted,
                    renderModal,
                    prompt,
                    getAdditionalInfoHeader(),
                    getAdditionalInfoFooter(),
                    modalTemplate,
                    modalTitleTemplate,
                    validations,
                    selectMode,
                    groups,
                    options,
                    rows,
                    hideNumber,
                    writeOnce);
            configure(question);
            return question;
        }
    }
}
