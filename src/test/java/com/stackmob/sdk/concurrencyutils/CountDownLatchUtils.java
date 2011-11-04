package com.stackmob.sdk.concurrencyutils;

import com.stackmob.sdk.exception.StackMobException;
import com.stackmob.sdk.util.Pair;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.fail;

public class CountDownLatchUtils {
    public static final Pair<Long, TimeUnit> MAX_LATCH_WAIT_TIME = new Pair<Long, TimeUnit>(4000L, TimeUnit.MILLISECONDS);

    public static boolean wasLatchFinished(CountDownLatch latch) throws InterruptedException {
        return latch.await(MAX_LATCH_WAIT_TIME.getFirst(), MAX_LATCH_WAIT_TIME.getSecond());
    }

    public static void assertLatchFinished(CountDownLatch latch, AtomicBoolean exceptionBoolean, AtomicReference<StackMobException> exceptionReference) throws StackMobException, InterruptedException {
        assertLatchFinished(latch, MAX_LATCH_WAIT_TIME, exceptionBoolean, exceptionReference);
    }

    public static void assertLatchFinished(CountDownLatch latch, Pair<Long, TimeUnit> latchWaitTime, AtomicBoolean exceptionBoolean, AtomicReference<StackMobException> exceptionReference)
    throws StackMobException, InterruptedException {
        boolean finished = wasLatchFinished(latch);
        if(!finished && exceptionBoolean.get()) {
            throw exceptionReference.get();
        }
        else if(!finished) {
            fail("latch didn't count down in " + latchWaitTime.getFirst() + " " + latchWaitTime.getSecond().toString().toLowerCase());
        }
    }

    public static CountDownLatch latch(int count) {
        return new CountDownLatch(count);
    }

    public static CountDownLatch latchOne() {
        return latch(1);
    }
}