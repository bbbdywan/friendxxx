package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.AliOssUtil;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.model.dto.CommentDTO;
import com.xzh.friendxxx.model.dto.LikesDTO;
import com.xzh.friendxxx.model.dto.UpSociaPost;
import com.xzh.friendxxx.model.entity.SocialPost;
import com.xzh.friendxxx.service.SocialPostService;
import com.xzh.friendxxx.mapper.SocialPostMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.xzh.friendxxx.exception.ErrorCode.PARAMS_ERROR;

/**
* @author bb
* @description 针对表【social_post(用户动态表)】的数据库操作Service实现
* @createDate 2025-07-25 22:31:35
*/
@Service
public class SocialPostServiceImpl extends ServiceImpl<SocialPostMapper, SocialPost>
    implements SocialPostService{

    @Autowired
    private AliOssUtil ossUtil;

    @Autowired
    private SocialPostMapper socialPostMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(PARAMS_ERROR, "上传文件不能为空");
        }

        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = "avatar/" + System.currentTimeMillis() + extension;

            return ossUtil.upload(fileBytes, objectName);
        } catch (IOException e) {
            throw new BusinessException(PARAMS_ERROR, "文件读取失败: " + e.getMessage());
        }
    }

    @Override
    public int remove(String deleteTtl) {
        int count = socialPostMapper.removebyttl(deleteTtl);
        return count;
    }

    @Override
    public List<SocialPost> getlist(Long userId) {
        List<SocialPost> list= socialPostMapper.getlist(userId);
        return list;
    }

    @Override
    public int updatecurrentpost(UpSociaPost upSociaPost) {
        String content = upSociaPost.getContent();
        Long id = upSociaPost.getId();
        int update = socialPostMapper.updatepost(content,id);

        return update;
    }

    @Override
    public void updateAvatarUrl(String avatarUrl) {
        Long currentId = BaseContext.getCurrentId();
        socialPostMapper.updateAvatarUrl(currentId,avatarUrl);
    }

    @Override
    public SocialPost getByCurrentId(Integer id) {
        return socialPostMapper.getbyupid(id);
    }

    @Override
    public int updateLikescount(LikesDTO likesDTO) {
        Long postId = likesDTO.getPostid();
        Long userId = likesDTO.getUserId();
        Integer type = likesDTO.getLikesId();

        String key = "post:like:" + postId;

        // 判断用户是否已经点赞
        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
        boolean liked = Boolean.TRUE.equals(isMember);

        // 点赞
        if (type == 0) {

            // 如果已经点赞，直接返回
            if (liked) {
                return 0;
            }

            // 1. 加入Redis
            redisTemplate.opsForSet().add(key, userId.toString());

            // 2. 数据库点赞数 +1
            return this.update()
                    .setSql("like_count = like_count + 1")
                    .eq("id", postId)
                    .update() ? 1 : 0;
        }

        // 取消点赞
        if (type == 1) {

            // 如果没点过赞，不处理
            if (!liked) {
                return 0;
            }

            // 1. 从Redis移除
            redisTemplate.opsForSet().remove(key, userId.toString());

            // 2. 数据库点赞数 -1（防止负数）
            return this.update()
                    .setSql("like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END")
                    .eq("id", postId)
                    .update() ? -1 : 0;
        }

        return 0;
    }

    @Override
    public void addComment(CommentDTO commentDTO) {
        String key = "post:comment:" + commentDTO.getPostId();
        commentDTO.setCreateTime(new Date());
        try {
            // 将评论对象序列化成JSON字符串
            String value = objectMapper.writeValueAsString(commentDTO);
            // 新评论追加到列表末尾
            redisTemplate.opsForList().rightPush(key, value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化评论失败", e);
        }
    }

    @Override
    public List<CommentDTO> getComments(Long postId) {
        String key = "post:comment:" + postId;
        List<String> list = redisTemplate.opsForList().range(key, 0, -1); // 0 到 -1 表示整个 List
        if (list == null || list.isEmpty()) return Collections.emptyList();

        List<CommentDTO> result = new ArrayList<>();
        for (String json : list) {
            try {
                result.add(objectMapper.readValue(json, CommentDTO.class));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        return result;
    }


    @Override
    public List<SocialPost> list() {
        // 使用自定义查询方法，确保类型处理器正确工作
        return socialPostMapper.selectAllWithTypeHandler();
    }
}




