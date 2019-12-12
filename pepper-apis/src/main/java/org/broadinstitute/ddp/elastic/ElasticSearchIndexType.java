package org.broadinstitute.ddp.elastic;

public enum ElasticSearchIndexType {
    PARTICIPANTS("participants"),
    PARTICIPANTS_STRUCTURED("participants_structured"),
    DRUG_LIST("drug_list"),
    INSTITUTION_LIST("institution_list"),
    ACTIVITY_DEFINITION("activity_definition");


    private String elasticSearchCompatibleLabel;

    private ElasticSearchIndexType(String elasticSearchCompatibleLabel) {
        this.elasticSearchCompatibleLabel = elasticSearchCompatibleLabel;
    }

    public String getElasticSearchCompatibleLabel() {
        return elasticSearchCompatibleLabel;
    }
}
