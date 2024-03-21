
package org.broadinstitute.dsm.util.export;

import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;

public class ElasticSearchParticipantExporterFactory {

    public static Exportable fromPayload(ParticipantExportPayload payload) {
        return new DefaultParticipantExporter(payload);
        // TODO: Keeping this code as a reminder of what this was doing before we wind this down.
        /*
        return isNewOsteoInstance(payload.getInstanceName())
                ? new NewOsteoParticipantExporter(payload)
                : new DefaultParticipantExporter(payload);
        */
    }

    private static boolean isNewOsteoInstance(String instanceName) {
        return instanceName.equals(StudyPostFilter.NEW_OSTEO_INSTANCE_NAME);
    }

}
