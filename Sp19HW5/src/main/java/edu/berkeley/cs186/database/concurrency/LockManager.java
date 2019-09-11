package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.BaseTransaction;

import java.util.*;

/**
 * LockManager maintains the bookkeeping for what transactions have
 * what locks on what resources. The lock manager should generally **not**
 * be used directly: instead, code should call methods of LockContext to
 * acquire/release/promote/escalate locks.
 *
 * The LockManager is primarily concerned with the mappings between
 * transactions, resources, and locks, and does not concern itself with
 * multiple levels of granularity (you can and should treat ResourceName
 * as a generic Object, rather than as an object encapsulating levels of
 * granularity, in this class).
 *
 * It follows that LockManager should allow **all**
 * requests that are valid from the perspective of treating every resource
 * as independent objects, even if they would be invalid from a
 * multigranularity locking perspective. For example, if LockManager#acquire
 * is called asking for an X lock on Table A, and the transaction has no
 * locks at the time, the request is considered valid (because the only problem
 * with such a request would be that the transaction does not have the appropriate
 * intent locks, but that is a multigranularity concern).
 *
 * Each resource the lock manager manages has its own queue of LockRequest objects
 * representing a request to acquire (or promote/acquire-and-release) a lock that
 * could not be satisfied at the time. This queue should be processed every time
 * a lock on that resource gets released, starting from the first request, and going
 * in order until a request cannot be satisfied. Requests taken off the queue should
 * be treated as if that transaction had made the request right after the resource was
 * released in absence of a queue (i.e. removing a request by T1 to acquire X(db) should
 * be treated as if T1 had just requested X(db) and there were no queue on db: T1 should
 * be given the X lock on db, and put in an unblocked state via BaseTransaction#unblock).
 *
 * This does mean that in the case of:
 *    queue: S(A) X(A) S(A)
 * only the first request should be removed from the queue when the queue is processed.
 */
public class LockManager {
    // transactionLocks is a mapping from transaction number to a list of lock
    // objects held by that tr nsaction.
    private Map<Long, List<Lock>> transactionLocks = new HashMap<>();
    // resourceEntries is a mapping from resource names to a ResourceEntry
    // object, which contains a list of Locks on the object, as well as a
    // queue for requests on that resource.
    private Map<ResourceName, ResourceEntry> resourceEntries = new HashMap<>();

    // A ResourceEntry contains the list of locks on a resource, as well as
    // the queue for requests for locks on the resource.
    private class ResourceEntry {
        // List of currently granted locks on the resource.
        List<Lock> locks = new ArrayList<>();
        // Queue for yet-to-be-satisfied lock requests on this resource.
        Deque<LockRequest> waitingQueue = new ArrayDeque<>();

        // You may add helper methods here if you wish
    }

    // You should not modify or use this directly.
    protected Map<Object, LockContext> contexts = new HashMap<>();

    /**
     * Helper method to fetch the resourceEntry corresponding to NAME.
     * Inserts a new (empty) resourceEntry into the map if no entry exists yet.
     */
    private ResourceEntry getResourceEntry(ResourceName name) {
        resourceEntries.putIfAbsent(name, new ResourceEntry());
        return resourceEntries.get(name);
    }

    // You may add helper methods here if you wish

