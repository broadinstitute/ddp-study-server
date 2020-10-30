package org.broadinstitute.ddp.thread;

/**
 * Simple {@link java.util.concurrent.ThreadFactory} that lets
 * you set the name for the group and the priority.
 */
public class ThreadFactory implements java.util.concurrent.ThreadFactory {

    private final String name;
    private final int priority;

    public ThreadFactory(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        ThreadGroup threadGroup = new ThreadGroup(name);
        Thread t = new Thread(threadGroup,r);
        t.setPriority(priority);
        return t;
    }
}
