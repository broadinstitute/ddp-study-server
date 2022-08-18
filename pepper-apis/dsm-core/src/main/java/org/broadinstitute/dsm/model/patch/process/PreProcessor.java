package org.broadinstitute.dsm.model.patch.process;

public interface PreProcessor<A> {
    A process(A a);
}