    /**
     * Acquire a LOCKTYPE lock on NAME, for transaction TRANSACTION, and releases all locks
     * in RELEASELOCKS after acquiring the lock, in one atomic action.
     *
     * Error checking must be done before any locks are acquired or released. If the new lock
     * is not compatible with another transaction's lock on the resource, the transaction is
     * blocked and the request is placed at the **front** of ITEM's queue.
     *
     * Locks in RELEASELOCKS should be released only after the requested lock has been acquired.
     * The corresponding queues should be processed.
     *
     * An acquire-and-release that releases an old lock on NAME **should not** change the
     * acquisition time of the lock on NAME, i.e.
     * if a transaction acquired locks in the order: S(A), X(B), acquire X(A) and release S(A), the
     * lock on A is considered to have been acquired before the lock on B.
     *
     * @throws DuplicateLockRequestException if a lock on NAME is held by TRANSACTION and
     * isn't being released
     * @throws NoLockHeldException if no lock on a name in RELEASELOCKS is held by TRANSACTION
     */
    public void acquireAndRelease(BaseTransaction transaction, ResourceName name,
                                  LockType lockType, List<ResourceName> releaseLocks)
    throws DuplicateLockRequestException, NoLockHeldException {
        // You may modify any part of this method. You are not required to keep all your
        // code within the given synchronized block -- in fact,
        // you will have to write some code outside the synchronized block to avoid locking up
        // the entire lock manager when a transaction is blocked. You are also allowed to
        // move the synchronized block elsewhere if you wish.
        Boolean shouldBlock = false;
        synchronized (this) {
            ResourceEntry RE = getResourceEntry(name);
            Boolean isCompatible;
            Long num = transaction.getTransNum();
            List<Lock> lockList = transactionLocks.get(num);
            Lock newLock = new Lock(name, lockType, num);
            LockRequest lr = new LockRequest(transaction, newLock); //w other constructor?
            Boolean lockHeld = false;

            if (lockList != null) {
                if (lockList.contains(newLock) && !releaseLocks.contains(newLock)) {
                    throw new DuplicateLockRequestException("Lock already exists");
                }
            }

            /*
            if (lockList != null) {
                for (int j = 0; j < lockList.size(); j++) {
                    if (lockList.get(j).name.equals(name)) {
                        noLockHeld = false;
                        break;
                    }
                }
            }

            if (lockList == null || noLockHeld) {
                throw new NoLockHeldException("No lock held by transaction in releaseLocks");
            }
            */

            /*
            if (lockList != null && releaseLocks != null) {
                outerloop:
                for (int i = 0; i < releaseLocks.size(); i++) {
                    for (int j = 0; j < lockList.size(); j++) {
                        if (lockList.get(j).name.equals(releaseLocks.get(i))) {
                            lockHeld = true;
                            break outerloop;
                        }
                    }
                }
            }

            if (!lockHeld) {
                throw new NoLockHeldException("No lock held by transaction in releaseLocks");
            }
            */

            //throw new UnsupportedOperationException("TODO(hw5_part1): implement");

            //promoting (same trans) vs conflict (two diff transactions)

            isCompatible = isCompatible(name, lr);
            Boolean sameTrans = false;
            for (int i = 0; i < RE.locks.size(); i++) {
                if (RE.locks.get(i).transactionNum == num) {
                    sameTrans = true;
                    break;
                }
            }

            //int indexTL = ((lockList != null) ? lockList.size() - 1: 0);
            //int indexREL = ((RE.locks != null) ? RE.locks.size() - 1: 0);
            if (isCompatible || sameTrans) {

                /*
                if (releaseLocks != null) {
                    for (int i = 0; i < lockList.size(); i++) {
                        if (releaseLocks.contains(lockList.get(i).name)) {
                            indexTL = i;
                        }
                    }

                    for (int i = 0; i < RE.locks.size(); i++) {
                        if (releaseLocks.contains(RE.locks.get(i).name)) {
                            indexREL = i;
                        }
                    }
                }
                */

                for (int i = 0; i < releaseLocks.size(); i++) {
                    release(transaction, releaseLocks.get(i));
                }

                RE.locks.add(newLock);
                if (transactionLocks.containsKey(num)) {
                    lockList.add(newLock);
                } else {
                    lockList = new ArrayList<>();
                    lockList.add(newLock);
                    transactionLocks.put(transaction.getTransNum(), lockList);
                }
            } else {
                RE.waitingQueue.addFirst(lr);
                shouldBlock = true;
            }
        }

        if (shouldBlock) {
            transaction.block();
        }
    }

    /**
     * Acquire a LOCKTYPE lock on NAME, for transaction TRANSACTION.
     *
     * Error checking must be done before the lock is acquired. If the new lock
     * is not compatible with another transaction's lock on the resource, or if there are
     * other transaction in queue for the resource, the transaction is
     * blocked and the request is placed at the **back** of NAME's queue.
     *
     * @throws DuplicateLockRequestException if a lock on NAME is held by
     * TRANSACTION
     */
    public void acquire(BaseTransaction transaction, ResourceName name,
                        LockType lockType) throws DuplicateLockRequestException {
        // You may modify any part of this method. You are not required to keep all your
        // code within the given synchronized block -- in fact,
        // you will have to write some code outside the synchronized block to avoid locking up
        // the entire lock manager when a transaction is blocked. You are also allowed to
        // move the synchronized block elsewhere if you wish.


        synchronized (this) {
            ResourceEntry RE = getResourceEntry(name);
            Boolean isCompatible;
            Long num = transaction.getTransNum();
            List<Lock> lockList = transactionLocks.get(num);
            Lock newLock = new Lock(name, lockType, num);
            LockRequest lr = new LockRequest(transaction, newLock); //w other constructor?

            if (lockList != null) {
                if (lockList.contains(newLock)) {
                    throw new DuplicateLockRequestException("Lock already exists");
                }
            }
            //throw new UnsupportedOperationException("TODO(hw5_part1): implement");

            //no queue

            isCompatible = isCompatible(name, lr);

            if (isCompatible) {
                if (RE.locks.isEmpty() || RE.waitingQueue.isEmpty()) {
                    RE.locks.add(newLock);
                    if (transactionLocks.containsKey(num)) {
                        lockList.add(newLock);
                        //w transactionLocks.replace(num, lockList); prob not needed cuz mutative
                    } else {
                        lockList = new ArrayList<>();
                        lockList.add(newLock);
                        transactionLocks.put(transaction.getTransNum(), lockList);
                    }
                    return;
                }
            }

            RE.waitingQueue.addLast(lr);
        }

        transaction.block();
    }

