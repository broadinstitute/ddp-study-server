package org.broadinstitute.ddp.model.activity.definition;

import static java.util.stream.Collectors.toMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.transformers.LocalDateTimeAdapter;
import org.broadinstitute.ddp.util.MiscUtil;

public class FormActivityDef extends ActivityDef {
    @NotNull
    @SerializedName("formType")
    protected FormType formType;

    @SerializedName("listStyleHint")
    protected ListStyleHint listStyleHint;

    @Valid
    @SerializedName("introduction")
    protected FormSectionDef introduction;

    @Valid
    @SerializedName("closing")
    protected FormSectionDef closing;

    @NotNull
    @SerializedName("sections")
    protected List<@Valid @NotNull FormSectionDef> sections;

    @Valid
    @SerializedName("lastUpdatedTextTemplate")
    protected Template lastUpdatedTextTemplate;

    @Valid
    @JsonAdapter(LocalDateTimeAdapter.class)
    @SerializedName("lastUpdated")
    protected LocalDateTime lastUpdated;

    @SerializedName("snapshotSubstitutionsOnSubmit")
    protected boolean snapshotSubstitutionsOnSubmit;

    @SerializedName("snapshotAddressOnSubmit")
    protected boolean snapshotAddressOnSubmit;

    private transient Set<FormBlockDef> cachedToggleableBlocks;
    private transient Map<String, QuestionDef> stableIdToQuestion;

    public static FormBuilder formBuilder() {
        return new FormBuilder();
    }

    public static FormBuilder formBuilder(FormType formType, String activityCode, String versionTag, String studyGuid) {
        return new FormBuilder()
                .setFormType(formType)
                .setActivityCode(activityCode)
                .setVersionTag(versionTag)
                .setStudyGuid(studyGuid);
    }

    public static FormBuilder generalFormBuilder(String activityCode, String versionTag, String studyGuid) {
        return new FormBuilder()
                .setFormType(FormType.GENERAL)
                .setActivityCode(activityCode)
                .setVersionTag(versionTag)
                .setStudyGuid(studyGuid);
    }

    public FormActivityDef(
            FormType formType,
            String parentActivityCode,
            String activityCode,
            String versionTag,
            String studyGuid,
            Integer maxInstancesPerUser,
            int displayOrder,
            boolean writeOnce,
            List<Translation> translatedNames,
            List<Translation> translatedTitles,
            List<Translation> translatedSubtitles,
            List<Translation> translatedDescriptions,
            List<SummaryTranslation> translatedSummaries,
            Template readonlyHintTemplate,
            FormSectionDef intro,
            List<FormSectionDef> sections,
            ListStyleHint listStyleHint,
            Template lastUpdatedTextTemplate,
            LocalDateTime lastUpdated,
            boolean isFollowup,
            boolean hideExistingInstancesOnCreation
    ) {
        super(
                ActivityType.FORMS,
                parentActivityCode,
                activityCode,
                versionTag,
                studyGuid,
                maxInstancesPerUser,
                displayOrder,
                writeOnce,
                translatedNames,
                translatedTitles,
                translatedSubtitles,
                translatedDescriptions,
                translatedSummaries,
                readonlyHintTemplate,
                isFollowup,
                hideExistingInstancesOnCreation
        );
        this.formType = MiscUtil.checkNonNull(formType, "formType");
        this.sections = MiscUtil.checkNonNull(sections, "sections");
        this.introduction = intro;
        this.listStyleHint = listStyleHint;
        this.lastUpdatedTextTemplate = lastUpdatedTextTemplate;
        this.lastUpdated = lastUpdated;
    }

    public FormType getFormType() {
        return formType;
    }

    public ListStyleHint getListStyleHint() {
        return listStyleHint;
    }

    public FormSectionDef getIntroduction() {
        return introduction;
    }

    public FormSectionDef getClosing() {
        return closing;
    }

    public List<FormSectionDef> getSections() {
        return sections;
    }

