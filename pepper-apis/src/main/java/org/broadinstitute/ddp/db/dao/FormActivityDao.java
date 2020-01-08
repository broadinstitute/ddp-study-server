package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface FormActivityDao extends SqlObject {

    @CreateSqlObject
    JdbiUmbrellaStudy getJdbiUmbrellaStudy();

    @CreateSqlObject
    JdbiActivity getJdbiActivity();

    @CreateSqlObject
    JdbiActivityVersion getJdbiActivityVersion();

    @CreateSqlObject
    JdbiStudyActivityNameTranslation getJdbiStudyActivityNameTranslation();

    @CreateSqlObject
    JdbiStudyActivitySubtitleTranslation getJdbiStudyActivitySubtitleTranslation();

    @CreateSqlObject
    JdbiStudyActivityDashboardNameTranslation getJdbiStudyActivityDashboardNameTranslation();

    @CreateSqlObject
    JdbiStudyActivityDescriptionTranslation getJdbiStudyActivityDescriptionTranslation();

    @CreateSqlObject
    JdbiStudyActivitySummaryTranslation getJdbiStudyActivitySummaryTranslation();

    @CreateSqlObject
    JdbiLanguageCode getJdbiLanguageCode();

    @CreateSqlObject
    JdbiExpression getJdbiExpression();

    @CreateSqlObject
    JdbiListStyleHint getJdbiListStyleHint();

    @CreateSqlObject
    JdbiFormActivitySetting getJdbiFormActivitySetting();

    @CreateSqlObject
    JdbiFormActivityFormSection getJdbiFormActivityFormSection();

    @CreateSqlObject
    SectionBlockDao getSectionBlockDao();

    @CreateSqlObject
    TemplateDao getTemplateDao();

    /**
     * Create new form activity by inserting all related data for activity definition. Use this to create the first
     * version of an activity.
     *
     * <p>See {@link ActivityDao#insertActivity(FormActivityDef, RevisionMetadata)} for a more convenient way for
     * inserting, and {@link ConsentActivityDao#insertActivity(ConsentActivityDef, long)} specifically for consent
     * activities. Changing content should be done with the appropriate add/disable methods.
     *
     * @param activity   the activity to create, without generated things like ids
     * @param revisionId the revision to use, will be shared for all created data
     */
    default void insertActivity(FormActivityDef activity, long revisionId) {
        if (activity.getActivityId() != null) {
            throw new IllegalStateException("Activity id already set to " + activity.getActivityId());
        }
        if (!DBUtils.matchesCodePattern(activity.getActivityCode())) {
            throw new IllegalStateException("Requires non-null activity code that follows accepted naming pattern");
        }

        JdbiActivity jdbiActivity = getJdbiActivity();
        JdbiActivityVersion jdbiVersion = getJdbiActivityVersion();
        JdbiStudyActivityNameTranslation jdbiActNameTranslation = getJdbiStudyActivityNameTranslation();
        JdbiStudyActivitySubtitleTranslation jdbiActSubtitleTranslation = getJdbiStudyActivitySubtitleTranslation();
        JdbiStudyActivityDashboardNameTranslation jdbiActDashboardNameTranslation = getJdbiStudyActivityDashboardNameTranslation();
        JdbiStudyActivityDescriptionTranslation jdbiActDescriptionTranslation = getJdbiStudyActivityDescriptionTranslation();
        JdbiStudyActivitySummaryTranslation jdbiActSummaryTranslation = getJdbiStudyActivitySummaryTranslation();
        JdbiLanguageCode jdbiLang = getJdbiLanguageCode();
        SectionBlockDao sectionBlockDao = getSectionBlockDao();
        TemplateDao templateDao = getTemplateDao();

        long activityTypeId = jdbiActivity.getActivityTypeId(activity.getActivityType());
        long studyId = jdbiActivity.getStudyId(activity.getStudyGuid());

        long activityId = jdbiActivity.insertActivity(activityTypeId, studyId, activity.getActivityCode(),
                activity.getMaxInstancesPerUser(), activity.getDisplayOrder(), activity.isWriteOnce(), activity.getEditTimeoutSec(),
                activity.isOndemandTriggerAllowed(), activity.isExcludeFromDisplay(), activity.isAllowUnauthenticated(),
                activity.isFollowup());
        activity.setActivityId(activityId);

        long versionId = jdbiVersion.insert(activity.getActivityId(), activity.getVersionTag(), revisionId);
        activity.setVersionId(versionId);

        for (Translation name : activity.getTranslatedNames()) {
            jdbiActNameTranslation.insert(activityId,
                    jdbiLang.getLanguageCodeId(name.getLanguageCode()), name.getText());
        }
        for (Translation subtitle : activity.getTranslatedSubtitles()) {
            jdbiActSubtitleTranslation.insert(activityId,
                    jdbiLang.getLanguageCodeId(subtitle.getLanguageCode()), subtitle.getText());

        }
        for (Translation dashboardName : activity.getTranslatedDashboardNames()) {
            jdbiActDashboardNameTranslation.insert(
                    activityId,
                    jdbiLang.getLanguageCodeId(dashboardName.getLanguageCode()), dashboardName.getText()
            );
        }
        for (SummaryTranslation summary : activity.getTranslatedSummaries()) {
            jdbiActSummaryTranslation.insert(
                    activityId,
                    summary.getStatusCode(),
                    jdbiLang.getLanguageCodeId(summary.getLanguageCode()),
                    summary.getText()
            );
        }
        for (Translation description : activity.getTranslatedDescriptions()) {
            jdbiActDescriptionTranslation.insert(
                    activityId,
                    jdbiLang.getLanguageCodeId(description.getLanguageCode()),
                    description.getText()
            );
        }

        FormType formType = activity.getFormType();
        int numRows = jdbiActivity.insertFormActivity(activityId, jdbiActivity.getFormTypeId(formType));
        if (numRows != 1) {
            throw new DaoException("Inserted " + numRows + " form activity rows for activity " + activityId);
        }

        sectionBlockDao.insertBodySections(activityId, activity.getSections(), revisionId);

        Long introductionSectionId = null;
        if (activity.getIntroduction() != null) {
            introductionSectionId = sectionBlockDao.insertSection(activityId, activity.getIntroduction(), revisionId);
        }

        Long closingSectionId = null;
        if (activity.getClosing() != null) {
            closingSectionId = sectionBlockDao.insertSection(activityId, activity.getClosing(), revisionId);
        }

        Long readonlyHintTemplateId = null;
        if (activity.getReadonlyHintTemplate() != null) {
            readonlyHintTemplateId = templateDao.insertTemplate(activity.getReadonlyHintTemplate(), revisionId);
        }

        Long lastUpdatedTextTemplateId = null;
        if (activity.getLastUpdatedTextTemplate() != null) {
            lastUpdatedTextTemplateId = templateDao.insertTemplate(activity.getLastUpdatedTextTemplate(), revisionId);
        }

        getJdbiFormActivitySetting().insert(
                activityId, activity.getListStyleHint(), introductionSectionId,
                closingSectionId, revisionId, readonlyHintTemplateId, activity.getLastUpdated(), lastUpdatedTextTemplateId);
    }

    default FormActivityDef findDefByDtoAndVersion(ActivityDto activityDto, ActivityVersionDto revisionDto) {
        return findDefByDtoAndVersion(activityDto, revisionDto.getVersionTag(), revisionDto.getId(), revisionDto.getRevStart());
    }

    default FormActivityDef findDefByDtoAndVersion(ActivityDto activityDto, String versionTag, long versionId, long revisionStart) {
        SectionBlockDao sectionBlockDao = getSectionBlockDao();

        FormType type = getJdbiActivity().findFormTypeByActivityId(activityDto.getActivityId());
        String studyGuid = getJdbiUmbrellaStudy().findGuidByStudyId(activityDto.getStudyId());

        FormActivityDef.FormBuilder builder = FormActivityDef
                .formBuilder(type, activityDto.getActivityCode(), versionTag, studyGuid)
                .setActivityId(activityDto.getActivityId())
                .setVersionId(versionId)
                .setMaxInstancesPerUser(activityDto.getMaxInstancesPerUser())
                .setDisplayOrder(activityDto.getDisplayOrder())
                .setWriteOnce(activityDto.isWriteOnce())
                .setEditTimeoutSec(activityDto.getEditTimeoutSec())
                .setAllowOndemandTrigger(activityDto.isOndemandTriggerAllowed());

        // todo: query name, translations, templates
        builder.addName(new Translation("en", ""));

        getJdbiFormActivitySetting()
                .findSettingDtoByActivityIdAndTimestamp(activityDto.getActivityId(), revisionStart)
                .ifPresent(dto -> {
                    builder.setListStyleHint(dto.getListStyleHint());
                    if (dto.getIntroductionSectionId() != null) {
                        builder.setIntroduction(sectionBlockDao
                                .findSectionDefByIdAndTimestamp(dto.getIntroductionSectionId(), revisionStart));
                    }
                    if (dto.getClosingSectionId() != null) {
                        builder.setClosing(sectionBlockDao
                                .findSectionDefByIdAndTimestamp(dto.getClosingSectionId(), revisionStart));
                    }
                });

        getJdbiFormActivityFormSection()
                .findOrderedSectionIdsByActivityIdAndTimestamp(activityDto.getActivityId(), revisionStart)
                .forEach(id -> builder.addSection(sectionBlockDao.findSectionDefByIdAndTimestamp(id, revisionStart)));

        return builder.build();
    }
}

