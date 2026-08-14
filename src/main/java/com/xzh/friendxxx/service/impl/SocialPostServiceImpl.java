package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.AliOssUtil;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.mapper.CommentMapper;
import com.xzh.friendxxx.mapper.UserMapper;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.dto.CommentDTO;
import com.xzh.friendxxx.model.dto.LikesDTO;
import com.xzh.friendxxx.model.dto.NotificationMessage;
import com.xzh.friendxxx.model.dto.UpSociaPost;
import com.xzh.friendxxx.model.entity.Comment;
import com.xzh.friendxxx.model.entity.SocialPost;
import com.xzh.friendxxx.service.SocialPostService;
import com.xzh.friendxxx.mapper.SocialPostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
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
@Slf4j
public class SocialPostServiceImpl extends ServiceImpl<SocialPostMapper, SocialPost>
    implements SocialPostService{

    @Autowired
    private AliOssUtil ossUtil;

    @Autowired
    private SocialPostMapper socialPostMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserMapper userMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(PARAMS_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new BusinessException(PARAMS_ERROR, "图片不能超过5MB");
        }
        String extensionName = org.springframework.util.StringUtils
                .getFilenameExtension(file.getOriginalFilename());
        if (extensionName == null || !java.util.Set.of("jpg", "jpeg", "png", "webp")
                .contains(extensionName.toLowerCase(java.util.Locale.ROOT))) {
            throw new BusinessException(PARAMS_ERROR, "仅支持 jpg、jpeg、png、webp 图片");
        }

        try {
            byte[] fileBytes = file.getBytes();
            String extension = "." + extensionName.toLowerCase(java.util.Locale.ROOT);
            String objectName = "post/" + java.util.UUID.randomUUID() + extension;

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
        List<SocialPost> posts = socialPostMapper.getlist(userId);
        fillCommentCount(posts);
        return posts;
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
    public SocialPost getByCurrentId(Long id) {
        SocialPost post = socialPostMapper.getbyupid(id);
        if (post != null) {
            Long count = commentMapper.countByPostId(post.getId());
            post.setCommentCount(count == null ? 0L : count);
        }
        return post;
    }

    @Override
    public int updateLikescount(LikesDTO likesDTO) {
        Long postId = likesDTO.getPostid();
        Long userId = likesDTO.getUserId();
        Integer type = likesDTO.getLikesId();

        if (socialPostMapper.getAuthorByPostId(postId) == null) {
            throw new BusinessException(PARAMS_ERROR, "动态不存在");
        }

        String key = "post:like:" + postId;

        // 点赞
        if (type == 0) {
            Long added = redisTemplate.opsForSet().add(key, userId.toString());
            if (added == null || added == 0) {
                return 0;
            }
            boolean updated = this.update()
                    .setSql("like_count = COALESCE(like_count, 0) + 1")
                    .eq("id", postId)
                    .update();
            if (updated) {
                publishLikeNotification(postId, userId);
            } else {
                redisTemplate.opsForSet().remove(key, userId.toString());
            }
            return updated ? 1 : 0;
        }

        // 取消点赞
        if (type == 1) {
            Long removed = redisTemplate.opsForSet().remove(key, userId.toString());
            if (removed == null || removed == 0) {
                return 0;
            }
            boolean updated = this.update()
                    .setSql("like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END")
                    .eq("id", postId)
                    .update();
            if (!updated) {
                redisTemplate.opsForSet().add(key, userId.toString());
            }
            return updated ? -1 : 0;
        }

        return 0;
    }

    @Override
    public void addComment(CommentDTO commentDTO) {
        // 1. 写入 MySQL（持久化）
        Comment comment = new Comment();
        comment.setPostId(commentDTO.getPostId());
        comment.setUserId(commentDTO.getUserId());
        comment.setNickname(commentDTO.getNickname());
        comment.setAvatarUrl(commentDTO.getAvatarUrl());
        comment.setContent(commentDTO.getContent());
        comment.setCreateTime(new Date());
        commentMapper.insert(comment);

        // 2. 同步写入 Redis 缓存（加速读取）
        String key = "post:comment:" + commentDTO.getPostId();
        commentDTO.setCreateTime(comment.getCreateTime());
        try {
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(commentDTO));
        } catch (JsonProcessingException e) {
            log.warn("评论写入Redis缓存失败，不影响主流程: {}", e.getMessage());
        }

        // 3. 发送评论通知到 MQ
        publishCommentNotification(commentDTO);
    }

    @Override
    public List<CommentDTO> getComments(Long postId) {
        String key = "post:comment:" + postId;

        // 优先读 Redis 缓存
        List<String> cached = redisTemplate.opsForList().range(key, 0, -1);
        if (cached != null && !cached.isEmpty()) {
            List<CommentDTO> result = new ArrayList<>();
            for (String json : cached) {
                try {
                    result.add(objectMapper.readValue(json, CommentDTO.class));
                } catch (JsonProcessingException e) {
                    log.warn("Redis评论反序列化失败: {}", e.getMessage());
                }
            }
            return result;
        }

        // 缓存未命中，从 MySQL 查询并回填 Redis
        List<Comment> comments = commentMapper.selectByPostId(postId);
        if (comments == null || comments.isEmpty()) return Collections.emptyList();

        List<CommentDTO> result = new ArrayList<>();
        for (Comment c : comments) {
            CommentDTO dto = new CommentDTO();
            dto.setPostId(c.getPostId());
            dto.setUserId(c.getUserId());
            dto.setNickname(c.getNickname());
            dto.setAvatarUrl(c.getAvatarUrl());
            dto.setContent(c.getContent());
            dto.setCreateTime(c.getCreateTime());
            result.add(dto);
            try {
                redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(dto));
            } catch (JsonProcessingException e) {
                log.warn("评论回填Redis失败: {}", e.getMessage());
            }
        }
        return result;
    }


    @Override
    public List<SocialPost> list() {
        List<SocialPost> posts = socialPostMapper.selectAllWithTypeHandler();
        fillCommentCount(posts);
        return posts;
    }

    /** 批量从 MySQL 填充评论数 */
    private void fillCommentCount(List<SocialPost> posts) {
        if (posts == null || posts.isEmpty()) return;
        for (SocialPost post : posts) {
            Long count = commentMapper.countByPostId(post.getId());
            post.setCommentCount(count == null ? 0L : count);
        }
    }

    /** 发布点赞通知到 MQ */
    private void publishLikeNotification(Long postId, Long likerUserId) {
        try {
            SocialPost author = socialPostMapper.getAuthorByPostId(postId);
            if (author == null || author.getUserId() == null) return;
            // 不通知自己点赞自己
            if (author.getUserId().equals(likerUserId)) return;

            // 查点赞者昵称
            User liker = userMapper.selectById(likerUserId);
            String likerNickname = liker != null ? liker.getUsername() : "";

            NotificationMessage msg = NotificationMessage.builder()
                    .type("like")
                    .fromUserId(likerUserId)
                    .fromNickname(likerNickname)
                    .toUserId(author.getUserId())
                    .postId(postId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            rabbitTemplate.convertAndSend(
                    MQConfig.EXCHANGE_NOTIFICATION,
                    MQConfig.ROUTINGKEY_NOTIFICATION,
                    com.alibaba.fastjson2.JSON.toJSONString(msg));

            log.info("点赞通知已发送到MQ: postId={}, likerUserId={}, toUserId={}", postId, likerUserId, author.getUserId());
        } catch (Exception e) {
            log.error("发送点赞通知到MQ失败: postId={}, likerUserId={}", postId, likerUserId, e);
        }
    }

    /** 发布评论通知到 MQ */
    private void publishCommentNotification(CommentDTO commentDTO) {
        try {
            SocialPost author = socialPostMapper.getAuthorByPostId(commentDTO.getPostId());
            if (author == null || author.getUserId() == null) return;
            // 不通知自己评论自己
            if (author.getUserId().equals(commentDTO.getUserId())) return;

            NotificationMessage msg = NotificationMessage.builder()
                    .type("comment")
                    .fromUserId(commentDTO.getUserId())
                    .fromNickname(commentDTO.getNickname())
                    .toUserId(author.getUserId())
                    .postId(commentDTO.getPostId())
                    .content(commentDTO.getContent())
                    .timestamp(System.currentTimeMillis())
                    .build();

            rabbitTemplate.convertAndSend(
                    MQConfig.EXCHANGE_NOTIFICATION,
                    MQConfig.ROUTINGKEY_NOTIFICATION,
                    com.alibaba.fastjson2.JSON.toJSONString(msg));

            log.info("评论通知已发送到MQ: postId={}, commentUserId={}, toUserId={}", commentDTO.getPostId(), commentDTO.getUserId(), author.getUserId());
        } catch (Exception e) {
            log.error("发送评论通知到MQ失败: postId={}, commentUserId={}", commentDTO.getPostId(), commentDTO.getUserId(), e);
        }
    }
}




