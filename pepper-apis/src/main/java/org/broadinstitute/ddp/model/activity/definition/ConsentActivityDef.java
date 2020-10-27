package org.broadinstitute.ddp.model.activity.definition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.util.MiscUtil;

public final class ConsentActivityDef extends FormActivityDef {

    @NotBlank
    @SerializedName("consentedExpr")
    private String consentedExpr;

    @NotNull
    @SerializedName("elections")
    private List<@Valid @NotNull ConsentElectionDef> elections;

    private transient Long consentConditionId;
    private transient Long consentedExprId;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String activityCode, String versionTag, String studyGuid, String consentedExpr) {
        return new Builder()
                .setConsentedExpr(consentedExpr)
                .setActivityCode(activityCode)
                .setVersionTag(versionTag)
                .setStudyGuid(studyGuid);
    }

    public ConsentActivityDef(
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
            List<FormSectionDef> sections,
            String consentedExpr,
            List<ConsentElectionDef> elections,
            FormSectionDef intro,
            ListStyleHint listStyle,
            Template lastUpdatedTextTemplate,
            LocalDateTime lastUpdated,
            boolean isFollowup,
            boolean hideExistingInstancesOnCreation
    ) {
        super(
                FormType.CONSENT,
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
                intro,
                sections,
                listStyle,
                lastUpdatedTextTemplate,
                lastUpdated,
                isFollowup,
                hideExistingInstancesOnCreation
        );
        this.consentedExpr = MiscUtil.checkNotBlank(consentedExpr, "consentedExpr");
        this.elections = MiscUtil.checkNonNull(elections, "elections");
    }

    public String getConsentedExpr() {
        return consentedExpr;
    }

    public List<ConsentElectionDef> getElections() {
        return elections;
    }

    public Long getConsentConditionId() {
        return consentConditionId;
    }

    public void setConsentConditionId(Long consentConditionId) {
        this.consentConditionId = consentConditionId;
    }

    public Long getConsentedExprId() {
        return consentedExprId;
    }

    public void setConsentedExprId(Long consentedExprId) {
        this.consentedExprId = consentedExprId;
    }

    public static final class Builder extends AbstractActivityBuilder<Builder> {

        private String consentedExpr;

        private Long consentConditionId = null;
        private Long consentedExprId = null;
        private List<FormSectionDef> sections = new ArrayList<>();
        private List<ConsentElectionDef> elections = new ArrayList<>();
        protected FormSectionDef introduction;
        protected FormSectionDef closing;
        protected ListStyleHint listStyleHint;
        private Template lastUpdatedTextTemplate;
        private LocalDateTime lastUpdated;
        private boolean snapshotSubstitutionsOnSubmit;

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        private Builder setConsentedExpr(String consentedExpr) {
            this.consentedExpr = consentedExpr;
            return this;
        }

        public Builder setConsentConditionId(Long consentConditionId) {
            this.consentConditionId = consentConditionId;
            return this;
        }

        public Builder setConsentedExprId(Long consentedExprId) {
            this.consentedExprId = consentedExprId;
            return this;
        }

        public Builder addSection(FormSectionDef section) {
            this.sections.add(section);
            return this;
        }

        public Builder addSections(Collection<FormSectionDef> sections) {
            this.sections.addAll(sections);
            return this;
        }

        public Builder clearSections() {
            this.sections.clear();
            return this;
        }

        public Builder addElection(ConsentElectionDef election) {
            this.elections.add(election);
            return this;
        }

        public Builder addElections(Collection<ConsentElectionDef> elections) {
            this.elections.addAll(elections);
            return this;
        }

        public Builder clearElections() {
            this.elections.clear();
            return this;
        }

        public ConsentActivityDef build() {
            ConsentActivityDef consent = new ConsentActivityDef(
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
                    sections,
                    consentedExpr,
                    elections,
                    introduction,
                    listStyleHint,
                    lastUpdatedTextTemplate,
                    lastUpdated,
                    isFollowup,
                    hideExistingInstancesOnCreation
            );
            configure(consent);
            consent.setConsentConditionId(consentConditionId);
            consent.setConsentedExprId(consentedExprId);
            consent.closing = closing;
            consent.snapshotSubstitutionsOnSubmit = snapshotSubstitutionsOnSubmit;
            return consent;
        }

        public Builder setListStyleHint(ListStyleHint listStyleHint) {
            this.listStyleHint = listStyleHint;
            return this;
        }

        public Builder setIntroduction(FormSectionDef introduction) {
            this.introduction = introduction;
            return this;
        }

        public Builder setClosing(FormSectionDef closing) {
            this.closing = closing;
            return this;
        }

        public Builder setLastUpdatedTextTemplate(Template template) {
            this.lastUpdatedTextTemplate = template;
            return this;
        }

        public Builder setLastUpdated(LocalDateTime lastUpdatedDate) {
            this.lastUpdated = lastUpdatedDate;
            return this;
        }

        public Builder setSnapshotSubstitutionsOnSubmit(boolean snapshotSubstitutionsOnSubmit) {
            this.snapshotSubstitutionsOnSubmit = snapshotSubstitutionsOnSubmit;
            return this;
        }
    }
}
