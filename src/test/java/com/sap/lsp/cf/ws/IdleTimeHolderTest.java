package com.sap.lsp.cf.ws;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class IdleTimeHolderTest {
    @Test
    public void initialIdleTimeVeryBig() {
        assertTrue(IdleTimeHolder.getInstance().getIdleTime() > 100);
    }

    @Test
    public void immediateCheck() {
        IdleTimeHolder.getInstance().registerUserActivity();
        assertTrue(IdleTimeHolder.getInstance().getIdleTime() < 100);
    }

    @Test
    public void delayedCheck() throws InterruptedException {
        IdleTimeHolder.getInstance().registerUserActivity();
        Thread.sleep(101);
        assertTrue(IdleTimeHolder.getInstance().getIdleTime() > 100);
    }


}
