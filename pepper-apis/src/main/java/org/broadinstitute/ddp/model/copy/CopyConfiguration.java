package org.broadinstitute.ddp.model.copy;

import java.util.ArrayList;
import java.util.List;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CopyConfiguration {

    private long id;
    private long studyId;
    private boolean copyFromPreviousInstance;
    private List<CopyPreviousInstanceFilter> previousInstanceFilters = new ArrayList<>();
    private List<CopyConfigurationPair> pairs = new ArrayList<>();

    @JdbiConstructor
    public CopyConfiguration(
            @ColumnName("copy_configuration_id") long id,
            @ColumnName("study_id") long studyId,
            @ColumnName("copy_from_previous_instance") boolean copyFromPreviousInstance) {
        this.id = id;
        this.studyId = studyId;
        this.copyFromPreviousInstance = copyFromPreviousInstance;
    }

    public CopyConfiguration(long studyId, boolean copyFromPreviousInstance, List<CopyConfigurationPair> pairs) {
        this.studyId = studyId;
        this.copyFromPreviousInstance = copyFromPreviousInstance;
        addPairs(pairs);
    }

    public CopyConfiguration(long studyId, boolean copyFromPreviousInstance,
                             List<CopyPreviousInstanceFilter> previousInstanceFilters,
                             List<CopyConfigurationPair> pairs) {
        this.studyId = studyId;
        this.copyFromPreviousInstance = copyFromPreviousInstance;
        addPreviousInstanceFilters(previousInstanceFilters);
        addPairs(pairs);
    }

    public long getId() {
        return id;
    }

    public long getStudyId() {
        return studyId;
    }

    public boolean shouldCopyFromPreviousInstance() {
        return copyFromPreviousInstance;
    }

    public List<CopyPreviousInstanceFilter> getPreviousInstanceFilters() {
        return previousInstanceFilters;
    }

    public void addPreviousInstanceFilters(List<CopyPreviousInstanceFilter> previousInstanceFilters) {
        if (previousInstanceFilters != null) {
            this.previousInstanceFilters.addAll(previousInstanceFilters);
        }
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
