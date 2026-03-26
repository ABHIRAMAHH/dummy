package com.cloudsim;

import org.cloudbus.cloudsim.vms.VmSimple;

public class CostVmSimple extends VmSimple {
    private double costPerSecond;

    public CostVmSimple(double mipsCapacity, long numberOfPes) {
        super(mipsCapacity, numberOfPes);
    }

    public double getCostPerSecond() {
        return costPerSecond;
    }

    public CostVmSimple setCostPerSecond(double costPerSecond) {
        this.costPerSecond = costPerSecond;
        return this;
    }
}