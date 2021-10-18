package org.broadinstitute.ddp.studybuilder.translation.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;


/**
 * This model used to serialize the activity JSON (conf document).
 * It adds template components in order to be possible to define
 * in a conf file standard templates (instead of non-standard lists of translations).
 * After a conf file serialization the templates are rendered and copied to
 * the translations properties of the base object {@link ActivityDef}.<br>
 * The following rules of translations copying:
 * <pre>
 *     nameTemplate -> translatedNames
 *     secondNameTemplate -> translatedSecondNames
 *     titleTemplate -> translatedTitles
 *     subtitleTemplate -> translatedSubtitles
 *     descriptionTemplate ->translatedDescriptions
 *     summaryTemplates -> translatedSummaries
 * </pre>
 */
public class ExtendedFormActivityDef extends FormActivityDef {

    @Valid
    @SerializedName("nameTemplate")
    protected Template nameTemplate;

    @Valid
    @SerializedName("secondNameTemplate")
    protected Template secondNameTemplate;

    @Valid
    @SerializedName("titleTemplate")
    protected Template titleTemplate;

    @Valid
    @SerializedName("subtitleTemplate")
    protected Template subtitleTemplate;

    @Valid
    @SerializedName("descriptionTemplate")
    protected Template descriptionTemplate;

    @Valid
    @SerializedName("summaryTemplates")
    protected Map<InstanceStatusType, @Valid @NotNull Template> summaryTemplates;

    public ExtendedFormActivityDef(
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
            Template readonlyHintTemplate, FormSectionDef intro,
            List<FormSectionDef> sections,
            ListStyleHint listStyleHint,
            Template lastUpdatedTextTemplate,
            LocalDateTime lastUpdated,
            boolean isFollowup,
            boolean hideExistingInstancesOnCreation) {
        super(formType, parentActivityCode, activityCode, versionTag, studyGuid, maxInstancesPerUser, displayOrder,
                writeOnce, translatedNames, translatedTitles, translatedSubtitles, translatedDescriptions,
                translatedSummaries, readonlyHintTemplate, intro, sections, listStyleHint, lastUpdatedTextTemplate,
                lastUpdated, isFollowup, hideExistingInstancesOnCreation);
    }

    public Template getNameTemplate() {
        return nameTemplate;
    }

    public Template getSecondNameTemplate() {
        return secondNameTemplate;
    }

    public Template getTitleTemplate() {
        return titleTemplate;
    }

    public Template getSubtitleTemplate() {
        return subtitleTemplate;
    }

    public Template getDescriptionTemplate() {
        return descriptionTemplate;
    }

    public Map<InstanceStatusType, Template> getSummaryTemplates() {
        return summaryTemplates;
    }
}
