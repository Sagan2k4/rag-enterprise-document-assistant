package com.enterprise.rag.observability;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/dashboard")
    public MetricsService.Dashboard dashboard(@RequestParam(defaultValue = "7") int days) {
        return metricsService.dashboard(Math.max(1, Math.min(days, 90)));
    }
}
