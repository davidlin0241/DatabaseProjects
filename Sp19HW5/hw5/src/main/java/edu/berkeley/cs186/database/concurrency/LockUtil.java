package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.BaseTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LockUtil is a declarative layer which simplifies multigranularity lock acquisition
 * for the user (you, in the second half of Part 2). Generally speaking, you should use LockUtil
 * for lock acquisition instead of calling LockContext methods directly.
 */
public class LockUtil {

    public static Boolean isPageContext(LockContext lC) {
        LockContext ancestor = lC.parent;
        int iterTimes = 0;

        while (ancestor != null) {
            ancestor = ancestor.parent;
            iterTimes += 1;
        }

        return iterTimes == 2;
    }

    public static Boolean isTableContext(LockContext lC) {
        LockContext ancestor = lC.parent;
        int iterTimes = 0;

        while (ancestor != null) {
            ancestor = ancestor.parent;
            iterTimes += 1;
        }

        return iterTimes == 1;
    }

    public static List<LockType> addAllLockTypes() {
        List<LockType> L = new ArrayList<>();
        L.add(LockType.S);
        L.add(LockType.X);
        L.add(LockType.IS);
        L.add(LockType.IX);
        L.add(LockType.SIX);
        return L;
    }

    public static LockType getDesiredLockType(LockType curr, LockType requested) {
        List<LockType> allLockTypes = addAllLockTypes();

        for (LockType ele: allLockTypes) {
            if (LockType.substitutable(ele, curr) && LockType.substitutable(ele, requested)) {
                return ele;
            }
        }

        return LockType.NL;
    }

    public static List<LockType> getDesiredLockTypes(List<LockType> current, List<LockType> real) {
        assert current.size() == real.size() : " The list sizes should be equal. ";
        LockType lC, lR;
        List<LockType> allLockTypes = addAllLockTypes();
        List<LockType> desiredLockTypes = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            lC = current.get(i);
            lR = real.get(i);

            for (LockType ele: allLockTypes) {
                if (LockType.substitutable(ele, lC) && LockType.substitutable(ele, lR)) {
                    desiredLockTypes.add(ele);
                    break;
                }
            }
        }
        return desiredLockTypes;
    }

    public static List<LockContext> getLockContexts(LockManager lockman, List<Lock> locks) {
        List<LockContext> lockContexts = new ArrayList<>();

        for (Lock L: locks) {
            lockContexts.add(LockContext.fromResourceName(lockman, L.name));
        }

        return lockContexts;
    }

    /**
     * Ensure that TRANSACTION can perform actions requiring LOCKTYPE on LOCKCONTEXT.
     *
     * This method should promote/escalate as needed, but should only grant the least
     * permissive set of locks needed.
     *
     * lockType must be one of LockType.S, LockType.X, and behavior is unspecified
     * if an intent lock is passed in to this method (you can do whatever you want in this case).
     *
     * If TRANSACTION is null, this method should do nothing.
     */
    public static void ensureSufficientLockHeld(BaseTransaction transaction, LockContext lockContext,
            LockType lockType) {
        //throw new UnsupportedOperationException("TODO(hw5_part2): implement");
        if (transaction == null
            || lockType == LockType.IS
            || lockType == LockType.IX
            || lockType == LockType.SIX) {
            return;
        }



        //maintain existing permissions before the call.
        //escalate only when promotion is illegal (would raise an exception)
        //escalate only to S or X (less permissive)
        //A promotion
        // * from lock type A to lock type B is valid if and only if B is substitutable
        // * for A, and B is not equal to A.

        //1. Ensure appropriate locks on ancestors

        List<Lock> transactionLocks = lockContext.lockman.getLocks(transaction);

        List<LockContext> lockContexts = getLockContexts(lockContext.lockman,
                transactionLocks); //top down)

        LockType parentLockType = LockType.parentLock(lockType), currLockType,
            desiredLockType;

        LockContext ancestor;

        List<LockContext> ancestors = new ArrayList<>();

        //int numChildLocks;

        Boolean isPage;
        Boolean autoEscalate = false;
        LockContext tableContext = null;

        if (isPageContext(lockContext)) {
            tableContext = lockContext.parent;
            if (tableContext.saturation(transaction)/tableContext.capacity() >= .20
                && tableContext.capacity() >= 10) {
                autoEscalate = true;
            }
        }


        for (LockContext lC: lockContexts) {
            currLockType = lC.getEffectiveLockType(transaction);
            //is current context level and lockContext level the page level
            //isNotPage = lockContext.parent != null && !lockContext.parent.equals(lC.parent);
            //numChildLocks = lC.getNumChildrenLocks(transaction);
            isPage = isPageContext(lC);


            if (!isPage && currLockType == LockType.X) {
                return;
            }


            if (!isPage && currLockType == LockType.S) {
                return;
            }



            if (!lC.equals(lockContext)) {

                if (currLockType != parentLockType
                        && !LockType.substitutable(currLockType, parentLockType)) {

                    /*
                    if (isNotPage) { //IX before IS case
                        desiredLockType = getDesiredLockType(currLockType, parentLockType);
                        lC.promote(transaction, desiredLockType);
                    }
                    */

                    if (!isPage) { //if 0, then it is a pageContext.
                        desiredLockType = getDesiredLockType(currLockType, parentLockType);
                        if (desiredLockType != currLockType) {
                            lC.promote(transaction, desiredLockType);
                        }
                    }
                }
            } else {
                break;
            }
        }

        //case where all locks are NL
        if (lockContexts.size() == 0) {
            ancestor = lockContext.parent;
            while (ancestor != null) {
                ancestors.add(ancestor);
                ancestor = ancestor.parent;
            }

            Collections.reverse(ancestors);

            for (LockContext a: ancestors) {
                a.acquire(transaction, parentLockType);
            }
        }

        //2. acquiring lock on LockContext's resource
        currLockType = lockContext.getEffectiveLockType(transaction);
        Boolean needPromote;


         if (autoEscalate) {
             tableContext.escalate(transaction);
             needPromote = !LockType.substitutable(tableContext.getExplicitLockType(transaction), lockType);

             if (needPromote) {
                 tableContext.promote(transaction, lockType);
             }

             return;
         }

         if (currLockType == LockType.NL) {
             lockContext.acquire(transaction, lockType);
             return;
         } else if (currLockType == lockType) {
             return;
         } else {
            try {
                lockContext.promote(transaction, lockType);
            }  catch (Exception e) {
                //specialLockType = getDesiredLockType(currLockType, lockType);
                lockContext.escalate(transaction);

                //Check if request is satisfied after escalation. If not, promote.
                currLockType = lockContext.getEffectiveLockType(transaction);
                needPromote = !LockType.substitutable(currLockType, lockType);

                if (needPromote) {
                    lockContext.promote(transaction, lockType);
                    /*
                    try {
                        lockContext.promote(transaction, lockType);
                    }  catch (Exception d) {
                        lockContext.promote(transaction, specialLockType);
                    }
                    */
                }


                /*
                currLockType = lockContext.getExplicitLockType(transaction);
                if (LockType.substitutable(lockType, currLockType) && currLockType != lockType) { //w
                    lockContext.promote(transaction, lockType);
                }
                */

            }
        }

    }

    // TODO(hw5): add helper methods as you see fit
}
