package com.engram.dashboard;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardView getDashboard(@RequestParam UUID userId) {
        return service.getDashboard(userId);
    }
}
