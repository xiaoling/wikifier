package edu.illinois.cs.cogcomp.wikifier.utils;

/**
 * A simple class for simple timing For complete benchmarks use profilers
 * 
 * @author cheng88
 * 
 */
public abstract class Timer {

    protected String name;

    public Timer(String name) {
        this.name = name;
    }

    protected abstract void run() throws Exception;

    public void timedRun() {
        long start = System.currentTimeMillis();
        try {
            run();
        } catch (Exception e) {
            e.printStackTrace();
        }
        summarize(start);
    }

    public void timedRun(int iterations) {

        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            try {
                run();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        System.out.printf("Took %d ms on average.\n",summarize(start)/iterations);
    }

    /**
     * Override to report time usage in other ways
     * 
     * @param startTime
     */
    public long summarize(long startTime) {
        long timeSpent = System.currentTimeMillis() - startTime;
        System.out.printf("%s took %d ms\n", name, timeSpent);
        return timeSpent;
    }

    public Thread getThread() {
        return new Thread() {
            public void run() {
                timedRun();
            }
        };
    }

}
