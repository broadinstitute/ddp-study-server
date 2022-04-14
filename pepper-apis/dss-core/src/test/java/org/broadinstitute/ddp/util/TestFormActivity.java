package org.broadinstitute.ddp.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.ActivityInstanceSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.core.Handle;

/**
 * A test data container for an activity. Comes with a builder that will build an activity with reasonable activity/question definitions
 * suitable for testing purposes.
 */
public class TestFormActivity {

    public static final long DEFAULT_MAX_FILE_SIZE_FOR_TEST = 1000;

    private FormActivityDef def;
    private ActivityVersionDto versionDto;
    private AgreementQuestionDef agreementQuestion;
    private BoolQuestionDef boolQuestion;
    private QuestionBlockDef boolQuestionBlock;
    private CompositeQuestionDef compositeQuestion;
    private DateQuestionDef dateFullQuestion;
    private FileQuestionDef fileQuestion;
    private NumericQuestionDef numericIntQuestion;
    private DecimalQuestionDef decimalQuestion;
    private PicklistQuestionDef picklistSingleListQuestion;
    private PicklistQuestionDef picklistMultiListQuestion;
    private MatrixQuestionDef matrixListQuestion;
    private TextQuestionDef textQuestion;
    private ActivityInstanceSelectQuestionDef activityInstanceSelectQuestion;

    public static Builder builder() {
        return new Builder();
    }

    public FormActivityDef getDef() {
        return def;
    }

    public ActivityVersionDto getVersionDto() {
        return versionDto;
    }

    public AgreementQuestionDef getAgreementQuestion() {
        return agreementQuestion;
    }

    public BoolQuestionDef getBoolQuestion() {
        return boolQuestion;
    }

    public QuestionBlockDef getBoolQuestionBlock() {
        return boolQuestionBlock;
    }

    public CompositeQuestionDef getCompositeQuestion() {
        return compositeQuestion;
    }

    public DateQuestionDef getDateFullQuestion() {
        return dateFullQuestion;
    }

    public FileQuestionDef getFileQuestion() {
        return fileQuestion;
    }

    public NumericQuestionDef getNumericIntQuestion() {
        return numericIntQuestion;
    }

    public DecimalQuestionDef getDecimalQuestion() {
        return decimalQuestion;
    }

    public PicklistQuestionDef getPicklistSingleListQuestion() {
        return picklistSingleListQuestion;
    }

    public PicklistQuestionDef getPicklistMultiListQuestion() {
        return picklistMultiListQuestion;
    }

    public MatrixQuestionDef getMatrixListQuestion() {
        return matrixListQuestion;
    }

    public TextQuestionDef getTextQuestion() {
        return textQuestion;
    }

    public ActivityInstanceSelectQuestionDef getActivityInstanceSelectQuestion() {
        return activityInstanceSelectQuestion;
    }

    public static class Builder {
        private Integer maxInstancesPerUser = null;
        private boolean hideExistingInstances = false;
        private boolean withAgreementQuestion = false;
        private boolean withBoolQuestion = false;
        private boolean withDateFullQuestion = false;
        private boolean withFileQuestion = false;
        private boolean withNumericIntQuestion = false;
        private boolean withDecimalQuestion = false;
        private boolean withTextQuestion = false;
        private boolean withActivityInstanceSelectQuestion = false;
        private List<PicklistOptionDef> picklistSingleListOptions = null;
        private List<PicklistOptionDef> picklistMultiListOptions = null;
        private MatrixSelectMode matrixSelectMode = null;
        private List<MatrixGroupDef> matrixGroups = null;
        private List<MatrixOptionDef> matrixOptions = null;
        private List<MatrixRowDef> matrixRows = null;
        private List<QuestionDef> compositeChildQuestions = null;

        private Builder() {
            // Use static factories.
        }

        public Builder setMaxInstancesPerUser(Integer maxInstancesPerUser) {
            this.maxInstancesPerUser = maxInstancesPerUser;
            return this;
        }

        public Builder setHideExistingInstances(boolean hideExistingInstances) {
            this.hideExistingInstances = hideExistingInstances;
            return this;
        }

        public Builder withAgreementQuestion(boolean include) {
            this.withAgreementQuestion = include;
            return this;
        }

        public Builder withBoolQuestion(boolean include) {
            this.withBoolQuestion = include;
            return this;
        }

