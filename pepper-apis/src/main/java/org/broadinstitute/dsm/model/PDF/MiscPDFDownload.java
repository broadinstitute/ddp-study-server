package org.broadinstitute.dsm.model.PDF;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MiscPDFDownload {

    private static final Logger logger = LoggerFactory.getLogger(MiscPDFDownload.class);

    public Object create(String ddpParticipantId, String realm) {
        if (StringUtils.isNotBlank(ddpParticipantId)) {
            return returnPDFS(ddpParticipantId, realm);
        }
        else {// it is the misc download
            return getPDFRole(realm);
        }
    }

    public Object returnPDFS(@NonNull String ddpParticipantId, String realm) {
        DDPInstance instance = DDPInstance.getDDPInstance(realm);
        Map<String, Map<String, Object>> participantESData;
        if (ParticipantUtil.isGuid(ddpParticipantId)) {
            participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance,
                    ElasticSearchUtil.BY_GUID + ddpParticipantId);
        }
        else {// altpid
            participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, ElasticSearchUtil.BY_LEGACY_ALTPID + ddpParticipantId);
        }
        if (participantESData != null && !participantESData.isEmpty() && participantESData.size() == 1) {
            Map<String, Object> participantData = participantESData.get(ddpParticipantId);
            if (participantData != null) {
                Map<String, Object> dsm = (Map<String, Object>) participantData.get(ElasticSearchUtil.DSM);
                if (dsm != null) {
                    Object pdf = dsm.get(ElasticSearchUtil.PDFS);
                    return pdf;
                }
            }
            else {
                for (Object value : participantESData.values()) {
                    participantData = (Map<String, Object>) value;
                    //check that it is really right participant
                    if (participantData != null) {
                        Map<String, Object> profile = (Map<String, Object>) participantData.get(ElasticSearchUtil.PROFILE);
                        if (profile != null) {
                            String guid = (String) profile.get(ElasticSearchUtil.GUID);
                            if (ddpParticipantId.equals(guid)) {
                                Map<String, Object> dsm = (Map<String, Object>) participantData.get(ElasticSearchUtil.DSM);
                                if (dsm != null) {
                                    Object pdf = dsm.get(ElasticSearchUtil.PDFS);
                                    return pdf;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }


    public List<String> getPDFRole(@NonNull String realm) {
        List<String> listOfPDFs = new ArrayList<>();
        if (DDPInstance.getDDPInstanceWithRole(realm, DBConstants.PDF_DOWNLOAD).isHasRole()) {
            listOfPDFs.add(DBConstants.PDF_DOWNLOAD);
            logger.info("Found " + DBConstants.PDF_DOWNLOAD + " role for realm " + realm);
            return listOfPDFs;
        }
        throw new RuntimeException("Couldn't get pdfs for realm " + realm + ", role for realm doesn't exist");
    }

}
