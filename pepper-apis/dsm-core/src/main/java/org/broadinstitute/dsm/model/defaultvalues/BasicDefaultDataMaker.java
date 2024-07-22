package org.broadinstitute.dsm.model.defaultvalues;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;

@Slf4j
public abstract class BasicDefaultDataMaker implements Defaultable {
    protected static final ElasticSearchService elasticSearchService = new ElasticSearchService();
    protected DDPInstance instance;

    protected abstract boolean setDefaultData(String ddpParticipantId, ElasticSearchParticipantDto esParticipant,
                                              String payload);

    @Override
    public boolean generateDefaults(String studyGuid, String participantId, String payload) {
        instance = DDPInstance.getDDPInstanceByGuid(studyGuid);
        if (instance == null) {
            throw new DSMBadRequestException("Invalid study GUID: " + studyGuid);
        }
        String esIndex = instance.getParticipantIndexES();
        if (StringUtils.isEmpty(esIndex)) {
            throw new DsmInternalError("No ES participant index for study " + studyGuid);
        }

        Optional<ElasticSearchParticipantDto> esParticipant =
                elasticSearchService.getParticipantDocument(participantId, esIndex);
        if (esParticipant.isEmpty()) {
            throw new ESMissingParticipantDataException("Participant %s does not have an ES document"
                    .formatted(participantId));
        }
        log.info("Calling setDefaultData for ES index {} and participant ID {}", esIndex, participantId);
        return setDefaultData(participantId, esParticipant.get(), payload);
    }
}
