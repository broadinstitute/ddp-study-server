package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.easypost.model.CustomsInfo;
import com.easypost.model.Parcel;
import com.easypost.model.PostageLabel;
import com.easypost.model.Shipment;
import com.easypost.model.Tracker;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitShippingIds;
import org.broadinstitute.dsm.model.KitSubKits;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.ddp.KitDetail;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.db.SimpleResult;
import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@TableName(name = DBConstants.DDP_KIT_REQUEST, alias = DBConstants.DDP_KIT_REQUEST_ALIAS,
        primaryKey = DBConstants.DSM_KIT_REQUEST_ID, columnPrefix = "")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder(setterPrefix = "with", toBuilder = true)
public class KitRequestShipping extends KitRequest implements HasDdpInstanceId {

    public static final String SQL_SELECT_KIT_REQUEST_FOR_ES =
            "SELECT * FROM ( SELECT req.upload_reason, kt.kit_type_name, kt.display_name, ddp_site.instance_name, ddp_site.ddp_instance_id, "
                    + "ddp_site.base_url, ddp_site.auth0_token, ddp_site.billing_reference, "
                    + "ddp_site.migrated_ddp, ddp_site.collaborator_id_prefix, ddp_site.es_participant_index, "
                    + "req.bsp_collaborator_participant_id, req.bsp_collaborator_sample_id, req.ddp_participant_id, "
                    + " if (LOCATE('_',req.ddp_label)>0, LEFT(req.ddp_label,LOCATE('_',req.ddp_label) - 1), req.ddp_label) as ddp_label, "
                    + "req.dsm_kit_request_id, "
                    + "req.kit_type_id, req.external_order_status, req.external_order_number, req.external_order_date, "
                    + "req.external_response, kt.no_return, req.created_by FROM kit_type kt, ddp_kit_request req, ddp_instance ddp_site  "
                    + "WHERE req.ddp_instance_id = ddp_site.ddp_instance_id AND req.kit_type_id = kt.kit_type_id) AS request  "
                    + "LEFT JOIN  "
                    + "(SELECT * FROM (SELECT kit.dsm_kit_request_id, kit.dsm_kit_id, kit.kit_complete, kit.label_url_to, "
                    + "kit.label_url_return, kit.tracking_to_id, "
                    + "kit.tracking_return_id, kit.easypost_tracking_to_url, kit.easypost_tracking_return_url, kit.easypost_to_id, "
                    + "kit.easypost_shipment_status, kit.scan_date, kit.label_date, kit.error, kit.message, "
                    + "kit.receive_date, kit.receive_by, kit.deactivated_date, kit.easypost_address_id_to, kit.deactivation_reason, "
                    + "tracking.tracking_id,  "
                    + "kit.kit_label, kit.express, kit.test_result, kit.needs_approval, kit.authorization, kit.denial_reason, "
                    + "kit.authorized_by, kit.ups_tracking_status, kit.ups_return_status, kit.CE_order, kit.collection_date, "
                    + "kit.sequencing_restriction, kit.sample_notes FROM ddp_kit kit  "
                    + "LEFT JOIN ddp_kit_tracking tracking  "
                    + "ON (kit.kit_label = tracking.kit_label) )as wtf) AS kit ON kit.dsm_kit_request_id = request.dsm_kit_request_id  "
                    + "LEFT JOIN ddp_participant_exit ex ON (ex.ddp_instance_id = request.ddp_instance_id  "
                    + "AND ex.ddp_participant_id = request.ddp_participant_id)  "
                    + "LEFT JOIN ddp_kit_request_settings dkc ON (request.ddp_instance_id = dkc.ddp_instance_id  "
                    + "AND request.kit_type_id = dkc.kit_type_id)  "
                    + "LEFT JOIN kit_type ktype ON (request.kit_type_id = ktype.kit_type_id)  "
                    + "WHERE ex.ddp_participant_exit_id is null ";

    public static final String SQL_SELECT_KIT_REQUEST =
            "SELECT * FROM ( SELECT req.upload_reason, kt.kit_type_name, kt.display_name, ddp_site.instance_name, ddp_site.ddp_instance_id,"
                    + " ddp_site.base_url, ddp_site.auth0_token, ddp_site.billing_reference, "
                    + "ddp_site.migrated_ddp, ddp_site.collaborator_id_prefix, ddp_site.es_participant_index, "
                    + "req.bsp_collaborator_participant_id, req.bsp_collaborator_sample_id, req.ddp_participant_id, "
                    + " if (LOCATE('_',req.ddp_label)>0, LEFT(req.ddp_label,LOCATE('_',req.ddp_label) - 1), req.ddp_label) as ddp_label, "
                    + "req.dsm_kit_request_id, "
                    + "req.kit_type_id, req.external_order_status, req.external_order_number, req.external_order_date, "
                    + "req.external_response, kt.no_return, req.created_by FROM kit_type kt, ddp_kit_request req, ddp_instance ddp_site "
                    + "WHERE req.ddp_instance_id = ddp_site.ddp_instance_id AND req.kit_type_id = kt.kit_type_id) AS request "
                    + "LEFT JOIN (SELECT * FROM (SELECT kit.dsm_kit_request_id, kit.dsm_kit_id, kit.kit_complete, kit.label_url_to, "
                    + "kit.label_url_return, kit.tracking_to_id, "
                    + "kit.tracking_return_id, kit.easypost_tracking_to_url, kit.easypost_tracking_return_url, kit.easypost_to_id, "
                    + "kit.easypost_shipment_status, kit.scan_date, kit.label_date, kit.error, kit.message, "
                    + "kit.receive_date, kit.receive_by, kit.deactivated_date, kit.easypost_address_id_to, kit.deactivation_reason, "
                    + "tracking.tracking_id,"
                    + " kit.kit_label, kit.express, kit.test_result, kit.needs_approval, kit.authorization, kit.denial_reason, "
                    + " kit.authorized_by, kit.ups_tracking_status, kit.ups_return_status, kit.CE_order, kit.collection_date, "
                    + "  kit.sequencing_restriction, kit.sample_notes FROM ddp_kit kit "
                    + "INNER JOIN (SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id FROM ddp_kit "
                    + "GROUP BY dsm_kit_request_id) groupedKit ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id "
                    + "AND kit.dsm_kit_id = groupedKit.kit_id LEFT JOIN ddp_kit_tracking tracking "
                    + "ON (kit.kit_label = tracking.kit_label))as wtf) AS kit ON kit.dsm_kit_request_id = request.dsm_kit_request_id "
                    + "LEFT JOIN ddp_participant_exit ex ON (ex.ddp_instance_id = request.ddp_instance_id "
                    + "AND ex.ddp_participant_id = request.ddp_participant_id) "
                    + "LEFT JOIN ddp_kit_request_settings dkc ON (request.ddp_instance_id = dkc.ddp_instance_id "
                    + "AND request.kit_type_id = dkc.kit_type_id) "
                    + "LEFT JOIN kit_type ktype ON (request.kit_type_id = ktype.kit_type_id) "
                    + "WHERE ex.ddp_participant_exit_id is null ";

    public static final String SQL_SELECT_KIT_WITH_QUERY_EXTENSION_FOR_UPS_TABLE =
            "SELECT kt.kit_type_name, realm.instance_name, request.bsp_collaborator_participant_id, request.bsp_collaborator_sample_id, "
                    + "request.ddp_participant_id, request.ddp_label, request.dsm_kit_request_id, "
                    + "request.kit_type_id, request.external_order_status, request.external_order_number, request.external_order_date, "
                    + "request.external_response, request.upload_reason, kt.no_return, request.created_by, "
                    + "kit.dsm_kit_request_id, kit.dsm_kit_id, kit.kit_complete, kit.label_url_to, kit.label_url_return, "
                    + "kit.tracking_to_id, kit.tracking_return_id, kit.easypost_tracking_to_url, kit.easypost_tracking_return_url, "
                    + "kit.easypost_to_id, kit.easypost_shipment_status, kit.scan_date, kit.label_date, kit.error, kit.message, "
                    + "kit.receive_date, kit.deactivated_date, kit.easypost_address_id_to, kit.deactivation_reason, "
                    + "(select t.tracking_id from ddp_kit_tracking t where t.kit_label = kit.kit_label) as tracking_id, "
                    + "kit.kit_label, kit.express, kit.test_result, kit.needs_approval, kit.authorization, kit.denial_reason, "
                    + "kit.authorized_by, kit.ups_tracking_status, kit.ups_return_status, kit.CE_order, kit.collection_date, "
                    + "activity.ups_status_description, pack.tracking_number, kit.sequencing_restriction, kit.sample_notes "
                    + "FROM ddp_kit_request request "
                    + "LEFT JOIN ddp_kit kit on (kit.dsm_kit_request_id = request.dsm_kit_request_id) "
                    + "LEFT JOIN ddp_instance realm on (realm.ddp_instance_id = request.ddp_instance_id) "
                    + "LEFT JOIN kit_type kt on (request.kit_type_id = kt.kit_type_id) "
                    + " left join  ups_shipment shipment on (shipment.dsm_kit_request_id = kit.dsm_kit_request_id) "
                    + " left join ups_package pack on ( pack.ups_shipment_id = shipment.ups_shipment_id) "
                    + " left join   ups_activity activity on (pack.ups_package_id = activity.ups_package_id) "
                    + " where (shipment.ups_shipment_id is null or activity.ups_activity_id is null  or  activity.ups_activity_id in  "
                    + " ( SELECT ac.ups_activity_id FROM ups_package pac INNER JOIN  "
                    + "    ( SELECT  ups_package_id, MAX(ups_activity_id) maxId   FROM ups_activity  "
                    + "    GROUP BY ups_package_id  ) lastActivity ON pac.ups_package_id = lastActivity.ups_package_id INNER JOIN  "
                    + "    ups_activity ac ON lastActivity.ups_package_id = ac.ups_package_id  "
                    + "    AND lastActivity.maxId = ac.ups_activity_id ))";
    public static final String KIT_AFTER_BOOKMARK_ORDER_BY_ID = " and kit.dsm_kit_request_id > ? ORDER BY request.dsm_kit_request_id";
    public static final String SQL_SELECT_KIT_REQUEST_BY_PARTICIPANT_ID =
            "SELECT * FROM ddp_kit_request req LEFT JOIN ddp_kit kit ON (req.dsm_kit_request_id = kit.dsm_kit_request_id) "
                    + "LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = req.ddp_instance_id) "
                    + "LEFT JOIN kit_type ty ON (req.kit_type_id = ty.kit_type_id) WHERE req.ddp_participant_id = ? "
                    + "AND realm.instance_name = ?";
    public static final String SQL_SELECT_KIT =
            "SELECT * FROM (SELECT realm.instance_name, re.dsm_kit_request_id, kt.kit_type_name FROM ddp_kit_request re, ddp_instance realm, kit_type kt "
                    + "WHERE realm.ddp_instance_id = re.ddp_instance_id and kt.kit_type_id = re.kit_type_id) AS request "
                    + "LEFT JOIN (SELECT * FROM (SELECT k.easypost_to_id, k.easypost_return_id, "
                    + "k.deactivated_date, k.deactivation_reason, k.dsm_kit_request_id, k.easypost_address_id_to, k.dsm_kit_id "
                    + "FROM ddp_kit k INNER JOIN( "
                    + "SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id FROM ddp_kit "
                    + "GROUP BY dsm_kit_request_id) groupedtt ON k.dsm_kit_request_id = groupedtt.dsm_kit_request_id "
                    + "AND k.dsm_kit_id = groupedtt.kit_id) AS wtf) AS kit ON kit.dsm_kit_request_id = request.dsm_kit_request_id"
                    + " WHERE request.dsm_kit_request_id = ?";

