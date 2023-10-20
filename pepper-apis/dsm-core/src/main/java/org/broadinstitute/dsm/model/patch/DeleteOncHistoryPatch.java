package org.broadinstitute.dsm.model.patch;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.elasticsearch.ElasticsearchException;

@Slf4j
public class DeleteOncHistoryPatch extends ExistingOncHistoryPatch {
    public DeleteOncHistoryPatch(Patch patch, NotificationUtil notificationUtil) {
        super(patch, notificationUtil);
        this.dbElementBuilder = new DefaultDBElementBuilder();
    }

    @Override
    public Object doPatch() {
        Object o = null;
        try {
            o = super.doPatch();
        } catch (DsmInternalError e){}
        catch (ElasticsearchException e){}
        finally {
            DeletePatchFactory.deleteChildrenFields(this.patch, this.getNotificationUtil());
            return o;
        }
    }
}
