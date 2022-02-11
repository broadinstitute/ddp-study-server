package org.broadinstitute.dsm.model.participant;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.db.AbstractionActivity;
import org.broadinstitute.dsm.db.AbstractionGroup;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class ParticipantWrapperDto {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantWrapperDto.class);

    private ElasticSearchParticipantDto esData;
    private Participant participant;
    private List<MedicalRecord> medicalRecords;
    private List<OncHistoryDetail> oncHistoryDetails;
    private List<KitRequestShipping> kits;
    private List<AbstractionActivity> abstractionActivities;
    private List<AbstractionGroup> abstractionSummary;
    private List<ElasticSearchParticipantDto> proxyData;
    private List<ParticipantData> participantData;

    public ParticipantWrapperDto(ElasticSearchParticipantDto esData, Participant participant, List<MedicalRecord> medicalRecords,
                                 List<OncHistoryDetail> oncHistoryDetails, List<KitRequestShipping> kits, List<AbstractionActivity> abstractionActivities,
                                 List<AbstractionGroup> abstractionSummary, List<ElasticSearchParticipantDto> proxyData, List<ParticipantData> participantData) {
        this.esData = esData;
        this.participant = participant;
        this.medicalRecords = medicalRecords;
        this.oncHistoryDetails = oncHistoryDetails;
        this.kits = kits;
        this.abstractionActivities = abstractionActivities;
        this.abstractionSummary = abstractionSummary;
        this.proxyData = proxyData;
        this.participantData = participantData;
    }

    public ParticipantWrapperDto() {

    }

    public Map<String, Object> getEsDataAsMap() {
        Map<String, Object> esDataMap = new HashMap<>();
        if (Objects.isNull(esData)) return esDataMap;
        ObjectMapper objectMapper = new ObjectMapper();
        String esDataAsJson = new Gson().toJson(esData);
        try {
            esDataMap = objectMapper.readValue(esDataAsJson, Map.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return esDataMap;
    }

}
