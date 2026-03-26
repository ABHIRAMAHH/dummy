package com.cloudsim.controller;

import com.cloudsim.Main;
import com.cloudsim.SchedulerType;
import com.cloudsim.model.SimulationResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/simulate")
@CrossOrigin
public class SimulationController {

    @GetMapping
    public SimulationResult run(@RequestParam String scheduler) throws Exception {

        SchedulerType type = SchedulerType.valueOf(scheduler);

        return Main.runSimulationAPI(type);
    }
}