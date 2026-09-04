package com.revenueRecovery.controller;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
@RestController @RequestMapping("/api") @CrossOrigin(origins="*")
public class PingController {
    @GetMapping("/ping") public Map<String,Object> ping(){return Map.of("status","ok","timestamp", Instant.now());}
}
