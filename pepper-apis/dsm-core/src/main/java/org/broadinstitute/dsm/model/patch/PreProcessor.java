package org.broadinstitute.dsm.model.patch;

public interface PreProcessor<A> {
    A process(A a);
}
