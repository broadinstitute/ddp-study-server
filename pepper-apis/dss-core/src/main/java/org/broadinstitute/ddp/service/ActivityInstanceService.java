package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.content.RendererInitialContextCreator.RenderContextSource.FORM_RESPONSE_AND_ACTIVITY_DEF;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderParams.createParams;
import static org.broadinstitute.ddp.util.TranslationUtil.extractOptionalActivitySummary;
import static org.broadinstitute.ddp.util.TranslationUtil.extractOptionalActivityTranslation;
import static org.broadinstitute.ddp.util.TranslationUtil.extractTranslatedActivityName;

import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.constants.LanguageConstants;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.StudyLanguageCachedDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceSummaryDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.instance.NestedActivityBlock;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.study.StudyLanguage;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderFactory;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.jdbi.v3.core.Handle;

import static org.broadinstitute.ddp.db.dao.ActivityInstanceDao.SubstitutionsWrapper;

@Slf4j
@AllArgsConstructor
public class ActivityInstanceService {
    private final ActivityInstanceDao actInstanceDao;
    private final PexInterpreter interpreter;
    private final I18nContentRenderer renderer;

    /**
     * Iterate through the instance and load activity instance summaries for each nested activity block.
     *
     * @param handle       the database handle
     * @param instance     the parent instance
     * @param studyGuid    the study guid
     * @param userGuid     the participant guid
     * @param operatorGuid the operator guid
     * @param isoLangCode  the preferred language iso code
     */
    public void loadNestedInstanceSummaries(Handle handle, FormInstance instance, String studyGuid,
                                            String userGuid, String operatorGuid, String isoLangCode) {
        ActivityDefStore activityStore = ActivityDefStore.getInstance();

        List<ActivityInstanceSummaryDto> summaryDtos = null;
        String studyDefaultLangCode = null;

        for (FormSection section : instance.getAllSections()) {
            for (FormBlock block : section.getBlocks()) {
                if (block.getBlockType() != BlockType.ACTIVITY) {
                    continue;
                }

                // There might not be any nested activity blocks, so we do lazy-loading of data here.
                if (summaryDtos == null) {
                    summaryDtos = handle
                            .attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                            .findNestedSortedInstanceSummaries(userGuid, studyGuid, instance.getInstanceId());
                }
                if (studyDefaultLangCode == null) {
                    studyDefaultLangCode = new StudyLanguageCachedDao(handle)
                            .findLanguages(studyGuid)
                            .stream()
                            .filter(StudyLanguage::isDefault)
                            .map(StudyLanguage::getLanguageCode)
                            .findFirst()
                            .orElse(LanguageConstants.EN_LANGUAGE_CODE);
                }

                NestedActivityBlock nestedActBlock = (NestedActivityBlock) block;
                List<ActivityInstanceSummaryDto> nestedSummaryDtos = summaryDtos.stream()
                        .filter(summary -> nestedActBlock.getActivityCode().equals(summary.getActivityCode()))
                        .collect(Collectors.toList());
                if (nestedSummaryDtos.isEmpty()) {
                    continue;
                }

                performInstanceNumbering(nestedSummaryDtos);
                List<ActivityInstanceSummary> nestedSummaries = buildTranslatedInstanceSummaries(
                        handle, activityStore, false, nestedSummaryDtos,
                        studyGuid, isoLangCode, studyDefaultLangCode);
                nestedSummaries = nestedSummaries.stream()
                        .filter(summary -> !summary.isHidden())
                        .collect(Collectors.toList());
                Map<String, FormResponse> nestedResponses = countQuestionsAndAnswers(
                        handle, userGuid, operatorGuid, studyGuid, nestedSummaries);
                renderInstanceSummaries(handle, instance.getParticipantUserId(),
                        operatorGuid, studyGuid, nestedSummaries, nestedResponses);

                nestedActBlock.addInstanceSummaries(nestedSummaries);
            }
        }
    }

