package com.neon.dao;

import com.neon.pojo.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ResourceDao extends JpaRepository<Resource, Long> {
    // 按置顶优先排序，然后按创建时间降序
    List<Resource> findByStatusOrderByTopDescCreatedAtDesc(Integer status);
    List<Resource> findByStatusOrderByViewCountDescCreatedAtDesc(Integer status);
    List<Resource> findByStatusInOrderByTopDescCreatedAtDesc(List<Integer> statuses);
    List<Resource> findByTitleContainingIgnoreCaseAndStatusInOrderByTopDescCreatedAtDesc(String title, List<Integer> statuses);
    Page<Resource> findByStatusInOrderByTopDescCreatedAtDesc(List<Integer> statuses, Pageable pageable);
    Page<Resource> findByTitleContainingIgnoreCaseAndStatusInOrderByTopDescCreatedAtDesc(String title, List<Integer> statuses, Pageable pageable);
    Page<Resource> findByCategoryAndStatusInOrderByTopDescCreatedAtDesc(String category, List<Integer> statuses, Pageable pageable);
    Page<Resource> findByTitleContainingIgnoreCaseAndCategoryAndStatusInOrderByTopDescCreatedAtDesc(String title, String category, List<Integer> statuses, Pageable pageable);
    Page<Resource> findByCategoryInAndStatusInOrderByTopDescCreatedAtDesc(List<String> categories, List<Integer> statuses, Pageable pageable);
    Page<Resource> findByTitleContainingIgnoreCaseAndCategoryInAndStatusInOrderByTopDescCreatedAtDesc(String title, List<String> categories, List<Integer> statuses, Pageable pageable);
    List<Resource> findByCreatedAtGreaterThanEqualAndStatusInOrderByViewCountDescCreatedAtDesc(LocalDateTime createdAt, List<Integer> statuses, Pageable pageable);
    List<Resource> findByCreatedAtGreaterThanEqualAndStatusIn(LocalDateTime createdAt, List<Integer> statuses);
    long countByStatusIn(List<Integer> statuses);
    
    // 保留旧方法用于兼容
    List<Resource> findByStatusOrderByCreatedAtDesc(Integer status);
}
