package com.xzh.friendxxx.config;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

//rabbitMQ的配置
@Configuration
public class MQConfig {
    //交换机
    public static final String EXCHNAGE_DELAY = "EXCHNAGE_DELAY";
    //订单队列，该队列中的消息设置过期时间
    public static final String QUEUE_ORDER = "QUEUE_ORDER";
    //该队列用来接收死信交换机转发过来的消息
    public static final String QUEUE_DELAY = "QUEUE_DELAY";
    //队列的路由键，该路由键用来接收订单消息传出到订单队列
    public static final String ROUTINGKEY_QUEUE_ORDER = "ROUTINGKEY_QUEUE_ORDER";
    //该路由键用来接收死信交换机转发过来的消息
    public static final String ROUTINGKEY_QUEUE_DELAY = "ROUTINGKEY_QUEUE_DELAY";
    // 用户删除交换机和队列相关常量
    public static final String EXCHANGE_USER_TTL = "EXCHANGE_USER_TTL";
    public static final String QUEUE_USER_REGISTER = "QUEUE_USER_REGISTER";
    public static final String QUEUE_USER_DELETE = "QUEUE_USER_DELETE";
    public static final String ROUTINGKEY_USER_REGISTER = "ROUTINGKEY_USER_REGISTER";
    public static final String ROUTINGKEY_USER_DELETE = "ROUTINGKEY_USER_DELETE";

    //定义交换机
    @Bean
    public Exchange exchangeDelay(){
        return ExchangeBuilder.topicExchange(EXCHNAGE_DELAY).durable(true).build();
    }
    @Bean
    public Exchange exchangeUserTTL() {
        return ExchangeBuilder.directExchange(EXCHANGE_USER_TTL).durable(true).build();
    }
    //该队列中的消息需要设置ttl
    @Bean
    public Queue queueOrder(){
        Map<String,Object> map = new HashMap<>();
        map.put("x-dead-letter-exchange", EXCHNAGE_DELAY);    //过期的消息给哪个交换机的名字
        map.put("x-dead-letter-routing-key", ROUTINGKEY_QUEUE_DELAY);   //死信交换机把消息个哪个个routingkey
        //map.put("x-message-ttl", 10000);    //队列过期时间10s
        return new Queue(QUEUE_ORDER,true,false,false,map);
    }
    @Bean
    public Queue queueUserRegister() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EXCHANGE_USER_TTL);
        args.put("x-dead-letter-routing-key", ROUTINGKEY_USER_DELETE);
        return new Queue(QUEUE_USER_REGISTER, true, false, false, args);
    }
    //该队列接收死信交换机转发过来的消息
    @Bean
    public Queue queueDelay(){
        return new Queue(QUEUE_DELAY,true);
    }
    @Bean
    public Queue queueUserDelete() {
        return new Queue(QUEUE_USER_DELETE, true);
    }
    @Bean
    public Binding queueOrderBinding(){
        return BindingBuilder.bind(queueOrder()).to(exchangeDelay()).with(ROUTINGKEY_QUEUE_ORDER).noargs();
    }
    @Bean
    public Binding queueDelayBinding(){
        return BindingBuilder.bind(queueDelay()).to(exchangeDelay()).with(ROUTINGKEY_QUEUE_DELAY).noargs();
    }
    @Bean
    public Binding bindingUserRegister() {
        return BindingBuilder
                .bind(queueUserRegister())
                .to(exchangeUserTTL())
                .with(ROUTINGKEY_USER_REGISTER)
                .noargs();
    }
    @Bean
    public MessageConverter messageConverter(){
        return new Jackson2JsonMessageConverter();
    }
    @Bean
    public Binding bindingUserDelete() {
        return BindingBuilder
                .bind(queueUserDelete())
                .to(exchangeUserTTL())
                .with(ROUTINGKEY_USER_DELETE)
                .noargs();
    }
}
