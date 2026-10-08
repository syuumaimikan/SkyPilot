package dev.skypilot.mod.core;

import java.util.Arrays;

/** Fixed-size rolling frame intervals; stalls count, loading/focus transitions can be reset. */
public final class FrameSamples {
    private final double[] samples;
    private int count, cursor;
    public FrameSamples(int capacity) { if (capacity < 1) throw new IllegalArgumentException(); samples = new double[capacity]; }
    public void add(double milliseconds) {
        if (!Double.isFinite(milliseconds) || milliseconds <= 0) return;
        samples[cursor] = milliseconds; cursor = (cursor + 1) % samples.length; count = Math.min(count + 1, samples.length);
    }
    public void clear() { count = cursor = 0; }
    public int count() { return count; }
    public double percentile(double fraction) {
        if (count == 0) return 0;
        double[] sorted = Arrays.copyOf(samples, count); Arrays.sort(sorted);
        int index = Math.max(0, Math.min(count - 1, (int)Math.ceil(fraction * count) - 1));
        return sorted[index];
    }
    public double slowFrameFps() { double p99 = percentile(.99); return p99 == 0 ? 0 : 1000 / p99; }
}
