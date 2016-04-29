package net.bigpoint.assessment.gasstation;

import java.util.concurrent.CountDownLatch;

public class Purchase {

    private final double amount;
    private final CountDownLatch completion = new CountDownLatch(1);

    public Purchase(double amount) {
        this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }

    public CountDownLatch getCompletion() {
        return completion;
    }

    public void complete() {
        completion.countDown();
    }
}
