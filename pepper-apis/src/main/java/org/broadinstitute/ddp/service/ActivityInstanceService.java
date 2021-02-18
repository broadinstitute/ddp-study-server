package org.broadinstitute.ddp.service;

import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.constants.LanguageConstants;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.StudyLanguageCachedDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceSummaryDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.study.StudyLanguage;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityInstanceService {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityInstanceService.class);

    private final ActivityInstanceDao actInstanceDao;
    private final PexInterpreter interpreter;
    private final I18nContentRenderer renderer;

    public ActivityInstanceService(ActivityInstanceDao actInstanceDao, PexInterpreter interpreter, I18nContentRenderer renderer) {
        this.actInstanceDao = actInstanceDao;
        this.interpreter = interpreter;
        this.renderer = renderer;
    }

    /**
     * Get an activity instance, translated to given language. If activity is a form, visibility of
     * blocks will be resolved as well.
     *
     * @param handle          the jdbi handle
     * @param userGuid        the user guid
     * @param actType         the activity type
     * @param actInstanceGuid the activity instance guid
     * @param isoLangCode     the iso language code
     * @param style           the content style to use for converting content
     * @return activity instance, if found
     * @throws DDPException if pex evaluation error
     */
    public Optional<ActivityInstance> getTranslatedActivity(Handle handle, String userGuid, String operatorGuid, ActivityType actType,
                                                            String actInstanceGuid, String isoLangCode, ContentStyle style) {
        ActivityInstance inst = actInstanceDao.getTranslatedActivityByTypeAndGuid(handle, actType, actInstanceGuid, isoLangCode, style);
        if (inst == null) {
            return Optional.empty();
        }

        if (ActivityType.FORMS.equals(inst.getActivityType())) {
            ((FormInstance) inst).updateBlockStatuses(handle, interpreter, userGuid, operatorGuid, actInstanceGuid, null);
        }

        return Optional.of(inst);
    }

    /**
     * Get a form instance, translated to given language and with visibility of blocks resolved.
     *
     * @param handle           the jdbi handle
     * @param userGuid         the user guid
     * @param formInstanceGuid the form instance guid
     * @param isoLangCode      the iso language code
     * @return form instance, if found
     * @throws DDPException if pex evaluation error
     */
    public Optional<FormInstance> getTranslatedForm(Handle handle, String userGuid, String operatorGuid, String formInstanceGuid,
                                                    String isoLangCode, ContentStyle style) {
        Function<ActivityInstance, FormInstance> typeChecker = (inst) -> {
            if (ActivityType.FORMS.equals(inst.getActivityType())) {
                return (FormInstance) inst;
            } else {
                LOG.warn("Expected a form instance but got type {} for guid {} lang code {}",
                        inst.getActivityType(), formInstanceGuid, isoLangCode);
                return null;
            }
        };
        return getTranslatedActivity(handle, userGuid, operatorGuid, ActivityType.FORMS,
                formInstanceGuid, isoLangCode, style).map(typeChecker);
    }

    /**
     * Find list of activity instance summaries for user and study. List will be sorted by activity display order
     * (ascending) and instance creation time (descending). Child nested activity instances will not be returned.
     *
     * <p>The instance summaries will be translated to the user's preferred language, or falls back to study's default
     * language if preferred language is not available. If study doesn't have a default language, English will be used
     * as the fallback.
     *
     * <p>Some computed properties such as instance numbering, question/answer count, or rendered strings will not be
     * set. Caller is responsible for performing the computations and setting them properly.
     *
     * @param handle            database handle
     * @param userGuid          GUID of the user to get activity instance summaries for
     * @param studyGuid         GUID of the study to get activity instance summaries for
     * @param preferredLangCode The desired translation language
     * @return Sorted list of top-level activity instance summaries for the user/study
     */
    public List<ActivityInstanceSummary> listTranslatedInstanceSummaries(Handle handle,
                                                                         String userGuid,
                                                                         String studyGuid,
                                                                         String preferredLangCode) {
        List<ActivityInstanceSummaryDto> summaryDtos = handle
                .attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                .findNonNestedSortedInstanceSummaries(userGuid, studyGuid);
        if (summaryDtos.isEmpty()) {
            return new ArrayList<>();
        }

        String studyDefaultLangCode = new StudyLanguageCachedDao(handle)
                .findLanguages(studyGuid)
                .stream()
                .filter(StudyLanguage::isDefault)
                .map(StudyLanguage::getLanguageCode)
                .findFirst()
                .orElse(LanguageConstants.EN_LANGUAGE_CODE);

        return buildTranslatedInstanceSummaries(handle, summaryDtos, studyGuid, preferredLangCode, studyDefaultLangCode);
    }

    // Does the heavy-lifting of translating activity properties and merging into a full activity instance summary.
    private List<ActivityInstanceSummary> buildTranslatedInstanceSummaries(Handle handle,
                                                                           List<ActivityInstanceSummaryDto> summaryDtos,
                                                                           String studyGuid,
                                                                           String preferredLangCode,
                                                                           String studyDefaultLangCode) {
        ActivityDefStore activityDefStore = ActivityDefStore.getInstance();
        Map<String, Blob> formTypeAndStatusTypeToIcon = activityDefStore.findActivityStatusIcons(handle, studyGuid);
        List<ActivityInstanceSummary> summaries = new ArrayList<>();

        for (var summaryDto : summaryDtos) {
            ActivityDto activityDto = activityDefStore
                    .findActivityDto(handle, summaryDto.getActivityId())
                    .orElseThrow(() -> new DDPException("Could not find activity dto for " + summaryDto.getActivityCode()));
            ActivityVersionDto versionDto = activityDefStore
                    .findVersionDto(handle, activityDto.getActivityId(), summaryDto.getCreatedAtMillis())
                    .orElseThrow(() -> new DDPException("Could not find activity version for instance" + summaryDto.getGuid()));
            FormActivityDef def = activityDefStore
                    .findActivityDef(handle, studyGuid, activityDto, versionDto)
                    .orElseThrow(() -> new DDPException("Could not find activity definition for " + summaryDto.getActivityCode()));

            Translation translatedName = extractTranslatedActivityName(def, preferredLangCode, studyDefaultLangCode);
            String isoLangCode = translatedName.getLanguageCode();
            String actName = translatedName.getText();

            // Look for other translations based on language of name.
            String actSecondName = extractOptionalActivityTranslation(def.getTranslatedSecondNames(), isoLangCode);
            String actTitle = extractOptionalActivityTranslation(def.getTranslatedTitles(), isoLangCode);
            String actSubtitle = extractOptionalActivityTranslation(def.getTranslatedSubtitles(), isoLangCode);
            String actDescription = extractOptionalActivityTranslation(def.getTranslatedDescriptions(), isoLangCode);
            String actSummary = extractOptionalActivitySummary(def.getTranslatedSummaries(), summaryDto.getStatusType(), isoLangCode);

            // If there's no title, leave it empty.
            actTitle = (actTitle == null ? "" : actTitle);

            String activityTypeCode = def.getActivityType().name();
            String formTypeCode = def.getFormType().name();
            String statusTypeCode = summaryDto.getStatusType().name();
            String iconBase64;
            try {
                Blob iconBlob = def.isExcludeStatusIconFromDisplay() ? null
                        : formTypeAndStatusTypeToIcon.get(formTypeCode + "-" + statusTypeCode);
                iconBase64 = iconBlob == null ? null
                        : Base64.getEncoder().encodeToString(iconBlob.getBytes(1, (int) iconBlob.length()));
            } catch (SQLException e) {
                throw new DDPException("Error while generating status icon", e);
            }

            boolean isReadonly = ActivityInstanceUtil.isReadonly(
                    def.getEditTimeoutSec(),
                    summaryDto.getCreatedAtMillis(),
                    statusTypeCode,
                    def.isWriteOnce(),
                    summaryDto.getReadonly());

            var summary = new ActivityInstanceSummary(
                    def.getActivityCode(),
                    summaryDto.getId(),
                    summaryDto.getGuid(),
                    actName,
                    actSecondName,
                    actTitle,
                    actSubtitle,
                    actDescription,
                    actSummary,
                    activityTypeCode,
                    formTypeCode,
                    statusTypeCode,
                    iconBase64,
                    isReadonly,
                    isoLangCode,
                    def.isExcludeFromDisplay(),
                    summaryDto.isHidden(),
                    summaryDto.getCreatedAtMillis(),
                    def.isFollowup(),
                    versionDto.getVersionTag(),
                    versionDto.getId(),
                    versionDto.getRevStart());
            summaries.add(summary);
        }

        return summaries;
    }

    private Translation extractTranslatedActivityName(FormActivityDef def, String preferredLangCode, String studyDefaultLangCode) {
        Translation preferredName = null;
        Translation studyDefaultName = null;
        for (var name : def.getTranslatedNames()) {
            if (name.getLanguageCode().equals(studyDefaultLangCode)) {
                studyDefaultName = name;
            }
            if (name.getLanguageCode().equals(preferredLangCode)) {
                preferredName = name;
            }
        }
        if (preferredName == null && studyDefaultName == null) {
            throw new DDPException("Could not find name for activity " + def.getActivityCode());
        }
        return preferredName != null ? preferredName : studyDefaultName;
    }

    private String extractOptionalActivityTranslation(List<Translation> translations, String isoLangCode) {
        return translations.stream()
                .filter(trans -> trans.getLanguageCode().equals(isoLangCode))
                .map(Translation::getText)
                .findFirst()
                .orElse(null);
    }

    private String extractOptionalActivitySummary(List<SummaryTranslation> summaryTranslations,
                                                  InstanceStatusType statusType,
                                                  String isoLangCode) {
        return summaryTranslations.stream()
                .filter(trans -> trans.getStatusType().equals(statusType) && trans.getLanguageCode().equals(isoLangCode))
                .map(Translation::getText)
                .findFirst()
                .orElse(null);
    }

    /**
     * Compute and set the instance numbers, as well as previousInstanceGuid, for the given list of activity instance
     * summaries. This is done in-place by mutating the given summary objects.
     *
     * @param summaries the activity instance summaries
     */
    public void performInstanceNumbering(List<ActivityInstanceSummary> summaries) {
        if (summaries.isEmpty()) {
            return;
        }

        // Group summaries by activity code
        Map<String, List<ActivityInstanceSummary>> summariesByActivityCode = summaries.stream()
                .collect(Collectors.groupingBy(ActivityInstanceSummary::getActivityCode, Collectors.toList()));
        for (List<ActivityInstanceSummary> summariesWithTheSameCode : summariesByActivityCode.values()) {
            // No need to bother with no items
            if (summariesWithTheSameCode.isEmpty()) {
                continue;
            }

            // Sort items by date
            summariesWithTheSameCode.sort(Comparator.comparing(ActivityInstanceSummary::getCreatedAt));

            // Number items within each group.
            int counter = 1;
            String previousInstanceGuid = null;
            for (var summary : summariesWithTheSameCode) {
                if (previousInstanceGuid != null) {
                    summary.setPreviousInstanceGuid(previousInstanceGuid);
                }
                summary.setInstanceNumber(counter);
                previousInstanceGuid = summary.getActivityInstanceGuid();
                counter++;
            }
        }
    }

    /**
     * Iterate through list of activity summaries and count up how many questions are answered.
     *
     * @param handle    the database handle
     * @param userGuid  the user guid
     * @param studyGuid the study guid
     * @param summaries the list of activity summaries
     */
    public void countQuestionsAndAnswers(Handle handle, String userGuid, String operatorGuid, String studyGuid,
                                         List<ActivityInstanceSummary> summaries) {
        if (summaries.isEmpty()) {
            return;
        }

        ActivityDefStore activityDefStore = ActivityDefStore.getInstance();

        Set<String> instanceGuids = summaries.stream()
                .map(ActivityInstanceSummary::getActivityInstanceGuid)
                .collect(Collectors.toSet());
        Map<String, FormResponse> instanceResponses;
        try (Stream<FormResponse> responseStream = handle
                .attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                .findFormResponsesWithAnswersByInstanceGuids(instanceGuids)) {
            instanceResponses = responseStream.collect(Collectors.toMap(ActivityResponse::getGuid, Function.identity()));
        }

        for (var summary : summaries) {
            if (ActivityType.valueOf(summary.getActivityType()) == ActivityType.FORMS) {
                String activityCode = summary.getActivityCode();
                String versionTag = summary.getVersionTag();
                long versionId = summary.getVersionId();
                long revisionStart = summary.getRevisionStart();

                FormActivityDef formActivityDef = activityDefStore.getActivityDef(studyGuid, activityCode, versionTag);
                if (formActivityDef == null) {
                    ActivityDto activityDto = handle.attach(JdbiActivity.class)
                            .findActivityByStudyGuidAndCode(studyGuid, activityCode).get();
                    formActivityDef = handle.attach(FormActivityDao.class)
                            .findDefByDtoAndVersion(activityDto, versionTag, versionId, revisionStart);
                    activityDefStore.setActivityDef(studyGuid, activityCode, versionTag, formActivityDef);
                }

                Pair<Integer, Integer> questionAndAnswerCounts = activityDefStore.countQuestionsAndAnswers(
                        handle, userGuid, operatorGuid, formActivityDef, summary.getActivityInstanceGuid(), instanceResponses);

                summary.setNumQuestions(questionAndAnswerCounts.getLeft());
                summary.setNumQuestionsAnswered(questionAndAnswerCounts.getRight());
            }
        }
    }

    /**
     * Iterate through activity instance summaries and render the naming details and summary texts, as necessary. For
     * activity instance name, the first instance may leverage content substitutions to render in it's activity number
     * (which is 1). For activity instance with number greater than 1, if there is a "second name" then the second name
     * will be used to render the name, otherwise " #N" will be appended to the original name (where N is the instance
     * number).
     *
     * @param handle    the database handle
     * @param userId    the user who owns the activity instances
     * @param summaries the list of summaries
     */
    public void renderInstanceSummaries(Handle handle, long userId, List<ActivityInstanceSummary> summaries) {
        if (summaries.isEmpty()) {
            return;
        }

        Set<Long> instanceIds = summaries.stream()
                .map(ActivityInstanceSummary::getActivityInstanceId)
                .collect(Collectors.toSet());
        var instanceDao = handle.attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class);
        Map<Long, Map<String, String>> substitutions;
        try (var substitionStream = instanceDao.bulkFindSubstitutions(instanceIds)) {
            substitutions = substitionStream
                    .collect(Collectors.toMap(wrapper -> wrapper.getActivityInstanceId(), wrapper -> wrapper.unwrap()));
        }
        var sharedSnapshot = I18nContentRenderer
                .newValueProviderBuilder(handle, userId)
                .build().getSnapshot();

        for (var summary : summaries) {
            Map<String, String> subs = substitutions.getOrDefault(summary.getActivityInstanceId(), new HashMap<>());
            var provider = new RenderValueProvider.Builder()
                    .setActivityInstanceNumber(summary.getInstanceNumber())
                    .withSnapshot(sharedSnapshot)
                    .withSnapshot(subs)
                    .build();
            Map<String, Object> context = new HashMap<>();
            context.put(I18nTemplateConstants.DDP, provider);

            // Render the name.
            final String nameText;
            if (summary.getInstanceNumber() > 1) {
                if (StringUtils.isNotBlank(summary.getActivitySecondName())) {
                    nameText = renderer.renderToString(summary.getActivitySecondName(), context);
                } else {
                    String originalName = renderer.renderToString(summary.getActivityName(), context);
                    nameText = originalName + " #" + summary.getInstanceNumber();
                }
            } else {
                nameText = renderer.renderToString(summary.getActivityName(), context);
            }
            summary.setActivityName(nameText);

            // Render the summary.
            if (StringUtils.isNotBlank(summary.getActivitySummary())) {
                String summaryText = renderer.renderToString(summary.getActivitySummary(), context);
                summary.setActivitySummary(summaryText);
            }
        }
    }
}