    /**
     * Find list of activity instance summaries for user and study. List will be sorted by activity display order
     * (ascending) and instance creation time (descending). Child nested activity instances will not be returned.
     *
     * <p>The instance summaries will be translated to the user's preferred language, or falls back to study's default
     * language if preferred language is not available. If study doesn't have a default language, English will be used
     * as the fallback.
     *
     * <p>Instance numbering will be performed, but other computed properties such as question/answer count, or rendered
     * strings will not be set. Caller is responsible for performing the computations and setting them properly.
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

        ActivityDefStore activityStore = ActivityDefStore.getInstance();
        performInstanceNumbering(summaryDtos);
        return buildTranslatedInstanceSummaries(
                handle, activityStore, true, summaryDtos,
                studyGuid, preferredLangCode, studyDefaultLangCode);
    }

    /**
     * Find a single activity instance summary. Instance numbering will be performed and instance summary will be
     * translated to the user's preferred language, or fallback to an appropriate language. Caller should set other
     * computed properties such as question/answer count, etc.
     *
     * @param handle            the database handle
     * @param userGuid          the user guid
     * @param studyGuid         the study guid
     * @param activityCode      the activity identifier
     * @param instanceGuid      the instance guid
     * @param preferredLangCode the desired language
     * @return an activity instance summary
     */
    public Optional<ActivityInstanceSummary> findTranslatedInstanceSummary(Handle handle,
                                                                           String userGuid,
                                                                           String studyGuid,
                                                                           String activityCode,
                                                                           String instanceGuid,
                                                                           String preferredLangCode) {
        // Find all instance summaries of the same activity so we can figure out numbering.
        List<ActivityInstanceSummaryDto> summaryDtos = handle
                .attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                .findSortedInstanceSummaries(userGuid, studyGuid, activityCode);
        if (summaryDtos.isEmpty()) {
            return Optional.empty();
        }

        performInstanceNumbering(summaryDtos);
        summaryDtos = summaryDtos.stream()
                .filter(summary -> summary.getGuid().equals(instanceGuid))
                .collect(Collectors.toList());
        if (summaryDtos.isEmpty()) {
            return Optional.empty();
        }

        String studyDefaultLangCode = new StudyLanguageCachedDao(handle)
                .findLanguages(studyGuid)
                .stream()
                .filter(StudyLanguage::isDefault)
                .map(StudyLanguage::getLanguageCode)
                .findFirst()
                .orElse(LanguageConstants.EN_LANGUAGE_CODE);

        ActivityDefStore activityStore = ActivityDefStore.getInstance();
        List<ActivityInstanceSummary> summaries = buildTranslatedInstanceSummaries(
                handle, activityStore, true, summaryDtos,
                studyGuid, preferredLangCode, studyDefaultLangCode);

        return Optional.of(summaries.get(0));
    }

    /**
     * Find a activity instance summaries. Instance numbering will be performed and instance summary will be
     * translated to the user's preferred language, or fallback to an appropriate language. Caller should set other
     * computed properties such as question/answer count, etc.
     *
     * @param handle            the database handle
     * @param userGuid          the user guid
     * @param studyGuid         the study guid
     * @param activityCodes     the activity identifiers
     * @param preferredLangCode the desired language
     * @return an activity instance summary
     */
    public List<ActivityInstanceSummary> findTranslatedInstanceSummaries(Handle handle,
                                                                       String userGuid,
                                                                       String studyGuid,
                                                                       Set<String> activityCodes,
                                                                       String preferredLangCode) {
        // Find all instance summaries of the same activity so we can figure out numbering.
        List<ActivityInstanceSummaryDto> summaryDtos = handle
                .attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                .findSortedInstanceSummaries(userGuid, studyGuid, activityCodes);
        if (summaryDtos.isEmpty()) {
            return Collections.emptyList();
        }

        performInstanceNumbering(summaryDtos);
        String studyDefaultLangCode = new StudyLanguageCachedDao(handle)
                .findLanguages(studyGuid)
                .stream()
                .filter(StudyLanguage::isDefault)
                .map(StudyLanguage::getLanguageCode)
                .findFirst()
                .orElse(LanguageConstants.EN_LANGUAGE_CODE);

        ActivityDefStore activityStore = ActivityDefStore.getInstance();

        return buildTranslatedInstanceSummaries(
                handle, activityStore, true, summaryDtos,
                studyGuid, preferredLangCode, studyDefaultLangCode);
    }

