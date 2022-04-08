package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;

public class SourceGeneratorFactory implements GeneratorFactory {

    @Override
    public BaseGenerator make(BaseGenerator.PropertyInfo propertyInfo) {
        BaseGenerator generator;
        if (propertyInfo.isCollection()) {
            if (isChildParentRelation(propertyInfo)) {
                generator = new ParentChildRelationGenerator();
            } else if (ParticipantData.class.isAssignableFrom(propertyInfo.getPropertyClass())) {
                generator = new ParticipantDataSourceGenerator();
            } else {
                generator = new CollectionSourceGenerator();
            }
        } else {
            generator = new SingleSourceGenerator();
        }
        return generator;
    }

    private boolean isChildParentRelation(BaseGenerator.PropertyInfo propertyInfo) {
        return Tissue.class.isAssignableFrom(propertyInfo.getPropertyClass()) || SmId.class.isAssignableFrom(propertyInfo.getPropertyClass());
    }
}
