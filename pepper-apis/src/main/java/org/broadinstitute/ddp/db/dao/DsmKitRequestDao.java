package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dto.dsm.DsmKitRequest;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

/**
 * Dao to fulfill all requests needed by DSM integration.
 */
public interface DsmKitRequestDao extends SqlObject {
    String KIT_REQUEST_TABLE = "kit_request";
    String KIT_REQUEST_GUID_COLUMN = "kit_request_guid";

    @CreateSqlObject
    JdbiUser buildUserDao();

    @CreateSqlObject
    JdbiMailAddress buildMailAddressDao();

    /**
     * Create kit request and save to database.
     *
     * @param studyGuid          the GUID for the study this request corresponds to.
     * @param participantAddress the mailing address to associate with kit request. Should already be saved and
     *                           should have an id
     * @param userId             the user this kit is for
     * @param kitType            the kit type to create
     * @param needsApproval      whether the kit needs manual approval
     * @return the database id of newly created request
     */
    default long createKitRequest(String studyGuid, MailAddress participantAddress, Long userId, KitType kitType, boolean needsApproval) {
        String guid = DBUtils.uniqueStandardGuid(getHandle(), KIT_REQUEST_TABLE, KIT_REQUEST_GUID_COLUMN);
        return createKitRequest(guid, studyGuid, participantAddress.getId(),
                kitType.getId(), userId, Instant.now().getEpochSecond(), needsApproval);
    }

    default long createKitRequest(String studyGuid, MailAddress participantAddress, Long userId, KitType kitType) {
        return createKitRequest(studyGuid, participantAddress, userId, kitType, false);
    }

    /**
     * Create a kit request and save it to the database. The address used will be the default address for the
     * participant identified by given GUID
     *
     * @param studyGuid       the study GUID
     * @param participantGuid the participant GUID
     * @param kitType         the type of kit request to create
     * @param needsApproval   whether the kit needs manual approval
     * @return the database id of the kit request
     * @throws RuntimeException if there is no default address can be found
     */
    default long createKitRequest(String studyGuid, String participantGuid, KitType kitType, boolean needsApproval) {
        JdbiUser jdbiUser = buildUserDao();
        Long userId = jdbiUser.getUserIdByGuid(participantGuid);

        JdbiMailAddress mailAddressDao = buildMailAddressDao();
        Optional<MailAddress> defaultAddress = mailAddressDao.findDefaultAddressForParticipant(participantGuid);
        if (defaultAddress.isPresent()) {
            return createKitRequest(studyGuid, defaultAddress.get(), userId, kitType, needsApproval);
        } else {
            throw new RuntimeException("User with GUID:" + participantGuid
                    + " could not be found or does not have a default address");
        }
    }

    default long createKitRequest(String studyGuid, String participantGuid, KitType kitType) {
        return createKitRequest(studyGuid, participantGuid, kitType, false);
    }

    /**
     * Convenience method to insert a new kit request when database ids are known.
     */
    default long createKitRequest(String studyGuid, long participantId, long addressId, long kitTypeId, boolean needsApproval) {
        String guid = DBUtils.uniqueStandardGuid(getHandle(), KIT_REQUEST_TABLE, KIT_REQUEST_GUID_COLUMN);
        return createKitRequest(guid, studyGuid, addressId, kitTypeId, participantId, Instant.now().getEpochSecond(), needsApproval);
    }

    default long createKitRequest(String studyGuid, long participantId, long addressId, long kitTypeId) {
        return createKitRequest(studyGuid, participantId, addressId, kitTypeId, false);
    }

    @SqlUpdate("insertKitRequest")
    @UseStringTemplateSqlLocator
    @GetGeneratedKeys
    long createKitRequest(@Bind("guid") String requestGuid, @Bind("studyGuid") String studyGuid,
                          @Bind("addressId") long addressId, @Bind("kitTypeId") long kitTypeId,
                          @Bind("participantUserId") Long participantUserId, @Bind("creationTime") long creationTime,
                          @Bind("needsApproval") boolean needsApproval);

    /**
     * Get all the kit requests in system for specified study.
     *
     * @param studyGuid the GUID for study of interest
     * @return a list of DSMKitRequest in order of insertion
     */
    @SqlQuery("selectAllKitRequestsForStudy")
    @UseStringTemplateSqlLocator
    @RegisterBeanMapper(DsmKitRequest.class)
    List<DsmKitRequest> findAllKitRequestsForStudy(String studyGuid);

