package org.broadinstitute.dsm.model.defaultvalues;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Slf4j
public abstract class BasicDefaultDataMaker implements Defaultable {
    protected final FieldSettings fieldSettings = new FieldSettings();
    protected final Bookmark bookmark = new Bookmark();
    protected final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    protected final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    protected DDPInstance instance;
    protected int instanceId;
    protected ElasticSearchParticipantDto elasticSearchParticipantDto;


    @VisibleForTesting
    protected abstract boolean setDefaultData();

    @Override
    public boolean generateDefaults(String studyGuid, String participantId) {
        instance = DDPInstance.getDDPInstanceByGuid(studyGuid);
        if (instance == null) {
            throw new DSMBadRequestException("Invalid study GUID: " + studyGuid);
        }
        instanceId = Integer.parseInt(instance.getDdpInstanceId());
        String esIndex = instance.getParticipantIndexES();
        if (StringUtils.isEmpty(esIndex)) {
            throw new DsmInternalError("No ES participant index for study " + studyGuid);
        }

        elasticSearchParticipantDto = ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, participantId);
        log.info("Calling setDefaultData for ES index {} and participant ID {}", esIndex, participantId);
        return setDefaultData();
    }
}
