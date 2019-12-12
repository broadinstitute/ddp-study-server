package org.broadinstitute.ddp.json.export;

import java.util.Collection;

public class FireCloudEntities {
    private Collection<FireCloudEntity> fireCloudEntities;
    private Collection<String> attributeHeader;

    public FireCloudEntities(Collection<FireCloudEntity> fireCloudEntities, Collection<String> attributeHeader) {
        this.fireCloudEntities = fireCloudEntities;
        this.attributeHeader = attributeHeader;
    }

    /**
     * Convert collection of FireCloud entities into a TSV format with column headers.
     * @return string in TSV format representing collection of FC entities
     */
    public String toTSV() {
        StringBuilder finalTsv = new StringBuilder();

        finalTsv.append("entity:participant_id");
        for (String attribute : attributeHeader) {
            finalTsv.append("\t ").append(attribute);
        }

        for (FireCloudEntity fireCloudEntity : fireCloudEntities) {
            finalTsv.append("\n").append(fireCloudEntity.toTSVRow(attributeHeader));
        }

        return finalTsv.toString();
    }

    public Collection<FireCloudEntity> getFireCloudEntities() {
        return fireCloudEntities;
    }
}
