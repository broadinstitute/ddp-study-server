package org.broadinstitute.ddp.model.activity.definition;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.util.MiscUtil;

public abstract class ActivityDef {

    @NotNull
    @SerializedName("activityType")
    protected ActivityType activityType;

    @NotBlank
    @SerializedName("studyGuid")
    protected String studyGuid;

    @NotBlank
    @SerializedName("activityCode")
    protected String activityCode;

    @NotBlank
    @SerializedName("versionTag")
    protected String versionTag;

    @SerializedName("maxInstancesPerUser")
    protected Integer maxInstancesPerUser;

    @SerializedName("displayOrder")
    protected int displayOrder;

    @SerializedName("writeOnce")
    protected boolean writeOnce;

    @SerializedName("editTimeoutSec")
    protected Long editTimeoutSec;

    @SerializedName("allowOndemandTrigger")
    protected boolean allowOndemandTrigger;

    @SerializedName("excludeFromDisplay")
    protected boolean excludeFromDisplay;

    @SerializedName("excludeStatusIconFromDisplay")
    protected boolean excludeStatusIconFromDisplay;

    @SerializedName("allowUnauthenticated")
    protected boolean allowUnauthenticated;

    @SerializedName("hideExistingInstancesOnCreation")
    protected boolean hideExistingInstancesOnCreation;

    @NotEmpty
    @SerializedName("translatedNames")
    protected List<@Valid @NotNull Translation> translatedNames;

    @NotNull
    @SerializedName("translatedSecondNames")
    protected List<@Valid @NotNull Translation> translatedSecondNames;

    @NotNull
    @SerializedName("translatedTitles")
    protected List<@Valid @NotNull Translation> translatedTitles;

    @NotNull
    @SerializedName("translatedSubtitles")
    protected List<@Valid @NotNull Translation> translatedSubtitles;

    @NotNull
    @SerializedName("translatedDescriptions")
    protected List<@Valid @NotNull Translation> translatedDescriptions;

    @NotNull
    @SerializedName("translatedSummaries")
    protected List<@Valid @NotNull SummaryTranslation> translatedSummaries;

    @Valid
    @SerializedName("readonlyHintTemplate")
    protected Template readonlyHintTemplate;

    @SerializedName("isFollowup")
    protected boolean isFollowup;

    protected transient Long activityId;
    protected transient Long versionId;

    ActivityDef(
            ActivityType activityType,
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
            boolean isFollowup,
            boolean hideExistingInstancesOnCreation
    ) {
        this.activityType = MiscUtil.checkNonNull(activityType, "activityType");
        this.activityCode = MiscUtil.checkNotBlank(activityCode, "activityCode");
        this.versionTag = MiscUtil.checkNotBlank(versionTag, "versionTag");
        this.studyGuid = MiscUtil.checkNotBlank(studyGuid, "studyGuid");
        this.maxInstancesPerUser = maxInstancesPerUser;
        this.displayOrder = displayOrder;
        this.writeOnce = writeOnce;
        if (translatedNames != null && !translatedNames.isEmpty()) {
            this.translatedNames = translatedNames;
        } else {
            throw new IllegalArgumentException("Need at least one name translation");
        }
        this.translatedSecondNames = new ArrayList<>();
        this.translatedTitles = translatedTitles;
        this.translatedSubtitles = translatedSubtitles;
        this.translatedDescriptions = translatedDescriptions;
        this.translatedSummaries = translatedSummaries;
        this.readonlyHintTemplate = readonlyHintTemplate;
        this.isFollowup = isFollowup;
        this.hideExistingInstancesOnCreation = hideExistingInstancesOnCreation;
    }

    /**
     * Get a tag that should be unique enough to identify an activity at a certain version.
     *
     * @return a tag string
     */
    public static String getTag(String activityCode, String versionTag) {
        return activityCode + "_" + versionTag;
    }

