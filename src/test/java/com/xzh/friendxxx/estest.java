//package com.xzh.friendxxx;
//
//
//import com.xzh.friendxxx.Repository.EsUserRepository;
//import com.xzh.friendxxx.service.PostSyncService;
//import com.xzh.friendxxx.service.UserSyncService;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.util.List;
//
//@SpringBootTest(properties = {"websocket.enabled=false"})
//public class estest {
//
//    @Autowired
//    private ProductRepository productRepository;
//
//    @Autowired
//    private EsUserRepository esUserRepository;
//
//
//    @Autowired
//    private PostSyncService postSyncService;
//    @Test
//    void testes(){
//        Product p = new Product();
//        p.setId("1");
//        p.setName("Apple");
//        p.setPrice(10.0);
//        productRepository.save(p);
//        List<Product> list = productRepository.findByName("user_index");
//
//        // 打印查询结果
//        list.forEach(product -> System.out.println(product.getId() + " " + product.getName() + " " + product.getPrice()));
//
//    }
//
//    @Autowired
//    private UserSyncService userSyncService;
//
//    @Test
//    public void testAsyncFullSync() throws InterruptedException {
//        long start = System.currentTimeMillis();
//        userSyncService.asyncFullSync();
//        long end = System.currentTimeMillis();
//        System.out.println("全量同步完成，耗时：" + (end - start) + " ms");
//    }
//
//    @Test
//    public void testAsyncFullSync1() throws InterruptedException {
//        long start = System.currentTimeMillis();
//        postSyncService.syncAll();
//        long end = System.currentTimeMillis();
//        System.out.println("全量同步完成，耗时：" + (end - start) + " ms");
//    }
//}
