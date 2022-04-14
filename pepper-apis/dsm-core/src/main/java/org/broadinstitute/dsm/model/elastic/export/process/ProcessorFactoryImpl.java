package org.broadinstitute.dsm.model.elastic.export.process;

import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class ProcessorFactoryImpl implements ProcessorFactory {

    @Override
    public BaseProcessor make(BaseGenerator.PropertyInfo propertyInfo) {
        BaseProcessor processor;
        if (propertyInfo.isCollection()) {
            processor = new CollectionProcessor();
            if (ESObjectConstants.MEDICAL_RECORD.equals(propertyInfo.getPropertyName())) {
                processor = new MedicalRecordCollectionProcessor();
            }
        } else {
            processor = new SingleProcessor();
        }
        return processor;
    }
}
