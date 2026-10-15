package com.souhenriza.sefazba.infrastructure.browser;


import java.util.concurrent.atomic.AtomicReference;

/**
 * Not a ReentrantLock: the browser's onDisconnected callback fires on Playwright's own
 * listener thread, not on the thread that called tryAcquire(), and a ReentrantLock
 * refuses to be unlocked by a thread other than its owner. Reentrancy here is manual
 * (tracked by owner + holdCount) so that a call like SefazSessionService.login() can
 * acquire again on the same thread when it's invoked as a step inside
 * NFeExtractionService.extract(), which already holds the lock, without releasing it
 * out from under the outer caller. forceRelease() is the escape hatch for the
 * disconnect callback: it clears the lock unconditionally, regardless of owner or
 * holdCount, so pending requests aren't stuck waiting on a browser that's gone.
 */
public class BrowserLockManager {

    private final AtomicReference<Thread> owner = new AtomicReference<>();
    private int holdCount = 0;

    public boolean tryAcquire() {
        Thread current = Thread.currentThread();
        if (owner.get() == current) {
            holdCount++;
            return true;
        }
        if (owner.compareAndSet(null, current)) {
            holdCount = 1;
            return true;
        }
        return false;
    }

    public void release() {
        if (owner.get() != Thread.currentThread()) {
            return;
        }
        if (--holdCount <= 0) {
            holdCount = 0;
            owner.set(null);
        }
    }

    public void forceRelease() {
        holdCount = 0;
        owner.set(null);
    }

    public boolean isBusy() {
        return owner.get() != null;
    }
}
