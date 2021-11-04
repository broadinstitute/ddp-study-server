package org.broadinstitute.dsm.cf;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.jobs.PubsubMessage;
import org.broadinstitute.dsm.model.ups.UPSActivity;
import org.broadinstitute.dsm.model.ups.UPSKit;
import org.broadinstitute.dsm.model.ups.UPSPackage;
import org.broadinstitute.dsm.model.ups.UPSStatus;
import org.broadinstitute.dsm.pubsub.KitTrackerPubSubPublisher;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.utils.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Identifies kits whose shipping history should be
 * queried (by another job) in order to produce
 * reports on turnaround time and study adherence.
 */
public class TestBostonKitTrackerDispatcher implements BackgroundFunction<PubsubMessage> {

    private static final Logger logger = LoggerFactory.getLogger(TestBostonKitTrackerDispatcher.class.getName());

    private String STUDY_MANAGER_SCHEMA = System.getenv("STUDY_MANAGER_SCHEMA") + ".";
    private int LOOKUP_CHUNK_SIZE;
    KitTrackerPubSubPublisher kitTrackerPubSubPublisher = new KitTrackerPubSubPublisher();
    private final InstanceSettings instanceSettings;

    public TestBostonKitTrackerDispatcher() {
        instanceSettings = new InstanceSettings();
    }

