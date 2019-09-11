package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.BaseTransaction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LockContext wraps around LockManager to provide the hierarchical structure
 * of multigranularity locking. Calls to acquire/release/etc. locks should
 * be mostly done through a LockContext, which provides access to locking
 * methods at a certain point in the hierarchy (database, table X, etc.)
 */
public class LockContext {
    // You should not remove any of these fields. You may add additional fields/methods as you see fit.
    // The underlying lock manager.
    protected LockManager lockman;
    // The parent LockContext object, or null if this LockContext is at the top of the hierarchy.
    protected LockContext parent;
    // The name of the resource this LockContext represents.
    protected ResourceName name;
    // Whether this LockContext is readonly. If a LockContext is readonly, acquire/release/promote/escalate should
    // throw an UnsupportedOperationException.
    protected boolean readonly;
    // A mapping between transaction numbers, and the number of locks on children of this LockContext
    // that the transaction holds.
    protected Map<Long, Integer> numChildLocks = new HashMap<>();
    // The number of children that this LockContext has, if it differs from the number of times
    // LockContext#childContext was called with unique parameters: for a table, we do not
    // explicitly create a LockContext for every page (we create them as needed), but
    // the capacity should be the number of pages in the table, so we use this
    // field to override the return value for capacity().
    protected int capacity;
    //protected LockType lockType;
    protected Map<BaseTransaction, LockType> lockTypeT = new HashMap<>();

    // You should not modify or use this directly.
    protected Map<Object, LockContext> children;

    // Whether or not any new child LockContexts should be marked readonly.
    protected boolean childLocksDisabled;

    public LockContext(LockManager lockman, LockContext parent, Object name) {
        this(lockman, parent, name, false);
    }

    protected LockContext(LockManager lockman, LockContext parent, Object name, boolean readonly) {
        this.lockman = lockman;
        this.parent = parent;
        if (parent == null) {
            this.name = new ResourceName(name);
        } else {
            this.name = new ResourceName(parent.getResourceName(), name);
        }
        this.readonly = readonly;
        this.numChildLocks = new ConcurrentHashMap<>();
        this.capacity = 0;
        this.children = new ConcurrentHashMap<>();
        this.childLocksDisabled = readonly;
    }

    /**
     * Gets a lock context corresponding to NAME from a lock manager.
     */
    public static LockContext fromResourceName(LockManager lockman, ResourceName name) {
        Iterator<Object> names = name.getNames().iterator();
        LockContext ctx;
        Object n1 = names.next();
        if (n1.equals("database")) {
            ctx = lockman.databaseContext();
        } else {
            ctx = lockman.orphanContext(n1);
        }
        while (names.hasNext()) {
            ctx = ctx.childContext(names.next());
        }
        return ctx;
    }

    /**
     * Get the name of the resource that this lock context pertains to.
     */
    public ResourceName getResourceName() {
        return name;
    }

    public LockContext getParent() { return parent; }

    /**
     * Acquire a LOCKTYPE lock, for transaction TRANSACTION.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or
     * else calls to LockContext#saturation will not work properly.
     *
     * @throws InvalidLockException if the request is invalid
     * @throws DuplicateLockRequestException if a lock is already held by TRANSACTION
     * @throws UnsupportedOperationException if context is readonly
     */
    public void acquire(BaseTransaction transaction, LockType lockType)
    throws InvalidLockException, DuplicateLockRequestException {
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
        if (readonly) {
            throw new UnsupportedOperationException("Context is read-only");
        }

        LockContext ancestor = parent;
        LockType actualParentLT, trueParentLT;
        Boolean invalid = false;
        int numChildren; //perspective of parent

        while (ancestor != null) {
            actualParentLT = lockman.getLockType(transaction, parent.name);
            trueParentLT = LockType.parentLock(lockType);
            if (actualParentLT != trueParentLT && !LockType.substitutable(actualParentLT, trueParentLT)) {
                throw new InvalidLockException("The request is invalid because of some ancestor lock type.");
            }
            ancestor = ancestor.parent;

        }


        if (!invalid) {
            lockman.acquire(transaction, name, lockType);
            lockTypeT.put(transaction, lockType);

            if (parent != null) {
                numChildren = parent.getNumChildrenLocks(transaction);
                parent.numChildLocks.put(transaction.getTransNum(), numChildren + 1);
            }
        }
    }