        public Builder withCompositeQuestion(boolean include, QuestionDef... children) {
            if (include) {
                compositeChildQuestions = List.of(children);
            } else {
                compositeChildQuestions = null;
            }
            return this;
        }

        public Builder withDateFullQuestion(boolean include) {
            this.withDateFullQuestion = include;
            return this;
        }

        public Builder withFileQuestion(boolean include) {
            this.withFileQuestion = include;
            return this;
        }

        public Builder withNumericIntQuestion(boolean include) {
            this.withNumericIntQuestion = include;
            return this;
        }

        public Builder withDecimalQuestion(boolean include) {
            this.withDecimalQuestion = include;
            return this;
        }

        public Builder withPicklistSingleList(boolean include, PicklistOptionDef... options) {
            if (include) {
                picklistSingleListOptions = List.of(options);
            } else {
                picklistSingleListOptions = null;
            }
            return this;
        }

        public Builder withPicklistMultiList(boolean include, PicklistOptionDef... options) {
            if (include) {
                picklistMultiListOptions = List.of(options);
            } else {
                picklistMultiListOptions = null;
            }
            return this;
        }

        public Builder withMatrixOptionsRowsList(boolean include, MatrixSelectMode mode,
                                                 List<MatrixOptionDef> options, List<MatrixRowDef> rows) {
            if (include) {
                matrixSelectMode = mode;
                matrixOptions = List.copyOf(options);
                matrixRows = List.copyOf(rows);
            } else {
                matrixSelectMode = null;
                matrixOptions = null;
                matrixRows = null;
            }
            return this;
        }

        public Builder withMatrixOptionsRowsGroupsList(boolean include, MatrixSelectMode mode, List<MatrixOptionDef> options,
                                                       List<MatrixRowDef> rows, List<MatrixGroupDef> groups) {
            if (include) {
                matrixSelectMode = mode;
                matrixGroups = List.copyOf(groups);
                matrixOptions = List.copyOf(options);
                matrixRows = List.copyOf(rows);
            } else {
                matrixGroups = null;
                matrixOptions = null;
                matrixRows = null;
            }
            return this;
        }

        public Builder withTextQuestion(boolean include) {
            this.withTextQuestion = include;
            return this;
        }

        public Builder withActivityInstanceSelectQuestion(boolean include) {
            this.withActivityInstanceSelectQuestion = include;
            return this;
        }

