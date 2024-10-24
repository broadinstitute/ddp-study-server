package org.broadinstitute.dsm.util;

import org.broadinstitute.dsm.util.export.DefaultParticipantExporter;
import org.broadinstitute.dsm.util.export.ElasticSearchParticipantExporterFactory;
import org.broadinstitute.dsm.util.export.NewOsteoParticipantExporter;
import org.broadinstitute.dsm.util.export.ParticipantExportPayload;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class ElasticSearchParticipantExporterFactoryTest {

    @Ignore
    @Test
    public void buildNewOsteoExporter() {
        Assert.assertTrue(ElasticSearchParticipantExporterFactory.fromPayload(
                new ParticipantExportPayload(0, null, 0, "osteo2", null)) instanceof NewOsteoParticipantExporter);
    }

    @Test
    public void buildPlainExporter() {
        Assert.assertTrue(ElasticSearchParticipantExporterFactory.fromPayload(
                new ParticipantExportPayload(0, null, 0, "Any study other than osteo2", null)) instanceof DefaultParticipantExporter);
    }
}