    /**
     * Release TRANSACTION's lock on NAME.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or
     * else calls to LockContext#saturation will not work properly.
     *
     * @throws NoLockHeldException if no lock on NAME is held by TRANSACTION
     * @throws InvalidLockException if the lock cannot be released (because doing so would
     *  violate multigranularity locking constraints)
     * @throws UnsupportedOperationException if context is readonly
     */
    public void release(BaseTransaction transaction)
    throws NoLockHeldException, InvalidLockException {
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
        if (readonly) {
            throw new UnsupportedOperationException("Context is read-only");
        }

        List<Lock> tLocks = lockman.getLocks(transaction);
        Lock L;
        LockType LT;
        int numChildren;
        LockType lockType = getLockTypeT(transaction);

        for (int i = 0; i < tLocks.size(); i++) {
            L = tLocks.get(i);
            if (L.name.isDescendantOf(name)) {
                LT = L.lockType;
                if (LockType.parentLock(LT) == lockType || LockType.substitutable(lockType, LockType.parentLock(LT))) {
                    throw new InvalidLockException("The lock cannot be released.");
                }
            }
        }

        lockman.release(transaction, name);
        lockTypeT.put(transaction, LockType.NL);

        if (parent != null) {
            numChildren = parent.getNumChildrenLocks(transaction);

            if (numChildren > 0) {
                parent.numChildLocks.put(transaction.getTransNum(), numChildren - 1);
            }
        }


        //Look at all descendant locks of LockContext that are from transaction
        //check if lockType is parentLock of the descendant lock or if substitutable(lockType,parentLock(descendant lock))
    }

    /**
     * Promote TRANSACTION's lock to NEWLOCKTYPE.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or
     * else calls to LockContext#saturation will not work properly.
     *
     * @throws DuplicateLockRequestException if TRANSACTION already has a NEWLOCKTYPE lock
     * @throws NoLockHeldException if TRANSACTION has no lock
     * @throws InvalidLockException if the requested lock type is not a promotion or promoting
     * would cause the lock manager to enter an invalid state (e.g. IS(parent), X(child)). A promotion
     * from lock type A to lock type B is valid if and only if B is substitutable
     * for A, and B is not equal to A.
     * @throws UnsupportedOperationException if context is readonly
     */
    public void promote(BaseTransaction transaction, LockType newLockType)
    throws DuplicateLockRequestException, NoLockHeldException, InvalidLockException {
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
        LockType lockType = getEffectiveLockType(transaction);
        if (readonly) {
            throw new UnsupportedOperationException("Context is read-only");
        }

        if (LockType.substitutable(newLockType, lockType) && newLockType != lockType) {
            lockman.promote(transaction, name, newLockType);
            lockTypeT.put(transaction, newLockType);
        } else {
            throw new InvalidLockException("Promotion would cause an invalid state.");
        }
    }

    /**
     * Escalate TRANSACTION's lock from descendants of this context to this level, using either
     * an S or X lock. There should be no descendant locks after this
     * call, and every operation valid on descendants of this context before this call
     * must still be valid. You should only make *one* mutating call to the lock manager,
     * and should only request information about TRANSACTION from the lock manager.
     *
     * For example, if a transaction has the following locks:
     *      IX(database) IX(table1) S(table2) S(table1 page3) X(table1 page5)
     * then after table1Context.escalate(transaction) is called, we should have:
     *      IX(database) X(table1) S(table2)
     *
     * You should not make any mutating calls if the locks held by the transaction do not change
     * (such as when you call escalate multiple times in a row).
     *
     * Note: you *must* make any necessary updates to numChildLocks of all relevant contexts, or
     * else calls to LockContext#saturation will not work properly.
     *
     * @throws NoLockHeldException if TRANSACTION has no lock at this level
     * @throws UnsupportedOperationException if context is readonly
     */
    public void escalate(BaseTransaction transaction) throws NoLockHeldException {
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
        LockType LT = getExplicitLockType(transaction);
        if (readonly) {
            throw new UnsupportedOperationException("Context is read-only");
        } else if (LT == LockType.NL) {
            throw new NoLockHeldException("Transaction has no lock held at this level");
        } else if (LT == LockType.S || LT == LockType.X) {
            return;
        }

        List<Lock> tLocks = lockman.getLocks(transaction);
        List<Lock> SIS = new ArrayList<>();
        List<Lock> XIXSIX = new ArrayList<>();
        List<ResourceName> allLocks = new ArrayList<>();
        Lock L;

        for (int i = 0; i < tLocks.size(); i++) {
            L = tLocks.get(i);
            if (L.name.isDescendantOf(name)) {
                if (L.lockType == LockType.S || L.lockType == LockType.IS) {
                    SIS.add(L);
                } else {
                    XIXSIX.add(L);
                }

                //lockman.release(transaction, L.name);
            }
        }

        if (LT == LockType.IS) {
            SIS.add(new Lock(name, LT, transaction.getTransNum()));
        } else if (LT == LockType.IX || LT == LockType.SIX) {
            XIXSIX.add(new Lock(name, LT, transaction.getTransNum()));
        }

        for (int i = 0; i < SIS.size(); i++) {
            allLocks.add(SIS.get(i).name);
        }

        for (int i = 0; i < XIXSIX.size(); i++) {
            allLocks.add(XIXSIX.get(i).name);
        }

        if (XIXSIX.size() == 0) {
            lockman.acquireAndRelease(transaction, name, LockType.S, allLocks);
            lockTypeT.put(transaction, LockType.S);
        } else {
            lockman.acquireAndRelease(transaction, name, LockType.X, allLocks);
            lockTypeT.put(transaction, LockType.X);
        }

        numChildLocks.put(transaction.getTransNum(), 0);


    }

