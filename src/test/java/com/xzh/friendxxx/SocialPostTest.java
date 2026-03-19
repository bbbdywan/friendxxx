package com.xzh.friendxxx;

import com.xzh.friendxxx.mapper.SocialPostMapper;
import com.xzh.friendxxx.model.entity.SocialPost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(properties = {"websocket.enabled=false"})
public class SocialPostTest {

    @Autowired
    private SocialPostMapper socialPostMapper;

    @Test
    public void testSelectAllWithTypeHandler() {
        System.out.println("=== 测试SocialPost查询和JSON转换 ===");
        
        List<SocialPost> socialPosts = socialPostMapper.selectAllWithTypeHandler();
        
        System.out.println("查询结果数量: " + socialPosts.size());
        
        for (SocialPost post : socialPosts) {
            System.out.println("=== SocialPost详细信息 ===");
            System.out.println("ID: " + post.getId());
            System.out.println("用户ID: " + post.getUserId());
            System.out.println("昵称: " + post.getNickname());
            System.out.println("内容: " + post.getContent());
            System.out.println("图片列表: " + post.getImageList());
            System.out.println("图片列表类型: " + (post.getImageList() != null ? post.getImageList().getClass().getName() : "null"));
            System.out.println("图片列表大小: " + (post.getImageList() != null ? post.getImageList().size() : "null"));
            System.out.println("心情列表: " + post.getMood());
            System.out.println("心情列表类型: " + (post.getMood() != null ? post.getMood().getClass().getName() : "null"));
            System.out.println("心情列表大小: " + (post.getMood() != null ? post.getMood().size() : "null"));
            System.out.println("点赞数: " + post.getLikeCount());
            System.out.println("头像URL: " + post.getAvatarUrl());
            System.out.println("创建时间: " + post.getCreateTime());
            System.out.println("更新时间: " + post.getUpdateTime());
            System.out.println("================================");
        }
    }
}
