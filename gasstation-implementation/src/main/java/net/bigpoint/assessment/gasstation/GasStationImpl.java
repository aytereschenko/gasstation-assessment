package net.bigpoint.assessment.gasstation;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

public class GasStationImpl implements GasStation {

    private AtomicDouble revenue = new AtomicDouble();
    private AtomicInteger numberOfSales = new AtomicInteger();
    private AtomicInteger numberOfCancellationsNoGas = new AtomicInteger();
    private AtomicInteger numberOfCancellationsTooExpensive = new AtomicInteger();

    private ConcurrentMap<GasType, CopyOnWriteArrayList<GasPump>> pumps = new ConcurrentHashMap<>();
    private ConcurrentMap<GasType, Double> prices = new ConcurrentHashMap<>();
    private ConcurrentMap<GasPump, Lock> pumpLocks = new ConcurrentHashMap<>();

    public GasStationImpl() {
        for (GasType type : GasType.values()) {
            pumps.put(type, new CopyOnWriteArrayList<GasPump>());
        }
    }

    @Override
    public void addGasPump(GasPump pump) {
        // method is idempotent - it won't duplicate pumps if it is called with the same pump twice
        // use fair lock so as to access locked pumps in FIFO fashion
        pumpLocks.putIfAbsent(pump, new ReentrantLock(true));
        pumps.putIfAbsent(pump.getGasType(), new CopyOnWriteArrayList<GasPump>());
        pumps.get(pump.getGasType()).addIfAbsent(pump);
    }

    @Override
    public Collection<GasPump> getGasPumps() {
        // return a copy of the original pump collection, but pumps themselves still can be changed
        List<GasPump> copy = new ArrayList<>();
        for (GasType type : GasType.values()) {
            copy.addAll(pumps.get(type));
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

        // we iterate through the pumps twice.
        // 1st pass: using tryLock() to ignore blocked pumps and find a suitable pump among free ones.
        // 2nd pass: using lock() through the pumps that were blocked during the 1st pass. we have to wait for them until they are free
        List<GasPump> blocked = new ArrayList<>();

        // 1st pass
        for (GasPump pump : pumps.get(type)) {
            Lock lock = pumpLocks.get(pump);
            if (lock.tryLock()) {
                try {
                    if (tryPump(pump, price, amountInLiters))
                        return price;
                } finally {
                    lock.unlock();
                }
            } else {
                blocked.add(pump);
            }
        }

        // 2nd pass
        for (GasPump pump : blocked) {
            Lock lock = pumpLocks.get(pump);
            lock.lock();
            try {
                if (tryPump(pump, price, amountInLiters))
                    return price;
            } finally {
                lock.unlock();
            }
        }

        numberOfCancellationsNoGas.incrementAndGet();
        throw new NotEnoughGasException();
    }

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    private boolean tryPump(GasPump pump, double price, double amountInLiters) {
        if (pump.getRemainingAmount() >= amountInLiters) {
            pump.pumpGas(amountInLiters);
            revenue.addAndSet(price * amountInLiters);
            numberOfSales.incrementAndGet();
            return true;
        } else {
            return false;
        }
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
