package com.xzh.friendxxx.consumer;

import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.service.UserService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class GuestCleanupConsumer {
    private final UserService userService;

    public GuestCleanupConsumer(UserService userService) {
        this.userService = userService;
    }

    @RabbitListener(queues = MQConfig.QUEUE_USER_DELETE)
    public void deleteExpiredGuest(String account) {
        if (account != null && account.startsWith("guest_")) {
            userService.removebyAccount(account);
        }
    }
}
