package org.broadinstitute.ddp.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Contains map of {@link ReentrantLock}'s.
 * The locks can be added to the map with assigned name (key).
 * For example, userGuid.<br>
 * Scenario: method M2 of service S2 should be called after method M2 of service S2 and this sequence should be met
 * for a certain user. Pseudocode example:
 * <pre>
 *   NamedLocksMap locksMap = new NamedLocksMap();
 *   S1.m1() {
 *       try {
 *         Lock lock = lockMap.addAndLock(userGuid);
 *         ...
 *       } finally {
 *         lock.unlock();
 *       }
 *   }
 *   ...
 *   S2.m2() {
 *       try {
 *         lockMap.lockIfExists(userGuid);
 *         ...
 *       } finally {
 *         lockMap.unlockAndRemove();
 *       }
 *   }
 * </pre>
 * In above example:
 * <pre>
 * - method M1 creates a new lock, locks it and saves to the map with key=`userGuid`;
 * - when M1 completed it unlocks the created lock;
 * - method M2 searches for a lock for the same `userGuid`;
 * - if lock is found then it tries to lock it too (if M1 still in progress then M2 blocked until M1 completed);
 * - after method M1 completion method M2 started and at the end it unlocks the lock and removes it from the map.
 * </pre>
 * NOTE: for each user created a separate lock.
 */
public class NamedLocksMap {

    private final Map<String, Lock> locks = new HashMap<>();

    /**
     * Find in the map a lock by key.
     * If lock is found then call lock() (try to set a lock).
     * If lock is not found in the map - nothing happens.
     */
    public synchronized Lock lockIfExists(String key) {
        Lock lock = locks.get(key);
        if (lock != null) {
            lock.lock();
        }
        return lock;
    }

    /**
     * Create a new {@link ReentrantLock} and add to the map.
     * Call lock() (try to set a lock).
     */
    public synchronized Lock addAndLock(String key) {
        Lock lock = locks.get(key);
        if (lock == null) {
            lock = new ReentrantLock();
            locks.put(key, lock);
        }
        lock.lock();
        return lock;
    }

    /**
     * Find in the map a lock by key.
     * If lock is found the run unlock() and remove the lock from the map.
     */
    public synchronized void unlockAndRemove(String key) {
        Lock lock = locks.get(key);
        if (lock != null) {
            lock.unlock();
            locks.remove(key);
        }
    }
}
