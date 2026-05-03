package com.neon.service;

import com.neon.dao.CardKeyDao;
import com.neon.pojo.CardKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class CardKeyService {

    @Autowired
    private CardKeyDao cardKeyDao;

    private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int KEY_LENGTH = 12;

    public boolean validateCardKey(String cardKey) {
        if (cardKey == null || cardKey.trim().isEmpty()) {
            return false;
        }

        Optional<CardKey> cardKeyOpt = cardKeyDao.findByCardKey(cardKey.trim());
        if (cardKeyOpt.isEmpty()) {
            return false;
        }

        CardKey ck = cardKeyOpt.get();
        return ck.getStatus() == 0;
    }

    @Transactional
    public Map<String, Object> useCardKey(String cardKey, String usedBy) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);

        if (cardKey == null || cardKey.trim().isEmpty()) {
            result.put("message", "卡密不能为空");
            return result;
        }

        Optional<CardKey> cardKeyOpt = cardKeyDao.findByCardKey(cardKey.trim());
        if (cardKeyOpt.isEmpty()) {
            result.put("message", "卡密不存在");
            return result;
        }

        CardKey ck = cardKeyOpt.get();
        if (ck.getStatus() != 0) {
            result.put("message", "卡密已被使用");
            return result;
        }

        ck.setStatus(1);
        ck.setUsedAt(LocalDateTime.now());
        ck.setUsedBy(usedBy);
        ck.setUpdatedAt(LocalDateTime.now());
        cardKeyDao.save(ck);

        result.put("success", true);
        result.put("memberType", ck.getMemberType());
        result.put("memberTypeName", ck.getMemberTypeName());
        result.put("expireDays", getExpireDays(ck.getMemberType()));
        return result;
    }

    public static int getExpireDays(Integer memberType) {
        if (memberType == null) return 0;
        switch (memberType) {
            case CardKey.TYPE_MONTHLY: return 30;
            case CardKey.TYPE_QUARTERLY: return 90;
            case CardKey.TYPE_YEARLY: return 360;
            case CardKey.TYPE_PERMANENT: return Integer.MAX_VALUE;
            default: return 0;
        }
    }

    public CardKey save(CardKey cardKey) {
        return cardKeyDao.save(cardKey);
    }

    public long countAvailable() {
        return cardKeyDao.findAll().stream()
                .filter(ck -> ck.getStatus() == 0)
                .count();
    }

    private String generateRandomKey() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    @Transactional
    public List<String> generateCardKeys(int count, int memberType) {
        List<String> generatedKeys = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            String key;
            do {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < KEY_LENGTH; j++) {
                    sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
                }
                key = sb.toString();
            } while (cardKeyDao.existsByCardKey(key));

            CardKey cardKey = new CardKey(key);
            cardKey.setMemberType(memberType);
            cardKeyDao.save(cardKey);
            generatedKeys.add(key);
        }

        return generatedKeys;
    }

    public List<CardKey> findAll() {
        return cardKeyDao.findAll();
    }

    @Transactional
    public void deleteById(Long id) {
        cardKeyDao.deleteById(id);
    }
}