        public TestFormActivity build(Handle handle, long userId, String studyGuid) {
            var result = new TestFormActivity();
            var builder = FormActivityDef
                    .generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                    .addName(new Translation("en", "test activity"))
                    .setMaxInstancesPerUser(maxInstancesPerUser)
                    .setHideInstances(hideExistingInstances);

            if (withAgreementQuestion) {
                var question = new AgreementQuestionDef(
                        "AGREE" + Instant.now().toEpochMilli(),
                        false,
                        Template.text("agreement prompt"),
                        null,
                        null,
                        null,
                        List.of(new RequiredRuleDef(Template.text("agreement required"))),
                        false,
                        false);
                result.agreementQuestion = question;
                builder.addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(question))));
            }

            if (withBoolQuestion) {
                result.boolQuestion = BoolQuestionDef
                        .builder("BOOL" + Instant.now().toEpochMilli(), Template.text("bool prompt"),
                                Template.text("bool yes"), Template.text("bool no"))
                        .build();
                result.boolQuestionBlock = new QuestionBlockDef(result.boolQuestion);
                builder.addSection(new FormSectionDef(null, List.of(result.boolQuestionBlock)));
            }

            if (compositeChildQuestions != null) {
                var question = CompositeQuestionDef.builder()
                        .setStableId("COMP" + Instant.now().toEpochMilli())
                        .setPrompt(Template.text("composite prompt"))
                        .setAllowMultiple(true)
                        .setAddButtonTemplate(Template.text("composite add button"))
                        .setAdditionalItemTemplate(Template.text("composite additional item"))
                        .addChildrenQuestions(compositeChildQuestions)
                        .build();
                result.compositeQuestion = question;
                builder.addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(question))));
            }

            var dateBlocks = new ArrayList<FormBlockDef>();
            if (withDateFullQuestion) {
                var question = DateQuestionDef
                        .builder(DateRenderMode.TEXT, "DATE" + Instant.now().toEpochMilli(), Template.text("date prompt"))
                        .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR)
                        .build();
                result.dateFullQuestion = question;
                dateBlocks.add(new QuestionBlockDef(question));
            }
            if (!dateBlocks.isEmpty()) {
                builder.addSection(new FormSectionDef(null, dateBlocks));
            }

            if (withFileQuestion) {
                var question = FileQuestionDef
                        .builder("FILE" + Instant.now().toEpochMilli(), Template.text("file prompt"))
                        .setMaxFileSize(DEFAULT_MAX_FILE_SIZE_FOR_TEST)
                        .build();
                result.fileQuestion = question;
                builder.addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(question))));
            }

            if (withNumericIntQuestion) {
                var question = NumericQuestionDef
                        .builder("NUM" + Instant.now().toEpochMilli(), Template.text("numeric prompt"))
                        .build();
                result.numericIntQuestion = question;
                builder.addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(question))));
            }

            if (withDecimalQuestion) {
                var question = DecimalQuestionDef
                        .builder("DEC" + Instant.now().toEpochMilli(), Template.text("decimal prompt"))
                        .build();
                result.decimalQuestion = question;
                builder.addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(question))));
            }

            var picklistBlocks = new ArrayList<FormBlockDef>();
            if (picklistSingleListOptions != null) {
                var question = PicklistQuestionDef
                        .buildSingleSelect(PicklistRenderMode.LIST, "PL" + Instant.now().toEpochMilli(), Template.text("pl single prompt"))
                        .addOptions(List.copyOf(picklistSingleListOptions))
                        .build();
                result.picklistSingleListQuestion = question;
                picklistBlocks.add(new QuestionBlockDef(question));
            }
            if (picklistMultiListOptions != null) {
                var question = PicklistQuestionDef
                        .buildMultiSelect(PicklistRenderMode.LIST, "PL" + Instant.now().toEpochMilli(), Template.text("pl multi prompt"))
                        .addOptions(List.copyOf(picklistMultiListOptions))
                        .build();
                result.picklistMultiListQuestion = question;
                picklistBlocks.add(new QuestionBlockDef(question));
            }
            if (!picklistBlocks.isEmpty()) {
                builder.addSection(new FormSectionDef(null, picklistBlocks));
            }

            var matrixBlocks = new ArrayList<FormBlockDef>();
            if (matrixOptions != null && matrixSelectMode != null) {
                var question = MatrixQuestionDef
                        .builder(matrixSelectMode, "MATRIX" + Instant.now().toEpochMilli(), Template.text("matrix multi prompt"))
                        .addOptions(List.copyOf(matrixOptions))
                        .addRows(List.copyOf(matrixRows))
                        .addGroups(List.copyOf(matrixGroups))
                        .build();
                result.matrixListQuestion = question;
                matrixBlocks.add(new QuestionBlockDef(question));
            }
            if (!matrixBlocks.isEmpty()) {
                builder.addSection(new FormSectionDef(null, matrixBlocks));
            }

            var textBlocks = new ArrayList<FormBlockDef>();
            if (withTextQuestion) {
                var question = TextQuestionDef
                        .builder(TextInputType.TEXT, "TEXT" + Instant.now().toEpochMilli(), Template.text("text prompt"))
                        .build();
                result.textQuestion = question;
                textBlocks.add(new QuestionBlockDef(question));
            }
            if (!textBlocks.isEmpty()) {
                builder.addSection(new FormSectionDef(null, textBlocks));
            }

            var aiBlocks = new ArrayList<FormBlockDef>();
            if (withActivityInstanceSelectQuestion) {
                var question = ActivityInstanceSelectQuestionDef
                        .builder("AI_SELECT" + Instant.now().toEpochMilli(), Template.text("text prompt"))
                        .build();
                result.activityInstanceSelectQuestion = question;
                aiBlocks.add(new QuestionBlockDef(question));
            }
            if (!aiBlocks.isEmpty()) {
                builder.addSection(new FormSectionDef(null, aiBlocks));
            }

            result.def = builder.build();
            result.versionDto = handle.attach(ActivityDao.class)
                    .insertActivity(result.def, RevisionMetadata.now(userId, "test activity"));

            return result;
        }
    }
}
