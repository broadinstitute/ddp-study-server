package org.broadinstitute.dsm.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

/**
 * A container that provides a nicer interface to updating the workflows list. The interface is similar to the "builder" pattern.
 */
public class WorkflowsEditor {

    private List<Map<String, Object>> workflows;
    private boolean hasChanged;

    public WorkflowsEditor(List<Map<String, Object>> originalList) {
        if (originalList == null) {
            originalList = new ArrayList<>();
        }
        this.workflows = new ArrayList<>(originalList);
    }

    public WorkflowsEditor clear() {
        int oldSize = workflows.size();
        workflows.clear();
        hasChanged = hasChanged || oldSize > 0;
        return this;
    }

    public WorkflowsEditor upsert(WorkflowForES workflow) {
        boolean updated;
        int oldSize = workflows.size();
        if (workflow.getStudySpecificData() != null) {
            updated = ElasticSearchUtil.updateWorkflowStudySpecific(
                    workflow.getWorkflow(), workflow.getStatus(),
                    workflows, workflow.getStudySpecificData());
        } else {
            updated = ElasticSearchUtil.updateWorkflow(
                    workflow.getWorkflow(), workflow.getStatus(), workflows);
        }
        boolean added = workflows.size() > oldSize;
        hasChanged = hasChanged || updated || added;
        return this;
    }

    public WorkflowsEditor removeIfNoData() {
        boolean removed = workflows.removeIf(wf -> !wf.containsKey(ESObjectConstants.DATA));
        hasChanged = hasChanged || removed;
        return this;
    }

    public WorkflowsEditor removeBySubjectId(String subjectId) {
        boolean removed = workflows.removeIf(wf -> {
            if (wf.containsKey(ESObjectConstants.DATA)) {
                Map<String, String> data = (Map<String, String>) wf.get(ESObjectConstants.DATA);
                if (data.containsKey(ESObjectConstants.SUBJECT_ID)) {
                    return data.get(ESObjectConstants.SUBJECT_ID).equals(subjectId);
                }
            }
            return false;
        });
        hasChanged = hasChanged || removed;
        return this;
    }

    public Map<String, Object> getMapForES() {
        Map<String, Object> partialDoc = new HashMap<>();
        partialDoc.put(ESObjectConstants.WORKFLOWS, workflows);
        return partialDoc;
    }

    public boolean hasChanged() {
        return hasChanged;
    }
}