    public static final String INSERT_KIT_REQUEST = "insert into ddp_kit_request (ddp_instance_id,  ddp_kit_request_id, kit_type_id,"
            + "  ddp_participant_id, bsp_collaborator_participant_id, bsp_collaborator_sample_id, ddp_label, created_by, created_date,"
            + " external_order_number, upload_reason) values (?,?,?,?,?,?,?,?,?,?,?)";
    public static final String DEACTIVATION_REASON = "Generated Express";
    public static final String UPLOADED = "uploaded";
    private static final Logger logger = LoggerFactory.getLogger(KitRequestShipping.class);
    private static final String SQL_UPDATE_KIT_REQUEST =
            "UPDATE ddp_kit_request SET bsp_collaborator_participant_id = ?, bsp_collaborator_sample_id = ? WHERE dsm_kit_request_id = ? "
                    + "AND bsp_collaborator_participant_id is null AND bsp_collaborator_sample_id is null";
    private static final String SQL_UPDATE_KIT =
            "UPDATE ddp_kit SET label_by = ?, label_date = ? WHERE dsm_kit_id = ? AND label_date is null";
    private static final String UPDATE_KIT_DEACTIVATION =
            "UPDATE ddp_kit kit INNER JOIN(SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id FROM ddp_kit "
                    + "GROUP BY dsm_kit_request_id) groupedKit "
                    + "ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id AND kit.dsm_kit_id = groupedKit.kit_id "
                    + "SET deactivated_date = ?,deactivation_reason = ?, deactivated_by = ? WHERE kit.dsm_kit_request_id = ?";
    private static final String INSERT_KIT =
            "INSERT INTO ddp_kit (dsm_kit_request_id, easypost_address_id_to,  error, message, needs_approval) VALUES (?,?,?,?,?)";
    private static final String UPDATE_KIT =
            "UPDATE ddp_kit SET label_url_to = ?, label_url_return = ?, easypost_to_id = ?, easypost_return_id = ?, tracking_to_id = ?, "
                    + "tracking_return_id = ?, easypost_tracking_to_url = ?, easypost_tracking_return_url = ?, error = ?, message = ?, "
                    + "easypost_address_id_to = ?, express = ? "
                    + "WHERE dsm_kit_id = ?";
    private static final String UPDATE_KIT_AUTHORIZE =
            "UPDATE ddp_kit kit INNER JOIN(SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id FROM ddp_kit "
                    + "GROUP BY dsm_kit_request_id) groupedKit "
                    + "ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id AND kit.dsm_kit_id = groupedKit.kit_id "
                    + "SET authorization = ?, authorization_date = ?, "
                    + "denial_reason = ?, authorized_by = ? WHERE kit.dsm_kit_request_id = ?";
    private static final String MARK_ORDER_AS_TRANSMITTED =
            "update ddp_kit_request set order_transmitted_at = ? where external_order_number = ?";

    private static final String GET_COUNT_KITS_WITH_SAME_COLLABORATOR_SAMPLE_ID_AND_KIT_TYPE =
            " SELECT count(*) kitRequestCount from ddp_kit_request where bsp_collaborator_sample_id REGEXP \"^%1\" and kit_type_id = ?";

    public static final String SELECT_1_FROM_KIT_REQUEST_WITH_DDP_LABEL =
            " select 1 from ddp_kit_request req  where req.ddp_label = ? ";

    private static final String QUEUE = "queue";
    private static final String ERROR = "error";
    private static final String SENT = "sent";
    private static final String RECEIVED = "received";
    public static final String OVERVIEW = "overview";
    private static final String DEACTIVATED = "deactivated";
    private static final String TRIGGERED = "triggered";
    private static final String WAITING = "waiting";

    private static final String PARTICIPANT_NOT_FOUND_MESSAGE = "Participant was not found at ";
    private static final String NO_PARTICIPANT_INFORMATION = "Please contact your DSM developer ";
    private static final String PARTICIPANT_NOT_RETRIEVED_MESSAGE = "Couldn't get participant address from easypost ";

    private static final int COLLABORATOR_MAX_LENGTH = 200;
    private static final String CARRIER_FEDEX = "FedEx";

    private static final String SHORT_ID = "SHORT_ID";
    private static final String SEARCH_TRACKING_NUMBER = "TRACKING_NUMBER";
    private static final String SEARCH_MF_BAR = "MF_BAR";

    @ColumnName(DBConstants.DSM_KIT_ID)
    private Long dsmKitId;

    @ColumnName(DBConstants.DDP_KIT_REQUEST_ID)
    private String ddpKitRequestId;

    private String ddpParticipantId;

    @ColumnName(DBConstants.LABEL_URL_TO)
    private String labelUrlTo;

    @ColumnName(DBConstants.LABEL_URL_RETURN)
    private String labelUrlReturn;

    @ColumnName(DBConstants.DSM_TRACKING_TO)
    private String trackingToId;

    @ColumnName(DBConstants.DSM_TRACKING_RETURN)
    private String trackingReturnId;

    @ColumnName(DBConstants.TRACKING_ID)
    private String trackingId;

    @ColumnName(DBConstants.DSM_TRACKING_URL_TO)
    private String easypostTrackingToUrl;

    @ColumnName(DBConstants.DSM_TRACKING_URL_RETURN)
    private String easypostTrackingReturnUrl;

    private String firstName;
    private String lastName;
    private String dateOfBirth;

    @ColumnName(DBConstants.BSP_COLLABORATOR_SAMPLE_ID)
    private String bspCollaboratorSampleId;

    @ColumnName(DBConstants.COLLABORATOR_PARTICIPANT_ID)
    private String bspCollaboratorParticipantId;
    private String easypostAddressId;
    private String realm;

    @ColumnName(DBConstants.KIT_TYPE_NAME)
    private String kitTypeName;

    // at runtime, this must be an integer that maps to kit_type_id foreign key

    @JsonProperty("displayName")
    private String displayName;

    private String kitTypeId;

    @ColumnName(DBConstants.DEACTIVATION_REASON)
    private String deactivationReason;

    @ColumnName(DBConstants.KIT_LABEL)
    private String kitLabel;

    @ColumnName(DBConstants.KIT_TEST_RESULT)
    private String testResult;
    @ColumnName(DBConstants.DSM_SCAN_DATE)
    @DbDateConversion(SqlDateConverter.EPOCH)
    private Long scanDate;
    @ColumnName(DBConstants.DSM_RECEIVE_DATE)
    @DbDateConversion(SqlDateConverter.EPOCH)
    private Long receiveDate;
    @ColumnName(DBConstants.DSM_DEACTIVATED_DATE)
    @DbDateConversion(SqlDateConverter.EPOCH)
    private Long deactivatedDate;
    @ColumnName(DBConstants.EXPRESS)
    private Boolean express;
    @ColumnName(DBConstants.EASYPOST_TO_ID)
    private String easypostToId;
    @ColumnName(DBConstants.LABEL_TRIGGERED_DATE)
    private Long labelDate;
    @ColumnName(DBConstants.NO_RETURN)
    private Boolean noReturn;
    @ColumnName(DBConstants.ERROR)
    private Boolean error;
    @ColumnName(DBConstants.MESSAGE)
    private String message;
    @ColumnName(DBConstants.EASYPOST_SHIPMENT_STATUS)
    private String easypostShipmentStatus;
    private String nameLabel;
    @ColumnName(DBConstants.CREATED_BY)
    private String createdBy;

    private Long createdDate;

    private String preferredLanguage;
    private String hruid;
    private String gender;
    private String receiveDateString;
    @ColumnName(DBConstants.UPS_TRACKING_STATUS)
    private String upsTrackingStatus;
    @ColumnName(DBConstants.UPS_RETURN_STATUS)
    private String upsReturnStatus;
    @ColumnName(DBConstants.CARE_EVOLVE)
    private Boolean careEvolve;
    @ColumnName(DBConstants.UPLOAD_REASON)
    private String uploadReason;
    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    private Long ddpInstanceId;
    @ColumnName(DBConstants.COLLECTION_DATE)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String collectionDate;
    @ColumnName(DBConstants.SEQUENCING_RESTRICTION)
    private String sequencingRestriction;
    @ColumnName(DBConstants.DSM_RECEIVE_BY)
    private String receivedBy;
    @ColumnName(DBConstants.SAMPLE_NOTES)
    private String sampleNotes;

    private Boolean requiresInsertInKitTracking;
    private String kitLabelPrefix;
    private Long kitLabelLength;

    private static final KitRequestDao kitRequestDao = new KitRequestDao();

    public KitRequestShipping() {
    }

    public KitRequestShipping(long ddpInstanceId) {
        this.ddpInstanceId = ddpInstanceId;
    }

    public KitRequestShipping(String bspCollaboratorParticipantId, String kitTypeName, Integer dsmKitRequestId,
                              Long scanDate, Boolean error, Long receiveDate, Long deactivatedDate, String testResult,
                              String upsTrackingStatus, String upsReturnStatus, String externalOrderStatus,
                              String externalOrderNumber, Long externalOrderDate, Boolean careEvolve,
                              String uploadReason) {
        this(null, bspCollaboratorParticipantId, null, null, null, kitTypeName, dsmKitRequestId, null, null, null, null, null, null, null,
                scanDate, error, null, receiveDate, null, deactivatedDate, null, null, null, null, null, null, externalOrderNumber, null,
                externalOrderStatus, null, testResult, upsTrackingStatus, upsReturnStatus, externalOrderDate, careEvolve, uploadReason,
                null, null, null, null, null, null, null, null, null, null);
    }

    public KitRequestShipping(String participantId, String bspCollaboratorParticipantId, String dsmKitId, String realm, String trackingToId,
                              String receiveDateString, String hruid, String gender) {
        this(participantId, bspCollaboratorParticipantId, null, null, realm, null, null, null, null, null, trackingToId, null, null, null,
                null, null, null, null, null, null, null, dsmKitId, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, receiveDateString, hruid, gender, null, null, null, null, null, null, null);
    }