    /** added **/

    public Integer getNumChildrenLocks(BaseTransaction T) {
        numChildLocks.putIfAbsent(T.getTransNum(), 0);
        return numChildLocks.get(T.getTransNum());
    }

    /** added **/
    public LockType getLockTypeT(BaseTransaction T) {
        lockTypeT.putIfAbsent(T, LockType.NL);
        return lockTypeT.get(T);
    }

    /**
     * Gets the type of lock that the transaction has at this level, either implicitly
     * (e.g. explicit S lock at higher level implies S lock at this level) or explicitly.
     * Returns NL if there is no explicit nor implicit lock.
     */
    public LockType getEffectiveLockType(BaseTransaction transaction) {

        if (transaction == null) {
            return LockType.NL;
        }
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
        LockType LT = getExplicitLockType(transaction);
        LockContext ancestor = parent;
        LockType lockTypeParent;

        while (ancestor != null) {
            lockTypeParent = ancestor.getLockTypeT(transaction);
            if (lockTypeParent == LockType.X || lockTypeParent == LockType.S) {
                return lockTypeParent; //implicit
            }
            ancestor = ancestor.parent;
        }

        return LT;
    }

    /**
     * Get the type of lock that TRANSACTION holds at this level, or NL if no lock is held at this level.
     */
    public LockType getExplicitLockType(BaseTransaction transaction) {
        LockType lockType = getLockTypeT(transaction);
        if (transaction == null) { //RHS is added.
            return LockType.NL;
        }
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
        return lockType;
    }

    /**
     * Disables locking descendants. This causes all new child contexts of this context
     * to be readonly. This is used for indices and temporary tables (where
     * we disallow finer-grain locks), the former due to complexity locking
     * B+ trees, and the latter due to the fact that temporary tables are only
     * accessible to one transaction, so finer-grain locks make no sense.
     */
    public void disableChildLocks() {
        this.childLocksDisabled = true;
    }

    /**
     * Gets the parent context.
     */
    public LockContext parentContext() {
        return parent;
    }

    /**
     * Gets the context for the child with name NAME.
     */
    public LockContext childContext(Object name) {
        LockContext temp = new LockContext(lockman, this, name,
                                           this.childLocksDisabled || this.readonly);
        LockContext child = this.children.putIfAbsent(name, temp);
        if (child == null) {
            child = temp;
        }
        return child;
    }

    /**
     * Sets the capacity (number of children).
     */
    public void capacity(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Gets the capacity. Defaults to number of child contexts if never explicitly set.
     */
    public int capacity() {
        return this.capacity == 0 ? this.children.size() : this.capacity;
    }

    /**
     * Gets the saturation (number of locks held on children / number of children) for
     * a single transaction. Saturation is 0 if number of children is 0.
     */
    public double saturation(BaseTransaction transaction) {
        if (transaction == null || capacity() == 0) {
            return 0.0;
        }
        return ((double) numChildLocks.getOrDefault(transaction.getTransNum(), 0)) / capacity();
    }

    @Override
    public String toString() {
        return "LockContext(" + name.toString() + ")";
    }
}

