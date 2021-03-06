package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.BaseTransaction;
import edu.berkeley.cs186.database.LoggingLockManager;
import edu.berkeley.cs186.database.TimeoutScaling;
import edu.berkeley.cs186.database.categories.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

@Category({HW5Tests.class, HW5Part2Tests.class})
public class TestLockUtil {
    private LoggingLockManager lockManager;
    private BaseTransaction[] transactions;
    private LockContext dbContext;
    private LockContext tableContext;
    private LockContext[] pageContexts;

    @Rule
    public TestRule globalTimeout = new DisableOnDebug(Timeout.millis((long) (
                1000 * TimeoutScaling.factor)));

    @Before
    public void setUp() {
        lockManager = new LoggingLockManager();
        transactions = new BaseTransaction[8];
        dbContext = lockManager.databaseContext();
        tableContext = dbContext.childContext("table1");
        pageContexts = new LockContext[8];
        for (int i = 0; i < transactions.length; ++i) {
            transactions[i] = new DummyTransaction(lockManager, i);
            pageContexts[i] = tableContext.childContext((long) i);
        }
    }

    @Test
    @Category(PublicTests.class)
    public void testRequestNullTransaction() {
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(null, pageContexts[4], LockType.S);
        assertEquals(Arrays.asList(), lockManager.log);
    }

    @Test
    @Category(PublicTests.class)
    public void testSimpleAcquire() {
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.S);
        assertEquals(Arrays.asList(
                         "acquire 0 database IS",
                         "acquire 0 database/table1 IS",
                         "acquire 0 database/table1/4 S"
                     ), lockManager.log);
    }

    @Test
    @Category(PublicTests.class)
    public void testSimplePromote() {
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.S);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.X);
        assertEquals(Arrays.asList(
                         "promote 0 database IX",
                         "promote 0 database/table1 IX",
                         "promote 0 database/table1/4 X"
                     ), lockManager.log);
    }

    //not completely sure about this one
    @Test
    @Category(PublicTests.class)
    public void testPiazza1() {
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.X);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.S);
        assertEquals(Arrays.asList(
                "acquire-and-release 0 database/table1 X [database/table1, database/table1/4]"
        ), lockManager.log);
    }

    @Test
    @Category(PublicTests.class)
    public void testPiazza4() {
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.S);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.X);
        assertEquals(Arrays.asList(
                "promote 0 database IX",
                "acquire-and-release 0 database/table1 S [database/table1, database/table1/4]",
                "promote 0 database/table1 X"
        ), lockManager.log);
    }


    @Test
    @Category(PublicTests.class)
    public void testPiazza2() {
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.S);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.X);
        assertEquals(Arrays.asList(
                "promote 0 database IX",
                "promote 0 database/table1 SIX",
                "acquire 0 database/table1/4 X"
        ), lockManager.log);
    }

    @Test
    @Category(PublicTests.class)
    public void testPiazza3() {
        LockUtil.ensureSufficientLockHeld(transactions[0], dbContext, LockType.X);
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.S);
    }

    @Test
    @Category(PublicTests.class)
    public void testPiazza5() {
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.S);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.X);
        assertEquals(Arrays.asList(
                "promote 0 database IX",
                "promote 0 database/table1 X"
        ), lockManager.log);
    }

    @Test
    @Category(PublicTests.class)
    public void testPiazza6() {
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.S);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], dbContext, LockType.X);
        assertEquals(Arrays.asList(
                "acquire-and-release 0 database S [database, database/table1]",
                "promote 0 database X"
        ), lockManager.log);
    }

    @Test
    @Category(PublicTests.class)
    public void testPiazza7() {
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.S);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.S);
    }

    @Test
    @Category(PublicTests.class)
    public void testPiazza8() {
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[0], LockType.S);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.S);
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[0], LockType.S);
        assertEquals(Arrays.asList(
                "acquire-and-release 0 database/table1 S [database/table1, database/table1/0]"
        ), lockManager.log);
    }


    @Test
    @Category(PublicTests.class)
    public void testIStoS() {
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.S);
        pageContexts[4].release(transactions[0]);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.S);
        assertEquals(Arrays.asList(
                         "acquire-and-release 0 database/table1 S [database/table1]"
                     ), lockManager.log);
    }

    @Test
    @Category(PublicTests.class)
    public void testSimpleEscalate() {
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.S);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], tableContext, LockType.S);
        assertEquals(Collections.singletonList(
                         "acquire-and-release 0 database/table1 S [database/table1, database/table1/4]"
                     ), lockManager.log);
    }

    @Test
    @Category(PublicTests.class)
    public void testIXBeforeIS() {
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[3], LockType.X);
        lockManager.startLog();
        LockUtil.ensureSufficientLockHeld(transactions[0], pageContexts[4], LockType.S);
        assertEquals(Arrays.asList(
                         "acquire 0 database/table1/4 S"
                     ), lockManager.log);
    }

}

