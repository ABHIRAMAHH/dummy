package com.cloudsim;


import com.cloudsim.model.SimulationResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimulationResultTest {
    @Test
    void testSimulationResultConstructor() {
        SimulationResult res = new SimulationResult(
                "TEST",
                new HashMap<>(),
                100.0,
                50.0,
                0.5,
                10,
                20,
                1.5,
                2.0
        );

        assertEquals("TEST", res.schedulerName);
    }
}
