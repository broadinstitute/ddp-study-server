package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;

import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;

public class SourceGeneratorFactory implements GeneratorFactory {

    @Override
    public BaseGenerator make(BaseGenerator.PropertyInfo propertyInfo) {
        BaseGenerator generator;
        if (propertyInfo.isCollection()) {
            generator = getCollectionGenerator(propertyInfo.getPropertyClass());
        } else {
            generator = new SingleSourceGenerator();
        }
        return generator;
    }

    private BaseGenerator getCollectionGenerator(Class<?> clazz) {
        Map<Class<?>, BaseGenerator> collectionGeneratorByClass = Map.of(
                Tissue.class, new ParentChildRelationGenerator(),
                SmId.class, new SMIDSourceGenerator(),
                ParticipantData.class, new ParticipantDataSourceGenerator()
        );
        return collectionGeneratorByClass.getOrDefault(clazz, new CollectionSourceGenerator());
    }
}