    public String getTag() {
        return ActivityDef.getTag(activityCode, versionTag);
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public Integer getMaxInstancesPerUser() {
        return maxInstancesPerUser;
    }

    public void setMaxInstancesPerUser(Integer maxInstancesPerUser) {
        this.maxInstancesPerUser = maxInstancesPerUser;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isWriteOnce() {
        return writeOnce;
    }

    public Long getEditTimeoutSec() {
        return editTimeoutSec;
    }

    public List<Translation> getTranslatedNames() {
        return translatedNames;
    }

    public List<Translation> getTranslatedSecondNames() {
        return translatedSecondNames;
    }

    public List<Translation> getTranslatedTitles() {
        return translatedTitles;
    }

    public List<Translation> getTranslatedSubtitles() {
        return translatedSubtitles;
    }

    public List<Translation> getTranslatedDescriptions() {
        return translatedDescriptions;
    }

    public List<SummaryTranslation> getTranslatedSummaries() {
        return translatedSummaries;
    }

    public Template getReadonlyHintTemplate() {
        return readonlyHintTemplate;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public ActivityDef setVersionId(Long versionId) {
        this.versionId = versionId;
        return this;
    }

    public boolean isOndemandTriggerAllowed() {
        return allowOndemandTrigger;
    }

    public boolean isExcludeFromDisplay() {
        return excludeFromDisplay;
    }

    public boolean isExcludeStatusIconFromDisplay() {
        return excludeStatusIconFromDisplay;
    }

    public boolean isAllowUnauthenticated() {
        return allowUnauthenticated;
    }

    public boolean isFollowup() {
        return isFollowup;
    }

    public boolean isHideInstances() {
        return hideExistingInstancesOnCreation;
    }

    /**
     * Builder that helps construct common elements of an activity definition.
     *
     * @param <T> Type of the subclass builder
     */
    protected abstract static class AbstractActivityBuilder<T extends AbstractActivityBuilder<T>> {

        protected String activityCode;
        protected String versionTag;
        protected String studyGuid;
        protected Long activityId = null;
        protected Long versionId = null;
        protected Integer maxInstancesPerUser = null;
        protected int displayOrder = 0;
        protected boolean writeOnce = false;
        protected Long editTimeoutSec = null;
        protected boolean allowOndemandTrigger = false;
        protected boolean excludeFromDisplay = false;
        protected boolean excludeStatusIconFromDisplay = false;
        protected boolean allowUnauthenticated = false;
        protected List<Translation> names = new ArrayList<>();
        protected List<Translation> secondNames = new ArrayList<>();
        protected List<Translation> titles = new ArrayList<>();
        protected List<Translation> subtitles = new ArrayList<>();
        protected List<Translation> descriptions = new ArrayList<>();
        protected List<SummaryTranslation> summaries = new ArrayList<>();
        protected Template readonlyHintTemplate;
        protected boolean isFollowup;
        protected boolean hideExistingInstancesOnCreation;

        /**
         * Returns the subclass builder instance to enable method chaining.
         */
        protected abstract T self();

        /**
         * Configure the base properties of an activity.
         *
         * @param activity the activity
         */
        protected void configure(ActivityDef activity) {
            activity.activityId = activityId;
            activity.versionId = versionId;
            activity.editTimeoutSec = editTimeoutSec;
            activity.allowOndemandTrigger = allowOndemandTrigger;
            activity.excludeFromDisplay = excludeFromDisplay;
            activity.excludeStatusIconFromDisplay = excludeStatusIconFromDisplay;
            activity.allowUnauthenticated = allowUnauthenticated;
            activity.readonlyHintTemplate = readonlyHintTemplate;
            activity.isFollowup = isFollowup;
            activity.hideExistingInstancesOnCreation = hideExistingInstancesOnCreation;
            activity.translatedSecondNames.addAll(secondNames);
        }

        public T setActivityCode(String activityCode) {
            this.activityCode = activityCode;
            return self();
        }

        public T setVersionTag(String versionTag) {
            this.versionTag = versionTag;
            return self();
        }

        public T setStudyGuid(String studyGuid) {
            this.studyGuid = studyGuid;
            return self();
        }

        public T setActivityId(Long activityId) {
            this.activityId = activityId;
            return self();
        }

        public T setVersionId(Long versionId) {
            this.versionId = versionId;
            return self();
        }

        public T setMaxInstancesPerUser(Integer maxInstancesPerUser) {
            this.maxInstancesPerUser = maxInstancesPerUser;
            return self();
        }

        public T setDisplayOrder(int displayOrder) {
            this.displayOrder = displayOrder;
            return self();
        }

        public T setWriteOnce(boolean writeOnce) {
            this.writeOnce = writeOnce;
            return self();
        }

        public T setEditTimeoutSec(Long editTimeoutSec) {
            this.editTimeoutSec = editTimeoutSec;
            return self();
        }

        public T setAllowOndemandTrigger(boolean allowOndemandTrigger) {
            this.allowOndemandTrigger = allowOndemandTrigger;
            return self();
        }

        public T setExcludeFromDisplay(boolean excludeFromDisplay) {
            this.excludeFromDisplay = excludeFromDisplay;
            return self();
        }

        public T setExcludeStatusIconFromDisplay(boolean excludeStatusIconFromDisplay) {
            this.excludeStatusIconFromDisplay = excludeStatusIconFromDisplay;
            return self();
        }

        public T setAllowUnauthenticated(boolean allowUnauthenticated) {
            this.allowUnauthenticated = allowUnauthenticated;
            return self();
        }

        public T addName(Translation name) {
            this.names.add(name);
            return self();
        }

        public T addNames(Collection<Translation> names) {
            this.names.addAll(names);
            return self();
        }

        public T clearNames() {
            this.names.clear();
            return self();
        }

        public T addSecondName(Translation secondName) {
            this.secondNames.add(secondName);
            return self();
        }

        public T addSecondNames(Collection<Translation> secondNames) {
            this.secondNames.addAll(secondNames);
            return self();
        }

        public T clearSecondNames() {
            this.secondNames.clear();
            return self();
        }

        public T addTitle(Translation title) {
            this.titles.add(title);
            return self();
        }

        public T addTitles(Collection<Translation> titles) {
            this.titles.addAll(titles);
            return self();
        }

        public T clearTitles() {
            this.titles.clear();
            return self();
        }

        public T addSubtitle(Translation subtitle) {
            this.subtitles.add(subtitle);
            return self();
        }

        public T addSubtitles(Collection<Translation> subtitles) {
            this.subtitles.addAll(subtitles);
            return self();
        }

        public T clearSubtitles() {
            this.subtitles.clear();
            return self();
        }

        public T addDescription(Translation description) {
            this.descriptions.add(description);
            return self();
        }

        public T addDescriptions(Collection<Translation> descriptions) {
            this.descriptions.addAll(descriptions);
            return self();
        }

        public T clearDescriptions() {
            this.descriptions.clear();
            return self();
        }

        public T addSummary(SummaryTranslation summary) {
            this.summaries.add(summary);
            return self();
        }

        public T addSummaries(Collection<SummaryTranslation> summaries) {
            this.summaries.addAll(summaries);
            return self();
        }

        public T clearSummaries() {
            this.summaries.clear();
            return self();
        }

        public T setReadonlyHintTemplate(Template readonlyHintTemplate) {
            this.readonlyHintTemplate = readonlyHintTemplate;
            return self();
        }

        public T setIsFollowup(boolean isFollowup) {
            this.isFollowup = isFollowup;
            return self();
        }

        public T setHideInstances(boolean hideExistingInstancesOnCreation) {
            this.hideExistingInstancesOnCreation = hideExistingInstancesOnCreation;
            return self();
        }
    }

    public static class Deserializer implements JsonDeserializer<ActivityDef> {
        @Override
        public ActivityDef deserialize(JsonElement elem, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            ActivityType actType = parseActivityType(elem);
            if (actType == ActivityType.FORMS) {
                FormType formType = parseFormType(elem);
                if (formType == FormType.CONSENT) {
                    return ctx.deserialize(elem, ConsentActivityDef.class);
                } else {
                    return ctx.deserialize(elem, FormActivityDef.class);
                }
            } else {
                throw new JsonParseException(String.format("Activity type '%s' is not supported", actType));
            }
        }

        private ActivityType parseActivityType(JsonElement elem) {
            try {
                return ActivityType.valueOf(elem.getAsJsonObject().get("activityType").getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Could not determine activity type", e);
            }
        }

        private FormType parseFormType(JsonElement elem) {
            try {
                return FormType.valueOf(elem.getAsJsonObject().get("formType").getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Could not determine form type", e);
            }
        }
    }
}
