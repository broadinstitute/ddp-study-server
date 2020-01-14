package org.broadinstitute.ddp.model.event;

import java.util.ArrayList;
import java.util.List;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CopyConfiguration {

    private long id;
    private long studyId;
    private List<CopyConfigurationPair> pairs = new ArrayList<>();

    @JdbiConstructor
    public CopyConfiguration(@ColumnName("copy_configuration_id") long id, @ColumnName("study_id") long studyId) {
        this.id = id;
        this.studyId = studyId;
    }

    public CopyConfiguration(long studyId, List<CopyConfigurationPair> pairs) {
        this.studyId = studyId;
        addPairs(pairs);
    }

    public long getId() {
        return id;
    }

    public long getStudyId() {
        return studyId;
    }

    public List<CopyConfigurationPair> getPairs() {
        return List.copyOf(pairs);
    }

    public void addPairs(List<CopyConfigurationPair> pairs) {
        if (pairs != null) {
            this.pairs.addAll(pairs);
        }
    }
}
