package com.neon.controller;

import com.neon.service.SiteStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/visitor")
public class VisitorController {

    @Autowired
    private SiteStatsService siteStatsService;

    @GetMapping("/count")
    public Map<String, Object> getVisitorCount() {
        Map<String, Object> result = new HashMap<>();
        try {
            Long visitorCount = siteStatsService.getVisitorCount();
            result.put("success", true);
            result.put("count", visitorCount);
        } catch (Exception e) {
            result.put("success", false);
            result.put("count", 92895L);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/count/increment")
    public Map<String, Object> incrementVisitorCount() {
        Map<String, Object> result = new HashMap<>();
        try {
            Long newCount = siteStatsService.incrementInRedis();
            result.put("success", true);
            result.put("count", newCount);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}