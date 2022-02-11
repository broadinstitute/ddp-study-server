package org.broadinstitute.dsm.model.elastic.export;

import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;

public interface ExportableFactory {

    BaseExporter make(BaseGenerator.PropertyInfo propertyInfo);

}
