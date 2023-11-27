package org.broadinstitute.ddp.elastic.participantslookup.model;

import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexType.ALL_TYPES;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexType.PARTICIPANTS_STRUCTURED;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexType.USERS;

import java.util.Arrays;

import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;

/**
 * Contains definition of different constants and enums used during
 * participants lookup in ElasticSearch.
 */
public class ESParticipantsLookupData {

    /**
     * Types of ES indices used for participants lookup.
     */
    public enum IndexType {

        ALL_TYPES(null),
        USERS(ElasticSearchIndexType.USERS),
        PARTICIPANTS_STRUCTURED(ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);

        private ElasticSearchIndexType elasticSearchIndexType;

        IndexType(ElasticSearchIndexType elasticSearchIndexType) {
            this.elasticSearchIndexType = elasticSearchIndexType;
        }

        public ElasticSearchIndexType getElasticSearchIndexType() {
            return elasticSearchIndexType;
        }
    }

    /**
     * Names of fields in ElasticSearch indices ("users", "participants_structured")
     * by which to do searching.
     */
    public enum LookupField {

        PROFILE__GUID("profile.guid", ALL_TYPES),
        PROFILE__HRUID("profile.hruid", ALL_TYPES),
        PROFILE__FIRST_NAME("profile.firstName", ALL_TYPES),
        PROFILE__LAST_NAME("profile.lastName", ALL_TYPES),
        PROFILE__EMAIL_TEXT("profile.email.text", ALL_TYPES),
        PROFILE__EMAIL("profile.email", ALL_TYPES),
        PROFILE__LEGACY_ALT_PID("profile.legacyAltPid", ALL_TYPES),
        PROFILE__LEGACY_SHORT_ID("profile.legacyShortId", ALL_TYPES),
        INVITATIONS__GUID("invitations.guid", PARTICIPANTS_STRUCTURED),
        GOVERNED_USERS("governedUsers", USERS);

        private String esField;
        private IndexType indexType;

        LookupField(String esField, IndexType indexType) {
            this.esField = esField;
            this.indexType = indexType;
        }

        public String getEsField() {
            return esField;
        }

        public IndexType getIndexType() {
            return indexType;
        }

        public boolean isQueryFieldForIndex(IndexType index) {
            return getIndexType() == ALL_TYPES || getIndexType() == index;
        }
    }

    /**
     * Names of ElasticSearch indices' _source-s  from which to fetch data.
     */
    public enum IndexSource {
        PROFILE("profile", ALL_TYPES),
        GOVERNED_USERS("governedUsers", USERS),
        STATUS("status", PARTICIPANTS_STRUCTURED),
        INVITATIONS("invitations", PARTICIPANTS_STRUCTURED),
        PROXIES("proxies", PARTICIPANTS_STRUCTURED);

        private String source;
        private IndexType indexType;

        IndexSource(String source, IndexType indexType) {
            this.source = source;
            this.indexType = indexType;
        }

        public String getSource() {
            return source;
        }

        public static String[] getSourcesByIndex(IndexType indexType) {
            return Arrays.stream(values())
                    .filter(s -> s.indexType == indexType || s.indexType == ALL_TYPES || indexType == ALL_TYPES)
                    .map(s -> s.source)
                    .toArray(String[]::new);
        }
    }

    /**
     * Field value (to fetch from source 'invitations')
     */
    public static final String GUID = "guid";

    /**
     * Array of sources for reading data from index "users"
     */
    public static String[] USERS_SOURCES = IndexSource.getSourcesByIndex(USERS);

    /**
     * Array of sources for reading data from index "participants_structured"
     */
    public static String[] PARTICIPANTS_STRUCTURED_SOURCES = IndexSource.getSourcesByIndex(PARTICIPANTS_STRUCTURED);
}
