package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
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
    JdbiExpression getJdbiExpression();

    @CreateSqlObject
    JdbiFormActivitySetting getJdbiFormActivitySetting();

    @CreateSqlObject
    JdbiFormActivityFormSection getJdbiFormActivityFormSection();

    @CreateSqlObject
    ActivityI18nDao getActivityI18nDao();

    @CreateSqlObject
    SectionBlockDao getSectionBlockDao();

    @CreateSqlObject
    TemplateDao getTemplateDao();

    /**
     * Create new form activity by inserting all related data for activity definition. Use this to create the first version of an activity.
     *
     * <p>See {@link ActivityDao#insertActivity(FormActivityDef, RevisionMetadata)} for a more convenient way for
     * inserting, and {@link ConsentActivityDao#insertActivity(ConsentActivityDef, long)} specifically for consent activities. Changing
     * content should be done with the appropriate add/disable methods.
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
        ActivityI18nDao activityI18nDao = getActivityI18nDao();
        SectionBlockDao sectionBlockDao = getSectionBlockDao();
        TemplateDao templateDao = getTemplateDao();

        long activityTypeId = jdbiActivity.getActivityTypeId(activity.getActivityType());
        long studyId = jdbiActivity.getStudyId(activity.getStudyGuid());

        long activityId = jdbiActivity.insertActivity(activityTypeId, studyId, activity.getActivityCode(),
                activity.getMaxInstancesPerUser(), activity.getDisplayOrder(), activity.isWriteOnce(), activity.getEditTimeoutSec(),
                activity.isOndemandTriggerAllowed(), activity.isExcludeFromDisplay(), activity.isExcludeStatusIconFromDisplay(),
                activity.isAllowUnauthenticated(), activity.isFollowup(), activity.isHideInstances());
        activity.setActivityId(activityId);

        long versionId = jdbiVersion.insert(activity.getActivityId(), activity.getVersionTag(), revisionId);
        activity.setVersionId(versionId);

        Map<String, String> names = activity.getTranslatedNames().stream()
                .collect(Collectors.toMap(Translation::getLanguageCode, Translation::getText));
        Map<String, String> titles = activity.getTranslatedTitles().stream()
                .collect(Collectors.toMap(Translation::getLanguageCode, Translation::getText));
        Map<String, String> subtitles = activity.getTranslatedSubtitles().stream()
                .collect(Collectors.toMap(Translation::getLanguageCode, Translation::getText));
        Map<String, String> descriptions = activity.getTranslatedDescriptions().stream()
                .collect(Collectors.toMap(Translation::getLanguageCode, Translation::getText));

        List<ActivityI18nDetail> details = new ArrayList<>();
        for (var entry : names.entrySet()) {
            String isoLangCode = entry.getKey();
            String name = entry.getValue();
            details.add(new ActivityI18nDetail(
                    activityId,
                    isoLangCode,
                    name,
                    titles.getOrDefault(isoLangCode, null),
                    subtitles.getOrDefault(isoLangCode, null),
                    descriptions.getOrDefault(isoLangCode, null)
            ));
        }

        activityI18nDao.insertDetails(details);
        activityI18nDao.insertSummaries(activityId, activity.getTranslatedSummaries());

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
                closingSectionId, revisionId, readonlyHintTemplateId, activity.getLastUpdated(), lastUpdatedTextTemplateId,
                activity.shouldSnapshotSubstitutionsOnSubmit());
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

        List<Translation> names = new ArrayList<>();
        List<Translation> titles = new ArrayList<>();
        List<Translation> subtitles = new ArrayList<>();
        List<Translation> descriptions = new ArrayList<>();
        getActivityI18nDao().findDetailsByActivityId(activityDto.getActivityId()).forEach(detail -> {
            String isoLangCode = detail.getIsoLangCode();
            names.add(new Translation(isoLangCode, detail.getName()));
            if (detail.getTitle() != null) {
                titles.add(new Translation(isoLangCode, detail.getTitle()));
            }
            if (detail.getSubtitle() != null) {
                subtitles.add(new Translation(isoLangCode, detail.getSubtitle()));
            }
            if (detail.getDescription() != null) {
                descriptions.add(new Translation(isoLangCode, detail.getDescription()));
            }
        });
        builder.addNames(names);
        builder.addTitles(titles);
        builder.addSubtitles(subtitles);
        builder.addDescriptions(descriptions);

        // todo: query other translations, templates, etc

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