    /**
     * Get all the kit requests in system for a specified study with certain address validation statuses.
     *
     * @param studyGuid   the GUID for study of interest
     * @param statusCodes the desired address validation status codes of kit requests
     * @return a list of DSMKitRequest in order of insertion that fit parameters
     */
    @SqlQuery("selectAllKitRequestsForStudyWithStatus")
    @UseStringTemplateSqlLocator
    @RegisterBeanMapper(DsmKitRequest.class)
    List<DsmKitRequest> findAllKitRequestsForStudyWithStatus(@BindList("statusCodes") List<Integer> statusCodes,
                                                             @Bind("studyGuid") String studyGuid);

    /**
     * Helper function to convert DsmAddressValidationStatuses into their codes so can make query to get all kit requests
     * in system for a specified study with certain address validation statuses.
     *
     * @param studyGuid the GUID for the study of interest
     * @param statuses  the desired address validation statuses of kit requests
     * @return a list of DSMKitRequest in order of insertion that fit parameters
     */
    default List<DsmKitRequest> findAllKitRequestsForStudyWithStatus(String studyGuid, DsmAddressValidationStatus... statuses) {
        List<Integer> statusCodes = Arrays.stream(statuses).map(DsmAddressValidationStatus::getCode).collect(Collectors.toList());
        return findAllKitRequestsForStudyWithStatus(statusCodes, studyGuid);
    }

    /**
     * Return all the request kits that have been inserted AFTER the insertion of request that corresponds to
     * the previousRequestGuid.
     * The kit request that corresponds to the previousRequestGuid will NOT be included in the results
     *
     * @param studyGuid           the GUID for the study
     * @param previousRequestGuid the guid of the kit request that you want to select for requests after
     * @return list of kit requests
     */
    @SqlQuery("selectAllKitRequestsAfterGuid")
    @UseStringTemplateSqlLocator
    @RegisterBeanMapper(DsmKitRequest.class)
    List<DsmKitRequest> findKitRequestsAfterGuid(@Bind("studyGuid") String studyGuid,
                                                 @Bind("previousGuid") String previousRequestGuid);

    /**
     * Return all the request kits that have been inserted AFTER the insertion of request that corresponds to
     * the previousRequestGuid. Also, all kit requests must meet the address validation status as indicated by
     * status code.
     * The kit request that corresponds to the previousRequestGuid will NOT be included in the results
     *
     * @param studyGuid           the GUID for the study
     * @param previousRequestGuid the guid of the kit request that you want to select for requests after
     * @param statusCodes         the mail address validation status codes that are acceptable for selecting kit requests
     * @return list of kit requests
     */
    @SqlQuery("selectAllKitRequestsAfterGuidWithStatus")
    @UseStringTemplateSqlLocator
    @RegisterBeanMapper(DsmKitRequest.class)
    List<DsmKitRequest> findKitRequestsAfterGuidWithStatus(@Bind("studyGuid") String studyGuid,
                                                           @Bind("previousGuid") String previousRequestGuid,
                                                           @BindList("statusCodes") List<Integer> statusCodes);

    default List<DsmKitRequest> findKitRequestsAfterGuidWithStatus(String studyGuid,
                                                                   String previousRequestGuid,
                                                                   DsmAddressValidationStatus... statuses) {
        List<Integer> statusCodes = Arrays.stream(statuses).map(DsmAddressValidationStatus::getCode).collect(Collectors.toList());
        return findKitRequestsAfterGuidWithStatus(studyGuid, previousRequestGuid, statusCodes);
    }

    /**
     * Return the kit request that corresponds to the given database id.
     *
     * @param kitId the id
     * @return an Optional with a DsmKitRequest present if found in database
     */
    @SqlQuery("selectKitRequestById")
    @UseStringTemplateSqlLocator
    @RegisterBeanMapper(DsmKitRequest.class)
    Optional<DsmKitRequest> findKitRequest(@Bind("id") long kitId);

    @SqlQuery("selectKitRequestByGuid")
    @UseStringTemplateSqlLocator
    @RegisterBeanMapper(DsmKitRequest.class)
    Optional<DsmKitRequest> findKitRequest(@Bind("guid") String kitGuid);

    /**
     * Delete the request kit. For support of testing only
     *
     * @param id the kit database id
     */
    @SqlUpdate("deleteKitRequestById")
    @UseStringTemplateSqlLocator
    void deleteKitRequest(Long id);

    @SqlUpdate("deleteKitRequestByParticipantId")
    @UseStringTemplateSqlLocator
    void deleteKitRequestsByParticipantId(@Bind("participantId") Long participantId);
}
