package org.broadinstitute.dsm.model.elastic.export;

import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class MappingExporterFactory implements ExportableFactory {

    @Override
    public BaseExporter make(PropertyInfo propertyInfo) {
        BaseExporter exporter = new NullObjectExporter();
        if (!propertyInfo.getFieldName().equals(ESObjectConstants.FOLLOW_UPS)) {
            exporter = new ElasticMappingExportAdapter();
        }
        return exporter;
    }
}
