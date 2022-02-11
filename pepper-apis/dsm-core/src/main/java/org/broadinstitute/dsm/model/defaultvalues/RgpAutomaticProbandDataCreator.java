package org.broadinstitute.dsm.model.defaultvalues;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.defaultvalues.BasicDefaultDataMaker;
import org.broadinstitute.dsm.model.elastic.ESActivities;
import org.broadinstitute.dsm.model.elastic.export.painless.*;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberDetails;
import org.broadinstitute.dsm.model.participant.data.ParticipantData;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.SystemUtil;

public class RgpAutomaticProbandDataCreator extends BasicDefaultDataMaker {


    public static final String RGP_FAMILY_ID = "rgp_family_id";

    @Override
    protected boolean setDefaultData() {

        List<FieldSettingsDto> fieldSettingsDtosByOptionAndInstanceId =
                FieldSettingsDao.of().getOptionAndRadioFieldSettingsByInstanceId(Integer.parseInt(instance.getDdpInstanceId()));

        return elasticSearchParticipantDto.getProfile()
                .map(esProfile -> {
                    logger.info("Got ES profile of participant: " + esProfile.getGuid());
                    Map<String, String> columnsWithDefaultOptions =
                            fieldSettings.getColumnsWithDefaultValues(fieldSettingsDtosByOptionAndInstanceId);
                    Map<String, String> columnsWithDefaultOptionsFilteredByElasticExportWorkflow =
                            fieldSettings.getColumnsWithDefaultOptionsFilteredByElasticExportWorkflow(fieldSettingsDtosByOptionAndInstanceId);
                    String participantId = StringUtils.isNotBlank(esProfile.getLegacyAltPid())
                            ? esProfile.getLegacyAltPid()
                            : esProfile.getGuid();
                    ParticipantData participantData = new ParticipantData(participantDataDao);
                    Optional<BookmarkDto> maybeFamilyIdOfBookmark = bookmarkDao.getBookmarkByInstance(RGP_FAMILY_ID);
                    Map<String, String> probandDataMap = extractProbandDefaultDataFromParticipantProfile(maybeFamilyIdOfBookmark);
                    participantData.setData(
                            participantId,
                            Integer.parseInt(instance.getDdpInstanceId()),
                            instance.getName().toUpperCase() + ParticipantData.FIELD_TYPE_PARTICIPANTS,
                            probandDataMap
                    );
                    participantData.addDefaultOptionsValueToData(columnsWithDefaultOptions);
                    participantData.insertParticipantData(SystemUtil.SYSTEM);

                    columnsWithDefaultOptionsFilteredByElasticExportWorkflow.forEach((col, val) ->
                            ElasticSearchUtil.writeWorkflow(WorkflowForES.createInstanceWithStudySpecificData(instance, participantId, col, val,
                                    new WorkflowForES.StudySpecificData(probandDataMap.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                                            probandDataMap.get(FamilyMemberConstants.FIRSTNAME), probandDataMap.get(FamilyMemberConstants.LASTNAME))), false)
                    );
                    maybeFamilyIdOfBookmark.ifPresent(familyIdBookmarkDto -> {
                        insertFamilyIdToDsmES(instance.getParticipantIndexES(), participantId, familyIdBookmarkDto.getValue());
                        familyIdBookmarkDto.setValue(familyIdBookmarkDto.getValue() + 1);
                        bookmarkDao.updateBookmarkValueByBookmarkId(familyIdBookmarkDto.getBookmarkId(), familyIdBookmarkDto.getValue());
                    });
                    logger.info("Automatic proband data for participant with id: " + participantId + " has been created");
                    return true;
                })
                .orElseGet(() -> {
                    logger.info("Participant does not have ES profile yet...");
                    return false;
                });
    }

    private Map<String, String> extractProbandDefaultDataFromParticipantProfile(Optional<BookmarkDto> maybeBookmark) {
        List<ESActivities> participantActivities = elasticSearchParticipantDto.getActivities();
        String mobilePhone = getPhoneNumberFromActivities(participantActivities);
        return elasticSearchParticipantDto.getProfile()
            .map(esProfile -> {
                logger.info("Starting extracting data from participant: " + esProfile.getGuid() + " ES profile");
                String firstName = esProfile.getFirstName();
                String lastName = esProfile.getLastName();
                long familyId = maybeBookmark
                        .map(bookmarkDto -> bookmarkDto.getValue())
                        .orElseThrow();
                String collaboratorParticipantId = instance.getName().toUpperCase() + "_" + familyId + "_" + FamilyMemberConstants.PROBAND_RELATIONSHIP_ID;
                String memberType = FamilyMemberConstants.MEMBER_TYPE_SELF;
                String email = esProfile.getEmail();
                FamilyMemberDetails probandMemberDetails =
                        new FamilyMemberDetails(firstName, lastName, memberType, familyId, collaboratorParticipantId);
                probandMemberDetails.setMobilePhone(mobilePhone);
                probandMemberDetails.setEmail(email);
                probandMemberDetails.setApplicant(true);
                logger.info("Profile data extracted from participant: " + esProfile.getGuid() + " ES profile");
                return probandMemberDetails.toMap();
            })
            .orElse(Map.of());
    }

    private String getPhoneNumberFromActivities(List<ESActivities> activities) {
        Optional<ESActivities> maybeEnrollmentActivity = activities.stream()
                .filter(activity -> DDPActivityConstants.ACTIVITY_ENROLLMENT.equals(activity.getActivityCode()))
                .findFirst();
        return (String) maybeEnrollmentActivity.map(enrollment -> {
            List<Map<String, Object>> questionsAnswers = enrollment.getQuestionsAnswers();
            Optional<Map<String, Object>> maybePhoneQuestionAnswer = questionsAnswers.stream()
                    .filter(q -> DDPActivityConstants.ENROLLMENT_ACTIVITY_PHONE.equals(q.get(DDPActivityConstants.DDP_ACTIVITY_STABLE_ID)))
                    .findFirst();
            return maybePhoneQuestionAnswer
                    .map(answer -> answer.get(DDPActivityConstants.ACTIVITY_QUESTION_ANSWER))
                    .orElse("");
            })
            .orElse("");
    }

    void insertFamilyIdToDsmES(@NonNull String esIndex, @NonNull String participantId, @NonNull long familyId) {
        try {
            Map<String, Object> esObjectMap = ElasticSearchUtil.getObjectsMap(esIndex, participantId, ESObjectConstants.DSM);
            Map<String, Object> esDsmObjectMap = (Map<String, Object>) esObjectMap.get(ESObjectConstants.DSM);
            esDsmObjectMap.put(ESObjectConstants.FAMILY_ID, familyId);
            ElasticSearchUtil.updateRequest(participantId, esIndex, esObjectMap);
            logger.info("Family id for participant" + participantId + "has successfully added to ES");
        } catch (Exception e) {
            logger.error("Could not insert family id for participant: " + participantId, e);
        }
    }

}
