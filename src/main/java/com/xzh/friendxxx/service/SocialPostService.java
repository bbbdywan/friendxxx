package com.xzh.friendxxx.service;

import com.xzh.friendxxx.model.entity.SocialPost;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
* @author bb
* @description 针对表【social_post(用户动态表)】的数据库操作Service
* @createDate 2025-07-25 22:31:35
*/
public interface SocialPostService extends IService<SocialPost> {

    String uploadAvatar(MultipartFile file);

    int  remove(String deleteTtl);

    List<SocialPost> getlist(Long userId);
}
