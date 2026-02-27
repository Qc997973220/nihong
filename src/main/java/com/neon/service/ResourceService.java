package com.neon.service;

import com.neon.dao.CommentDao;
import com.neon.dao.ResourceDao;
import com.neon.pojo.Comment;
import com.neon.pojo.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResourceService {
    @Autowired
    private ResourceDao resourceRepository;
    @Autowired
    private CommentDao commentRepository;

    public List<Resource> getAllResources() {
        return resourceRepository.findAll();
    }

    public Resource getResourceDetail(Long id) {
        return resourceRepository.findById(id).orElse(null);
    }

    public List<Comment> getCommentsByResourceId(Long resourceId) {
        return commentRepository.findByResourceIdOrderByCreatedAtDesc(resourceId);
    }
}