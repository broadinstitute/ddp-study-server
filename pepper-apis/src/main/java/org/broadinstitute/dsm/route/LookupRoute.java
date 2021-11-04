package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.LookupResponse;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class LookupRoute extends RequestHandler {

    private static final String SQL_SELECT_COLLABORATOR_PREFIX = "SELECT collaborator_id_prefix FROM ddp_instance WHERE instance_name LIKE ?";
    private static final String SQL_SELECT_TISSUE_SITE = "SELECT DISTINCT tissue_site FROM ddp_tissue WHERE tissue_site LIKE ?";
    private static final String SQL_SELECT_TYPE = "SELECT DISTINCT type_px " +
            "FROM ddp_onc_history_detail oD " +
            "LEFT JOIN ddp_medical_record m on (m.medical_record_id = oD.medical_record_id AND NOT m.deleted <=> 1) " +
            "LEFT JOIN ddp_institution inst on (m.institution_id = inst.institution_id) " +
            "LEFT JOIN  ddp_participant p on (p.participant_id = inst.participant_id) " +
            "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_instance_group gr on (realm.ddp_instance_id = gr.ddp_instance_id) " +
            "WHERE oD.type_px LIKE ? AND NOT (oD.deleted <=> 1) AND gr.ddp_group_id = ?";
    private static final String SQL_SELECT_CONTACT = "SELECT DISTINCT name, contact, phone, fax FROM ddp_medical_record WHERE name LIKE ?";
    private static final String SQL_SELECT_FACILITY_IN_GROUP = "SELECT DISTINCT oD.facility, oD.phone, oD.fax, oD.destruction_policy " +
            "FROM ddp_onc_history_detail oD " +
            "LEFT JOIN ddp_medical_record m on (m.medical_record_id = oD.medical_record_id AND NOT m.deleted <=> 1) " +
            "LEFT JOIN ddp_institution inst on (m.institution_id = inst.institution_id) " +
            "LEFT JOIN  ddp_participant p on (p.participant_id = inst.participant_id) " +
            "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_instance_group gr on (realm.ddp_instance_id = gr.ddp_instance_id) " +
            "WHERE facility LIKE ? AND NOT (oD.deleted <=> 1) AND gr.ddp_group_id = ?";


    private static final String SQL_SELECT_HISTOLOGY = "SELECT DISTINCT histology FROM ddp_onc_history_detail onc, ddp_medical_record rec, ddp_institution inst, ddp_participant part," +
            " ddp_instance realm WHERE onc.medical_record_id = rec.medical_record_id AND rec.institution_id = inst.institution_id AND inst.participant_id = part.participant_id" +
            " AND NOT rec.deleted <=> 1 AND realm.ddp_instance_id = part.ddp_instance_id AND onc.histology LIKE ? AND realm.instance_name = ?";

    private static final String MEDICAL_RECORD_CONTACT = "mrContact";
    private static final String TISSUE_FACILITY = "tFacility";
    private static final String TISSUE_TYPE = "tType";
    private static final String TISSUE_HISTOLOGY = "tHistology";
    private static final String TISSUE_SITE = "tSite";
    private static final String COLLABORATOR_ID = "tCollab";

    private static final String SHORT_ID = "shortId";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String field = null;
        if (queryParams.value(RequestParameter.LOOKUP_FIELD) != null) {
            field = queryParams.get(RequestParameter.LOOKUP_FIELD).value();
        }
        String value = null;
        if (queryParams.value(RequestParameter.LOOKUP_VALUE) != null) {
            value = queryParams.get(RequestParameter.LOOKUP_VALUE).value();
        }
        String realm = null;
        String group = null;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        }
        String shortId = null;
        if (queryParams.value(SHORT_ID) != null) {
            shortId = queryParams.get(SHORT_ID).value();
        }
        if (StringUtils.isNotBlank(field)) {
            if (UserUtil.checkUserAccess(realm, userId, "mr_view", null)) {
                String query = null;
                if (MEDICAL_RECORD_CONTACT.equals(field)) {
                    query = SQL_SELECT_CONTACT;
                }
                else if (TISSUE_FACILITY.equals(field)) {
                    query = SQL_SELECT_FACILITY_IN_GROUP;
                    group = DDPInstance.getDDPGroupId(realm);
                }
                else if (TISSUE_TYPE.equals(field)) {
                    query = SQL_SELECT_TYPE;
                    group = DDPInstance.getDDPGroupId(realm);
                }
                else if (TISSUE_HISTOLOGY.equals(field)) {
                    query = SQL_SELECT_HISTOLOGY;
                    if (StringUtils.isBlank(realm)) {
                        throw new RuntimeException("Error getting histology, realm is missing ");
                    }
                }
                else if (TISSUE_SITE.equals(field)) {
                    query = SQL_SELECT_TISSUE_SITE;
                }
                else if (COLLABORATOR_ID.equals(field)) {
                    query = SQL_SELECT_COLLABORATOR_PREFIX;
                    if (StringUtils.isBlank(realm)) {
                        throw new RuntimeException("Error getting collaboratorId, realm is missing ");
                    }
                    DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
                    String collaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance.getBaseUrl(), ddpInstance.getDdpInstanceId(), ddpInstance.isMigratedDDP(),
                            ddpInstance.getCollaboratorIdPrefix(), value, shortId, "4"); //4 was length of CMI in Gen2
                    if (StringUtils.isNotBlank(collaboratorParticipantId)) {
                        //if participant has already a sample collaborator participant id, return just the participant id
                        List<LookupResponse> responseList = new ArrayList<>();
                        responseList.add(new LookupResponse(collaboratorParticipantId));
                        return responseList;
                    }
                }
                return getLookupValue(field, query, value, realm, group);
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        return null;
    }

    public List<LookupResponse> getLookupValue(@NonNull String field, @NonNull String query, String value, String realm, String group) {
        List<LookupResponse> response = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, value.concat("%"));
                if (StringUtils.isNotBlank(group)) {
                    stmt.setString(2, group);
                }
                else if (StringUtils.isNotBlank(realm)) {
                    stmt.setString(2, realm);
                }
                if (stmt.toString().contains("like ?") || stmt.toString().contains("= ?")) {
                    throw new RuntimeException("Parameter missing in query " + stmt.toString());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        LookupResponse lookupResponse = getResponse(field, rs);
                        response.add(lookupResponse);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of lookups ", results.resultException);
        }
        return response;
    }

    private LookupResponse getResponse(@NonNull String field, @NonNull ResultSet rs) throws SQLException {
        if (MEDICAL_RECORD_CONTACT.equals(field)) {
            return new LookupResponse(rs.getString(DBConstants.NAME),
                    rs.getString(DBConstants.CONTACT),
                    rs.getString(DBConstants.PHONE),
                    rs.getString(DBConstants.FAX), null);
        }
        else if (TISSUE_FACILITY.equals(field)) {
            return new LookupResponse(rs.getString(DBConstants.FACILITY), null,
                    rs.getString(DBConstants.PHONE),
                    rs.getString(DBConstants.FAX),
                    rs.getString(DBConstants.DESTRUCTION_POLICY));
        }
        else if (TISSUE_TYPE.equals(field)) {
            return new LookupResponse(rs.getString(DBConstants.TYPE_PX));
        }
        else if (TISSUE_HISTOLOGY.equals(field)) {
            return new LookupResponse(rs.getString(DBConstants.HISTOLOGY));
        }
        else if (TISSUE_SITE.equals(field)) {
            return new LookupResponse(rs.getString(DBConstants.TISSUE_SITE));
        }
        else if (COLLABORATOR_ID.equals(field)) {
            return new LookupResponse(rs.getString(DBConstants.COLLABORATOR_ID_PREFIX));
        }
        return null;
    }
}
