package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;

import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;

public class SourceGeneratorFactory implements GeneratorFactory {

    @Override
    public BaseGenerator make(PropertyInfo propertyInfo) {
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
                SmId.class, new SMIDSourceGenerator(),
                ParticipantData.class, new ParticipantDataSourceGenerator(),
                OncHistoryDetail.class, new OncHistoryDetailSourceGenerator(),
                MedicalRecord.class, new MedicalRecordSourceGenerator(),
                Tissue.class, new TissueSourceGenerator()
        );
        return collectionGeneratorByClass.getOrDefault(clazz, new CollectionSourceGenerator());
    }
}
