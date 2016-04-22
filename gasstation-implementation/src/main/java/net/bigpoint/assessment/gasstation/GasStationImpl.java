package net.bigpoint.assessment.gasstation;

import java.util.Collection;

import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

public class GasStationImpl implements GasStation {

    @Override
    public void addGasPump(GasPump pump) {

    }

    @Override
    public Collection<GasPump> getGasPumps() {
        return null;
    }

    @Override
    public double buyGas(GasType type, double amountInLiters, double maxPricePerLiter) throws NotEnoughGasException, GasTooExpensiveException {
        return 0;
    }

    @Override
    public double getRevenue() {
        return 0;
    }

    @Override
    public int getNumberOfSales() {
        return 0;
    }

    @Override
    public int getNumberOfCancellationsNoGas() {
        return 0;
    }

    @Override
    public int getNumberOfCancellationsTooExpensive() {
        return 0;
    }

    @Override
    public double getPrice(GasType type) {
        return 0;
    }

    @Override
    public void setPrice(GasType type, double price) {

    }
}
