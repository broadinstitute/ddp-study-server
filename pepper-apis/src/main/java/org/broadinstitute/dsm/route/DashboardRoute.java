package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.db.KitType;
import org.broadinstitute.dsm.model.*;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.*;
import org.broadinstitute.dsm.util.AbstractionUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.jruby.embed.ScriptingContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class DashboardRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DashboardRoute.class);

    private static final String SQL_SELECT_KIT_REQUESTS_SENT = "SELECT kit.scan_date, realm.instance_name, request.ddp_instance_id FROM ddp_kit_request request " +
            "LEFT JOIN ddp_kit kit on (kit.dsm_kit_request_id = request.dsm_kit_request_id) LEFT JOIN ddp_instance realm on (request.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_participant_exit ex on (ex.ddp_participant_id = request.ddp_participant_id and ex.ddp_instance_id = request.ddp_instance_id) " +
            "WHERE scan_date IS NOT NULL AND ex.ddp_participant_exit_id IS NULL AND realm.instance_name = ? AND request.kit_type_id = ?";
    private static final String SQL_SELECT_KIT_REQUESTS_DEACTIVATED = "SELECT kit.receive_date, realm.instance_name, request.ddp_instance_id FROM ddp_kit_request request " +
            "LEFT JOIN ddp_kit kit on (kit.dsm_kit_request_id = request.dsm_kit_request_id) LEFT JOIN ddp_instance realm on (request.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_participant_exit ex on (ex.ddp_participant_id = request.ddp_participant_id and ex.ddp_instance_id = request.ddp_instance_id) " +
            "WHERE receive_date IS NOT NULL AND ex.ddp_participant_exit_id IS NULL AND realm.instance_name = ? AND request.kit_type_id = ?";

    private final KitUtil kitUtil;

    private ScriptingContainer container;
    private Object receiver;

    public DashboardRoute(@NonNull KitUtil kitUtil, @NonNull ScriptingContainer container,
                          @NonNull Object receiver) {
        this.kitUtil = kitUtil;
        this.container = container;
        this.receiver = receiver;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        try {
            if (UserUtil.checkUserAccess(null, userId, "kit_shipping") || UserUtil.checkUserAccess(null, userId, "kit_shipping_view")
                    || UserUtil.checkUserAccess(null, userId, "mr_view")) {
                String userIdRequest = UserUtil.getUserId(request);
                if (!userId.equals(userIdRequest)) {
                    throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
                }
                String startDate = request.params(RequestParameter.START);
                if (StringUtils.isNotBlank(startDate)) {
                    String endDate = request.params(RequestParameter.END);
                    if (StringUtils.isNotBlank(endDate)) {
                        logger.info("Getting report/dashboard for period " + startDate + " - " + endDate);
                        final long start = SystemUtil.getLongFromDateString(startDate);
                        //set endDate to midnight of that date
                        endDate = endDate + " 23:59:59";
                        final long end = SystemUtil.getLongFromDetailDateString(endDate);
                        if (request.url().contains(RoutePath.SAMPLE_REPORT_REQUEST)) {
                            return getShippingReport(userIdRequest, start, end);
                        }
                        else {
                            return getMedicalRecordDashboard(start, end, RoutePath.getRealm(request));
                        }
                    }
                    else {
                        throw new RuntimeException("End date is missing");
                    }
                }
                else {
                    if (request.url().contains(RoutePath.SAMPLE_REPORT_REQUEST)) {
                        return getShippingReportDownload(userIdRequest);
                    }
                    else {
                        String realm = null;
                        QueryParamsMap queryParams = request.queryMap();
                        if (queryParams.value(RoutePath.REALM) != null) {
                            realm = queryParams.get(RoutePath.REALM).value();
                            return getShippingDashboard(realm, userIdRequest);
                        }
                        else {
                            Collection<String> allowedRealms = UserUtil.getListOfAllowedRealms(userIdRequest);
                            Map<String, List<KitType>> kitTypesPerDDP = new HashMap<>();
                            for (String ddp : allowedRealms) {
                                kitTypesPerDDP.put(ddp, KitType.getKitTypes(ddp, userIdRequest));
                            }
                            return new DashboardInformation(
                                    getRealmValueList(kitUtil.getUnsentExpressKits(false), allowedRealms, kitTypesPerDDP),
                                    KitDDPSummary.getUnsentKits(false, allowedRealms));
                        }
                    }
                }
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        catch (Exception e) {
            logger.error("Couldn't get dashboard information ", e);
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
    }

    public DashboardInformation getShippingDashboard(@NonNull String realm, @NonNull String userId) {
        List<DashboardInformation.KitCounter> sentMap = new ArrayList<>();
        List<DashboardInformation.KitCounter> receivedMap = new ArrayList<>();
        List<NameValue> deactivatedMap = new ArrayList<>();
        List<KitType> kitTypes = KitType.getKitTypes(realm, userId);
        for (KitType kitType : kitTypes) {
            int kitTypeId = getKitTypeId(realm, kitType);
            //normal kit type
            KitRequestsPerDate kits = getAllKitsForQuery(SQL_SELECT_KIT_REQUESTS_SENT, realm, kitTypeId, DBConstants.DSM_SCAN_DATE);
            DashboardInformation.KitCounter kitCounter = new DashboardInformation.KitCounter(kitType.getName(), getNameValueList(kits));
            sentMap.add(kitCounter);

            kits = getAllKitsForQuery(SQL_SELECT_KIT_REQUESTS_DEACTIVATED, realm, kitTypeId, DBConstants.DSM_RECEIVE_DATE);
            kitCounter = new DashboardInformation.KitCounter(kitType.getName(), getNameValueList(kits));
            receivedMap.add(kitCounter);

            Integer count = getCountOfDeactivatedKits(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_DASHBOARD_INFORMATION_OF_KIT_REQUESTS_DEACTIVATED),
                    realm, kitTypeId, DBConstants.KITREQUEST_COUNT);
            deactivatedMap.add(new NameValue(kitType.getName(), count));
        }
        return new DashboardInformation(realm, sentMap, receivedMap, deactivatedMap);
    }

    public int getCountOfDeactivatedKits(@NonNull String query, @NonNull String realm, @NonNull int kitTypeId,
                                         @NonNull String returnColumn) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query,ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_READ_ONLY)) {
                stmt.setString(1, realm);
                stmt.setInt(2, kitTypeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    rs.beforeFirst();
                    if (count < 2) { // 0 rows are ok, more than 1 row is not good!
                        if (rs.next()) {
                            dbVals.resultValue = rs.getInt(returnColumn);
                        }
                    }
                    else {
                        throw new RuntimeException("Got more than 1 row back. Rowcount: " + count);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting kit request information ", results.resultException);
        }
        return (Integer) results.resultValue;
    }

    public KitRequestsPerDate getAllKitsForQuery(@NonNull String query, @NonNull String realm, @NonNull int kitTypeId,
                                                 @NonNull String returnColumn) {
        KitRequestsPerDate kitRequestsPerDate = new KitRequestsPerDate();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, realm);
                stmt.setInt(2, kitTypeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String dateString = SystemUtil.getDateFormatted(rs.getLong(returnColumn));
                        if (kitRequestsPerDate.containsKey(dateString)) {
                            Integer counter = kitRequestsPerDate.get(dateString);
                            counter++;
                            kitRequestsPerDate.put(dateString, counter);
                        }
                        else {
                            kitRequestsPerDate.put(dateString, 1);
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting kit request information ", results.resultException);
        }
        return kitRequestsPerDate;
    }


    /**
     * Find all information needed for medical record dashboard
     *
     * @return DashboardInformation
     */
    public DashboardInformation getMedicalRecordDashboard(@NonNull long start, @NonNull long end, @NonNull String realm) {
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);

        Map<String, Map<String, Object>> participantESData = ParticipantWrapper.getESData(ddpInstance);
        Map<String, Participant> participants = Participant.getParticipants(realm);
        Map<String, List<MedicalRecord>> medicalRecords = MedicalRecord.getMedicalRecords(realm);
        Map<String, List<OncHistoryDetail>> oncHistoryDetails = OncHistoryDetail.getOncHistoryDetails(realm);
        Map<String, List<KitRequestShipping>> kitRequests = KitRequestShipping.getAllKitRequestsByRealm(realm, null, null, true);
        Map<String, List<AbstractionActivity>> abstractionActivities = AbstractionActivity.getAllAbstractionActivityByRealm(realm);
        Map<String, List<AbstractionGroup>> abstractionSummary = AbstractionFinal.getAbstractionFinal(realm);

        List<ParticipantWrapper> participantWrapperList = ParticipantWrapper.addAllData(new ArrayList<>(participantESData.keySet()), participantESData,
                participants, medicalRecords, oncHistoryDetails, kitRequests, abstractionActivities, abstractionSummary, null);

        Map<String, Integer> dashboardValues = new HashMap(); //counts only pt
        Map<String, Integer> dashboardValuesDetailed = new HashMap(); //counts number of institutions in total
        Map<String, Integer> dashboardValuesPeriod = new HashMap(); //counts only pt per period
        Map<String, Integer> dashboardValuesPeriodDetailed = new HashMap(); //counts number of institutions in total per period
        //number of pts in ES
        dashboardValues.put("all", participantWrapperList.size());
        for (ParticipantWrapper wrapper : participantWrapperList) {
            //es data information
            Map<String, Object> esData = wrapper.getData();
            //count pt enrollment status
            String enrollmentStatus = (String) esData.get("status");
            countParameter(dashboardValues, "status." + enrollmentStatus, esData, "status", false);

            if (esData.get("profile") != null) {
                Map<String, Object> profileData = (Map<String, Object>) esData.get("profile");
                //count pt creation in period
                countParameterPeriod(dashboardValuesPeriod, "all", profileData, "createdAt", start, end);
            }
            if (esData.get("dsm") != null) {
                Map<String, Object> dsmSpecificInformation = (Map<String, Object>) esData.get("dsm");
                // count pt count consented to tissue
                countBooleanParameter(dashboardValues, "tissueConsent", dsmSpecificInformation, "hasConsentedToTissueSample");
                countBooleanParameter(dashboardValues, "bloodConsent", dsmSpecificInformation, "hasConsentedToBloodDraw");
            }
            if (esData.get("activities") != null) {
                List<Object> surveyList = (ArrayList<Object>) esData.get("activities");
                for (Object survey : surveyList) {
                    Map<String, Object> surveyMap = (Map<String, Object>) survey;
                    String version = (String) surveyMap.get("activityVersion");
                    String code = (String) surveyMap.get("activityCode");

                    if (surveyMap.get("lastUpdatedAt") != null) {
                        //get number of pt for survey x (which started to fill out survey)
                        countParameter(dashboardValues, "activity." + code + "." + version, surveyMap, "activityCode", false);
                        countParameter(dashboardValues, "activity." + code, surveyMap, "activityCode", false);
                    }

                    //get number of pt who completed survey x with version z
                    countParameter(dashboardValues, "activity." + code + "." + version + ".completed", surveyMap,
                            "completedAt", true);
                    countParameterPeriod(dashboardValuesPeriod, "activity." + code + "." + version + ".completed", surveyMap,
                            "completedAt", start, end);

                    //get number of pt who completed survey x ignoring version
                    countParameter(dashboardValues, "activity." + code + ".completed", surveyMap,
                            "completedAt", true);
                    countParameterPeriod(dashboardValuesPeriod, "activity." + code + ".completed", surveyMap,
                            "completedAt", start, end);
                }
            }

            if (wrapper.getParticipant() != null) {
                if (wrapper.getParticipant().isMinimalMR()) {
                    incrementCounter(dashboardValues, "minimalMR");
                }
            }

            Set<String> foundAtPt = new HashSet<>();
            Set<String> foundAtPtPeriod = new HashSet<>();
            if (wrapper.getMedicalRecords() != null && !wrapper.getMedicalRecords().isEmpty()) {
                countMedicalRecordData(wrapper.getMedicalRecords(), foundAtPt, foundAtPtPeriod, dashboardValuesDetailed, dashboardValuesPeriodDetailed, start, end,
                        kitRequests);
            }
            if (wrapper.getOncHistoryDetails() != null && !wrapper.getOncHistoryDetails().isEmpty()) {
                countOncHistoryData(wrapper.getOncHistoryDetails(), foundAtPt, foundAtPtPeriod, dashboardValuesDetailed, dashboardValuesPeriodDetailed, start, end);
            }
            if (wrapper.getKits() != null && !wrapper.getKits().isEmpty()) {
                countKits(wrapper.getKits(), foundAtPt, foundAtPtPeriod, dashboardValuesDetailed, dashboardValuesPeriodDetailed, start, end);
            }

            if (wrapper.getAbstractionActivities() != null && !wrapper.getAbstractionActivities().isEmpty()) {
                for (AbstractionActivity activity : wrapper.getAbstractionActivities()) {
                    if (AbstractionUtil.ACTIVITY_FINAL.equals(activity.getActivity()) && AbstractionUtil.STATUS_DONE.equals(activity.getAStatus())) {
                        incrementCounter(dashboardValues, "abstraction.done");
                        incrementCounterPeriod(dashboardValuesPeriod, "abstraction.done", activity.getLastChanged(), start, end);
                    }
                }
            }

            for (String found : foundAtPt) {
                incrementCounter(dashboardValues, found);
            }
            for (String found : foundAtPtPeriod) {
                incrementCounter(dashboardValuesPeriod, found);
            }
        }
        logger.info("Done calculating dashboard. Returning map now");
        return new DashboardInformation(dashboardValues, dashboardValuesDetailed, dashboardValuesPeriod, dashboardValuesPeriodDetailed);
    }

    private void countMedicalRecordData(@NonNull List<MedicalRecord> medicalRecordList, @NonNull Set<String> foundAtPT, @NonNull Set<String> foundAtPtPeriod,
                                        @NonNull Map<String, Integer> dashboardValuesDetailed, @NonNull Map<String, Integer> dashboardValuesPeriodDetailed,
                                        long start, long end, Map<String, List<KitRequestShipping>> kitRequests) {
        for (MedicalRecord medicalRecord : medicalRecordList) {
            if (medicalRecord.isDuplicate()) {
                incrementCounter(dashboardValuesDetailed, "duplicateMedicalRecord");
                foundAtPT.add("duplicateMedicalRecord");
            }
            if (medicalRecord.isInternational()) {
                incrementCounter(dashboardValuesDetailed, "internationalMedicalRecord");
                foundAtPT.add("internationalMedicalRecord");
            }
            if (medicalRecord.isMrProblem()) {
                incrementCounter(dashboardValuesDetailed, "medicalRecordWithProblem");
                foundAtPT.add("medicalRecordWithProblem");
            }
            if (medicalRecord.isUnableObtain()) {
                incrementCounter(dashboardValuesDetailed, "unableToObtainMedicalRecord");
                foundAtPT.add("unableToObtainMedicalRecord");
            }
            if (medicalRecord.isFollowUpRequired()) {
                incrementCounter(dashboardValuesDetailed, "followupRequiredMedicalRecord");
                foundAtPT.add("followupRequiredMedicalRecord");
                if (medicalRecord.getFollowUps() == null || medicalRecord.getFollowUps().length == 0) {
                    incrementCounter(dashboardValuesDetailed, "followupNotRequested");
                    foundAtPT.add("followupNotRequested");
                }
            }
            if (medicalRecord.isCrRequired()) {
                incrementCounter(dashboardValuesDetailed, "paperCRRequired");
                foundAtPT.add("paperCRRequired");
            }
            if (medicalRecord.isReviewMedicalRecord()) {
                incrementCounter(dashboardValuesDetailed, "reviewMedicalRecord");
                foundAtPT.add("reviewMedicalRecord");
            }

            countRequestsReceive(dashboardValuesDetailed, dashboardValuesPeriodDetailed, foundAtPT, foundAtPtPeriod, medicalRecord.getFaxSent3(),
                    medicalRecord.getFaxSent2(), medicalRecord.getFaxSent(), medicalRecord.getMrReceived(), start, end,
                    "notRequested", "faxSent", "mrReceived", medicalRecord.isDuplicate());

            // MR ready to request (at least saliva or blood received and mr not flagged as "duplicate" or "problem" or "unable to obtain" and fax sent date is not entered)
            if (!medicalRecord.isDuplicate() && !medicalRecord.isMrProblem() && !medicalRecord.isUnableObtain() && StringUtils.isBlank(medicalRecord.getFaxSent())) {
                List<KitRequestShipping> kits = kitRequests.get(medicalRecord.getDdpParticipantId());
                if (kits != null) {
                    for (KitRequestShipping kit : kits) {
                        if (kit.getReceiveDate() != 0) {
                            // one kit was received
                            incrementCounter(dashboardValuesDetailed, "readyToRequest");
                            foundAtPT.add("readyToRequest");
                            break;
                        }
                    }
                }
            }

            if (medicalRecord.getFollowUps() != null) {
                int index = 1;
                for (FollowUp followUp : medicalRecord.getFollowUps()) {
                    countRequestsReceive(dashboardValuesDetailed, dashboardValuesPeriodDetailed, foundAtPT, foundAtPtPeriod, followUp.getFRequest3(),
                            followUp.getFRequest2(), followUp.getFRequest1(), followUp.getFReceived(), start, end,
                            null, "followUpSent." + index + ".", "followUpReceived" + index, false);
                    index++;
                }
            }
        }
    }

    private void countOncHistoryData(@NonNull List<OncHistoryDetail> oncHistoryDetailList, @NonNull Set<String> foundAtPT, @NonNull Set<String> foundAtPtPeriod,
                                     @NonNull Map<String, Integer> dashboardValuesDetailed, @NonNull Map<String, Integer> dashboardValuesPeriodDetailed,
                                     long start, long end) {

        for (OncHistoryDetail oncHistoryDetail : oncHistoryDetailList) {
            if (oncHistoryDetail.isUnableToObtain()) {
                incrementCounter(dashboardValuesDetailed, "unableToObtainTissue");
                foundAtPT.add("unableToObtainTissue");
            }
            if (StringUtils.isNotBlank(oncHistoryDetail.getRequest())) {
                if (OncHistoryDetail.STATUS_REVIEW.equals(oncHistoryDetail.getRequest())) {
                    incrementCounter(dashboardValuesDetailed, "request.review");
                    foundAtPT.add("request.review");
                }
                else if (OncHistoryDetail.STATUS_SENT.equals(oncHistoryDetail.getRequest())) {
                    incrementCounter(dashboardValuesDetailed, "request.sent");
                    foundAtPT.add("request.sent");
                }
                else if (OncHistoryDetail.STATUS_RECEIVED.equals(oncHistoryDetail.getRequest())) {
                    incrementCounter(dashboardValuesDetailed, "request.received");
                    foundAtPT.add("request.received");
                }
                else if (OncHistoryDetail.STATUS_HOLD.equals(oncHistoryDetail.getRequest())) {
                    incrementCounter(dashboardValuesDetailed, "request.hold");
                    foundAtPT.add("request.hold");
                }
                else if (OncHistoryDetail.STATUS_DO_NOT_REQUEST.equals(oncHistoryDetail.getRequest())) {
                    incrementCounter(dashboardValuesDetailed, "request.no");
                    foundAtPT.add("request.no");
                }
                else if (OncHistoryDetail.STATUS_RETURNED.equals(oncHistoryDetail.getRequest())) {
                    incrementCounter(dashboardValuesDetailed, "request.returned");
                    foundAtPT.add("request.returned");
                }
                else if (OncHistoryDetail.STATUS_REQUEST.equals(oncHistoryDetail.getRequest())) {
                    incrementCounter(dashboardValuesDetailed, "request.request");
                    foundAtPT.add("request.request");
                    //count where tissue request is set to request but fax is not sent out yet
                    if (StringUtils.isBlank(oncHistoryDetail.getTFaxSent())) {
                        incrementCounter(dashboardValuesDetailed, "tFaxNotSent");
                        foundAtPT.add("tFaxNotSent");
                    }
                }
                else if (OncHistoryDetail.STATUS_UNABLE_TO_OBTAIN.equals(oncHistoryDetail.getRequest())) {
                    incrementCounter(dashboardValuesDetailed, "request.unable");
                    foundAtPT.add("request.unable");
                }
            }
            if (StringUtils.isNotBlank(oncHistoryDetail.getTissueProblemOption())) {
                incrementCounter(dashboardValuesDetailed, "tissueProblemOption");
                foundAtPT.add("tissueProblemOption");
                if (OncHistoryDetail.PROBLEM_INSUFFICIENT_PATH.equals(oncHistoryDetail.getTissueProblemOption())) {
                    incrementCounter(dashboardValuesDetailed, "tissueProblemOption.insufficientPath");
                    foundAtPT.add("tissueProblemOption.insufficientPath");
                }
                else if (OncHistoryDetail.PROBLEM_INSUFFICIENT_SHL.equals(oncHistoryDetail.getTissueProblemOption())) {
                    incrementCounter(dashboardValuesDetailed, "tissueProblemOption.insufficientSHL");
                    foundAtPT.add("tissueProblemOption.insufficientSHL");
                }
                else if (OncHistoryDetail.PROBLEM_NO_E_SIGN.equals(oncHistoryDetail.getTissueProblemOption())) {
                    incrementCounter(dashboardValuesDetailed, "tissueProblemOption.noESign");
                    foundAtPT.add("tissueProblemOption.noESign");
                }
                else if (OncHistoryDetail.PROBLEM_PATH_POLICY.equals(oncHistoryDetail.getTissueProblemOption())) {
                    incrementCounter(dashboardValuesDetailed, "tissueProblemOption.pathPolicy");
                    foundAtPT.add("tissueProblemOption.pathPolicy");
                }
                else if (OncHistoryDetail.PROBLEM_PATH_NO_LOCATE.equals(oncHistoryDetail.getTissueProblemOption())) {
                    incrementCounter(dashboardValuesDetailed, "tissueProblemOption.pathNoLocate");
                    foundAtPT.add("tissueProblemOption.pathNoLocate");
                }
                else if (OncHistoryDetail.PROBLEM_DESTROYED.equals(oncHistoryDetail.getTissueProblemOption())) {
                    incrementCounter(dashboardValuesDetailed, "tissueProblemOption.destroyed");
                    foundAtPT.add("tissueProblemOption.destroyed");
                }
                else if (OncHistoryDetail.PROBLEM_OTHER.equals(oncHistoryDetail.getTissueProblemOption())
                        || OncHistoryDetail.PROBLEM_OTHER_OLD.equals(oncHistoryDetail.getTissueProblemOption())) {
                    incrementCounter(dashboardValuesDetailed, "tissueProblemOption.other");
                    foundAtPT.add("tissueProblemOption.other");
                }
            }
            countRequestsReceive(dashboardValuesDetailed, dashboardValuesPeriodDetailed, foundAtPT, foundAtPtPeriod, oncHistoryDetail.getTFaxSent3(),
                    oncHistoryDetail.getTFaxSent2(), oncHistoryDetail.getTFaxSent(), oncHistoryDetail.getTissueReceived(),
                    start, end, null, "tFaxSent", "tissueReceived", false);
        }
    }

    private void countKits(@NonNull List<KitRequestShipping> kits, @NonNull Set<String> foundAtPT, @NonNull Set<String> foundAtPtPeriod,
                           @NonNull Map<String, Integer> dashboardValuesDetailed, @NonNull Map<String, Integer> dashboardValuesPeriodDetailed,
                           long start, long end) {
        for (KitRequestShipping kit : kits) {
            if (kit.getScanDate() != 0) {
                incrementCounter(dashboardValuesDetailed, "kit." + kit.getKitType() + ".sent", foundAtPT);
                incrementCounterPeriod(dashboardValuesPeriodDetailed, "kit." + kit.getKitType() + ".sent", kit.getScanDate(), start, end, foundAtPtPeriod);
            }
            else if (kit.getDeactivatedDate() == 0) {
                incrementCounter(dashboardValuesDetailed, "kit." + kit.getKitType() + ".waiting", foundAtPT);
            }
            if (kit.getReceiveDate() != 0) {
                incrementCounter(dashboardValuesDetailed, "kit." + kit.getKitType() + ".received", foundAtPT);
                incrementCounterPeriod(dashboardValuesPeriodDetailed, "kit." + kit.getKitType() + ".received", kit.getReceiveDate(), start, end, foundAtPtPeriod);
            }
            if (kit.getDeactivatedDate() != 0) {
                incrementCounter(dashboardValuesDetailed, "kit." + kit.getKitType() + ".deactivated", foundAtPT);
                incrementCounterPeriod(dashboardValuesPeriodDetailed, "kit." + kit.getKitType() + ".deactivated", kit.getDeactivatedDate(), start, end, foundAtPtPeriod);
            }
            if (StringUtils.isNotBlank(kit.getEasypostShipmentStatus())) {
                incrementCounter(dashboardValuesDetailed, "kit." + kit.getKitType() + "." + kit.getEasypostShipmentStatus(), foundAtPT);
            }
        }
    }

    private void countRequestsReceive(@NonNull Map<String, Integer> dashboardValuesDetailed, @NonNull Map<String, Integer> dashboardValuesPeriodDetailed,
                                      @NonNull Set<String> foundAtPT, @NonNull Set<String> foundAtPtPeriod, String faxSent3, String faxSent2, String faxSent, String received,
                                      long start, long end, String dashboardValueNameWaiting, @NonNull String dashboardValueNameSent, @NonNull String dashboardValueNameReceived,
                                      boolean isDuplicate) {
        //count fax sent
        if (StringUtils.isNotBlank(faxSent3)) {
            //was requested 3 times
            incrementCounter(dashboardValuesDetailed, dashboardValueNameSent + "3", foundAtPT);
            incrementCounter(dashboardValuesDetailed, dashboardValueNameSent + "2", foundAtPT);
            incrementCounter(dashboardValuesDetailed, dashboardValueNameSent, foundAtPT);

            incrementCounterPeriod(dashboardValuesPeriodDetailed, dashboardValueNameSent + "3",
                    SystemUtil.getLongFromDateString(faxSent3), start, end, foundAtPtPeriod);
            incrementCounterPeriod(dashboardValuesPeriodDetailed, dashboardValueNameSent + "2",
                    SystemUtil.getLongFromDateString(faxSent2), start, end, foundAtPtPeriod);
            incrementCounterPeriod(dashboardValuesPeriodDetailed, dashboardValueNameSent,
                    SystemUtil.getLongFromDateString(faxSent), start, end, foundAtPtPeriod);
        }
        else if (StringUtils.isNotBlank(faxSent2)) {
            //was requested 2 times
            incrementCounter(dashboardValuesDetailed, dashboardValueNameSent + "2", foundAtPT);
            incrementCounter(dashboardValuesDetailed, dashboardValueNameSent, foundAtPT);

            incrementCounterPeriod(dashboardValuesPeriodDetailed, dashboardValueNameSent + "2",
                    SystemUtil.getLongFromDateString(faxSent2), start, end, foundAtPtPeriod);
            incrementCounterPeriod(dashboardValuesPeriodDetailed, dashboardValueNameSent,
                    SystemUtil.getLongFromDateString(faxSent), start, end, foundAtPtPeriod);
        }
        else if (StringUtils.isNotBlank(faxSent)) {
            //was requested 1 time
            incrementCounter(dashboardValuesDetailed, dashboardValueNameSent, foundAtPT);

            incrementCounterPeriod(dashboardValuesPeriodDetailed, dashboardValueNameSent,
                    SystemUtil.getLongFromDateString(faxSent), start, end, foundAtPtPeriod);
        }
        else {
            //Total requestable MR - only if they are not flagged as duplicate
            if (!isDuplicate) {
                if (dashboardValueNameWaiting != null) {
                    incrementCounter(dashboardValuesDetailed, dashboardValueNameWaiting, foundAtPT);
                }
            }
        }
        if (StringUtils.isNotBlank(received)) {
            //was received
            incrementCounter(dashboardValuesDetailed, dashboardValueNameReceived, foundAtPT);
            incrementCounterPeriod(dashboardValuesPeriodDetailed, dashboardValueNameReceived,
                    SystemUtil.getLongFromDateString(received), start, end, foundAtPtPeriod);
        }

    }

    private void countParameter(@NonNull Map<String, Integer> dashboardValues, @NonNull String dashboardValueName,
                                @NonNull Map<String, Object> map, @NonNull String key, boolean date) {
        if (map.get(key) != null) {
            if (date) {
                if ((Long) map.get(key) == 0) {
                    return;
                }
            }
            incrementCounter(dashboardValues, dashboardValueName);
        }
    }

    private void countBooleanParameter(@NonNull Map<String, Integer> dashboardValues, @NonNull String dashboardValueName,
                                       @NonNull Map<String, Object> map, @NonNull String key) {
        if (map.get(key) != null) {
            Boolean value = (Boolean) map.get(key);
            if (value) {
                incrementCounter(dashboardValues, dashboardValueName);
            }
        }
    }

    private void countParameterPeriod(@NonNull Map<String, Integer> dashboardValues, @NonNull String dashboardValueName,
                                      @NonNull Map<String, Object> map, @NonNull String key, long start, long end) {
        if (map.get(key) != null) {
            Long date = (Long) map.get(key);
            if (date != 0) {
                incrementCounterPeriod(dashboardValues, dashboardValueName, date, start, end);
            }
        }
    }

    private void incrementCounter(@NonNull Map<String, Integer> dashboardValues, @NonNull String dashboardValueName) {
        incrementCounter(dashboardValues, dashboardValueName, null);
    }

    private void incrementCounter(@NonNull Map<String, Integer> dashboardValues, @NonNull String dashboardValueName,
                                  Set<String> foundAtPt) {
        int counter = 0;
        if (dashboardValues.containsKey(dashboardValueName)) {
            counter = dashboardValues.get(dashboardValueName);
        }
        counter = counter + 1;
        if (foundAtPt != null) {
            foundAtPt.add(dashboardValueName);
        }
        dashboardValues.put(dashboardValueName, counter);
    }

    private void incrementCounterPeriod(@NonNull Map<String, Integer> dashboardValues, @NonNull String dashboardValueName,
                                        long date, long start, long end) {
        incrementCounterPeriod(dashboardValues, dashboardValueName, date, start, end, null);
    }

    private void incrementCounterPeriod(@NonNull Map<String, Integer> dashboardValues, @NonNull String dashboardValueName,
                                        long date, long start, long end, Set<String> foundAtPtPeriod) {
        int counter = 0;
        if (dashboardValues.containsKey(dashboardValueName)) {
            counter = dashboardValues.get(dashboardValueName);
        }
        if (date != 0 && date >= start && date <= end) {
            counter = counter + 1;
            if (foundAtPtPeriod != null) {
                foundAtPtPeriod.add(dashboardValueName);
            }
            dashboardValues.put(dashboardValueName, counter);
        }
    }

    public SummaryKitType getKitRequestInformation(@NonNull long start, @NonNull long end, @NonNull String realm,
                                                   @NonNull int  kitTypeId, @NonNull String kitTypeName) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_DASHBOARD_INFORMATION_OF_KIT_REQUESTS),
                    ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_READ_ONLY)) {
                stmt.setString(1, realm);
                stmt.setInt(2, kitTypeId);
                stmt.setString(3, realm);
                stmt.setInt(4, kitTypeId);
                stmt.setLong(5, start);
                stmt.setLong(6, end);
                stmt.setString(7, realm);
                stmt.setInt(8, kitTypeId);
                stmt.setString(9, realm);
                stmt.setInt(10, kitTypeId);
                stmt.setLong(11, start);
                stmt.setLong(12, end);
                stmt.setString(13, realm);
                stmt.setInt(14, kitTypeId);
                stmt.setString(15, realm);
                stmt.setInt(16, kitTypeId);
                stmt.setLong(17, start);
                stmt.setLong(18, end);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    rs.beforeFirst();
                    if (count < 2) { // 0 rows are ok, more than 1 row is not good!
                        if (rs.next()) {
                            SummaryKitType summaryKitType = new SummaryKitType(kitTypeName, rs.getInt(DBConstants.KIT_NEW),
                                    rs.getInt(DBConstants.KIT_SENT), rs.getInt(DBConstants.KIT_RECEIVED),
                                    rs.getInt(DBConstants.KIT_NEW_PERIOD), rs.getInt(DBConstants.KIT_SENT_PERIOD),
                                    rs.getInt(DBConstants.KIT_RECEIVED_PERIOD));
                            dbVals.resultValue = summaryKitType;
                        }
                    }
                    else {
                        throw new RuntimeException("Got more than 1 row back. Row count: " + count);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting kit request information ", results.resultException);
        }
        return (SummaryKitType) results.resultValue;
    }

    public ArrayList<NameValue> getNameValueList(KitRequestsPerDate map) {
        ArrayList<NameValue> nameValueList = new ArrayList<>();
        for (String date : map.keySet()) {
            nameValueList.add(new NameValue(date, map.get(date)));
        }
        return nameValueList;
    }

    public ArrayList<NameValue> getRealmValueList(Map<String, String> map, Collection<String> allowedRealms,
                                                  Map<String, List<KitType>> kitTypesPerDDP) {
        ArrayList<NameValue> nameValueList = new ArrayList<>();
        if (allowedRealms != null && !allowedRealms.isEmpty()) {
            for (String key : map.keySet()) {
                if (key.contains("_")) {
                    String[] values = key.split("_");
                    if (allowedRealms.contains(values[0])) {
                        String ddp = values[0];
                        if (kitTypesPerDDP.containsKey(ddp)) {
                            List<KitType> kitTypes = kitTypesPerDDP.get(ddp);
                            for (KitType type : kitTypes) {
                                if (type.getName().equals(values[1])) {
                                    String value = map.get(key);
                                    if (!"0".equals(value)) {
                                        nameValueList.add(new NameValue(key, value));
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        return nameValueList;
    }

    public Collection<KitReport> getShippingReport(@NonNull String userId, @NonNull long start, @NonNull long end) {
        logger.info("Shipping report");
        Collection<String> allowedRealms = UserUtil.getListOfAllowedRealms(userId);
        Collection<KitReport> kitTypesPerDDP = new ArrayList<>();

        for (String ddp : allowedRealms) {
            List<SummaryKitType> kitReports = new ArrayList<>();
            List<KitType> kitTypes = KitType.getKitTypes(ddp, userId);
            for (KitType kitType : kitTypes) {
                int kitTypeId = getKitTypeId(ddp, kitType);
                kitReports.add(getKitRequestInformation(start, end, ddp, kitTypeId, kitType.getName()));
            }
            KitReport kitreport = new KitReport(ddp, kitReports);
            kitTypesPerDDP.add(kitreport);
        }
        return kitTypesPerDDP;
    }

    public Collection<KitReport> getShippingReportDownload(@NonNull String userId) {
        logger.info("Shipping report for download");
        Collection<String> allowedRealms = UserUtil.getListOfAllowedRealms(userId);
        Collection<KitReport> kitTypesPerDDP = new ArrayList<>();
        for (String ddp : allowedRealms) {
            ArrayList<SummaryKitType> kitReports = new ArrayList<>();
            List<KitType> kitTypes = KitType.getKitTypes(ddp, userId);
            for (KitType kitType : kitTypes) {
                HashMap<String, SummaryKitType> summaryKitTypeMonth = new HashMap<>();
                getKitRequestInformationPerMonth(ddp, kitType,
                        ApplicationConfigConstants.GET_DASHBOARD_INFORMATION_OF_KIT_REQUESTS_SENT_REPORT, summaryKitTypeMonth);
                getKitRequestInformationPerMonth(ddp, kitType,
                        ApplicationConfigConstants.GET_DASHBOARD_INFORMATION_OF_KIT_REQUESTS_RECEIVED_REPORT, summaryKitTypeMonth);
                kitReports.addAll(new ArrayList<>(summaryKitTypeMonth.values()));
            }
            KitReport kitreport = new KitReport(ddp, kitReports);
            kitTypesPerDDP.add(kitreport);

        }
        return kitTypesPerDDP;
    }

    public void getKitRequestInformationPerMonth(@NonNull String realm, @NonNull KitType kitType,
                                                 @NonNull String query, HashMap<String, SummaryKitType> summaryKitTypeMonth) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(query))) {
                stmt.setString(1, realm);
                stmt.setInt(2, kitType.getKitId());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String month = rs.getString(DBConstants.MONTH);
                        if (summaryKitTypeMonth.containsKey(month)) {
                            //sent number is already for that month
                            SummaryKitType summaryKitType = summaryKitTypeMonth.get(month);
                            summaryKitType.received = rs.getInt(DBConstants.KIT_RECEIVED);
                        }
                        else {
                            if (ApplicationConfigConstants.GET_DASHBOARD_INFORMATION_OF_KIT_REQUESTS_RECEIVED_REPORT.equals(query)) {
                                //only receive number for that month
                                SummaryKitType summaryKitType = new SummaryKitType(kitType.getName(),
                                        0, rs.getInt(DBConstants.KIT_RECEIVED),
                                        month);
                                summaryKitTypeMonth.put(month, summaryKitType);
                            }
                            else {
                                //month not there yet and it is not receive query
                                SummaryKitType summaryKitType = new SummaryKitType(kitType.getName(),
                                        rs.getInt(DBConstants.KIT_SENT), 0,
                                        month);
                                summaryKitTypeMonth.put(month, summaryKitType);
                            }
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting kit request information for download ", results.resultException);
        }
    }

    private int getKitTypeId (@NonNull String realm, @NonNull KitType kitType) {
        int kitTypeId = kitType.getKitId();
        List<KitSubKits> subKits = KitSubKits.getSubKits(realm, kitType.getName());
        if (subKits != null && !subKits.isEmpty()) {
            //kit has sub kits (assumption: all subkits stay together and will therefore be counted as just "one" kit)
            KitSubKits firstSubKit = subKits.get(0);
            if (firstSubKit != null) {
                kitTypeId = firstSubKit.getKitTypeId();
            }
        }
        return kitTypeId;
    }
}
