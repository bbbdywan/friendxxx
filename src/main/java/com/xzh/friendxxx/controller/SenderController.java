package com.xzh.friendxxx.controller;

import com.xzh.friendxxx.config.RabbitmqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SenderController {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostMapping("/sender/hello/{message}")
    public String senderHello(@PathVariable("message") String message) {
        /**
         * 参数说明
         * exchnage: 交换机，默认交换机指定为“”即可
         * routingKey ：发送消息的路由键，该模式下使用队列名即可
         * message:消息的内容
         */
        rabbitTemplate.convertAndSend("", RabbitmqConfig.NAME_HELLO,message);
        return "success";
    }
}

