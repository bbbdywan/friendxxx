package com.xzh.friendxxx.service;

import com.xzh.friendxxx.model.dto.LikesDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeService {
    private final StringRedisTemplate redisTemplate;

    public Integer handleLike(LikesDTO dto) {

        String key = "post:like:" + dto.getPostid();
        String userId = dto.getUserId().toString();

        if (dto.getLikesId() == 1) {

            // 点赞
            redisTemplate.opsForSet().add(key, userId);

        } else {

            // 取消点赞
            redisTemplate.opsForSet().remove(key, userId);
        }

        // 返回当前点赞数
        Long size = redisTemplate.opsForSet().size(key);

        return size == null ? 0 : size.intValue();
    }
}
