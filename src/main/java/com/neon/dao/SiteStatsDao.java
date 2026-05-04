package com.neon.dao;

import com.neon.pojo.SiteStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteStatsDao extends JpaRepository<SiteStats, Long> {
}