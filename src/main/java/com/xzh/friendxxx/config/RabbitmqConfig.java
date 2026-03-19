package com.xzh.friendxxx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.amqp.core.Queue;


@Configuration
public class RabbitmqConfig {
    //定义消息队列的名字
    public static final String NAME_HELLO = "queue_hello";

    @Bean
    public Queue queue() {
        //创建一个队列队列，并指定队列的名字
        return new Queue(NAME_HELLO,true);
    }
}