    public KitRequestShipping(Integer dsmKitRequestId, Long dsmKitId, String easypostToId, String easypostAddressId, Boolean error,
                              String message) {
        this(null, null, null, null, null, null, dsmKitRequestId, dsmKitId, null, null, null, null, null, null, null, error, message, null,
                easypostAddressId, null, null, null, null, easypostToId, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    public KitRequestShipping(Integer dsmKitRequestId, Long dsmKitId, String easypostToId, String easypostAddressId, Boolean error,
                              String message, String kitTypeName) {
        this(null, null, null, null, null, kitTypeName, dsmKitRequestId, dsmKitId, null, null, null, null, null, null, null, error, message, null,
                easypostAddressId, null, null, null, null, easypostToId, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    // shippingId = ddp_label !!!
    public KitRequestShipping(String participantId, String bspCollaboratorParticipantId, String bspCollaboratorSampleId, String shippingId,
                              String realm, String kitTypeName, Integer dsmKitRequestId, Long dsmKitId, String labelUrlTo,
                              String labelUrlReturn, String trackingToId, String trackingReturnId, String easypostTrackingToUrl,
                              String trackingUrlReturn, Long scanDate, Boolean error, String message, Long receiveDate,
                              String easypostAddressId, Long deactivatedDate, String deactivationReason, String kitLabel, Boolean express,
                              String easypostToId, Long labelDate, String easypostShipmentStatus, String externalOrderNumber,
                              Boolean noReturn, String externalOrderStatus, String createdBy, String testResult, String upsTrackingStatus,
                              String upsReturnStatus, Long externalOrderDate, Boolean careEvolve, String uploadReason,
                              String receiveDateString, String hruid, String gender, String collectionDate, String sequencingRestriction,
                              String receivedBy, String sampleNotes, String kitLabelPrefix, Long kitLabelLength,
                              String kitTypeDisplayName) {
        super(dsmKitRequestId, participantId, null, shippingId, externalOrderNumber, null, externalOrderStatus, null, externalOrderDate);
        this.bspCollaboratorParticipantId = bspCollaboratorParticipantId;
        this.bspCollaboratorSampleId = bspCollaboratorSampleId;
        this.realm = realm;
        this.kitTypeName = kitTypeName;
        this.dsmKitId = dsmKitId;
        this.labelUrlTo = labelUrlTo;
        this.labelUrlReturn = labelUrlReturn;
        this.trackingToId = trackingToId;
        this.trackingReturnId = trackingReturnId;
        this.easypostTrackingToUrl = easypostTrackingToUrl;
        this.easypostTrackingReturnUrl = trackingUrlReturn;
        this.scanDate = scanDate;
        this.error = error;
        this.message = message;
        this.receiveDate = receiveDate;
        this.easypostAddressId = easypostAddressId;
        this.deactivatedDate = deactivatedDate;
        this.deactivationReason = deactivationReason;
        this.kitLabel = kitLabel;
        this.express = express;
        this.easypostToId = easypostToId;
        this.labelDate = labelDate;
        this.easypostShipmentStatus = easypostShipmentStatus;
        this.noReturn = noReturn;
        this.createdBy = createdBy;
        this.testResult = testResult;
        this.upsTrackingStatus = upsTrackingStatus;
        this.upsReturnStatus = upsReturnStatus;
        this.careEvolve = careEvolve;
        this.uploadReason = uploadReason;
        this.receiveDateString = receiveDateString;
        this.hruid = hruid;
        this.gender = gender;
        this.collectionDate = collectionDate;
        this.sequencingRestriction = sequencingRestriction;
        this.receivedBy = receivedBy;
        this.sampleNotes = sampleNotes;
        this.kitLabelPrefix = kitLabelPrefix;
        this.kitLabelLength = kitLabelLength;
        this.displayName = kitTypeDisplayName;
        if (StringUtils.isBlank(displayName)) {
            this.displayName = this.kitTypeName;
        }
    }

    public static KitRequestShipping getKitRequestShipping(@NonNull ResultSet rs) throws SQLException {
        String returnTrackingId = rs.getString(DBConstants.TRACKING_ID);
        if (StringUtils.isBlank(returnTrackingId)) {
            returnTrackingId = rs.getString(DBConstants.DSM_TRACKING_RETURN);
        }
        KitRequestShipping kitRequestShipping =
                new KitRequestShipping(rs.getString(DBConstants.DDP_PARTICIPANT_ID), rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID),
                        rs.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID), rs.getString(DBConstants.DSM_LABEL),
                        rs.getString(DBConstants.INSTANCE_NAME), rs.getString(DBConstants.KIT_TYPE_NAME),
                        rs.getInt(DBConstants.DSM_KIT_REQUEST_ID), rs.getLong(DBConstants.DSM_KIT_ID),
                        rs.getString(DBConstants.DSM_LABEL_TO), rs.getString(DBConstants.DSM_LABEL_RETURN),
                        rs.getString(DBConstants.DSM_TRACKING_TO), returnTrackingId, rs.getString(DBConstants.DSM_TRACKING_URL_TO),
                        rs.getString(DBConstants.DSM_TRACKING_URL_RETURN), (Long) rs.getObject(DBConstants.DSM_SCAN_DATE),
                        rs.getBoolean(DBConstants.ERROR), rs.getString(DBConstants.MESSAGE),
                        (Long) rs.getObject(DBConstants.DSM_RECEIVE_DATE),
                        rs.getString(DBConstants.EASYPOST_ADDRESS_ID_TO), (Long) rs.getObject(DBConstants.DSM_DEACTIVATED_DATE),
                        rs.getString(DBConstants.DEACTIVATION_REASON), rs.getString(DBConstants.KIT_LABEL),
                        rs.getBoolean(DBConstants.EXPRESS), rs.getString(DBConstants.EASYPOST_TO_ID),
                        (Long) rs.getObject(DBConstants.LABEL_TRIGGERED_DATE), rs.getString(DBConstants.EASYPOST_SHIPMENT_STATUS),
                        rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER), rs.getBoolean(DBConstants.NO_RETURN),
                        rs.getString(DBConstants.EXTERNAL_ORDER_STATUS), rs.getString(DBConstants.CREATED_BY),
                        rs.getString(DBConstants.KIT_TEST_RESULT), rs.getString(DBConstants.UPS_TRACKING_STATUS),
                        rs.getString(DBConstants.UPS_RETURN_STATUS), (Long) rs.getObject(DBConstants.EXTERNAL_ORDER_DATE),
                        rs.getBoolean(DBConstants.CARE_EVOLVE), rs.getString(DBConstants.UPLOAD_REASON), null, null, null,
                        rs.getString(DBConstants.COLLECTION_DATE), rs.getString(DBConstants.SEQUENCING_RESTRICTION),
                        rs.getString(DBConstants.DSM_RECEIVE_BY), rs.getString(DBConstants.SAMPLE_NOTES),
                        rs.getString(DBConstants.KIT_LABEL_PREFIX), rs.getLong(DBConstants.KIT_LABEL_LENGTH),
                        rs.getString(DBConstants.KIT_DISPLAY_NAME));
        kitRequestShipping.setDdpParticipantId(rs.getString(DBConstants.DDP_PARTICIPANT_ID));
        kitRequestShipping.setDdpInstanceId(rs.getLong(DBConstants.DDP_INSTANCE_ID));
        if (DBUtil.columnExists(rs, DBConstants.UPS_STATUS_DESCRIPTION) && StringUtils.isNotBlank(
                rs.getString(DBConstants.UPS_STATUS_DESCRIPTION))) {
            String upsPackageTrackingNumber = rs.getString(DBConstants.UPS_PACKAGE_TABLE_ABBR + DBConstants.UPS_TRACKING_NUMBER);
            if (StringUtils.isNotBlank(upsPackageTrackingNumber) && upsPackageTrackingNumber.equals(kitRequestShipping.getTrackingToId())) {
                kitRequestShipping.setUpsTrackingStatus(rs.getString(DBConstants.UPS_STATUS_DESCRIPTION));

            } else if (StringUtils.isNotBlank(upsPackageTrackingNumber) && upsPackageTrackingNumber.equals(
                    kitRequestShipping.getTrackingReturnId())) {
                kitRequestShipping.setUpsReturnStatus(rs.getString(DBConstants.UPS_STATUS_DESCRIPTION));
            }

        }
        return kitRequestShipping;
    }

    /**
     * This method is used to get the KitRequestShipping from the result set, and only extracts values in the
     * ddp_kit_request tables.
     * */
    public static KitRequestShipping getKitRequestFromResultSet(@NonNull ResultSet rs) throws SQLException {
        KitRequestShipping kitRequestShipping = KitRequestShipping.builder()
                .withDdpParticipantId(rs.getString(DBConstants.DDP_PARTICIPANT_ID))
                .withBspCollaboratorParticipantId(rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID))
                .withBspCollaboratorSampleId(rs.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID))
                .withDdpLabel(rs.getString(DBConstants.DSM_LABEL))
                .withDdpKitRequestId(rs.getString(DBConstants.DDP_KIT_REQUEST_ID))
                .withDsmKitRequestId(rs.getInt(DBConstants.DSM_KIT_REQUEST_ID))
                .withCreatedBy(rs.getString(DBConstants.CREATED_BY))
                .withDdpInstanceId(rs.getLong(DBConstants.DDP_INSTANCE_ID)).build();
        return kitRequestShipping;
    }

    private static Map<String, List<KitRequestShipping>> getKitRequestsByKitTypeId(@NonNull String realm, String target, Integer kitTypeId,
                                                                                   boolean getAll, String selectSQL) {
        Map<String, List<KitRequestShipping>> kitRequests = new HashMap<>();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = getSelectKitReqPreparedStatement(conn, target, realm, kitTypeId, getAll, selectSQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        addKitRequest(rs, kitRequests);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error getting list of kitRequests for " + realm, results.resultException);
        }
        return kitRequests;
    }

    private static void addKitRequest(ResultSet rs, Map<String, List<KitRequestShipping>> kitRequests) throws SQLException {
        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
        List<KitRequestShipping> kitRequestList = new ArrayList<>();
        if (kitRequests.containsKey(ddpParticipantId)) {
            kitRequestList = kitRequests.get(ddpParticipantId);
        } else {
            kitRequests.put(ddpParticipantId, kitRequestList);
        }
        kitRequestList.add(getKitRequestShipping(rs));
    }

    private static void addUPSKitRequest(ResultSet rs, Map<String, List<KitRequestShipping>> kitRequests) throws SQLException {
        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
        List<KitRequestShipping> kitRequestList = new ArrayList<>();
        KitRequestShipping krs = getKitRequestShipping(rs);
        if (kitRequests.containsKey(ddpParticipantId)) {
            kitRequestList = kitRequests.get(ddpParticipantId);
            boolean kitPresent =
                    kitRequestList.stream().filter(k -> k.getDsmKitRequestId() == krs.getDsmKitRequestId()).findFirst().isPresent();
            if (kitRequestList != null && kitPresent) {
                KitRequestShipping kit =
                        kitRequestList.stream().filter(k -> k.getDsmKitRequestId() == krs.getDsmKitRequestId()).findFirst().get();
                kitRequestList.removeIf((k -> k.getDsmKitRequestId() == krs.getDsmKitRequestId()));
                if (StringUtils.isBlank(kit.getUpsTrackingStatus()) && StringUtils.isNotBlank(krs.getUpsTrackingStatus())) {
                    kit.setUpsTrackingStatus(krs.getUpsTrackingStatus());
                } else if (StringUtils.isBlank(kit.getUpsReturnStatus()) && StringUtils.isNotBlank(krs.getUpsReturnStatus())) {
                    kit.setUpsReturnStatus(krs.getUpsReturnStatus());
                }
                kitRequestList.add(kit);
            } else {
                if (kitRequestList == null) {
                    kitRequestList = new ArrayList<>();
                }
                kitRequestList.add(krs);
                kitRequests.put(ddpParticipantId, kitRequestList);
            }
        } else {
            kitRequestList.add(krs);
            kitRequests.put(ddpParticipantId, kitRequestList);
        }
    }

    public static List<KitRequestShipping> getKitRequestsByParticipant(@NonNull String realm, @NonNull String ddpParticipantId,
                                                                       boolean showNotReceived) {
        List<KitRequestShipping> kitRequests = new ArrayList<>();

        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST_BY_PARTICIPANT_ID)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        KitRequestShipping kitRequest = new KitRequestShipping(rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID),
                                rs.getString(DBConstants.KIT_TYPE_NAME), rs.getInt(DBConstants.DSM_KIT_REQUEST_ID),
                                rs.getLong(DBConstants.DSM_SCAN_DATE), false, rs.getLong(DBConstants.DSM_RECEIVE_DATE),
                                rs.getLong(DBConstants.DSM_DEACTIVATED_DATE), rs.getString(DBConstants.KIT_TEST_RESULT),
                                rs.getString(DBConstants.UPS_TRACKING_STATUS), rs.getString(DBConstants.UPS_RETURN_STATUS),
                                rs.getString(DBConstants.EXTERNAL_ORDER_STATUS), rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER),
                                rs.getLong(DBConstants.EXTERNAL_ORDER_DATE), rs.getBoolean(DBConstants.CARE_EVOLVE),
                                rs.getString(DBConstants.UPLOAD_REASON));
                        if (showNotReceived) {
                            if (kitRequest.getReceiveDate() == 0 && kitRequest.getDeactivatedDate() == 0) {
                                kitRequests.add(kitRequest);
                            }
                        } else {
                            kitRequests.add(kitRequest);
                        }
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error getting list of kitRequests for " + realm, results.resultException);
        }
        return kitRequests;
    }

    /**
     * Read KitRequests form ddp_kit_request
     * request participants information from ddp
     */
    public static List<KitRequestShipping> getKitRequestsByRealm(@NonNull String realm, String target, String kitTypeName) {
        if (StringUtils.isNotBlank(realm) && StringUtils.isNotBlank(kitTypeName)) {
            List<KitSubKits> subKits = KitSubKits.getSubKits(realm, kitTypeName);
            //selected kit type has sub kits, so query for them
            if (subKits != null && !subKits.isEmpty()) {
                List<KitRequestShipping> wholeList = new ArrayList<>();
                for (KitSubKits kit : subKits) {
                    if (!kit.isHideOnSamplePages()) {
                        Collection<List<KitRequestShipping>> kits =
                                getAllKitRequestsByRealm(realm, target, kit.getKitTypeId(), false).values();
                        for (List<KitRequestShipping> kitRequestList : kits) {
                            wholeList.addAll(kitRequestList);
                        }
                    } else if (!target.equals(UPLOADED) && !target.equals(ERROR) && !target.equals(QUEUE) && !target.equals(DEACTIVATED)) {
                        Collection<List<KitRequestShipping>> kits =
                                getAllKitRequestsByRealm(realm, target, kit.getKitTypeId(), false).values();
                        for (List<KitRequestShipping> kitRequestList : kits) {
                            wholeList.addAll(kitRequestList);
                        }
                    }
                }
                return wholeList;
            }
        }
        List<KitRequestShipping> wholeList = new ArrayList<>();
        KitTypeDto kitTypes = KitTypeImpl.getKitTypes(realm, kitTypeName);
        Collection<List<KitRequestShipping>> kits =
                getAllKitRequestsByRealm(realm, target, kitTypes.getKitTypeId(), false).values();
        for (List<KitRequestShipping> kitRequestList : kits) {
            wholeList.addAll(kitRequestList);
        }
        ElasticSearch participantsByIds = new ElasticSearch()
                .getParticipantsByIds(new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow().getEsParticipantIndex(),
                        wholeList.stream().map(KitRequestShipping::getDdpParticipantId).collect(Collectors.toList()));
        List<ElasticSearchParticipantDto> esParticipants = participantsByIds.getEsParticipants();
        for (KitRequestShipping kit : wholeList) {
            esParticipants.stream().filter(elasticSearchParticipantDto ->
                            existsParticipant(kit, elasticSearchParticipantDto))
                    .findFirst()
                    .ifPresent(elasticSearchParticipantDto -> setFirstLastShortIdDOB(kit, elasticSearchParticipantDto));
        }
        return wholeList;
    }

    private static void setFirstLastShortIdDOB(KitRequestShipping kit, ElasticSearchParticipantDto elasticSearchParticipantDto) {
        kit.setFirstName(elasticSearchParticipantDto.getProfile().map(Profile::getFirstName).orElse(StringUtils.EMPTY));
        kit.setLastName(elasticSearchParticipantDto.getProfile().map(Profile::getLastName).orElse(StringUtils.EMPTY));
        kit.setDateOfBirth(elasticSearchParticipantDto.getDsm().map(Dsm::getDateOfBirth).orElse(StringUtils.EMPTY));
        kit.setShortId(elasticSearchParticipantDto.getProfile().map(Profile::getHruid).orElse(StringUtils.EMPTY));
    }

    private static boolean existsParticipant(KitRequestShipping kit, ElasticSearchParticipantDto elasticSearchParticipantDto) {
        return Objects.nonNull(elasticSearchParticipantDto.getParticipantId())
                && elasticSearchParticipantDto.getParticipantId().equals(kit.getDdpParticipantId());
    }

    public static Map<String, List<KitRequestShipping>> getAllKitRequestsByRealm(@NonNull String realm, String target,
                                                                                 Integer kitTypeId, boolean getAll) {
        logger.info("Collecting kit information");
        Map<String, List<KitRequestShipping>> kitRequests = getKitRequestsByKitTypeId(realm, target, kitTypeId, getAll, null);
        if (!kitRequests.isEmpty() && !getAll) {
            if (StringUtils.isBlank(realm)) {
                logger.info("Found " + kitRequests.size() + " " + target + " KitRequests across all realms ");
            } else {
                logger.info("Found " + kitRequests.size() + " " + target + " KitRequests for " + realm);
            }
            //if queue get address if instance = rgp
            //or if kits no label > for shortId
            if (ERROR.equals(target) || QUEUE.equals(target) || UPLOADED.equals(target) || DEACTIVATED.equals(target) || TRIGGERED.equals(
                    target) || OVERVIEW.equals(target) || WAITING.equals(target)) {

                DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.NEEDS_NAME_LABELS);
                Map<String, Map<String, Object>> participantsESData = null;
                if (ddpInstance.hasEsIndex()) {
                    participantsESData =
                            ElasticSearchUtil.getDDPParticipantsFromES(ddpInstance.getName(), ddpInstance.getParticipantIndexES());
                }

                for (String key : kitRequests.keySet()) {
                    List<KitRequestShipping> kitRequest = kitRequests.get(key);
                    DDPParticipant ddpParticipant = null;
                    boolean checkedParticipant = false;

                    for (KitRequestShipping kit : kitRequest) {
                        if (StringUtils.isNotBlank(kit.getRealm())) {
                            if (participantsESData != null && !participantsESData.isEmpty()) {
                                kit.setPreferredLanguage(ElasticSearchUtil.getPreferredLanguage(participantsESData, key));
                            }
                            // ERROR need address; QUEUE need name label if realm = RGP
                            // UPLOADED and DEACTIVATED and TRIGGERED and WAITING need shortId if getCollaboratorParticipantId is blank
                            if ((ERROR.equals(target) || ((QUEUE.equals(target) || UPLOADED.equals(target)) && ddpInstance.isHasRole()))
                                    || (
                                    (UPLOADED.equals(target) || DEACTIVATED.equals(target) || TRIGGERED.equals(target) || OVERVIEW.equals(
                                            target) || WAITING.equals(target))
                                            && !kit.hasBSPCollaboratorParticipantId())) {
                                String apiKey = DSMServer.getDDPEasypostApiKey(ddpInstance.getName());
                                if (StringUtils.isNotBlank(apiKey) && kit.getEasypostAddressId() != null && StringUtils.isNotBlank(
                                        kit.getEasypostAddressId())) {
                                    getAddressPerEasypost(ddpInstance, kit, apiKey);
                                } else {
                                    if (participantsESData != null && !participantsESData.isEmpty()) {
                                        ddpParticipant = ElasticSearchUtil.getParticipantAsDDPParticipant(participantsESData, key);
                                        if (ddpParticipant != null) {
                                            kit.setParticipant(ddpParticipant);
                                        } else {
                                            kit.setMessage(PARTICIPANT_NOT_FOUND_MESSAGE + kit.getRealm());
                                            kit.setError(true);
                                        }
                                    } else {
                                        kit.setMessage(NO_PARTICIPANT_INFORMATION);
                                        kit.setError(true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        logger.info("Got " + kitRequests.size() + " participants kits in DSM DB for " + realm);
        return kitRequests;
    }

    public static Map<String, List<KitRequestShipping>> getKitRequestsForExport(@NonNull String realm, boolean getAll) {
        logger.info("Collecting kit information for ES export");
        //this method will include deactivated kits as well even if it is reactivated so that elastic has the deactivated record
        Map<String, List<KitRequestShipping>> kitRequests = getKitRequestsByKitTypeId(realm, null, null, getAll, SQL_SELECT_KIT_REQUEST_FOR_ES);
        return kitRequests;
    }

    private static PreparedStatement getSelectKitReqPreparedStatement(@NonNull Connection conn, String target, @NonNull String realm,
                                                                      Integer kitTypeId, boolean getAll, String selectQuery) throws SQLException {
        PreparedStatement stmt = null;
        if (selectQuery == null) {
            selectQuery = SQL_SELECT_KIT_REQUEST;
        }
        if (getAll) {
            String query = selectQuery.concat(QueryExtension.BY_REALM);
            stmt = conn.prepareStatement(query);
            stmt.setString(1, realm);
        } else {
            String query = addQueryExtension(target, selectQuery.concat(QueryExtension.BY_REALM_AND_TYPE_ID));
            stmt = conn.prepareStatement(query);
            stmt.setString(1, realm);
            stmt.setInt(2, kitTypeId);
        }
        return stmt;
    }

    private static String addQueryExtension(@NonNull String target, @NonNull String queryString) {
        String query = queryString;
        if (QUEUE.equals(target) || "".equals(target)) {
            query = query.concat(QueryExtension.KIT_NOT_COMPLETE_NO_ERROR);
        } else if (ERROR.equals(target)) {
            query = query.concat(QueryExtension.KIT_NOT_COMPLETE_HAS_ERROR);
        } else if (SENT.equals(target)) {
            query = query.concat(QueryExtension.KIT_COMPLETE);
        } else if (RECEIVED.equals(target)) {
            query = query.concat(QueryExtension.KIT_RECEIVED);
        } else if (UPLOADED.equals(target)) {
            query = query.concat(QueryExtension.KIT_NO_LABEL);
        } else if (DEACTIVATED.equals(target)) {
            query = query.concat(QueryExtension.KIT_DEACTIVATED + " and kit.deactivation_reason != '" + DEACTIVATION_REASON + "'");
        } else if (TRIGGERED.equals(target)) {
            query = query.concat(QueryExtension.KIT_LABEL_TRIGGERED);
        } else if (WAITING.equals(target)) {
            query = query.concat(QueryExtension.KIT_WAITING);
        } else if (!OVERVIEW.equals(target)) {
            throw new DSMBadRequestException("Target is not known");
        }
        return query;
    }

    private static void getAddressPerEasypost(DDPInstance ddpInstance, KitRequestShipping kitRequest, @NonNull String apiKey) {
        try {
            logger.info("Requesting address from easypost address ID " + kitRequest.getEasypostAddressId());
            Address participantAddress = Address.retrieve(kitRequest.getEasypostAddressId(), apiKey);
            DDPParticipant participant =
                    new DDPParticipant(kitRequest.getParticipantId(), null, participantAddress.getName(), participantAddress.getCountry(),
                            participantAddress.getCity(), participantAddress.getZip(), participantAddress.getStreet1(),
                            participantAddress.getStreet2(), participantAddress.getState(),
                            kitRequest.getShortId(kitRequest.getBspCollaboratorParticipantId()), null);
            kitRequest.setParticipant(participant);
            if (ddpInstance.isHasRole()) { //if instance hasRole NEEDS_NAME_LABELS
                kitRequest.setNameLabel(participantAddress.getName());
            }
        } catch (EasyPostException e) {
            logger.error("Couldn't get participant address ", e);
            kitRequest.setMessage(PARTICIPANT_NOT_RETRIEVED_MESSAGE);
            kitRequest.setError(true);
        }
    }

    public static void deactivateKitRequest(int dsmKitRequestId, @NonNull String deactivationReason, String easypostApiKey,
                                            @NonNull String userId, DDPInstanceDto ddpInstanceDto) {
        long deactivatedDate = System.currentTimeMillis();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT_DEACTIVATION)) {
                stmt.setLong(1, deactivatedDate);
                stmt.setString(2, deactivationReason);
                stmt.setString(3, userId);
                stmt.setInt(4, dsmKitRequestId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Deactivated kitRequest w/ dsm_kit_request_id " + dsmKitRequestId);
                } else {
                    throw new DsmInternalError(
                            "Error setting kitRequest " + dsmKitRequestId + " to deactivated. It was updating " + result + " rows");
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error setting kitRequest to deactivated w/ dsm_kit_request_id " + dsmKitRequestId,
                    results.resultException);
        }
        if (Objects.nonNull(ddpInstanceDto)) {
            if (ddpInstanceDto.isESUpdatePossible()) {
                //skip if no elastic index. ex: juniper studies/eating-disorder
                KitRequestShipping kitRequestShipping = new KitRequestShipping();
                kitRequestShipping.setDsmKitRequestId(dsmKitRequestId);
                kitRequestShipping.setDeactivationReason(deactivationReason);
                kitRequestShipping.setDeactivatedDate(deactivatedDate);
                kitRequestShipping.setKitTypeName(kitRequestDao.getKitTypeByKitRequestId(dsmKitRequestId));
                try {
                    //todo: if multiple kits exist for same kit-request(dsmKitRequestId), we are updating all elements? edge case though
                    UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto,
                            ESObjectConstants.DSM_KIT_REQUEST_ID, ESObjectConstants.DSM_KIT_REQUEST_ID, dsmKitRequestId,
                            new PutToNestedScriptBuilder()).export();
                } catch (Exception e) {
                    logger.error("Error updating kit request shipping deactivate reason with dsm kit request id: {} "
                            + " in ElasticSearch. Exception: {}", dsmKitRequestId, e.getMessage());
                    e.printStackTrace();
                }
            }
        } else {
            //todo check out when we need to request refund and probably remove else once we confirm its never invoked.
            logger.error("DDPInstanceDto null for deactivation of kitRequest: {}", dsmKitRequestId);
            if (easypostApiKey != null) {
                logger.info("Requesting refund for kitRequest: {} ", dsmKitRequestId);
                KitRequestShipping.refundKit(dsmKitRequestId, easypostApiKey, ddpInstanceDto);
            }
        }
    }

    public static KitRequestShipping getKitRequest(Integer dsmKitRequestId) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST + KitDao.KIT_BY_KIT_REQUEST_ID)) {
                stmt.setInt(1, dsmKitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    int numRows = 0;
                    while (rs.next()) {
                        numRows++;
                        dbVals.resultValue = getKitRequestShipping(rs);
                    }
                    if (numRows > 1) {
                        throw new DsmInternalError("Found " + numRows + " kits for dsm_kit_request_id " + dsmKitRequestId);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error setting kitRequest to deactivated w/ dsm_kit_request_id " + dsmKitRequestId,
                    results.resultException);
        }
        return (KitRequestShipping) results.resultValue;
    }

    //adding kit request to db (called by hourly job to add kits into DSM)
    public static void addKitRequests(@NonNull String instanceId, @NonNull KitDetail kitDetail, @NonNull int kitTypeId,
                                      @NonNull KitRequestSettings kitRequestSettings, String collaboratorParticipantId,
                                      String externalOrderNumber, String uploadReason, DDPInstance ddpInstance, String subkitsDdpLabel) {
        addKitRequests(instanceId, kitDetail.getKitType(), kitDetail.getParticipantId(), kitDetail.getKitRequestId(), kitTypeId,
                kitRequestSettings, collaboratorParticipantId, kitDetail.isNeedsApproval(), externalOrderNumber, uploadReason, ddpInstance,
                subkitsDdpLabel);
    }

    //adding kit request to db (called by hourly job to add kits into DSM)
    public static void addKitRequests(@NonNull String instanceId, @NonNull String kitType, @NonNull String participantId,
                                      @NonNull String kitRequestId, @NonNull int kitTypeId, @NonNull KitRequestSettings kitRequestSettings,
                                      String collaboratorParticipantId, boolean needsApproval, String externalOrderNumber,
                                      String uploadReason, DDPInstance ddpInstance, String subkitsDdpLabel) {
        inTransaction(conn -> {
            String errorMessage = "";
            String collaboratorSampleId = null;
            String bspCollaboratorSampleType = kitType;
            if (kitRequestSettings.getCollaboratorSampleTypeOverwrite() != null) {
                bspCollaboratorSampleType = kitRequestSettings.getCollaboratorSampleTypeOverwrite();
            }
            if (StringUtils.isNotBlank(collaboratorParticipantId)) {
                collaboratorSampleId =
                        KitRequestShipping.generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, kitTypeId,
                                ddpInstance);
                if (collaboratorParticipantId == null) {
                    errorMessage += "collaboratorParticipantId was too long ";
                }
                if (collaboratorSampleId == null) {
                    errorMessage += "bspCollaboratorSampleId was too long ";
                }
            }
            writeRequest(instanceId, kitRequestId, kitTypeId, participantId, collaboratorParticipantId, collaboratorSampleId, "SYSTEM",
                    null, errorMessage, externalOrderNumber, needsApproval, uploadReason, ddpInstance, bspCollaboratorSampleType,
                    subkitsDdpLabel);
            return null;
        });
    }


    /**
     * Inserts kit in ddp_kit_request table and then ddp_kit table
     * called by
     *      1. hourly job to add kit requests into db
     *      2. kit upload
     *      3. Juniper shipKit route
     *      4. BSP and Mercury dummy kit routes in non-prod
     * @return dsm_kit_request_id of the new kit
     * */
    public static String writeRequest(@NonNull String instanceId, @NonNull String ddpKitRequestId, int kitTypeId,
                                      @NonNull String ddpParticipantId, String bspCollaboratorParticipantId, String collaboratorSampleId,
                                      @NonNull String createdBy, String addressIdTo, String errorMessage, String externalOrderNumber,
                                      boolean needsApproval, String uploadReason, DDPInstance ddpInstance, String kitTypeName,
                                      String subKitddpLabel) {

        String ddpLabel = StringUtils.isBlank(subKitddpLabel)
                ? (StringUtils.isNotBlank(externalOrderNumber) ? null : generateDdpLabelID()) : subKitddpLabel;

        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement insertKitRequest = conn.prepareStatement(INSERT_KIT_REQUEST, Statement.RETURN_GENERATED_KEYS)) {
                insertKitRequest.setString(1, instanceId);
                insertKitRequest.setString(2, ddpKitRequestId);
                insertKitRequest.setInt(3, kitTypeId);
                insertKitRequest.setString(4, ddpParticipantId);
                insertKitRequest.setObject(5, bspCollaboratorParticipantId);
                insertKitRequest.setObject(6, collaboratorSampleId);
                insertKitRequest.setObject(7, ddpLabel); //ddp_label or shipping_id
                insertKitRequest.setString(8, createdBy);
                insertKitRequest.setLong(9, System.currentTimeMillis());
                insertKitRequest.setObject(10,
                        StringUtils.isNotBlank(externalOrderNumber) ? externalOrderNumber : null); //external_order_number
                insertKitRequest.setString(11, uploadReason); //upload reason
                insertKitRequest.executeUpdate();
                try (ResultSet rs = insertKitRequest.getGeneratedKeys()) {
                    if (rs.next()) {
                        KitRequestShipping kitRequestShipping = new KitRequestShipping();
                        kitRequestShipping.setDsmKitRequestId(rs.getInt(1));
                        dbVals.resultValue = kitRequestShipping;
                    }
                } catch (Exception e) {
                    throw new DsmInternalError("Error getting id of new kit request ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            if (dbVals.resultException == null && dbVals.resultValue != null) {
                KitRequestShipping kitRequestShipping = (KitRequestShipping) dbVals.resultValue;
                SimpleResult simpleResultKitWriting = writeNewKit(conn, kitRequestShipping.getDsmKitRequestId(),
                        addressIdTo, errorMessage, needsApproval);
                kitRequestShipping.setDsmKitId((Long) simpleResultKitWriting.resultValue);
                dbVals.resultValue = kitRequestShipping;
            }
            return dbVals;
        });


        if (results.resultException != null) {
            throw new DsmInternalError("Error adding kit request  w/ ddpKitRequestId " + ddpKitRequestId, results.resultException);
        }

        if (Objects.nonNull(ddpInstance)) {
            KitRequestShipping kitRequestShipping = (KitRequestShipping) results.resultValue;
            kitRequestShipping.setParticipantId(ddpParticipantId);
            kitRequestShipping.setBspCollaboratorParticipantId(bspCollaboratorParticipantId);
            kitRequestShipping.setBspCollaboratorSampleId(collaboratorSampleId);
            kitRequestShipping.setMessage(errorMessage);
            kitRequestShipping.setExternalOrderNumber(externalOrderNumber);
            kitRequestShipping.setCreatedBy(createdBy);
            kitRequestShipping.setUploadReason(uploadReason);
            kitRequestShipping.setKitTypeName(kitTypeName);
            kitRequestShipping.setDdpInstanceId((long) ddpInstance.getDdpInstanceIdAsInt());
            kitRequestShipping.setDdpLabel(ddpLabel);

            DDPInstanceDto ddpInstanceDto =
                    new DDPInstanceDao().getDDPInstanceByInstanceId(Integer.valueOf(ddpInstance.getDdpInstanceId())).orElseThrow();
            // update ES only if it's a pepper study, not for Darwin's arc or Juniper studies
            if (ddpInstanceDto.isESUpdatePossible()) {
                try {
                    UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto,
                            ESObjectConstants.DSM_KIT_REQUEST_ID, ESObjectConstants.DOC_ID,
                            Exportable.getParticipantGuid(ddpParticipantId, ddpInstance.getParticipantIndexES()),
                            new PutToNestedScriptBuilder()).export();
                } catch (Exception e) {
                    logger.error(String.format("Error inserting newly created kit request shipping with dsm kit request id: %s in "
                            + "ElasticSearch", kitRequestShipping.getDsmKitRequestId()));
                    e.printStackTrace();
                }
            }

            logger.info("Added kitRequest w/ ddpKitRequestId " + ddpKitRequestId);
            return String.valueOf(kitRequestShipping.getDsmKitRequestId());
        }
        throw new DsmInternalError("Error returning kit request information for ddpKitRequestId " + ddpKitRequestId);
    }

    private static SimpleResult writeNewKit(Connection conn, int kitRequestId, String addressIdTo, String errorMessage,
                                            boolean needsApproval) {
        SimpleResult dbVals = new SimpleResult(0);
        try (PreparedStatement insertKit = conn.prepareStatement(INSERT_KIT, Statement.RETURN_GENERATED_KEYS)) {
            insertKit.setInt(1, kitRequestId);
            if (StringUtils.isNotBlank(addressIdTo)) {
                insertKit.setString(2, addressIdTo);
            } else {
                insertKit.setString(2, null);
            }
            if (StringUtils.isNotBlank(errorMessage) && !KitUtil.IGNORE_AUTO_DEACTIVATION.equals(errorMessage)) {
                insertKit.setInt(3, 1);
            } else {
                insertKit.setInt(3, 0);
            }
            insertKit.setObject(4, errorMessage);
            insertKit.setBoolean(5, needsApproval);
            insertKit.executeUpdate();
            try (ResultSet rs = insertKit.getGeneratedKeys()) {
                if (rs.next()) {
                    dbVals.resultValue = rs.getLong(1);
                }
            } catch (Exception e) {
                throw new DsmInternalError("Error getting id of new kit request ", e);
            }
        } catch (SQLException e) {
            dbVals.resultException = e;
        }
        return dbVals;
    }

    // called by reactivation of a deactivated kit
    public static long writeNewKit(Integer kitRequestId, String addressIdTo, String errorMessage, boolean needsApproval) {
        SimpleResult results = inTransaction(conn -> writeNewKit(conn, kitRequestId, addressIdTo, errorMessage, needsApproval));

        if (results.resultException != null) {
            logger.error("Error writing new kit w/ dsm_kit_id " + kitRequestId, results.resultException);
        } else {
            logger.info("Wrote new kit w/ dsm_kit_id " + kitRequestId, results.resultException);
        }
        return Long.parseLong(String.valueOf(results.resultValue));
    }

    // update kit with label trigger user and date
    public static void updateKit(long dsmKitId, String userId, DDPInstanceDto ddpInstanceDto) {
        long labelDate = System.currentTimeMillis();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_KIT)) {
                stmt.setString(1, userId);
                stmt.setLong(2, labelDate);
                stmt.setLong(3, dsmKitId);

                int result = stmt.executeUpdate();
                if (result > 1) {
                    throw new DsmInternalError("Error updating kit " + dsmKitId + " it was updating " + result + " rows");
                }
                if (result == 0) {
                    logger.warn("Kit " + dsmKitId + " wasn't update. Label_Date was already set");
                }
            } catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error updating kit w/ dsm_kit_id " + dsmKitId, results.resultException);
        } else {
            logger.info("Updated kit w/ kit_id " + dsmKitId, results.resultException);

            KitRequestShipping kitRequestShipping = new KitRequestShipping(null, dsmKitId, null, null, null, null);
            kitRequestShipping.setLabelDate(labelDate);
            upsertUpdatedKitInfoIntoES(ddpInstanceDto, kitRequestShipping, dsmKitId);
        }
    }

    /** updates kit with information gathered during label creation, such as tracking code, label url, possible errors
     * and address
     * @param dsmKitId dsm_kit_id from ddp_kit table
     * @param participantShipment Easypost Shipment object for this participant's kit
     * @param returnShipment EasyPost Shipment object for this participant's return of the kit
     * @param errorMessage  error message if any
     * @param toAddress Easypost Address object for this participant's address
     * @param isExpress boolean if this kit is express shipping
     * @param ddpInstanceDto DDPInstanceDto object of the study that kit belongs to
     */
    public static void updateKit(String dsmKitId, Shipment participantShipment, Shipment returnShipment, String errorMessage,
                                 Address toAddress, boolean isExpress, DDPInstanceDto ddpInstanceDto) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT)) {
                if (participantShipment != null) {
                    PostageLabel participantLabel = participantShipment.getPostageLabel();
                    Tracker participantTracker = participantShipment.getTracker();
                    stmt.setString(1, participantLabel.getLabelUrl());
                    stmt.setString(3, participantShipment.getId());
                    stmt.setString(5, participantShipment.getTrackingCode());
                    stmt.setString(7, participantTracker.getPublicUrl());
                    kitRequestShipping.setLabelUrlTo(participantLabel.getLabelUrl());
                    kitRequestShipping.setEasypostToId(participantShipment.getId());
                    kitRequestShipping.setTrackingToId(participantShipment.getTrackingCode());
                    kitRequestShipping.setEasypostTrackingToUrl(participantTracker.getPublicUrl()); //TODO
                } else {
                    stmt.setString(1, null);
                    stmt.setString(3, null);
                    stmt.setString(5, null);
                    stmt.setString(7, null);
                }
                if (returnShipment != null) {
                    PostageLabel returnLabel = returnShipment.getPostageLabel();
                    Tracker returnTracker = returnShipment.getTracker();
                    stmt.setString(2, returnLabel.getLabelUrl());
                    stmt.setString(4, returnShipment.getId());
                    stmt.setString(6, returnShipment.getTrackingCode());
                    stmt.setString(8, returnTracker.getPublicUrl());
                    kitRequestShipping.setLabelUrlReturn(returnLabel.getLabelUrl());
                    kitRequestShipping.setTrackingReturnId(returnShipment.getTrackingCode());
                    kitRequestShipping.setEasypostTrackingReturnUrl(returnTracker.getPublicUrl());
                } else {
                    stmt.setString(2, null);
                    stmt.setString(4, null);
                    stmt.setString(6, null);
                    stmt.setString(8, null);
                }

                if (StringUtils.isNotBlank(errorMessage)) {
                    stmt.setInt(9, 1);
                    stmt.setString(10, errorMessage);
                    kitRequestShipping.setError(true);
                    kitRequestShipping.setMessage(errorMessage);
                    logger.info("Added kit request with error message " + errorMessage);
                } else {
                    stmt.setInt(9, 0);
                    stmt.setString(10, null);
                    kitRequestShipping.setError(false);
                }

                if (toAddress != null) {
                    stmt.setString(11, toAddress.getId());
                    kitRequestShipping.setEasypostAddressId(toAddress.getId());
                } else {
                    stmt.setString(11, null);
                }
                if (isExpress) {
                    stmt.setInt(12, 1);
                    kitRequestShipping.setExpress(true);
                } else {
                    stmt.setInt(12, 0);
                    kitRequestShipping.setExpress(false);
                }
                stmt.setString(13, dsmKitId);
                kitRequestShipping.setDsmKitId(Long.valueOf(dsmKitId));
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new DsmInternalError("Error updating kit " + dsmKitId + " it was updating " + result + " rows");
                }
            } catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error updating kit w/ dsm_kit_id " + dsmKitId, results.resultException);
        } else {
            logger.info("Updated kit w/ dsm_kit_id " + dsmKitId);
            upsertUpdatedKitInfoIntoES(ddpInstanceDto, kitRequestShipping, dsmKitId);
        }
    }

    private static void upsertUpdatedKitInfoIntoES(DDPInstanceDto ddpInstanceDto, KitRequestShipping kitRequestShipping, Object dsmKitId) {
        if (ddpInstanceDto.isESUpdatePossible()) {
            try {
                UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto,
                        ESObjectConstants.DSM_KIT_ID,
                        ESObjectConstants.DSM_KIT_ID, dsmKitId, new PutToNestedScriptBuilder()).export();
            } catch (Exception e) {
                logger.error(String.format("Error updating kit request shipping with dsm kit id: %s in ElasticSearch",
                        kitRequestShipping.getDsmKitId()));
                e.printStackTrace();
            }

        }
    }

    // update request with collaborator ids
    public static void updateKitBspParticipantAndSampleId(@NonNull KitRequestCreateLabel kit, @NonNull DDPParticipant participant,
                                                          @NonNull KitType kitType, @NonNull KitRequestSettings kitRequestSettings) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement updateStmt = conn.prepareStatement(SQL_UPDATE_KIT_REQUEST)) {
                String collaboratorParticipantId = generateBspParticipantID(kit.getCollaboratorIdPrefix(),
                        kitRequestSettings.getCollaboratorParticipantLengthOverwrite(), participant.getShortId());
                String bspCollaboratorSampleType = kitType.getKitTypeName();
                if (kitRequestSettings.getCollaboratorSampleTypeOverwrite() != null) {
                    bspCollaboratorSampleType = kitRequestSettings.getCollaboratorSampleTypeOverwrite();
                }
                DDPInstance ddpInstance = DDPInstance.getDDPInstance(kit.getInstanceName());
                String collaboratorSampleId =
                        generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, kitType.getKitTypeId(),
                                ddpInstance);

                updateStmt.setString(1, collaboratorParticipantId);
                updateStmt.setString(2, collaboratorSampleId);
                updateStmt.setString(3, kit.getDsmKitRequestId());
                int result = updateStmt.executeUpdate();
                if (result > 1) {
                    logger.error("Error updating request " + kit.getDsmKitId() + ". It was updating " + result + " rows");
                }
                if (result == 0) {
                    logger.warn("Kit request " + kit.getDsmKitId() + " wasn't update. Request already had bsp ids.");
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error updating ddp_kit_request ", e);
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error updating kit request w/ id " + kit.getDsmKitId(), results.resultException);
        }
    }

    public static String generateBspParticipantID(String collaboratorIdPrefix, String collaboratorParticipantLength,
                                                  @NonNull String shortId) {
        String name = "";
        if (StringUtils.isNotBlank(collaboratorIdPrefix) && !shortId.startsWith(collaboratorIdPrefix)) {
            name = collaboratorIdPrefix + "_";
        }
        String collaboratorId = name;
        if (collaboratorParticipantLength != null) {
            try {
                int overwriteLength = Integer.parseInt(collaboratorParticipantLength);
                collaboratorId += StringUtils.leftPad(shortId, overwriteLength, "0");
            } catch (Exception e) {
                logger.error("Failed to overwrite collaboratorParticipantId with length " + collaboratorParticipantLength + " from db ", e);
            }
        } else {
            collaboratorId += StringUtils.leftPad(shortId, 4, "0");
        }
        if (collaboratorId.length() < COLLABORATOR_MAX_LENGTH) {
            return collaboratorId;
        } else {
            // this should not happen, let's put an error log here to see if this is happening and why
            logger.error("Error generating collaboratorParticipantId for shortId %s (Got %d characters)".formatted(shortId,
                    collaboratorId.length()));
        }
        return null;
    }

    public static String generateBspSampleID(@NonNull Connection conn, String collaboratorParticipantId, String type, int kitTypeId,
                                             DDPInstance ddpInstance) {
        if (collaboratorParticipantId != null && collaboratorParticipantId.length() < COLLABORATOR_MAX_LENGTH) {
            String collaboratorSampleId = collaboratorParticipantId;
            if (StringUtils.isNotBlank(type)) {
                collaboratorSampleId += "_" + type;
            }

            int counter = getKitCounter(conn, collaboratorSampleId, kitTypeId);

            if  (ddpInstance.isMigratedDDP() && collaboratorParticipantId.contains("_")) {
                String participantId = collaboratorParticipantId.split("_")[1];
                //For Migrated studies, if either of the legacy id or pepper short id is given, DSM needs count the number of kits that
                // were created for this participant with both of these ids and consider that total number when creating the
                // collaborator sample id for this new kit. For example, if participant PABCDE has legacy short id 1, and has saliva
                // kits `0001_SALIVA_1` and `PABCDE_SALIVA_2`, the next kit should be 0001_SALIVA_3, and not 0001_SALIVA_2.
                //Ideally the kits should all have one type of id but because of a bug in the kit creation process, some kits might have
                //been created with the wrong id
                if (!ParticipantUtil.isHruid(participantId)) {
                    //We can assume the participantId is a legacy short id here
                    counter += getPepperKitCounter(conn, collaboratorSampleId, participantId, kitTypeId,
                            ddpInstance.getParticipantIndexES());
                } else {
                    counter += getLegacyKitCounter(conn, collaboratorSampleId, participantId, kitTypeId,
                            ddpInstance.getParticipantIndexES());
                }
            }
            if (counter == 0) {
                if (collaboratorSampleId.length() < COLLABORATOR_MAX_LENGTH) {
                    return collaboratorSampleId;
                }
            }
            String collaboratorId = collaboratorSampleId + "_" + (counter + 1);
            if (collaboratorId.length() < COLLABORATOR_MAX_LENGTH) {
                return collaboratorId;
            }
        }
        return null;
    }

    /**
     * Given a participant's legacy short id, counts the number of participant's kits that have the collaborator sample id with the Pepper
     * short id and with this kit type id
     * */
    private static int getPepperKitCounter(Connection conn, String bspCollaboratorSampleId, String legacyShortId, int kitTypeId,
                                           String index) {
        Optional<ElasticSearchParticipantDto> maybeParticipantDto = new ElasticSearchService()
                .getSingleParticipantDocument(legacyShortId, "profile.legacyShortId", index);
        if (maybeParticipantDto.isEmpty()) {
            return 0;
        }
        ElasticSearchParticipantDto participantDto = maybeParticipantDto.get();
        String pepperCollaboratorSampleId = bspCollaboratorSampleId.replace(legacyShortId,
                participantDto.getProfile().get().getHruid());
        return getKitCounter(conn, pepperCollaboratorSampleId, kitTypeId);
    }

    /**
     * Given a participant's Pepper short id, counts the number of participant's kits that have the collaborator sample id with the Legacy
     * short id and with this kit type id
     * */
    private static int getLegacyKitCounter(Connection conn, String bspCollaboratorSampleId, String shortId, int kitTypeId,
                                           String index) {
        Optional<ElasticSearchParticipantDto> maybeParticipantDto = new ElasticSearchService()
                .getSingleParticipantDocument(shortId, "profile.hruid", index);
        if (maybeParticipantDto.isEmpty()) {
            return 0;
        }
        ElasticSearchParticipantDto participantDto = maybeParticipantDto.get();
        if (participantDto.getProfile().isEmpty() || participantDto.getProfile().get().getLegacyShortId() == null) {
            return 0;
        }
        String pepperCollaboratorSampleId = bspCollaboratorSampleId.replace(shortId,
                participantDto.getProfile().get().getLegacyShortId());
        return getKitCounter(conn, pepperCollaboratorSampleId, kitTypeId);
    }

    public static int getKitCounter(@NonNull Connection conn, String collaboratorSampleId, int kitTypeId) {
        String query = GET_COUNT_KITS_WITH_SAME_COLLABORATOR_SAMPLE_ID_AND_KIT_TYPE.replace("%1", collaboratorSampleId);
        try (PreparedStatement stmt = conn.prepareStatement(query, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            stmt.setInt(1, kitTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.last();
                int count = rs.getRow();
                rs.beforeFirst();
                if (count == 1 && rs.next()) {
                    return rs.getInt(DBConstants.KITREQUEST_COUNT);
                }
                throw new DsmInternalError("Error getting counter for bsp_collaborator_sample_id (Got " + count + " row back)");
            }
        } catch (SQLException e) {
            throw new DsmInternalError("Error getting counter for bsp_collaborator_sample_id ", e);
        }
    }

    /**
     * Generating an GUID
     * and checking it against db if unique
     */
    public static String generateDdpLabelID() {
        return generateDdpLabelID(15, SELECT_1_FROM_KIT_REQUEST_WITH_DDP_LABEL);
    }

    public static String generateDdpLabelID(int length, String query) {
        while (true) {
            try {
                final String labelId = createRandom(length);
                if (!labelId.matches("\\d.*")) { //the first character is not allowed to be a number!
                    SimpleResult results = inTransaction(conn -> {
                        SimpleResult dbVals = new SimpleResult(0);
                        try (PreparedStatement stmt = conn.prepareStatement(query)) {
                            stmt.setString(1, labelId);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) {
                                    dbVals.resultValue = rs.getInt("found");
                                }
                            }
                        } catch (SQLException ex) {
                            dbVals.resultException = ex;
                        }
                        return dbVals;
                    });

                    if (results.resultException != null) {
                        throw new DsmInternalError("Error checking generated random ID", results.resultException);
                    }

                    if ((int) results.resultValue == 0) {
                        return labelId;
                    }
                }
            } catch (Exception ex) {
                throw new DsmInternalError("Error creating random ID ", ex);
            }
        }
    }

    /**
     * Creating a random String
     * consisting of A..Z and 0..9
     *
     * @param len length of random String
     * @return String
     */
    public static String createRandom(int len) {
        Random random = new Random();
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".toCharArray();

        StringBuffer out = new StringBuffer(len);
        for (int i = 0; i < len; i++) {
            int randomInt = random.nextInt(alphabet.length);
            out.append(alphabet[randomInt]);
        }
        return out.toString();
    }

    /**
     * This method is used to get the address where the kit should be sent to. If the addressId or the participant is not given,
     * then it is a return label.
     * In that case, the address is generated based on the return address settings in the kit request settings.
     * */
    public static Address getToAddressId(@NonNull EasyPostUtil easyPostUtil, KitRequestSettings kitRequestSettings, String addressId,
                                         DDPParticipant participant, DDPInstanceDto ddpInstanceDto) throws EasyPostException {
        Address toAddress = null;
        if (isReturnAddress(addressId, participant)) {
            try {
                toAddress = createReturnAddress(kitRequestSettings, easyPostUtil);
            } catch (EasyPostException e) {
                logger.error("Couldn't generate return address for a kit for ddpInstance " + kitRequestSettings.getDdpInstanceId(), e);
            }
        } else {
            if (StringUtils.isNotBlank(addressId)) {
                toAddress = easyPostUtil.getAddress(addressId);
            } else if (participant != null) {
                if (kitRequestSettings.getHasCareOF() != 1) {
                    // aside form singular, all other studies should go here and proceed with normal label
                    toAddress = easyPostUtil.createAddress(participant, kitRequestSettings.getPhone());
                } else {
                    toAddress = getAddressForStudiesWithCareOfField(easyPostUtil, kitRequestSettings, participant, ddpInstanceDto);
                }
            }
        }
        if (toAddress == null) {
            throw new DsmInternalError("Couldn't get address for kit request");
        }
        return toAddress;
    }

    /**
     * This method is used to get the return address in Broad that kit should come back to.
     * That address comes from KitRequestSettings. It is in the kit_return_information table and is collected as part of the
     * kit request settings.
     * */
    public static Address createReturnAddress(KitRequestSettings kitRequestSettings, EasyPostUtil easyPostUtil) throws EasyPostException {
        return easyPostUtil.createAddressWithoutValidation(kitRequestSettings.getReturnName(), kitRequestSettings.getReturnStreet1(),
                kitRequestSettings.getReturnStreet2(), kitRequestSettings.getReturnCity(), kitRequestSettings.getReturnZip(),
                kitRequestSettings.getReturnState(), kitRequestSettings.getReturnCountry(), kitRequestSettings.getPhone());
    }

    /**
     * Addresses are generated based on the addressId from where a kit should go to (given when the kit is uploaded) or the participant's
     * address if the addressId is not given. If the addressId is not given and the participant is null, then it is a return label.
     *
     * @param addressId addressId from ddp_kit table in case of an uploaded kit
     * @param participant participant object in case of a system generated kit
     * @return true if it is a return label, false otherwise
     */
    private static boolean isReturnAddress(String addressId, DDPParticipant participant) {
        return addressId == null && participant == null;
    }

    private static Address getAddressForStudiesWithCareOfField(@NonNull EasyPostUtil easyPostUtil, KitRequestSettings kitRequestSettings,
                                                               DDPParticipant participant, DDPInstanceDto ddpInstanceDto)
            throws EasyPostException {
        Address toAddress = null;
        String proxyFirstName = null;
        String proxyLastName = null;
        Map<String, Map<String, Object>> participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstanceDto,
                ElasticSearchUtil.BY_GUID + participant.getParticipantId());
        if (participantESData != null && !participantESData.isEmpty()) {
            ArrayList<String> proxies =
                    (ArrayList<String>) participantESData.get(participant.getParticipantId()).get(ElasticSearchUtil.PROXIES);
            if (proxies != null && proxies.size() > 0) {
                String proxyParticipantId = proxies.get(0);
                Map<String, Map<String, Object>> proxyESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstanceDto,
                        ElasticSearchUtil.BY_GUID + proxyParticipantId);
                Map<String, Object> proxyProfile = (Map<String, Object>) proxyESData.get(proxyParticipantId)
                        .get(ElasticSearchUtil.PROFILE);
                proxyFirstName = (String) proxyProfile.get("firstName");
                proxyLastName = (String) proxyProfile.get("lastName");
                String careOf = String.format("C/O %s %s", Objects.toString(proxyFirstName, ""),
                        Objects.toString(proxyLastName, ""));
                toAddress = easyPostUtil.createAddress(participant, kitRequestSettings.getPhone(), careOf);
            } else { // participant doesn't have proxies, proceed with normal label
                toAddress = easyPostUtil.createAddress(participant, kitRequestSettings.getPhone());
            }
        } else { // participant is not in ES
            throw new DsmInternalError(String.format("Participant %s was not found in ES", participant.getParticipantId()));
        }
        return toAddress;
    }

    public static Shipment getShipment(@NonNull EasyPostUtil easyPostUtil, String billingReference, KitType kitType,
                                       KitRequestSettings kitRequestSettings, boolean returnLabel, Address toAddress) throws Exception {
        String carrier = null;
        String carrierId = null;
        String service = null;
        if (returnLabel) { //if both are set to null then it is return label!
            carrier = kitRequestSettings.getCarrierReturn();
            carrierId = kitRequestSettings.getCarrierReturnId();
            service = kitRequestSettings.getServiceReturn();
        } else {
            carrier = kitRequestSettings.getCarrierTo();
            carrierId = kitRequestSettings.getCarrierToId();
            service = kitRequestSettings.getServiceTo();
        }
        return getShipment(easyPostUtil, billingReference, kitType, kitRequestSettings, toAddress, carrier, carrierId, service);
    }

    public static Shipment getShipment(@NonNull EasyPostUtil easyPostUtil, String billingReference, KitType kitType,
                                       KitRequestSettings kitRequestSettings, Address toAddress, String carrier, String carrierId,
                                       String service) throws Exception {
        CustomsInfo customsInfo = null;
        if (kitType != null) {

            Address returnAddress =
                    easyPostUtil.createAddressWithoutValidation(kitRequestSettings.getReturnName(), kitRequestSettings.getReturnStreet1(),
                            kitRequestSettings.getReturnStreet2(), kitRequestSettings.getReturnCity(), kitRequestSettings.getReturnZip(),
                            kitRequestSettings.getReturnState(), kitRequestSettings.getReturnCountry(), kitRequestSettings.getPhone());

            Parcel parcel =
                    easyPostUtil.createParcel(kitRequestSettings.getWeight(), kitRequestSettings.getHeight(), kitRequestSettings.getWidth(),
                            kitRequestSettings.getLength());

            if (!"US".equals(toAddress.getCountry()) && kitType.getCustomsJson() != null) {
                customsInfo = easyPostUtil.createCustomsInfo(kitType.getCustomsJson());
            }
            return getEasyPostShipment(easyPostUtil, carrier, carrierId, service, billingReference, toAddress, returnAddress, parcel,
                    customsInfo);
        }
        return null;
    }

    private static Shipment getEasyPostShipment(@NonNull EasyPostUtil easyPostUtil, String carrier, String carrierId, String service,
                                                String billingReference, @NonNull Address toAddress, @NonNull Address senderAddress,
                                                @NonNull Parcel parcel, CustomsInfo customsInfo) throws Exception {
        if (carrier != null) {
            String billingRef = null;
            if (CARRIER_FEDEX.equals(carrier) && billingReference != null) {
                billingRef = billingReference;
            }
            return easyPostUtil.buyShipment(carrier, carrierId, service, toAddress, senderAddress, parcel, billingRef, customsInfo);
        }
        return null;
    }

    public static void refundKit(int kitRequestId, @NonNull String easypostApiKey, DDPInstanceDto ddpInstanceDto) {
        KitShippingIds shippingIds = KitShippingIds.getKitShippingIds(kitRequestId, easypostApiKey);
        if (shippingIds != null) {
            String message = "";
            if (StringUtils.isNotBlank(shippingIds.getEasyPostShipmentToId())) {
                try {
                    Shipment shipmentTo = Shipment.retrieve(shippingIds.getEasyPostShipmentToId(), shippingIds.getEasyPostApiKey());
                    shipmentTo.refund(shippingIds.getEasyPostApiKey());
                } catch (EasyPostException ex) {
                    logger.error("Couldn't refund shipment to participant w/ dsm_kit_request_id " + kitRequestId + " " + ex.getMessage());
                    message += "To: Refund was not possible ";
                }
            }
            if (StringUtils.isNotBlank(shippingIds.getEasyPostShipmentReturnId())) {
                try {
                    Shipment shipmentReturn = Shipment.retrieve(shippingIds.getEasyPostShipmentReturnId(), shippingIds.getEasyPostApiKey());
                    shipmentReturn.refund(shippingIds.getEasyPostApiKey());
                } catch (EasyPostException ex) {
                    logger.error("Couldn't refund shipment from participant w/ dsm_kit_request_id " + kitRequestId + " " + ex.getMessage());
                    message += "Return: Refund was not possible";
                }
            }
            if (StringUtils.isNotBlank(message)) {
                updateKitError(kitRequestId, message, ddpInstanceDto);
            }
        }
    }

    public static void updateKitError(Integer dsmKitRequestId, @NonNull String message, DDPInstanceDto ddpInstanceDto) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(DSMConfig.getSqlFromConfig(ApplicationConfigConstants.UPDATE_KIT_ERROR))) {
                stmt.setInt(1, 1);
                stmt.setString(2, message);
                stmt.setLong(3, dsmKitRequestId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updated error/message for kit request w/ dsm_kit_request_id " + dsmKitRequestId);
                } else {
                    throw new DsmInternalError(
                            "Error updating error/message of kitRequest " + dsmKitRequestId + ". It was updating " + result + " rows");
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error updating error/message of kitRequest w/ dsm_kit_request_id " + dsmKitRequestId,
                    results.resultException);
        }

        KitRequestShipping kitRequestShipping = new KitRequestShipping(dsmKitRequestId, null, null, null, null, message);

        try {
            UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto,
                    ESObjectConstants.DSM_KIT_REQUEST_ID, ESObjectConstants.DSM_KIT_REQUEST_ID,
                    dsmKitRequestId, new PutToNestedScriptBuilder()).export();
        } catch (Exception e) {
            logger.error(String.format("Error updating error message for kit request shipping with dsm kit request id: %s in ElasticSearch",
                    dsmKitRequestId));
            e.printStackTrace();
        }
    }

    public static void reactivateKitRequest(Integer kitRequestId, DDPInstanceDto ddpInstanceDto) {
        reactivateKitRequest(kitRequestId, null, ddpInstanceDto);
    }

    public static void reactivateKitRequest(Integer dsmKitRequestId, String message, DDPInstanceDto ddpInstanceDto) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT + QueryExtension.KIT_DEACTIVATED,
                    ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setInt(1, dsmKitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    rs.beforeFirst();
                    if (count != 1) {
                        // with the current UI, the backend is providing the kit request ID, so log at error level
                        // so we can investigate
                        logger.error("Could not find kit with request ID {} for reactivation. (Got {} result rows.)",
                                dsmKitRequestId, count);
                        throw new DSMBadRequestException("Could not find kit with request ID  " + dsmKitRequestId);
                    }
                    if (rs.next()) {
                        dbVals.resultValue =
                                new KitRequestShipping(rs.getInt(DBConstants.DSM_KIT_REQUEST_ID), rs.getLong(DBConstants.DSM_KIT_ID),
                                        rs.getString(DBConstants.EASYPOST_TO_ID), rs.getString(DBConstants.EASYPOST_ADDRESS_ID_TO), false,
                                        null, rs.getString(DBConstants.KIT_TYPE_NAME));
                    }
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error reactivating kit request w/ dsm_kit_request_id " + dsmKitRequestId, results.resultException);
        }
        if (results.resultValue != null) {
            KitRequestShipping kitRequestShipping = (KitRequestShipping) results.resultValue;

            long dsmKitId = KitRequestShipping.writeNewKit(dsmKitRequestId, kitRequestShipping.getEasypostAddressId(), message, false);
            kitRequestShipping.setDsmKitId(dsmKitId);
            if (ddpInstanceDto.isESUpdatePossible()) {
                //skip if no elastic index. ex: juniper studies/eating-disorder
                try {
                    UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, ESObjectConstants.DSM_KIT_ID,
                            ESObjectConstants.DSM_KIT_REQUEST_ID, dsmKitRequestId,
                            new PutToNestedScriptBuilder()).export();
                } catch (Exception e) {
                    logger.error("Error inserting reactivated kit request shipping with id: {} in ElasticSearch. Exception: {} ", dsmKitRequestId, e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public static List<KitRequestShipping> findKitRequest(@NonNull String field, @NonNull String value, String[] realms) {
        Map<String, KitRequestShipping> kitRequests = new HashMap<>();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            String search = "";
            if (SEARCH_TRACKING_NUMBER.equals(field)) {
                search = " and tracking_id like \"%" + value + "%\"";
            } else if (SEARCH_MF_BAR.equals(field)) {
                search = " and kit_label like \"%" + value + "%\"";
            } else if (SHORT_ID.equals(field)) {
                search = " and bsp_collaborator_participant_id like \"%" + value + "%\"";
            } else {
                throw new DSMBadRequestException("Search field not known: " + field);
            }
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST.concat(search))) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String realm = rs.getString(DBConstants.INSTANCE_NAME);
                        if (Arrays.asList(realms).contains(realm)) { //only if user has right to see that realm
                            String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                            String bspSampleId = rs.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID);
                            String kitTypeName = rs.getString(DBConstants.KIT_TYPE_NAME);
                            String key = ddpParticipantId + "_" + bspSampleId + "_" + kitTypeName;
                            KitRequestShipping kitRequest = getKitRequestShipping(rs);
                            kitRequests.put(key, kitRequest);
                        }
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error searching for kit w/ field " + field + " and value " + value, results.resultException);
        }
        logger.info("Found {} kits", kitRequests.values().size());
        return new ArrayList<KitRequestShipping>(kitRequests.values());
    }

    public static List<KitRequestShipping> getKitRequestsAfterBookmark(long bookmark) {
        List<KitRequestShipping> kits = new ArrayList<>();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST + KIT_AFTER_BOOKMARK_ORDER_BY_ID)) {
                stmt.setLong(1, bookmark);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        KitRequestShipping kitRequest = getKitRequestShipping(rs);
                        kits.add(kitRequest);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error getting kit requests after bookmark ", results.resultException);
        }
        return kits;
    }

    /**
     * Generates the CollaboratorParticipantId for a kit request, called from the hourly kit creation job
     * */
    public static String getCollaboratorParticipantId(LatestKitRequest latestKitRequest, String ddpParticipantId, String shortId,
                                                      String collaboratorParticipantLengthOverwrite) {
        int instanceId = Integer.parseInt(latestKitRequest.getInstanceID());
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(instanceId);
        return getCollaboratorParticipantId(ddpInstance, ddpParticipantId, shortId, collaboratorParticipantLengthOverwrite);
    }

    /**
     * <p>
     * Retrieves the collaborator participant ID for a given participant. The method first checks if the participant has kit with
     * a legacy collaborator participant ID . If so that ID is returned. If the kit is a Gen2 migrated kit, a collaborator
     * participant ID is generated based on previous Gen2 samples.
     * Otherwise, a new collaborator participant ID is generated from Pepper short id.
     *</p>
     * <p>
     * Called  during  kit creation through KitUpload, BSPDummyRoutes, KitLookUp
     * and NonPepperKitCreationService
     *      </p>
     * @return the collaborator participant ID
     */

    public static String getCollaboratorParticipantId(DDPInstance ddpInstance, String ddpParticipantId, String shortId,
                                                      String collaboratorParticipantLengthOverwrite) {
        int instanceId = ddpInstance.getDdpInstanceIdAsInt();
        String baseUrl = ddpInstance.getBaseUrl();
        String collaboratorIdPrefix = ddpInstance.getCollaboratorIdPrefix();
        //TODO PT collaboratorParticipantLengthOverwrite should be an int
        //
        if (ddpInstance.isMigratedDDP() && ParticipantUtil.isHruid(shortId)) {
            // This check should be performed only for studies that have an ES index and are migrated studies, like CMI,
            // and not for studies like Darwin. It should only be applied if the shortId used to create the kit is a Pepper HRUID
            // rather than a legacy shortId or RGP's family/subject ID.
            String legacyId = getLegacyCollaboratorParticipantId(ddpInstance, shortId, collaboratorParticipantLengthOverwrite);
            if (legacyParticipantIdExists(legacyId)) {
                //this participant have kits with legacy participant id from before, use that id instead of generating new one to keep the
                //GP data consistent
                return legacyId;
            }
        }
        if (isGen2MigratedKit(ddpInstance, ddpParticipantId, baseUrl)) {
            // if kit uploaded with a gen2 legacy id, then use that id to generate the collaborator id
            return handleGen2MigratedKit(instanceId, ddpParticipantId, shortId, collaboratorIdPrefix,
                    collaboratorParticipantLengthOverwrite);
        }
        return generateCollaboratorParticipantId(shortId, ddpParticipantId, collaboratorIdPrefix, collaboratorParticipantLengthOverwrite);
    }

    /**
     * Generates a legacy collaborator participant ID if the participant has a legacy short ID
     * and has previously used that ID for kits. Returns the ID if it exists; otherwise, returns null.
     *
     * @param ddpInstance the DDPInstance of the participant
     * @param shortId the Pepper short ID
     * @param collaboratorParticipantLengthOverwrite the length of the collaborator participant ID
     * @return the legacy collaborator participant ID if it exists, otherwise null
     */

    private static String getLegacyCollaboratorParticipantId(DDPInstance ddpInstance, String shortId,
                                                             String collaboratorParticipantLengthOverwrite) {
        String legacyCollaboratorPtId = generateLegacyCollaboratorParticipantId(ddpInstance, shortId,
                collaboratorParticipantLengthOverwrite);
        if (StringUtils.isNotBlank(legacyCollaboratorPtId)
                && !kitRequestDao.getKitRequestsForCollaboratorParticipantId(legacyCollaboratorPtId).isEmpty()) {
            return legacyCollaboratorPtId;
        }
        return null;
    }

    /**
     * Checks if the legacyCollaboratorParticipantId is not null or empty
     * */
    private static boolean legacyParticipantIdExists(String legacyCollaboratorParticipantId) {
        return StringUtils.isNotBlank(legacyCollaboratorParticipantId);
    }

    private static String handleGen2MigratedKit(int instanceId, String ddpParticipantId, String shortId, String collaboratorIdPrefix,
                                                String overwrite) {
        String collaboratorId = KitUtil.getKitCollaboratorId(ddpParticipantId, instanceId);
        if (StringUtils.isNotBlank(collaboratorId)) {
            return collaboratorId;
        }
        // if a gen2 migrated kit without previous collab id, then use the sample id to extract the participant id
        String sampleId = KitUtil.getTissueCollaboratorId(ddpParticipantId, instanceId);
        if (StringUtils.isBlank(sampleId)) {
            return handleBlankSampleId(shortId, collaboratorIdPrefix, overwrite);
        }

        return extractParticipantIdFromSampleId(sampleId);
    }

    private static String handleBlankSampleId(String shortId, String collaboratorIdPrefix, String overwrite) {
        //legacy short id is  4 characters long and Pepper's HURID is 6 characters long, so here we check if the short id is 6 characters
        //long and if so, we use it as the participant id
        if (StringUtils.isNotBlank(shortId) && shortId.length() == 6) {
            return KitRequestShipping.generateBspParticipantID(collaboratorIdPrefix, overwrite, shortId.trim());
        }
        //For this case, in time of buying a shipping label, KitUtil.createCollaboratorParticipantId() will generate a new collaborator id
        return null;
    }

    private static String extractParticipantIdFromSampleId(String sampleId) {
        if (sampleId.contains("_")) {
            return sampleId.substring(0, sampleId.indexOf("_", sampleId.indexOf("_") + 1));
        }
        return null;
    }

    private static String generateCollaboratorParticipantId(String shortId, String ddpParticipantId, String collaboratorIdPrefix,
                                                            String collaboratorParticipantLengthOverwrite) {
        String id = StringUtils.isNotBlank(shortId) ? shortId.trim() : ddpParticipantId.trim();
        return KitRequestShipping.generateBspParticipantID(collaboratorIdPrefix, collaboratorParticipantLengthOverwrite, id);
    }


    /**
     * returns true if the kit with the given ddpParticipantId is a migrated Gen2 kit, only works when kit was uploaded/created
     * using the legacy guid
     * */
    private static boolean isGen2MigratedKit(DDPInstance ddpInstance, String ddpParticipantId, String baseUrl) {
        //assumes that Gen2 legacy participantId contains "." & Pepper participantId does NOT contain "."
        return ddpInstance.isMigratedDDP() && ddpParticipantId.contains(".") && baseUrl != null;
    }

    /**
     * If participant has a legacy short id, then it generates a collaborator participant id based on that.
     * If participant is not a legacy participant then returns null.
     * */
    private static String generateLegacyCollaboratorParticipantId(DDPInstance ddpInstance, String shortId,
                                                              String collaboratorParticipantLengthOverwrite) {
        Profile profile = new ElasticSearchService().getParticipantProfileByShortID(shortId, ddpInstance.getParticipantIndexES());
        if (profile == null) {
            throw new DsmInternalError("Could not find profile for shortId " + shortId);
        }
        if (StringUtils.isBlank(profile.getLegacyShortId())) {
            return null;
        }
        return KitRequestShipping.generateBspParticipantID(ddpInstance.getCollaboratorIdPrefix(), collaboratorParticipantLengthOverwrite,
                profile.getLegacyShortId());
    }

    public static void changeAuthorizationStatus(Integer kitRequestId, String reason, @NonNull String userId,
                                                 boolean authorization) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT_AUTHORIZE)) {
                stmt.setBoolean(1, authorization);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setObject(3, reason);
                stmt.setString(4, userId);
                stmt.setInt(5, kitRequestId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Changed authorization status for kitRequest w/ dsm_kit_request_id " + kitRequestId);
                } else {
                    throw new DsmInternalError(
                            "Error changing authorization status for kitRequest " + kitRequestId + ". It was updating " + result + " rows");
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error changing authorization status for kitRequest w/ dsm_kit_request_id " + kitRequestId,
                    results.resultException);
        }
    }

    /**
     * Marks the order as transmitted (successfully) at the given time
     */
    public static void markOrderTransmittedAt(Connection conn, String kitExternalOrderId, Instant transmittedAt) {
        try (PreparedStatement stmt = conn.prepareStatement(MARK_ORDER_AS_TRANSMITTED)) {
            stmt.setTimestamp(1, Timestamp.from(transmittedAt));
            stmt.setString(2, kitExternalOrderId);

            int numRows = stmt.executeUpdate();

            if (numRows == 0) {
                throw new DsmInternalError("No rows updated when setting transmission date for " + kitExternalOrderId);
            }
            logger.info("Updated {} rows when setting order transmission date for {} to {}", numRows, transmittedAt, kitExternalOrderId);
        } catch (SQLException e) {
            throw new DsmInternalError("Could not set order transmission date for " + kitExternalOrderId, e);
        }
    }

    public static String getCollaboratorSampleId(int kitTypeId, String participantCollaboratorId, String kitType, DDPInstance ddpInstance) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            String collaboratorSampleId = generateBspSampleID(conn, participantCollaboratorId, kitType, kitTypeId, ddpInstance);
            dbVals.resultValue = collaboratorSampleId;
            return dbVals;
        });
        if (results.resultException != null) {
            throw new DsmInternalError("Error getting Collaborator Sample Id for  " + participantCollaboratorId, results.resultException);
        } else {
            return (String) results.resultValue;
        }
    }

    private static boolean isLegacyCollaboratorParticipantId(String collaboratorParticipantId, String legacyShortId) {
        return StringUtils.isNotBlank(collaboratorParticipantId) && collaboratorParticipantId.contains("_" + legacyShortId);
    }

    public Boolean getError() {
        if (Objects.isNull(error)) {
            return false;
        }
        return error;
    }

    public List<Map<String, Object>> getTestResult() {
        try {
            if (StringUtils.isBlank(testResult)) {
                return ObjectMapperSingleton.instance().readValue("", new TypeReference<List<Map<String, Object>>>() {
                });
            }
            return ObjectMapperSingleton.instance().readValue(testResult, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (IOException | NullPointerException e) {
            return Collections.emptyList();
        }
    }

    public String getShortId(String collaboratorParticipantId) {
        if (StringUtil.isNotBlank(collaboratorParticipantId)) {
            if (collaboratorParticipantId.contains("_")) {
                String[] idSplit = collaboratorParticipantId.split("_");
                if (idSplit.length == 2) {
                    return idSplit[1];
                }
            }
        }
        return getParticipantId();
    }

    @Override
    public Optional<Long> extractDdpInstanceId() {
        return Optional.of(ddpInstanceId != null ? ddpInstanceId : 0);
    }

    public boolean isKitRequiringTrackingScan() {
        if (Objects.isNull(requiresInsertInKitTracking)) {
            return false;
        }
        return requiresInsertInKitTracking;
    }

    public boolean hasTrackingScan() {
        return StringUtils.isNotBlank(trackingId);
    }

    public boolean hasBSPCollaboratorParticipantId() {
        return StringUtils.isNotBlank(bspCollaboratorParticipantId);
    }
}
