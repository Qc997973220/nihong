package com.neon.controller;

import com.neon.pojo.CardKey;
import com.neon.service.CardKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/cardkey")
public class CardKeyController {

    @Autowired
    private CardKeyService cardKeyService;

    @PostMapping("/generate")
    public Map<String, Object> generateCardKeys(@RequestParam(defaultValue = "10") int count) {
        Map<String, Object> result = new HashMap<>();
        if (count <= 0 || count > 100) {
            result.put("success", false);
            result.put("message", "生成数量必须在1-100之间");
            return result;
        }
        
        List<String> keys = cardKeyService.generateCardKeys(count);
        result.put("success", true);
        result.put("message", "成功生成" + count + "个卡密");
        result.put("count", keys.size());
        result.put("keys", keys);
        return result;
    }

    @GetMapping("/list")
    public Map<String, Object> listCardKeys() {
        Map<String, Object> result = new HashMap<>();
        List<CardKey> keys = cardKeyService.findAll();
        result.put("success", true);
        result.put("data", keys);
        result.put("total", keys.size());
        return result;
    }

    @GetMapping("/count")
    public Map<String, Object> countAvailable() {
        Map<String, Object> result = new HashMap<>();
        long count = cardKeyService.countAvailable();
        result.put("success", true);
        result.put("availableCount", count);
        return result;
    }

    @DeleteMapping("/delete/{id}")
    public Map<String, Object> deleteCardKey(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            cardKeyService.deleteById(id);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
        }
        return result;
    }
}