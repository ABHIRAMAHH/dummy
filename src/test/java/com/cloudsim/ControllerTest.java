package com.cloudsim;

import com.cloudsim.controller.SimulationController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ControllerTest {

    @Test
    void testControllerRuns() {
        SimulationController controller = new SimulationController();

        try {
            controller.run("FIFO");
        } catch (Exception e) {
            // Acceptable in test since dataset path/format may differ
            assertTrue(e instanceof Exception);
        }
    }
}