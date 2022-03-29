package org.broadinstitute.dsm.model.participant;

import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.model.ParticipantColumn;

@Getter
@Setter
public class DownloadParticipantListPayload {
    private List<ParticipantColumn> columnNames;
    private List<String> headerNames;

    public List<String> getHeaderNames() {
        if (headerNames == null) {
          headerNames = columnNames.stream().map(ParticipantColumn::getName).collect(Collectors.toList());
        }
        return headerNames;
    }
}
