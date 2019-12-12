package org.broadinstitute.ddp.model.activity.definition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
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
            String activityCode,
            String versionTag,
            String studyGuid,
            Integer maxInstancesPerUser,
            int displayOrder,
            boolean writeOnce,
            List<Translation> translatedNames,
            List<Translation> translatedSubtitles,
            List<Translation> translatedDashboardNames,
            List<Translation> translatedDescriptions,
            List<SummaryTranslation> translatedSummaries,
            Template readonlyHintTemplate,
            FormSectionDef intro,
            List<FormSectionDef> sections,
            ListStyleHint listStyleHint,
            Template lastUpdatedTextTemplate,
            LocalDateTime lastUpdated,
            boolean isFollowup
    ) {
        super(
                ActivityType.FORMS,
                activityCode,
                versionTag,
                studyGuid,
                maxInstancesPerUser,
                displayOrder,
                writeOnce,
                translatedNames,
                translatedSubtitles,
                translatedDashboardNames,
                translatedDescriptions,
                translatedSummaries,
                readonlyHintTemplate,
                isFollowup
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

    // Note: this builder is named a bit differently so we don't clash with builders in subclasses.
    public static class FormBuilder extends AbstractActivityBuilder<FormBuilder> {

        private FormType formType;

        private List<FormSectionDef> sections = new ArrayList<>();
        private ListStyleHint listStyleHint = null;
        private FormSectionDef introduction = null;
        private FormSectionDef closing = null;
        private Template lastUpdatedTextTemplate = null;
        private LocalDateTime lastUpdated = null;

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


        public FormActivityDef build() {
            FormActivityDef form = new FormActivityDef(
                    formType,
                    activityCode,
                    versionTag,
                    studyGuid,
                    maxInstancesPerUser,
                    displayOrder,
                    writeOnce,
                    names,
                    subtitles,
                    dashboardNames,
                    descriptions,
                    dashboardSummaries,
                    readonlyHintTemplate,
                    introduction,
                    sections,
                    listStyleHint,
                    lastUpdatedTextTemplate,
                    lastUpdated,
                    isFollowup
            );
            configure(form);
            form.listStyleHint = listStyleHint;
            form.introduction = introduction;
            form.closing = closing;
            return form;
        }
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
}
