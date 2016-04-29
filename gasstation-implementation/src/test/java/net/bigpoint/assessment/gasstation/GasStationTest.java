package net.bigpoint.assessment.gasstation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GasStationTest {

    private final double eps = 1e-6;
    private GasStation station;

    @Before
    public void setUp() throws Exception {
        station = new GasStationImpl();
        station.addGasPump(new GasPump(GasType.REGULAR, 10d));
        station.addGasPump(new GasPump(GasType.DIESEL, 10d));
        station.setPrice(GasType.REGULAR, 1d);
        station.setPrice(GasType.DIESEL, 1d);
    }

    @Test
    public void testGetGasPump() {
        assertEquals(2, station.getGasPumps().size());
    }

    @Test
    public void testAddGasPump() {
        GasPump pump = new GasPump(GasType.REGULAR, 1);
        station.addGasPump(pump);
        assertEquals(3, station.getGasPumps().size());
        // addGasPump is idempotent
        station.addGasPump(pump);
        assertEquals(3, station.getGasPumps().size());
    }

    @Test (expected = GasTooExpensiveException.class)
    public void testGasTooExpensive() throws Exception {
        try {
            station.buyGas(GasType.REGULAR, 1d, 0.5d);
        } catch (Exception e) {
            assertEquals(0, station.getNumberOfSales());
            assertEquals(0, station.getNumberOfCancellationsNoGas());
            assertEquals(1, station.getNumberOfCancellationsTooExpensive());
            throw e;
        }
    }

    @Test (expected = NotEnoughGasException.class)
    public void testNotEnoughGas() throws Exception {
        try {
            station.buyGas(GasType.REGULAR, 20d, 2d);
        } catch (Exception e) {
            assertEquals(0, station.getNumberOfSales());
            assertEquals(1, station.getNumberOfCancellationsNoGas());
            assertEquals(0, station.getNumberOfCancellationsTooExpensive());
            throw e;
        }
    }

    @Test (expected = IllegalArgumentException.class)
    public void testUnspecifiedPrice() throws Exception {
        station.buyGas(GasType.SUPER, 1d, 1d);
    }

    @Test
    public void testChangePrice() {
        assertEquals(1d, station.getPrice(GasType.REGULAR), eps);
        station.setPrice(GasType.REGULAR, 2d);
        assertEquals(2d, station.getPrice(GasType.REGULAR), eps);
    }

    @Test
    public void testBuyGas() throws Exception {
        assertEquals(1d, station.buyGas(GasType.REGULAR, 5d, 2d), eps);
        assertEquals(5d, station.getRevenue(), eps);
        assertEquals(1, station.getNumberOfSales());
        assertEquals(0, station.getNumberOfCancellationsNoGas());
        assertEquals(0, station.getNumberOfCancellationsTooExpensive());
    }

    @Test (timeout = 1500)
    public void testBuyGasMultithreaded() throws Exception {
        station.addGasPump(new GasPump(GasType.REGULAR, 10d));
        station.addGasPump(new GasPump(GasType.DIESEL, 10d));
        // two pumps of REGULAR, 2 * 10 litres
        // two pumps of DIESEL, 2 * 10 litres
        // 8 threads
        ExecutorService executor = Executors.newFixedThreadPool(8);

        List<Future<Double>> results = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            results.add(asyncBuyGas(executor, GasType.REGULAR, 0.1d, 2d));
            results.add(asyncBuyGas(executor, GasType.DIESEL, 0.1d, 2d));
        }

        for (Future<Double> result : results) {
            assertEquals(1d, result.get(), eps);
        }

        // and plus last purchase to ensure all gas was consumed - there must be 2 NotEnoughGasException's
        asyncBuyGas(executor, GasType.REGULAR, 0.1d, 2d);
        asyncBuyGas(executor, GasType.DIESEL, 0.1d, 2d);

        executor.shutdown();
        // test must be finished within 1 second: 10 litres * 100 ms, all pumps work in parallel
        executor.awaitTermination(1, TimeUnit.MINUTES);

        assertEquals(40d, station.getRevenue(), eps);
        assertEquals(400, station.getNumberOfSales());
        assertEquals(2, station.getNumberOfCancellationsNoGas());
        assertEquals(0, station.getNumberOfCancellationsTooExpensive());
    }

    private Future<Double> asyncBuyGas(ExecutorService executor, final GasType type, final double amount, final double price) {
        Callable<Double> async = () -> station.buyGas(type, amount, price);
        return executor.submit(async);
    }
}
