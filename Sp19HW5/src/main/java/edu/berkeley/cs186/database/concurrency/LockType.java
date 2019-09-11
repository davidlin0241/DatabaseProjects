package edu.berkeley.cs186.database.concurrency;

public enum LockType {
    S,   // shared
    X,   // exclusive
    IS,  // intention shared
    IX,  // intention exclusive
    SIX, // shared intention exclusive
    NL;  // no lock held

    /**
     * This method checks whether lock types A and B are compatible with
     * each other. If a transaction can hold lock type A on a resource
     * at the same time another transaction holds lock type B on the same
     * resource, the lock types are compatible.
     */
    public static boolean compatible(LockType a, LockType b) {
        if (a == null || b == null) {
            throw new NullPointerException("null lock type");
        }
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
        if (a == NL || b == NL) {
            return true;
        } else if (a == IS) {
            if (b != X) {
                return true;
            }
        } else if (a == IX) {
            if (b != S &&  b != SIX && b != X) {
                return true;
            }
        } else if (a == S) {
            if (b != IX && b != SIX && b != X) {
                return true;
            }
        } else if (a == SIX) {
            if (b == IS) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method returns the least permissive lock on the parent resource
     * that must be held for a lock of type A to be granted.
     */
    public static LockType parentLock(LockType a) {
        if (a == null) {
            throw new NullPointerException("null lock type");
        }
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");
        if (a == NL) {
            return NL;
        }

        if (a == IX || a == SIX || a == X) {
            return IX;
        }

        if (a == S || a == IS) {
            return IS;
        }

        return null;
    }

    /**
     * This method returns whether a lock can be used for a situation
     * requiring another lock (e.g. an S lock can be substituted with
     * an X lock, because an X lock allows the transaction to do everything
     * the S lock allowed it to do).
     */
    public static boolean substitutable(LockType substitute, LockType required) {
        if (required == null || substitute == null) {
            throw new NullPointerException("null lock type");
        }
        //throw new UnsupportedOperationException("TODO(hw5_part1): implement");

        if (substitute == required) {
            return true;
        }

        if (required == NL) {
            return true;
        }

        if (substitute == X && required == S) {
            return true;
        }

        if (substitute == SIX && (required == S || required == IS || required == IX)) {
            return true;
        }

        if (substitute == IX && required == IS)  {
            return true;
        }

        return false;
    }

    @Override
    public String toString() {
        switch (this) {
        case S: return "S";
        case X: return "X";
        case IS: return "IS";
        case IX: return "IX";
        case SIX: return "SIX";
        case NL: return "NL";
        default: throw new UnsupportedOperationException("bad lock type");
        }
    }
};

