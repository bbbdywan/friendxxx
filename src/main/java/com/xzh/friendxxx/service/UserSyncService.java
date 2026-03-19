package com.xzh.friendxxx.service;

//import com.xzh.friendxxx.Repository.EsUserRepository;
//import com.xzh.friendxxx.mapper.UserMapper;
//import com.xzh.friendxxx.model.entity.User;
//import com.xzh.friendxxx.model.entity.esentity.EsUser;
//import org.springframework.beans.BeanUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Collectors;
//
//@Service
//public class UserSyncService {
//
//    private final int PAGE_SIZE = 5000;
//    private final int THREAD_POOL_SIZE = 6;  // 并发线程数，可根据ES性能调节
//
//    @Autowired
//    private UserMapper userMapper;
//
//    @Autowired
//    private EsUserRepository esUserRepository;
//
//    private ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
//
//    public void asyncFullSync() throws InterruptedException {
//        int pageNum = 1;
//        List<User> batch;
//        List<Future<?>> futures = new ArrayList<>();
//
//        do {
//            batch = userMapper.select((pageNum - 1) * PAGE_SIZE, PAGE_SIZE);
//            if (!batch.isEmpty()) {
//                List<EsUser> esUsers = batch.stream()
//                        .map(this::convertToEsUser)
//                        .collect(Collectors.toList());
//
//                // 提交异步写入任务
//                Future<?> future = executor.submit(() -> {
//                    esUserRepository.saveAll(esUsers);
//                });
//                futures.add(future);
//            }
//            pageNum++;
//        } while (batch.size() == PAGE_SIZE);
//
//        // 等待所有任务完成
//        for (Future<?> f : futures) {
//            try {
//                f.get();  // 可设置超时
//            } catch (Exception e) {
//                // 这里可以做日志记录和异常处理、重试等
//                e.printStackTrace();
//            }
//        }
//
//        executor.shutdown();
//        executor.awaitTermination(10, TimeUnit.MINUTES);
//    }
//
//    private EsUser convertToEsUser(User user) {
//        EsUser esUser = new EsUser();
//        BeanUtils.copyProperties(user, esUser);
//        return esUser;
//    }
//}