    /**
     * Release TRANSACTION's lock on NAME.
     *
     * Error checking must be done before the lock is released.
     *
     * NAME's queue should be processed after this call. If any requests in
     * the queue have locks to be released, those should be released, and the
     * corresponding queues also processed.
     *
     * @throws NoLockHeldException if no lock on NAME is held by TRANSACTION
     */
    public void release(BaseTransaction transaction, ResourceName name)
    throws NoLockHeldException {
        // You may modify any part of this method.

        synchronized (this) {
            //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
            Long num = transaction.getTransNum();
            List<Lock> lockList = transactionLocks.get(num);

            if (lockList == null) {
                throw new NoLockHeldException("The lock list is null.");
            }
            int containsNameIndex = -1, RELockIndex = -1;
            ResourceEntry RE = getResourceEntry(name);
            LockRequest lr;


            for (int i = 0; i < lockList.size(); i++) {
                if (lockList.get(i).name == name) {
                    containsNameIndex = i;
                    break;
                }
            }

            if (containsNameIndex == -1) {
                throw new NoLockHeldException("No lock on NAME is held by TRANSACTION");
            }

            //should always exist.
            for (int i = 0; i < RE.locks.size(); i++) {
                if (RE.locks.get(i).transactionNum == num) {
                    RELockIndex = i;
                    break;
                }
            }

            lockList.remove(containsNameIndex);
            RE.locks.remove(RELockIndex);

            //process queue
            while (!RE.waitingQueue.isEmpty()) {
                lr = RE.waitingQueue.peek();

                Boolean sameTrans = false;
                for (int i = 0; i < RE.locks.size(); i++) {
                    if (RE.locks.get(i).transactionNum == lr.transaction.getTransNum()) {
                        sameTrans = true;
                        break;
                    }
                }

                if (isCompatible(name, lr)) {
                    RE.waitingQueue.pop();
                    acquire(lr.transaction, name, lr.lock.lockType);

                    for (int i = 0; i < lr.releasedLocks.size(); i++) {
                        release(lr.transaction, lr.releasedLocks.get(i).name);
                    }

                    lr.transaction.unblock();
                } else {
                    if (sameTrans) {
                        //transactionLocks.get(lr.transaction.getTransNum())
                        RE.waitingQueue.pop();
                        release(lr.transaction, name);
                        acquire(lr.transaction, name, lr.lock.lockType);

                        for (int i = 0; i < lr.releasedLocks.size(); i++) {
                            release(lr.transaction, lr.releasedLocks.get(i).name);
                        }

                        lr.transaction.unblock();
                    } else {
                        break;
                    }
                }


            }
        }
    }

    /**
     * Promote TRANSACTION's lock on NAME to NEWLOCKTYPE (i.e. change TRANSACTION's lock
     * on NAME from the current lock type to NEWLOCKTYPE, which must be strictly more
     * permissive).
     *
     * Error checking must be done before any locks are changed. If the new lock
     * is not compatible with another transaction's lock on the resource, the transaction is
     * blocked and the request is placed at the **front** of ITEM's queue.
     *
     * A lock promotion **should not** change the acquisition time of the lock, i.e.
     * if a transaction acquired locks in the order: S(A), X(B), promote X(A), the
     * lock on A is considered to have been acquired before the lock on B.
     *
     * @throws DuplicateLockRequestException if TRANSACTION already has a
     * NEWLOCKTYPE lock on NAME
     * @throws NoLockHeldException if TRANSACTION has no lock on NAME
     * @throws InvalidLockException if the requested lock type is not a promotion. A promotion
     * from lock type A to lock type B is valid if and only if B is substitutable
     * for A, and B is not equal to A.
     */

