package org.broadinstitute.dsm.model.elastic.export;

import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;

public interface ExportableFactory {

    BaseExporter make(PropertyInfo propertyInfo);

}
