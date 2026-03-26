package com.cloudsim;

public class VmTemplate {
    public String name;
    public double mips;
    public int ram;
    public int pes;
    public long bw;
    public double costPerSec; // cost per simulation second

   public VmTemplate(String name, double mips, int ram, long bw) {
        this.name = name;
        this.mips = mips;
        this.ram = ram;
        this.pes=pes;
        this.bw = bw;
        this.costPerSec = 0.01; // default value, you can modify this if needed
    }

    // (Optional) No-args constructor for JSON parsing
    public VmTemplate() {
    }
}
