package net.bigpoint.assessment.gasstation;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PumpQueue {

    private final GasPump pump;
    private final Lock lock = new ReentrantLock();
    private final Queue<Purchase> queue = new LinkedList<>();
    private double realAmount;
    // how much gas this pump will have taking into account all queued customers
    private volatile double estimatedAmount;
    // time to wait for all queued customers. it helps to pick the fastest pump (not the real time, just some value proportional to the requested amount of gas)
    private volatile double estimatedWaitingTime;

    public PumpQueue(GasPump pump) {
        this.pump = pump;
        realAmount = estimatedAmount = pump.getRemainingAmount();
        estimatedWaitingTime = 0;
    }

    public GasPump getPump() {
        return pump;
    }

    public double getEstimatedAmount() {
        return estimatedAmount;
    }

    public double getEstimatedWaitingTime() {
        return estimatedWaitingTime;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public Collection<Purchase> enqueuePurchase(Purchase purchase) {
        List<Purchase> old = new ArrayList<>(queue);
        queue.offer(purchase);
        reestimate();
        return old;
    }

    public void dequePurchase() {
        Purchase purchase = queue.poll();
        if (purchase != null) {
            purchase.complete();
            realAmount = pump.getRemainingAmount();
            reestimate();
        }
    }

    private void reestimate() {
        double queuedAmount = 0;
        for (Purchase p : queue) {
            queuedAmount += p.getAmount();
        }

        estimatedAmount = realAmount - queuedAmount;
        estimatedWaitingTime = queuedAmount;
    }
}
