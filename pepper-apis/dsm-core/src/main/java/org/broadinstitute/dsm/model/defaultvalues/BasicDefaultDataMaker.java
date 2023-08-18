package org.broadinstitute.dsm.model.defaultvalues;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BasicDefaultDataMaker implements Defaultable {

    protected static final Logger logger = LoggerFactory.getLogger(BasicDefaultDataMaker.class);

    protected final FieldSettings fieldSettings = new FieldSettings();
    protected final Bookmark bookmark = new Bookmark();
    protected final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    protected final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    protected DDPInstance instance;
    protected ElasticSearchParticipantDto elasticSearchParticipantDto;


    protected abstract boolean setDefaultData();

    @Override
    public boolean generateDefaults(String studyGuid, String participantId) {
        instance = DDPInstance.getDDPInstanceByGuid(studyGuid);
        if (instance == null) {
            throw new DSMBadRequestException("Invalid study GUID: " + studyGuid);
        }
        String esIndex = instance.getParticipantIndexES();
        if (StringUtils.isEmpty(esIndex)) {
            throw new DsmInternalError("No ES participant index for study " + studyGuid);
        }

        Optional<ElasticSearchParticipantDto> maybeParticipantESDataByParticipantId =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, participantId);
        if (maybeParticipantESDataByParticipantId.isEmpty()) {
            throw new ESMissingParticipantDataException("Participant ES data is null for participant " + participantId);
        }
        elasticSearchParticipantDto = maybeParticipantESDataByParticipantId.get();
        logger.info("Calling setDefaultData for ES index {} and participant ID {}", esIndex, participantId);
        return setDefaultData();
    }
}