    // Compute and set the instance numbers, as well as previousInstanceGuid, for the given list of activity instance
    // summaries. This is done in-place by mutating the given summary objects.
    private void performInstanceNumbering(List<ActivityInstanceSummaryDto> summaryDtos) {
        Map<String, List<ActivityInstanceSummaryDto>> summariesByActivityCode = summaryDtos.stream()
                .collect(Collectors.groupingBy(
                        ActivityInstanceSummaryDto::getActivityCode,
                        Collectors.toList()));
        for (List<ActivityInstanceSummaryDto> summariesWithTheSameCode : summariesByActivityCode.values()) {
            if (summariesWithTheSameCode.isEmpty()) {
                continue;
            }

            // Ensure items are sorted in ascending order.
            summariesWithTheSameCode.sort(Comparator.comparing(ActivityInstanceSummaryDto::getCreatedAtMillis));

            // Number items within each group.
            int counter = 1;
            String previousInstanceGuid = null;
            for (var summary : summariesWithTheSameCode) {
                if (previousInstanceGuid != null) {
                    summary.setPreviousInstanceGuid(previousInstanceGuid);
                }
                summary.setInstanceNumber(counter);
                previousInstanceGuid = summary.getGuid();
                counter++;
            }
        }
    }

    // Does the heavy-lifting of translating activity properties and merging into a full activity instance summary.
    // Activity instance summaries should have been numbered beforehand.
    private List<ActivityInstanceSummary> buildTranslatedInstanceSummaries(Handle handle,
                                                                           ActivityDefStore activityStore,
                                                                           boolean renderIcon,
                                                                           List<ActivityInstanceSummaryDto> summaryDtos,
                                                                           String studyGuid,
                                                                           String preferredLangCode,
                                                                           String studyDefaultLangCode) {
        List<ActivityInstanceSummary> summaries = new ArrayList<>();
        Map<String, Blob> formTypeAndStatusTypeToIcon =
                renderIcon ? activityStore.findActivityStatusIcons(handle, studyGuid) : null;

        for (var summaryDto : summaryDtos) {
            ActivityDto activityDto = activityStore
                    .findActivityDto(handle, summaryDto.getActivityId())
                    .orElseThrow(() -> new DDPException("Could not find activity dto for " + summaryDto.getActivityCode()));
            ActivityVersionDto versionDto = activityStore
                    .findVersionDto(handle, activityDto.getActivityId(), summaryDto.getCreatedAtMillis())
                    .orElseThrow(() -> new DDPException("Could not find activity version for instance" + summaryDto.getGuid()));
            FormActivityDef def = activityStore
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
            String iconBase64 = null;
            if (renderIcon) {
                try {
                    Blob iconBlob = def.isExcludeStatusIconFromDisplay() ? null
                            : formTypeAndStatusTypeToIcon.get(formTypeCode + "-" + statusTypeCode);
                    iconBase64 = iconBlob == null ? null
                            : Base64.getEncoder().encodeToString(iconBlob.getBytes(1, (int) iconBlob.length()));
                } catch (SQLException e) {
                    throw new DDPException("Error while generating status icon", e);
                }
            }

            boolean isReadonly = ActivityInstanceUtil.isReadonly(
                    def.getEditTimeoutSec(),
                    summaryDto.getCreatedAtMillis(),
                    statusTypeCode,
                    def.isWriteOnce(),
                    summaryDto.getIsReadonly());

            boolean isFirstInstance = StringUtils.isBlank(summaryDto.getPreviousInstanceGuid());
            boolean canDelete = ActivityInstanceUtil.computeCanDelete(
                    def.canDeleteInstances(),
                    def.getCanDeleteFirstInstance(),
                    isFirstInstance);

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
                    canDelete,
                    def.isFollowup(),
                    versionDto.getVersionTag(),
                    versionDto.getId(),
                    versionDto.getRevStart());
            summary.setInstanceNumber(summaryDto.getInstanceNumber());
            summary.setPreviousInstanceGuid(summaryDto.getPreviousInstanceGuid());
            summary.setParentInstanceGuid(summaryDto.getParentInstanceGuid());
            summaries.add(summary);
        }

