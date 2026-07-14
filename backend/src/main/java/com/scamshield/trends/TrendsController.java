package com.scamshield.trends;

import com.scamshield.trends.dto.TrendsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public trends page: which scam patterns are rising, aggregated from stored verdict features. */
@RestController
@RequestMapping("/api/v1/trends")
public class TrendsController {

    private final TrendsService trends;

    public TrendsController(TrendsService trends) {
        this.trends = trends;
    }

    @GetMapping
    public TrendsResponse trends(@RequestParam(defaultValue = "30d") String window) {
        return trends.trends(window);
    }
}
