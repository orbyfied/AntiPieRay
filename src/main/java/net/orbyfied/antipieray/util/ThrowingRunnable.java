package net.orbyfied.antipieray.util;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Throwable;
}
