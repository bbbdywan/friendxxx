package com.xzh.friendxxx;

import com.xzh.friendxxx.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.xzh.friendxxx.service.UserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;

@SpringBootTest
class DemoRunApplicationTests {

    @Autowired
    UserService userService;
    @Autowired
    RedisTemplate redisTemplate;
    @Test
    void contextLoads() {
        ValueOperations ops = redisTemplate.opsForValue();
        ops.set("name", "xzh");
    }


}
