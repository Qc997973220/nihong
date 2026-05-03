package com.neon.service;

import com.neon.dao.CardKeyDao;
import com.neon.pojo.CardKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CardKeyService {

    @Autowired
    private CardKeyDao cardKeyDao;

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
    public boolean useCardKey(String cardKey, String usedBy) {
        if (cardKey == null || cardKey.trim().isEmpty()) {
            return false;
        }

        Optional<CardKey> cardKeyOpt = cardKeyDao.findByCardKey(cardKey.trim());
        if (cardKeyOpt.isEmpty()) {
            return false;
        }

        CardKey ck = cardKeyOpt.get();
        if (ck.getStatus() != 0) {
            return false;
        }

        ck.setStatus(1);
        ck.setUsedAt(LocalDateTime.now());
        ck.setUsedBy(usedBy);
        ck.setUpdatedAt(LocalDateTime.now());
        cardKeyDao.save(ck);
        return true;
    }

    public CardKey save(CardKey cardKey) {
        return cardKeyDao.save(cardKey);
    }

    public long countAvailable() {
        return cardKeyDao.findAll().stream()
                .filter(ck -> ck.getStatus() == 0)
                .count();
    }
}
