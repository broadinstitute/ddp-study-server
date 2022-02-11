package org.broadinstitute.dsm.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class PermalinkRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(PermalinkRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        //        String participant = request.params(RequestParameter.PARTICIPANTID);
        //        String medicalRecord = request.params(RequestParameter.MEDICALRECORDID);
        //
        //        if (StringUtils.isNotBlank(participant) && StringUtils.isBlank(medicalRecord)) {
        //            QueryParamsMap queryParams = request.queryMap();
        //            String realm;
        //            if (queryParams.value(RoutePath.REALM) != null) {
        //                realm = queryParams.get(RoutePath.REALM).value();
        //            }
        //            else{
        //                throw new RuntimeException("No realm query param was sent");
        //            }
        //            logger.info("Checking for participant list from " + realm);
        //            return Participant.getParticipant(ddpParticipantId, realm);
        //        }
        //        else if (StringUtils.isNotBlank(ddpParticipantId) && StringUtils.isNotBlank(medicalRecord)) {
        //            return getMedicalRecord(ddpParticipantId, medicalRecord);
        //        }
        throw new RuntimeException("Error missing participantID");
    }

    /**
     * Get medical record
     * for the given participant and institution
     *
     * @return MedicalRecord
     * @throws Exception
     */
//    public MedicalRecord getMedicalRecord(@NonNull String ddpParticipantId, @NonNull String medicalRecordId) {
//        logger.info("Checking for medical record of participant " + ddpParticipantId + " and medicalRecordId " + medicalRecordId);
//        MedicalRecord medicalRecord = null;
//        DDPInstance instance = null;
//        String ddpInstitutionId = null;
//        SimpleResult results = inTransaction((conn) -> {
//            SimpleResult dbVals = new SimpleResult();
//            try (PreparedStatement stmt = conn.prepareStatement(MedicalRecord.SQL_SELECT_MEDICAL_RECORD_INFORMATION + QueryExtension.BY_INSTITUTION_ID)) {
//                stmt.setString(1, ddpParticipantId);
//                stmt.setString(2, medicalRecordId);
//                try (ResultSet rs = stmt.executeQuery()) {
//                    if (rs.next()) {
//                        String notificationRecipient = rs.getString(DBConstants.NOTIFICATION_RECIPIENT);
//                        List<String> recipients = null;
//                        if (StringUtils.isNotBlank(notificationRecipient)) {
//                            notificationRecipient = notificationRecipient.replaceAll("\\s", "");
//                            recipients = Arrays.asList(notificationRecipient.split(","));
//                        }
//                        dbVals.resultValue = new MedicalRecordAndInstance(
//                                new DDPInstance(
//                                        rs.getString(DBConstants.DDP_INSTANCE_ID),
//                                        rs.getString(DBConstants.INSTANCE_NAME),
//                                        rs.getString(DBConstants.BASE_URL), null, false,
//                                        rs.getInt(DBConstants.DAYS_MR_ATTENTION_NEEDED),
//                                        rs.getInt(DBConstants.DAYS_TISSUE_ATTENTION_NEEDED),
//                                        rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN),
//                                        recipients, rs.getBoolean(DBConstants.MIGRATED_DDP),
//                                        rs.getString(DBConstants.BILLING_REFERENCE),
//                                        rs.getString(DBConstants.ES_PARTICIPANT_INDEX)),
//                                MedicalRecord.getMedicalRecord(rs),
//                                rs.getString(DBConstants.DDP_INSTITUTION_ID),
//                                rs.getString(DBConstants.DDP_PARTICIPANT_ID));
//                    }
//                }
//            }
//            catch (SQLException ex) {
//                dbVals.resultException = ex;
//            }
//            return dbVals;
//        });
//
//        if (results.resultException != null) {
//            throw new RuntimeException("Error getting list of medicalRecords " + medicalRecordId + " of participant " + ddpParticipantId, results.resultException);
//        }
//        else {
//            MedicalRecordAndInstance result = (MedicalRecordAndInstance) results.resultValue;
//            medicalRecord = result.getMedicalRecord();
//            instance = result.getDdpInstance();
//            ddpInstitutionId = result.getDdpInstanceId();
//        }
//
//        if (medicalRecord != null && instance != null && StringUtils.isNotBlank(ddpInstitutionId) && StringUtils.isNotBlank(ddpParticipantId)) {
//            if (StringUtils.isNotBlank(instance.getName()) && StringUtils.isNotBlank(instance.getBaseUrl())) {
//                String dsmRequest = instance.getBaseUrl() + RoutePath.DDP_INSTITUTION_PATH.replace(RequestParameter.PARTICIPANTID, ddpParticipantId);
//                try {
//                    MedicalInfo medicalInfo = DDPRequestUtil.getResponseObject(MedicalInfo.class, dsmRequest, instance.getName(), instance.isHasAuth0Token());
//                    if (medicalInfo != null) {
//                        for (InstitutionDetail institutionDetail : medicalInfo.getInstitutions()) {
//                            if (ddpInstitutionId.equals(institutionDetail.getId())) {
//                                medicalRecord.setTypeDDP(institutionDetail.getType());
//                                medicalRecord.setInstitutionDDP(institutionDetail.getInstitution());
//                                medicalRecord.setNameDDP(institutionDetail.getPhysician());
//                                medicalRecord.setStreetAddressDDP(institutionDetail.getStreetAddress());
//                                medicalRecord.setCityDDP(institutionDetail.getCity());
//                                medicalRecord.setStateDDP(institutionDetail.getState());
//                                medicalRecord.setDobDDP(medicalInfo.getDob());
//                                medicalRecord.setDateOfDiagnosisDDP(medicalInfo.getDateOfDiagnosis());
//                                medicalRecord.setDeleted(false);
//                                break;
//                            }
//                        }
//                    }
//                }
//                catch (Exception e) {
//                    throw new RuntimeException("Couldn't get participants and institutions for instance " + instance.getName(), e);
//                }
//            }
//            else {
//                throw new RuntimeException("DDPInstance information missing");
//            }
//        }
//        else {
//            throw new RuntimeException("Information missing");
//        }
//        return medicalRecord;
//    }
//
//    public class MedicalRecordAndInstance {
//        private DDPInstance ddpInstance;
//        private MedicalRecord medicalRecord;
//        private String ddpInstanceId;
//        private String ddpParticipantId;
//
//        public MedicalRecordAndInstance(DDPInstance ddpInstance, MedicalRecord medicalRecord, String ddpInstanceId, String ddpParticipantId) {
//            this.ddpInstance = ddpInstance;
//            this.medicalRecord = medicalRecord;
//            this.ddpInstanceId = ddpInstanceId;
//            this.ddpParticipantId = ddpParticipantId;
//        }
//
//        public DDPInstance getDdpInstance() {
//            return ddpInstance;
//        }
//
//        public MedicalRecord getMedicalRecord() {
//            return medicalRecord;
//        }
//
//        public String getDdpInstanceId() {
//            return ddpInstanceId;
//        }
//
//        public String getDdpParticipantId() {
//            return ddpParticipantId;
//        }
//    }
}
