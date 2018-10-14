package org.tools4j.nobark.loop;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test for {@link Step}
 */
public class StepTest {

    @Test
    public void composite_workDoneIfOneStepPerformsWork() {
        //given
        final AtomicBoolean workToDo = new AtomicBoolean();
        final Step workStep = workToDo::get;

        //when + then (NO WORK)
        assertFalse("all steps perform no work", Step.composite(
                () -> false,
                workStep,
                () -> false
        ).perform());

        //when + then (Step has WORK)
        workToDo.set(true);
        assertTrue("work step performed some work", Step.composite(
                () -> false,
                workStep,
                () -> false
        ).perform());

        //when + then (Other steps have WORK)
        workToDo.set(false);
        assertTrue("other step performed some work", Step.composite(
                () -> false,
                workStep,
                () -> true
        ).perform());
    }

    @Test
    public void composite_performEmptyReturnsFalse() {
        assertFalse("empty steps perform no work", Step.composite().perform());
    }

    @Test
    public void noop_performsNoWork() {
        assertFalse("NO_OP step performs no work", Step.NO_OP.perform());
    }

}