    @Override
    public void accept(PubsubMessage pubsubMessage, Context context) throws Exception {
        Config cfg = CFUtil.loadConfig();
        String dbUrl = cfg.getString(ApplicationConfigConstants.CF_DSM_DB_URL);
        PoolingDataSource<PoolableConnection> dataSource = CFUtil.createDataSource(2, dbUrl);
        String data = new String(Base64.getDecoder().decode(pubsubMessage.getData()));
        final String SQL_SELECT_KITS_WITH_LATEST_ACTIVITY = "SELECT  * " +
                " FROM  " + STUDY_MANAGER_SCHEMA + "ddp_kit kit  " +
                " LEFT JOIN     " + STUDY_MANAGER_SCHEMA + "ddp_kit_request req  ON (kit.dsm_kit_request_id = req.dsm_kit_request_id)   " +
                " left join    " + STUDY_MANAGER_SCHEMA + "ups_shipment shipment on (shipment.dsm_kit_request_id = kit.dsm_kit_request_id) " +
                " left join  " + STUDY_MANAGER_SCHEMA + "ups_package pack on ( pack.ups_shipment_id = shipment.ups_shipment_id) " +
                " left join  " + STUDY_MANAGER_SCHEMA + "ups_activity activity on (pack.ups_package_id = activity.ups_package_id) " +
                " WHERE req.ddp_instance_id = ? and ( kit_label not like \"%\\\\_1\") and kit.dsm_kit_request_id > ? " +
                " and (shipment.ups_shipment_id is null or activity.ups_activity_id is null  or  activity.ups_activity_id in  " +
                " ( SELECT ac.ups_activity_id  " +
                " FROM ups_package pac INNER JOIN  " +
                "    ( SELECT  ups_package_id, MAX(ups_activity_id) maxId  " +
                "    FROM ups_activity  " +
                "    GROUP BY ups_package_id  ) lastActivity ON pac.ups_package_id = lastActivity.ups_package_id INNER JOIN  " +
                "    ups_activity ac ON   lastActivity.ups_package_id = ac.ups_package_id  " +
                "    AND lastActivity.maxId = ac.ups_activity_id  " +
                " ))";
        String SQL_AVOID_DELIVERED = " and (tracking_to_id is not null or tracking_return_id is not null ) and kit.test_result is null " +
                " and ( ups_status_description is null or ups_status_description not like \"%Delivered%\") "+
                " and from_unixtime(created_date/1000) > NOW() - INTERVAL 360 DAY"+
                " and (kit.ups_tracking_status is null or kit.ups_tracking_status not like \"%Delivered%\" or kit.ups_return_status is null or kit.ups_return_status not like \"%Delivered%\") " +
                " order by kit.dsm_kit_request_id ASC LIMIT ?";
        logger.info("Starting the UPS lookup job");
        LOOKUP_CHUNK_SIZE = new JsonParser().parse(data).getAsJsonObject().get("size").getAsInt();
        logger.info("The chunk size for each cloud function is " + LOOKUP_CHUNK_SIZE);
        JSONArray subsetOfKits = new JSONArray();
        String project = cfg.getString(ApplicationConfigConstants.PUBSUB_PROJECT_ID);
        String topicId = cfg.getString(ApplicationConfigConstants.PUBSUB_TOPIC_ID);
        try (Connection conn = dataSource.getConnection()) {
            List<DDPInstance> ddpInstanceList = getDDPInstanceListWithRole(conn, DBConstants.UPS_TRACKING_ROLE);
            for (DDPInstance ddpInstance : ddpInstanceList) {
                if (ddpInstance != null && ddpInstance.isHasRole()) {
                    int lastKitId = 0;
                    UPSKit kit = null;
                    logger.info("tracking ups ids for " + ddpInstance.getName());
                    InstanceSettingsDto instanceSettings = this.instanceSettings.getInstanceSettings(conn, ddpInstance.getName());
                    boolean gbfShippedTriggerDSSDelivered = instanceSettings
                            .isGbfShippedTriggerDSSDelivered()
                            .orElse(Boolean.FALSE);
                    loop:
                    while (true) {
                        try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KITS_WITH_LATEST_ACTIVITY + SQL_AVOID_DELIVERED)) {
                            stmt.setString(1, ddpInstance.getDdpInstanceId());
                            stmt.setInt(2, lastKitId);
                            stmt.setInt(3, LOOKUP_CHUNK_SIZE);
                            subsetOfKits = new JSONArray();
                            stmt.setFetchSize(LOOKUP_CHUNK_SIZE);
                            try (ResultSet rs = stmt.executeQuery()) {
                                while (rs.next()) {
                                    String shipmentId = rs.getString(DBConstants.UPS_SHIPMENT_ID);
                                    UPSPackage upsPackage;
                                    if (StringUtils.isNotBlank(shipmentId)) {
                                        UPSStatus latestStatus = new UPSStatus(rs.getString(DBConstants.UPS_ACTIVITY_TABLE_ABBR + DBConstants.UPS_STATUS_TYPE),
                                                rs.getString(DBConstants.UPS_ACTIVITY_TABLE_ABBR + DBConstants.UPS_STATUS_DESCRIPTION),
                                                rs.getString(DBConstants.UPS_ACTIVITY_TABLE_ABBR + DBConstants.UPS_STATUS_CODE));
                                        UPSActivity packageLastActivity = new UPSActivity(
                                                rs.getString(DBConstants.UPS_ACTIVITY_TABLE_ABBR + DBConstants.UPS_LOCATION),
                                                latestStatus,
                                                "",
                                                "",
                                                rs.getString(DBConstants.UPS_ACTIVITY_TABLE_ABBR + DBConstants.UPS_ACTIVITY_ID),
                                                rs.getString(DBConstants.UPS_ACTIVITY_TABLE_ABBR + DBConstants.UPS_PACKAGE_ID),
                                                rs.getString(DBConstants.UPS_ACTIVITY_TABLE_ABBR + DBConstants.UPS_ACTIVITY_DATE_TIME)
                                        );
                                        upsPackage = new UPSPackage(
                                                rs.getString(DBConstants.UPS_PACKAGE_TABLE_ABBR + DBConstants.UPS_TRACKING_NUMBER),
                                                new UPSActivity[] { packageLastActivity },
                                                rs.getString(DBConstants.UPS_PACKAGE_TABLE_ABBR + DBConstants.UPS_SHIPMENT_ID),
                                                rs.getString(DBConstants.UPS_PACKAGE_TABLE_ABBR + DBConstants.UPS_PACKAGE_ID),
                                                null, null);
                                    }
                                    else {
                                        upsPackage = new UPSPackage(
                                                null,
                                                null,
                                                null,
                                                null,
                                                null, null);
                                    }
                                    kit = new UPSKit(upsPackage,
                                            rs.getString(DBConstants.DDP_KIT_TABLE_ABBR + DBConstants.KIT_LABEL),
                                            rs.getBoolean(DBConstants.DDP_KIT_TABLE_ABBR + DBConstants.CE_ORDER),
                                            rs.getString(DBConstants.DDP_KIT_TABLE_ABBR + DBConstants.DSM_KIT_REQUEST_ID),
                                            rs.getString(DBConstants.DDP_KIT_REQUEST_TABLE_ABBR + DBConstants.EXTERNAL_ORDER_NUMBER),
                                            rs.getString(DBConstants.DDP_KIT_TABLE_ABBR + DBConstants.DSM_TRACKING_TO),
                                            rs.getString(DBConstants.DDP_KIT_TABLE_ABBR + DBConstants.DSM_TRACKING_RETURN),
                                            rs.getString(DBConstants.DDP_KIT_REQUEST_TABLE_ABBR + DBConstants.DDP_INSTANCE_ID),
                                            rs.getString(DBConstants.DDP_KIT_REQUEST_TABLE_ABBR + DBConstants.COLLABORATOR_PARTICIPANT_ID),
                                            gbfShippedTriggerDSSDelivered
                                    );
                                    JSONObject jsonKit = new JSONObject(kit);
                                    subsetOfKits.put(jsonKit);
                                    logger.info("added label " + kit.getKitLabel() + " with tracking number " + kit.getUpsPackage().getTrackingNumber() + " size of array " + subsetOfKits.length());
                                }
                            }
                            catch (Exception e) {
                                logger.error("Trouble executing select query", e);
                            }

                        }
                        catch (Exception e) {
                            logger.error("Trouble creating the statement ", e);
                        }
                        logger.info("kit is " + (kit == null ? "null" : kit.getDsmKitRequestId()));
                        if (kit != null) {
                            logger.info("lastKitId in this batch is " + kit.getDsmKitRequestId());
                            lastKitId = Integer.parseInt(kit.getDsmKitRequestId());
                            kitTrackerPubSubPublisher.publishMessage(project, topicId, subsetOfKits.toString());
                            subsetOfKits = new JSONArray();
                        }
                        else {
                            break loop;
                        }
                        kit = null;
                    }
                }
            }
            kitTrackerPubSubPublisher.publishMessage(project, topicId, subsetOfKits.toString());
        }
        catch (Exception e) {
            logger.error("Trouble creating a connection to DB ", e);
        }

    }

    public List<DDPInstance> getDDPInstanceListWithRole(Connection conn, @NonNull String role) {
        final String SQL_SELECT_INSTANCE_WITH_ROLE = "SELECT ddp_instance_id, instance_name, base_url, collaborator_id_prefix, migrated_ddp, billing_reference, " +
                "es_participant_index, es_activity_definition_index,  es_users_index, (SELECT count(role.name) " +
                "FROM " + STUDY_MANAGER_SCHEMA + "ddp_instance realm, " + STUDY_MANAGER_SCHEMA + "ddp_instance_role inRol, " + STUDY_MANAGER_SCHEMA + "instance_role role WHERE realm.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id AND role.name = ? " +
                "AND realm.ddp_instance_id = main.ddp_instance_id) AS 'has_role', mr_attention_flag_d, tissue_attention_flag_d, auth0_token, notification_recipients FROM  " + STUDY_MANAGER_SCHEMA + "ddp_instance main " +
                "WHERE is_active = 1";

        List<DDPInstance> ddpInstances = new ArrayList<>();
        try (PreparedStatement statement = conn.prepareStatement(SQL_SELECT_INSTANCE_WITH_ROLE)) {
            statement.setString(1, role);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRoleFormResultSet(rs);
                    ddpInstances.add(ddpInstance);
                }
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException("Error looking ddpInstances ", ex);
        }

        return ddpInstances;
    }

}
