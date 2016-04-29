package net.bigpoint.assessment.gasstation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

public class GasStationImpl implements GasStation {

    private final AtomicDouble revenue = new AtomicDouble();
    private final AtomicInteger numberOfSales = new AtomicInteger();
    private final AtomicInteger numberOfCancellationsNoGas = new AtomicInteger();
    private final AtomicInteger numberOfCancellationsTooExpensive = new AtomicInteger();

    private final Map<GasType, Lock> gasTypeLocks = new ConcurrentHashMap<>();
    private final Map<GasType, List<PumpQueue>> pumps = new ConcurrentHashMap<>();
    private final ConcurrentMap<GasType, Double> prices = new ConcurrentHashMap<>();

    public GasStationImpl() {
        for (GasType type : GasType.values()) {
            gasTypeLocks.put(type, new ReentrantLock());
            // we use non-synchronous list, because it is guarded by gasTypeLock
            pumps.put(type, new ArrayList<>());
        }
    }

    @Override
    public void addGasPump(GasPump pump) {
        // method is idempotent - it won't duplicate pumps if it is called with the same pump twice
        Lock lock = gasTypeLocks.get(pump.getGasType());
        lock.lock();
        try {
            List<PumpQueue> pumpList = pumps.get(pump.getGasType());
            if (pumpList.stream().noneMatch(p -> p.getPump() == pump)) {
                pumpList.add(new PumpQueue(pump));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Collection<GasPump> getGasPumps() {
        // return a copy of the original pump collection, but pumps themselves still can be changed
        List<GasPump> copy = new ArrayList<>();
        for (GasType type : GasType.values()) {
            Lock lock = gasTypeLocks.get(type);
            lock.lock();
            try {
                copy.addAll(pumps.get(type).stream().map(PumpQueue::getPump).collect(Collectors.toList()));
            } finally {
                lock.unlock();
            }
        }
        return copy;
    }

    @Override
    public void setPrice(GasType type, double price) {
        prices.put(type, price);
    }

    @Override
    public double getPrice(GasType type) {
        Double price = prices.get(type);
        if (price == null) {
            throw new IllegalArgumentException("Price was not specified for gas type " + type);
        }
        return price;
    }

    @Override
    public double buyGas(GasType type, double amountInLiters, double maxPricePerLiter) throws NotEnoughGasException, GasTooExpensiveException {
        double price = getPrice(type);
        if (price > maxPricePerLiter) {
            numberOfCancellationsTooExpensive.incrementAndGet();
            throw new GasTooExpensiveException();
        }

        // 1)
        // So as to fail-fast we need to know the amount of gas a pump will have after all enqueued customers will be served. We achieve this using PumpQueue class.
        // PumpQueue also tracks the estimated waiting time for the queue of customers. It allows us to find the fastest pump.

        // 2)
        // We also need to ensure that the execution order of buyGas() is the same as the order of customers in the pump queue. To achieve this we use
        // CountDownLatch for each purchase, that becomes signaled when the purchase is completed. When a new customer is enqueued in the queue it receives
        // the list of CountDownLatch'es of all previous customers and must wait for them to become completed.

        PumpQueue pump = null;
        Collection<Purchase> prev = null;
        Lock lock = gasTypeLocks.get(type);
        lock.lock();
        try {
            pump = pickFastestPump(type, amountInLiters);
            prev = pump.enqueuePurchase(new Purchase(amountInLiters));
        } finally {
            lock.unlock();
        }

        // Method GasPump::pumpGas() ignores InterruptedException that is a very bad style. It must pass this exception to the caller.
        // Method buyGas must throw InterruptedException as well. Since we may not change the interface let's throw RuntimeException
        try {
            for (Purchase p : prev) {
                p.getCompletion().await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Operation interrupted", e);
        }

        pump.lock();
        try {
            pump.getPump().pumpGas(amountInLiters);
            revenue.addAndSet(price * amountInLiters);
            numberOfSales.incrementAndGet();
            lock.lock();
            try {
                pump.dequePurchase();
            } finally {
                lock.unlock();
            }
        } finally {
            pump.unlock();
        }

        return price;
    }

    private PumpQueue pickFastestPump(GasType type, double amount) throws NotEnoughGasException {
        return pumps.get(type).stream()
                .filter(p -> p.getEstimatedAmount() >= amount)
                .sorted((p1, p2) -> Double.compare(p1.getEstimatedWaitingTime(), p2.getEstimatedWaitingTime()))
                .findFirst()
                .orElseThrow(() -> {
                    numberOfCancellationsNoGas.incrementAndGet();
                    return new NotEnoughGasException();
                });
    }

    @Override
    public double getRevenue() {
        return revenue.get();
    }

    @Override
    public int getNumberOfSales() {
        return numberOfSales.get();
    }

    @Override
    public int getNumberOfCancellationsNoGas() {
        return numberOfCancellationsNoGas.get();
    }

    @Override
    public int getNumberOfCancellationsTooExpensive() {
        return numberOfCancellationsTooExpensive.get();
    }
}
