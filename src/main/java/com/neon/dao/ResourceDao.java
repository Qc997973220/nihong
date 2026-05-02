package com.neon.dao;

import com.neon.pojo.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceDao extends JpaRepository<Resource, Long> {
    List<Resource> findByStatusOrderByCreatedAtDesc(Integer status);
    List<Resource> findByStatusInOrderByCreatedAtDesc(List<Integer> statuses);
    List<Resource> findByTitleContainingIgnoreCaseAndStatusIn(String title, List<Integer> statuses);
}
