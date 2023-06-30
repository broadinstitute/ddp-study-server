package org.broadinstitute.dsm.service.onchistory;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.BaseExporter;

public class MockElasticExporter extends BaseExporter {
    @Override
    public void export() {
    }

    public void setExpected(Map<String, Object> expected) {

    }
}