    public Template getLastUpdatedTextTemplate() {
        return lastUpdatedTextTemplate;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public boolean shouldSnapshotSubstitutionsOnSubmit() {
        return snapshotSubstitutionsOnSubmit;
    }

    public boolean shouldSnapshotAddressOnSubmit() {
        return snapshotAddressOnSubmit;
    }

    public List<FormSectionDef> getAllSections() {
        List<FormSectionDef> allSections = new ArrayList<>();
        if (introduction != null) {
            allSections.add(introduction);
        }
        allSections.addAll(sections);
        if (closing != null) {
            allSections.add(closing);
        }
        return allSections;
    }

    public QuestionDef getQuestionByStableId(String stableId) {
        if (stableIdToQuestion == null) {
            stableIdToQuestion = buildStableIdToQuestionMap();
        }
        return stableIdToQuestion.get(stableId);
    }

    private Map<String, QuestionDef> buildStableIdToQuestionMap() {
        return getAllSections().stream()
                .flatMap(section -> section.getBlocks().stream())
                .flatMap(FormBlockDef::getQuestions)
                .collect(toMap(QuestionDef::getStableId, s -> s));
    }

    public Collection<FormBlockDef> getAllToggleableBlocks() {
        if (cachedToggleableBlocks == null) {
            Set<FormBlockDef> blocks = getFilteredBlocks(b -> b.getShownExpr() != null);
            blocks.addAll(getFilteredBlocks(b -> b.getEnabledExpr() != null));
            cachedToggleableBlocks = Set.copyOf(blocks);   // Make immutable.
        }
        return cachedToggleableBlocks;
    }

    public Set<FormBlockDef> getFilteredBlocks(Function<FormBlockDef, Boolean> check) {
        Set<FormBlockDef> blocks = new HashSet<>();
        for (var section : getAllSections()) {
            for (var block : section.getBlocks()) {
                if (check.apply(block)) {
                    blocks.add(block);
                }
                List<FormBlockDef> nested = null;
                if (block.getBlockType() == BlockType.CONDITIONAL) {
                    nested = ((ConditionalBlockDef) block).getNested();
                } else if (block.getBlockType() == BlockType.GROUP) {
                    nested = ((GroupBlockDef) block).getNested();
                } else if (block.getBlockType() == BlockType.TABULAR) {
                    nested = ((TabularBlockDef) block).getBlocks();
                }
                if (nested != null) {
                    nested.stream().filter(check::apply).forEach(blocks::add);
                }
            }
        }
        return blocks;
    }

    // Note: this builder is named a bit differently so we don't clash with builders in subclasses.
    public static class FormBuilder extends AbstractActivityBuilder<FormBuilder> {

        private FormType formType;

        private final List<FormSectionDef> sections = new ArrayList<>();
        private ListStyleHint listStyleHint = null;
        private FormSectionDef introduction = null;
        private FormSectionDef closing = null;
        private Template lastUpdatedTextTemplate = null;
        private LocalDateTime lastUpdated = null;
        private boolean snapshotSubstitutionsOnSubmit = false;
        private boolean snapshotAddressOnSubmit = false;

        protected FormBuilder() {
            // Use static factories.
        }

        @Override
        protected FormBuilder self() {
            return this;
        }

        public FormBuilder setFormType(FormType formType) {
            this.formType = formType;
            return this;
        }

        public FormBuilder setListStyleHint(ListStyleHint listStyleHint) {
            this.listStyleHint = listStyleHint;
            return this;
        }

        public FormBuilder setIntroduction(FormSectionDef introduction) {
            this.introduction = introduction;
            return this;
        }

        public FormBuilder setClosing(FormSectionDef closing) {
            this.closing = closing;
            return this;
        }

        public FormBuilder addSection(FormSectionDef section) {
            this.sections.add(section);
            return this;
        }

        public FormBuilder addSections(Collection<FormSectionDef> sections) {
            this.sections.addAll(sections);
            return this;
        }

        public FormBuilder clearSections() {
            this.sections.clear();
            return this;
        }

        public FormBuilder setLastUpdatedTextTemplate(Template template) {
            this.lastUpdatedTextTemplate = template;
            return this;
        }

        public FormBuilder setLastUpdated(LocalDateTime lastUpdatedDate) {
            this.lastUpdated = lastUpdatedDate;
            return this;
        }

        public FormBuilder setSnapshotSubstitutionsOnSubmit(boolean snapshotSubstitutionsOnSubmit) {
            this.snapshotSubstitutionsOnSubmit = snapshotSubstitutionsOnSubmit;
            return this;
        }

        public FormBuilder setSnapshotAddressOnSubmit(boolean snapshotAddressOnSubmit) {
            this.snapshotAddressOnSubmit = snapshotAddressOnSubmit;
            return this;
        }

        public FormActivityDef build() {
            FormActivityDef form = new FormActivityDef(
                    formType,
                    parentActivityCode,
                    activityCode,
                    versionTag,
                    studyGuid,
                    maxInstancesPerUser,
                    displayOrder,
                    writeOnce,
                    names,
                    titles,
                    subtitles,
                    descriptions,
                    summaries,
                    readonlyHintTemplate,
                    introduction,
                    sections,
                    listStyleHint,
                    lastUpdatedTextTemplate,
                    lastUpdated,
                    isFollowup,
                    hideExistingInstancesOnCreation
            );
            configure(form);
            form.listStyleHint = listStyleHint;
            form.introduction = introduction;
            form.closing = closing;
            form.snapshotSubstitutionsOnSubmit = snapshotSubstitutionsOnSubmit;
            form.snapshotAddressOnSubmit = snapshotAddressOnSubmit;
            return form;
        }
    }
}