    public boolean isCompatible(ResourceName name, LockRequest lr) {
        ResourceEntry RE = getResourceEntry(name);

        for (int i = 0; i < RE.locks.size(); i++) {
            if (!LockType.compatible(lr.lock.lockType, RE.locks.get(i).lockType)) {
                return false;
            }
        }

        return true;

    }

    public boolean isCompatible(ResourceName name, LockType lockType, int indexRE) {
        ResourceEntry RE = getResourceEntry(name);

        for (int i = 0; i < RE.locks.size(); i++) {
            if (i != indexRE) {
                if (!LockType.compatible(lockType, RE.locks.get(i).lockType)) {
                    return false;
                }
            }
        }

        return true;

    }

    public void promote(BaseTransaction transaction, ResourceName name,
                        LockType newLockType)
    throws DuplicateLockRequestException, NoLockHeldException, InvalidLockException {
        // You may modify any part of this method.
        Boolean shouldBlock = false;

        synchronized (this) {
            //throw new UnsupportedOperationException("TODO(hw5_part1): implement");

            Long num = transaction.getTransNum();
            List<Lock> L = transactionLocks.get(num);
            ResourceEntry RE = getResourceEntry(name);

            if (L == null || newLockType == LockType.NL) {
                throw new NoLockHeldException("Transaction has no locks");
            }

            int indexRE = -1, indexL = -1;

            for (int i = 0; i < L.size(); i++) {
                if (L.get(i).name.equals(name)) {

                    if (newLockType == L.get(i).lockType) {
                        //z = L.get(i).lockType;
                        throw new DuplicateLockRequestException("Promote lock already held.");
                    }

                    indexL = i;

                    /*
                    if (RE.locks.size() == 1) {
                        L.remove(i);
                        L.add(i, new Lock(name, newLockType, num));
                    }
                    */
                }
            }

            if (indexL == -1) {
                throw new NoLockHeldException("Transaction has no lock on the resource");
            } else if (!LockType.substitutable(newLockType, L.get(indexL).lockType)) {
                throw new InvalidLockException("Desired lock is not substitutable.");
            }



            for (int i = 0; i < RE.locks.size(); i++) {
                if (RE.locks.get(i).transactionNum.equals(num)) {
                    indexRE = i;
                }
            }

            if (isCompatible(name, newLockType, indexRE)) {
                L.remove(indexL);
                RE.locks.remove(indexRE);

                L.add(indexL, new Lock(name, newLockType, num));
                RE.locks.add(indexRE, new Lock(name, newLockType, num));
            } else { //if there are multiple locks on the resource
                RE.waitingQueue.addFirst(new LockRequest(transaction, new Lock(name, newLockType, num)));
                shouldBlock = true;
            }

        }

        if (shouldBlock) {
            transaction.block();
        }
    }

    /**
     * Return the type of lock TRANSACTION has on NAME (return NL if no lock is held).
     */
    public synchronized LockType getLockType(BaseTransaction transaction, ResourceName name) {
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
        List<Lock> L = transactionLocks.get(transaction.getTransNum());
        if (L != null) {
            for (int i = 0; i < L.size(); i++) {
                if (L.get(i).name.equals(name)) {
                    return L.get(i).lockType;
                }
            }
        }
        return LockType.NL;
        //return resourceEntries.get()
    }

    /**
     * Returns the list of locks held on NAME, in order of acquisition.
     * A promotion or acquire-and-release should count as acquired
     * at the original time.
     */
    public synchronized List<Lock> getLocks(ResourceName name) {
        return new ArrayList<>(resourceEntries.getOrDefault(name, new ResourceEntry()).locks);
    }

    /**
     * Returns the list of locks locks held by
     * TRANSACTION, in order of acquisition. A promotion or
     * acquire-and-release should count as acquired at the original time.
     */
    public synchronized List<Lock> getLocks(BaseTransaction transaction) {
        return new ArrayList<>(transactionLocks.getOrDefault(transaction.getTransNum(),
                               Collections.emptyList()));
    }

    /**
     * Create a lock context for the database. See comments at
     * the top of this file and the top of LockContext.java for more information.
     */
    public synchronized LockContext databaseContext() {
        contexts.putIfAbsent("database", new LockContext(this, null, "database"));
        return contexts.get("database");
    }

    /**
     * Create a lock context with no parent. Cannot be called "database".
     */
    public synchronized LockContext orphanContext(Object name) {
        if (name.equals("database")) {
            throw new IllegalArgumentException("cannot create orphan context named 'database'");
        }
        contexts.putIfAbsent(name, new LockContext(this, null, name));
        return contexts.get(name);
    }
}
