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
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.core.Handle;

/**
 * A test data container for an activity. Comes with a builder that will build an activity with reasonable activity/question definitions
 * suitable for testing purposes.
 */
public class TestFormActivity {

    private FormActivityDef def;
    private ActivityVersionDto versionDto;
    private AgreementQuestionDef agreementQuestion;
    private BoolQuestionDef boolQuestion;
    private CompositeQuestionDef compositeQuestion;
    private DateQuestionDef dateFullQuestion;
    private NumericQuestionDef numericIntQuestion;
    private PicklistQuestionDef picklistSingleListQuestion;
    private PicklistQuestionDef picklistMultiListQuestion;
    private TextQuestionDef textQuestion;

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

    public CompositeQuestionDef getCompositeQuestion() {
        return compositeQuestion;
    }

    public DateQuestionDef getDateFullQuestion() {
        return dateFullQuestion;
    }

    public NumericQuestionDef getNumericIntQuestion() {
        return numericIntQuestion;
    }

    public PicklistQuestionDef getPicklistSingleListQuestion() {
        return picklistSingleListQuestion;
    }

    public PicklistQuestionDef getPicklistMultiListQuestion() {
        return picklistMultiListQuestion;
    }

    public TextQuestionDef getTextQuestion() {
        return textQuestion;
    }

    public static class Builder {
        private boolean withAgreementQuestion = false;
        private boolean withBoolQuestion = false;
        private boolean withDateFullQuestion = false;
        private boolean withNumericIntQuestion = false;
        private boolean withTextQuestion = false;
        private List<PicklistOptionDef> picklistSingleListOptions = null;
        private List<PicklistOptionDef> picklistMultiListOptions = null;
        private List<QuestionDef> compositeChildQuestions = null;

        private Builder() {
            // Use static factories.
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

        public Builder withNumericIntQuestion(boolean include) {
            this.withNumericIntQuestion = include;
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

        public Builder withTextQuestion(boolean include) {
            this.withTextQuestion = include;
            return this;
        }

        public TestFormActivity build(Handle handle, long userId, String studyGuid) {
            var result = new TestFormActivity();
            var builder = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                    .addName(new Translation("en", "test activity"));

            if (withAgreementQuestion) {
                var question = new AgreementQuestionDef(
                        "AGREE" + Instant.now().toEpochMilli(),
                        false,
                        Template.text("agreement prompt"),
                        null,
                        null,
                        List.of(new RequiredRuleDef(Template.text("agreement required"))),
                        false);
                result.agreementQuestion = question;
                builder.addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(question))));
            }

            if (withBoolQuestion) {
                var question = BoolQuestionDef
                        .builder("BOOL" + Instant.now().toEpochMilli(), Template.text("bool prompt"),
                                Template.text("bool yes"), Template.text("bool no"))
                        .build();
                result.boolQuestion = question;
                builder.addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(question))));
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

            if (withNumericIntQuestion) {
                var question = NumericQuestionDef
                        .builder(NumericType.INTEGER, "NUM" + Instant.now().toEpochMilli(), Template.text("numeric prompt"))
                        .build();
                result.numericIntQuestion = question;
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

            result.def = builder.build();
            result.versionDto = handle.attach(ActivityDao.class)
                    .insertActivity(result.def, RevisionMetadata.now(userId, "test activity"));

            return result;
        }
    }
}
