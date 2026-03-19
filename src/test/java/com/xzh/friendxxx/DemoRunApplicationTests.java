package com.xzh.friendxxx;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.util.ListUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.mapper.UserMapper;
import com.xzh.friendxxx.model.entity.ChatMessage;
import com.xzh.friendxxx.model.entity.GroupChat;
import com.xzh.friendxxx.model.entity.SocialPost;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.vo.SenderVO;
import com.xzh.friendxxx.service.ChatMessageService;
import com.xzh.friendxxx.service.GroupChatService;
import com.xzh.friendxxx.service.SocialPostService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.xzh.friendxxx.service.UserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SpringBootTest(properties = {"websocket.enabled=false"})
@Slf4j
class DemoRunApplicationTests implements   Runnable {

    @Autowired
    UserService userService;
    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    SocialPostService socialPostService;
    @Autowired
    GroupChatService groupChatService;

    @Autowired
    ChatMessageService chatMessageService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    private UserMapper userMapper;
    @Test
    void contextLoads() {
        System.out.println("发送消息:我是一个延迟消息，开始时间："+System.currentTimeMillis());
        rabbitTemplate.convertAndSend(
                MQConfig.EXCHNAGE_DELAY,
                MQConfig.ROUTINGKEY_QUEUE_ORDER,
                "我是一个延迟消息",
                message -> {
                    // 设置过期时间（比如 5 分钟 = 300000 毫秒）
                    message.getMessageProperties().setExpiration("10000");
                    return message;
                }
        );

    }
    @Test
    void testEASYEXCEL() {
        String fileName;
        fileName = "C:\\Users\\bb\\Desktop\\pythonProject1\\users_1M.xlsx";
        // 这里 需要指定读用哪个class去读，然后读取第一个sheet 文件流会自动关闭
        EasyExcel.read(fileName, User.class, new ReadListener<User>() {
            /**
             * 单次缓存的数据量
             */
            public static final int BATCH_COUNT = 1000;
            /**
             *临时存储
             */
            private List<User> cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

            @Override
            public void invoke(User data, AnalysisContext context) {
                cachedDataList.add(data);

                    if (cachedDataList.size() >= BATCH_COUNT) {
//                        saveData();
//                        // 存储完成清理 list
                        userService.saveBatch(cachedDataList);
//                        cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

//                        ThreadPoolManager.THREAD_POOL.execute(() -> {
//
//                    });
                }

            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                saveData();
            }

            /**
             * 加上存储数据库
             */
            private void saveData() {
                log.info("{}条数据，开始存储数据库！", cachedDataList.size());
                log.info("存储数据库成功！");
            }
        }).sheet().doRead();
    }

    @Test
    void testEASYEXCELnew() {
        String fileName = "C:\\Users\\bb\\Desktop\\pythonProject1\\users_50M.xlsx";

        EasyExcel.read(fileName, User.class, new ReadListener<User>() {

            public static final int BATCH_COUNT = 5000;
            private List<User> cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

            @Override
            public void invoke(User data, AnalysisContext context) {
                cachedDataList.add(data);

                if (cachedDataList.size() >= BATCH_COUNT) {
                    // 拷贝当前数据，避免线程共享问题
                    List<User> batchList = new ArrayList<>(cachedDataList);

                    // 异步处理
                    ThreadPoolManager.THREAD_POOL.execute(() -> {
                        userService.saveBatch(batchList);
                    });

                    // 清空缓存
                    cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 最后一批不足BATCH_COUNT的数据
                if (!cachedDataList.isEmpty()) {
                    List<User> batchList = new ArrayList<>(cachedDataList);
                    ThreadPoolManager.THREAD_POOL.execute(() -> {
                        saveData(batchList);
                    });
                }
            }

            private void saveData(List<User> batchList) {
                log.info("线程：{}，读取数据 {} 条", Thread.currentThread().getName(), batchList.size());

                // 模拟耗时任务（可选）
                // try { Thread.sleep(100); } catch (InterruptedException e) { }

                for (User user : batchList) {
                    log.debug("{}", user);
                }
            }

        }).sheet().doRead();
    }
    // 启动线程
    @Test
    void testThread() {
        Thread t = new Thread(new DemoRunApplicationTests());
        t.start();
        for(int i = 0; i < 100; i++){
            System.out.println("主线程--->" + i);
        }
    }

    @Override
    public void run() {
        for(int i = 0; i < 100; i++){
            System.out.println("分支线程--->" + i);
        }
    }

    @Test
    void pagerhelpertest() {
        PageInfo<User> userByTag = userService.findUserByTag(2, 10);
        System.out.println(userByTag);
    }
}
