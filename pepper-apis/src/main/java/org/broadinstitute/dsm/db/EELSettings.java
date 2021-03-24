package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class EELSettings {

    private static final Logger logger = LoggerFactory.getLogger(EELSettings.class);

    private static final String SQL_SELECT_SETTINGS = "SELECT eel.template_id, eel.name, eel.workflow_id, eel.response_days FROM eel_settings eel, ddp_instance realm WHERE eel.instance_id = realm.ddp_instance_id AND realm.instance_name = ?";
    private static final String SQL_INSERT_SETTINGS = "INSERT INTO eel_settings SET instance_id = (SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?), template_id = ?";
    private static final String SQL_UPDATE_SETTINGS = "UPDATE eel_settings SET name = ?, workflow_id = ?, response_days = ? WHERE instance_id = (SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?) AND template_id = ?";

    public static final String EMPTY_WORKFLOW = "-";

    private String templateId;
    private String name;
    private String workflowId;
    private String responseDays;

    public EELSettings(String templateId, String name, String workflowId, String responseDays) {
        this.templateId = templateId;
        this.name = name;
        this.workflowId = workflowId;
        this.responseDays = responseDays;
    }

    /**
     * Get eel settings for given source
     * @param source instance_name (name of the instance. In table ddp_instance)
     * @return Map<String, EELSettings>
     *     Key: (String) template_id from table eel_settings
     *     Value: EELSettings (information about the template like workflow step and how many days user has to open it)
     */
    public static Map<String, EELSettings> getEELSettings(@NonNull String source) {
        Map<String, EELSettings> eelSettings = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_SETTINGS)) {
                stmt.setString(1, source);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String template = rs.getString(DBConstants.TEMPLATE_ID);
                        String workflowId = rs.getString(DBConstants.WORKFLOW_ID);
                        if (StringUtils.isBlank(workflowId)) {
                            workflowId = EMPTY_WORKFLOW;
                        }
                        String responseDays = rs.getString(DBConstants.RESPONSE_DAYS);
                        if (StringUtils.isBlank(responseDays)) {
                            responseDays = EMPTY_WORKFLOW;
                        }
                        eelSettings.put(template, new EELSettings(template,
                                rs.getString(DBConstants.NAME),
                                workflowId, responseDays
                        ));
                    }
                }
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting data from eel db ", results.resultException);
        }
        logger.info("Found " + eelSettings.size() + " eel setting data for source " + source);
        return eelSettings;
    }

    public static void insertEELSetting(@NonNull String source, @NonNull String templateId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement insertStmt = conn.prepareStatement(SQL_INSERT_SETTINGS)) {
                insertStmt.setString(1, source);
                insertStmt.setString(2, templateId);
                insertStmt.executeUpdate();
            }
            catch (SQLException ex) {
                logger.error("Couldn't add new template " + templateId + " for source " + source + ", already exists but doesn't have any access roles or is set to is_active=0...");
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of realms ", results.resultException);
        }
        else {
            logger.info("Added new template " + templateId + " for source " + source);
        }
    }

    public static void updateEELSetting(@NonNull String source, @NonNull EELSettings mailSettings) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement updateStmt = conn.prepareStatement(SQL_UPDATE_SETTINGS)) {
                String workflowId = null;
                if (!EMPTY_WORKFLOW.equals(mailSettings.getWorkflowId())){
                    workflowId = mailSettings.getWorkflowId();
                }
                String responseDays = null;
                if (!EMPTY_WORKFLOW.equals(mailSettings.getResponseDays())){
                    responseDays = mailSettings.getResponseDays();
                }
                updateStmt.setString(1, mailSettings.getName());
                updateStmt.setString(2, workflowId);
                updateStmt.setString(3, responseDays);
                updateStmt.setString(4, source);
                updateStmt.setString(5, mailSettings.getTemplateId());
                int result = updateStmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating template " + mailSettings.getTemplateId() + " for source " + source + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                logger.error("Couldn't update template " + mailSettings.getTemplateId() + " for source " + source, ex);
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of realms ", results.resultException);
        }
        else {
            logger.info("Updated template " + mailSettings.getTemplateId() + " for source " + source);
        }
    }
}
