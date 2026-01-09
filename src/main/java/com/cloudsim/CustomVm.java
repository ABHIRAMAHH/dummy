package com.cloudsim;

import org.cloudbus.cloudsim.vms.VmSimple;
import java.time.LocalDateTime;

public class CustomVm extends VmSimple {

    private LocalDateTime taskTime;

    public CustomVm(double mips, int pes) {
        super(mips, pes);
    }

    public void setTaskTime(LocalDateTime time) {
        this.taskTime = time;
    }

    public LocalDateTime getTaskTime() {
        return this.taskTime;
    }
}
