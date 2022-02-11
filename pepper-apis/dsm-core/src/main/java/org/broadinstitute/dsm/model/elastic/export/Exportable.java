package org.broadinstitute.dsm.model.elastic.export;

import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.ParticipantUtil;

public interface Exportable {

    void export();

    static String getParticipantGuid(String participantId, String index) {
        if (!(ParticipantUtil.isGuid(participantId))) {
            ElasticSearchParticipantDto participantById =
                    new ElasticSearch().getParticipantById(index, participantId);
            participantId = participantById.getParticipantId();
        }
        return participantId;
    }

}
