package net.bigpoint.assessment.gasstation;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicDouble extends Number {

    private final AtomicLong longValue;

    public AtomicDouble() {
        longValue = new AtomicLong(0L);
    }

    @Override
    public int intValue() {
        return (int) get();
    }

    @Override
    public long longValue() {
        return (long) get();
    }

    @Override
    public float floatValue() {
        return (float) get();
    }

    @Override
    public double doubleValue() {
        return get();
    }

    final public double get() {
        return Double.longBitsToDouble(longValue.get());
    }

    final public void set(double value) {
        longValue.set(Double.doubleToLongBits(value));
    }

    final public double addAndSet(final double value) {
        for(;;) {
            final long currentValue = longValue.get();
            final double next = Double.longBitsToDouble(currentValue) + value;
            long nextValue = Double.doubleToLongBits(next);
            if (longValue.compareAndSet(currentValue, nextValue)) {
                return next;
            }
        }
    }
}