        return summaries;
    }

    /**
     * Iterate through list of activity summaries and count up how many questions are answered.
     *
     * @param handle    the database handle
     * @param userGuid  the user guid
     * @param studyGuid the study guid
     * @param summaries the list of activity summaries
     * @return mapping of instance guid to object containing answer responses used for counting
     */
    public Map<String, FormResponse> countQuestionsAndAnswers(Handle handle, String userGuid, String operatorGuid, String studyGuid,
                                                              List<ActivityInstanceSummary> summaries) {
        if (summaries.isEmpty()) {
            return new HashMap<>();
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
                FormActivityDef formActivityDef = getDefinitionForSummary(handle, activityDefStore, studyGuid, summary);

                Pair<Integer, Integer> questionAndAnswerCounts = activityDefStore.countQuestionsAndAnswers(
                        handle, userGuid, operatorGuid, formActivityDef, summary.getActivityInstanceGuid(), instanceResponses);

                summary.setNumQuestions(questionAndAnswerCounts.getLeft());
                summary.setNumQuestionsAnswered(questionAndAnswerCounts.getRight());
            }
        }

        return instanceResponses;
    }

    private FormActivityDef getDefinitionForSummary(Handle handle, ActivityDefStore activityDefStore,
                                                    String studyGuid, ActivityInstanceSummary summary) {
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

        return formActivityDef;
    }

    /**
     * Iterate through activity instance summaries and render the naming details and summary texts, as necessary. For
     * activity instance name, the first instance may leverage content substitutions to render in it's activity number
     * (which is 1). For activity instance with number greater than 1, if there is a "second name" then the second name
     * will be used to render the name, otherwise " #N" will be appended to the original name (where N is the instance
     * number).
     *
     * @param handle            the database handle
     * @param userId            the user who owns the activity instances
     * @param studyGuid         the study guid
     * @param summaries         the list of summaries
     * @param instanceResponses the mapping of instance guid to answer response objects
     */
    public void renderInstanceSummaries(Handle handle, long userId, String operatorGuid, String studyGuid,
                                        List<ActivityInstanceSummary> summaries,
                                        Map<String, FormResponse> instanceResponses) {
        if (summaries.isEmpty()) {
            return;
        }

        Set<Long> instanceIds = summaries.stream()
                .map(ActivityInstanceSummary::getActivityInstanceId)
                .collect(Collectors.toSet());
        var instanceDao = handle.attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class);
        Map<Long, Map<String, String>> substitutions;
        try (var substitutionStream = instanceDao.bulkFindSubstitutions(instanceIds)) {
            substitutions = substitutionStream
                    .collect(Collectors.toMap(SubstitutionsWrapper::getActivityInstanceId, SubstitutionsWrapper::unwrap));
        }

        ActivityDefStore activityDefStore = ActivityDefStore.getInstance();

        for (var summary : summaries) {
            var activityContext = I18nContentRenderer
                    .newValueProviderBuilder(handle, summary.getActivityInstanceId(), userId, operatorGuid, studyGuid)
                    .build()
                    .getSnapshot();

            FormResponse formResponse = instanceResponses.get(summary.getActivityInstanceGuid());
            FormActivityDef formActivityDef = formResponse == null ? null
                    : getDefinitionForSummary(handle, activityDefStore, studyGuid, summary);

            Map<String, String> subs = substitutions.getOrDefault(summary.getActivityInstanceId(), new HashMap<>());
            var provider = new RenderValueProvider.Builder()
                    .setActivityInstanceNumber(summary.getInstanceNumber())
                    .withFormResponse(formResponse, formActivityDef, summary.getIsoLanguageCode())
                    .withSnapshot(activityContext)
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

            // Render other properties.
            if (StringUtils.isNotBlank(summary.getActivityTitle())) {
                summary.setActivityTitle(renderer.renderToString(summary.getActivityTitle(), context));
            }
            if (StringUtils.isNotBlank(summary.getActivitySubtitle())) {
                summary.setActivitySubtitle(renderer.renderToString(summary.getActivitySubtitle(), context));
            }
            if (StringUtils.isNotBlank(summary.getActivityDescription())) {
                summary.setActivityDescription(renderer.renderToString(summary.getActivityDescription(), context));
            }
            if (StringUtils.isNotBlank(summary.getActivitySummary())) {
                summary.setActivitySummary(renderer.renderToString(summary.getActivitySummary(), context));
            }
        }
    }

    /**
     * Delete the provided activity instance and its associated answer data. Caller is responsible for checking if
     * instance is eligible to be deleted.
     *
     * @param handle      the database handle
     * @param instanceDto the instance
     */
    public void deleteInstance(Handle handle, ActivityInstanceDto instanceDto) {
        // Note: deal with potential child instances here when we support deleting top-level parent instances.
        var instanceDao = handle.attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class);
        int numDeleted = instanceDao.deleteAllByIds(Set.of(instanceDto.getId()));
        DBUtils.checkDelete(1, numDeleted);
    }

    /**
     * Build {@link ActivityInstance} from data cached stored in {@link ActivityDefStore}.
     * Some of data (answers, rule messages) are queried from DB.
     * This method provide full building and rendering of {@link FormInstance}:
     * <pre>
     * - create form;
     * - add children;
     * - render form title/subtitle;
     * - render content;
     * - set display numbers;
     * - update block statuses.
     * </pre>
     */
    public Optional<ActivityInstance> buildInstanceFromDefinition(
            Handle handle,
            String userGuid,
            String operatorGuid,
            String studyGuid,
            String instanceGuid,
            ContentStyle style,
            String isoLangCode) {

        var context = AIBuilderFactory.createAIBuilder(handle,
                createParams(userGuid, studyGuid, instanceGuid)
                        .setReadPreviousInstanceId(true)
                        .setOperatorGuid(operatorGuid)
                        .setIsoLangCode(isoLangCode)
                        .setStyle(style))
                .checkParams()
                    .readFormInstanceData()
                    .readActivityDef()
                    .createRendererContext(FORM_RESPONSE_AND_ACTIVITY_DEF)
                .startBuild()
                    .buildFormInstance()
                    .buildFormChildren()
                    .renderFormTitles()
                    .renderContent()
                    .setDisplayNumbers()
                    .updateBlockStatuses()
                    .populateSnapshottedAddress()
                .endBuild()
                    .getContext();

        if (context.getFailedStep() != null) {
            log.warn("ActivityInstance build failed: {}, step={}", context.getFailedMessage(), context.getFailedStep());
        }
        return Optional.ofNullable(context.getFormInstance());
    }

    /**
     * Build {@link ActivityInstance} from data cached stored in {@link ActivityDefStore}.
     * Some of data (answers, rule messages) are queried from DB.
     * This method provide partial building and rendering of {@link FormInstance}, plus it passes 'instanceSummary'.<br>
     * The following building steps executed:
     * <pre>
     * - create form;
     * - add children;
     * - render form title/subtitle;
     * - update block statuses.
     * </pre>
     */
    public Optional<ActivityInstance> buildInstanceFromDefinition(
            Handle handle,
            String userGuid,
            String operatorGuid,
            String studyGuid,
            String instanceGuid,
            String isoLangCode,
            UserActivityInstanceSummary instanceSummary) {

        var context = AIBuilderFactory.createAIBuilder(handle,
                createParams(userGuid, studyGuid, instanceGuid)
                        .setReadPreviousInstanceId(true)
                        .setOperatorGuid(operatorGuid)
                        .setIsoLangCode(isoLangCode)
                        .setInstanceSummary(instanceSummary)
                        .setDisableTemplatesRendering(true))
                .checkParams()
                    .readFormInstanceData()
                    .readActivityDef()
                    .createRendererContext(FORM_RESPONSE_AND_ACTIVITY_DEF)
                .startBuild()
                    .buildFormInstance()
                    .buildFormChildren()
                    .renderFormTitles()
                    .updateBlockStatuses()
                    .populateSnapshottedAddress()
                .endBuild()
                    .getContext();

        if (context.getFailedStep() != null) {
            log.warn("ActivityInstance build failed: {}, step={}", context.getFailedMessage(), context.getFailedStep());
        }
        return Optional.ofNullable(context.getFormInstance());
    }
}
