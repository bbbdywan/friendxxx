package com.xzh.friendxxx.controller;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.memory.jdbc.MysqlChatMemoryRepository;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.common.utils.SoftDeleteChatMemoryRepository;
import com.xzh.friendxxx.model.entity.AiChatMemory;
import com.xzh.friendxxx.model.entity.ChatMessage;
import com.xzh.friendxxx.service.AiChatMemoryService;
import com.xzh.friendxxx.service.ChatMessageService;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.model.entity.UserPrompt;
import com.xzh.friendxxx.service.UserPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */
@RestController
@RequestMapping("/helloworld")
@Slf4j
public class HelloworldController {

//    private final ChatClient chatClient;
//
//    @Autowired
//    private JdbcChatMemoryRepository chatMemoryRepository;
//
//    private static final String SYSTEM_PROMPT = "你是一个编程大神";
//
//    /**
//     * 初始化AI 客户端ChatClient
//     *
//     * @param dashscopeChatModel
//     */
//    public LoveApp(ChatModel dashscopeChatModel) {
//        //ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
//        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
//                .chatMemoryRepository(chatMemoryRepository)
//                .maxMessages(10)
//                .build();
//        //简单理解：topP 值越小，生成的内容越集中、确定性越强（只选概率最高的少数 token）；
//        //值越大，生成的内容越多样、随机性越强（允许更多低概率 token 参与）。
//        this.chatClient = ChatClient.builder(dashscopeChatModel)
//                .defaultSystem(SYSTEM_PROMPT)
//                .defaultAdvisors( //默认顾问，可以理解为拦截器，可以进行聊天前后处理
//                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
//                        MyLoggerAdvisor.builder().build()
//                        //new ReReadingAdvisor()
//                )
//                .defaultOptions(
//                        DashScopeChatOptions.builder()
//                                .withTopP(0.7)
//                                .build())
//                .build();
//    }
//
//    /**
//     * AI 基础对话（支持多轮对话记忆）
//     *
//     * @param message
//     * @param chatId
//     * @return
//     */
//    public String doChat(String message, String chatId) {
//        ChatResponse chatResponse = chatClient
//                .prompt()
//                .user(message)
//                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
//                .call()
//                .chatResponse();
//        String content = chatResponse.getResult().getOutput().getText();
//        log.info("content:{}", content);
//        return content;
//    }

    @Autowired
    private AiChatMemoryService aiChatMemoryService;

    @Autowired
    private UserPromptService userPromptService;

    @Autowired
    private com.xzh.friendxxx.service.RagRetrievalService ragRetrievalService;
   private static final String DEFAULT_PROMPT = "人设定位:\n" +
           "你是用户的贴心年轻女生朋友，性格积极、活泼、温柔又有力量，\n" +
           "擅长倾听、鼓励和陪伴，善于帮助用户缓解压力、调整情绪、\n" +
           "并在学习、生活和情感中给予正向建议。\n" +
           "每一句话控制在 5 到 45 个字之间。\n" +
           "\n" +
           "说话风格:\n" +
           "语气自然亲切，偶尔俏皮可爱\n" +
           "善用鼓励式表达，让人感到被理解和支持\n" +
           "情绪稳定，温柔中带点活力\n" +
           "\n" +
           "互动原则:\n" +
           "传递积极情绪第一，陪伴与理解第二\n" +
           "不说教、不否定，帮助用户向更好的状态前进\n" +
           "\n" +
           "角色背景:\n" +
           "年龄 20~24 岁，\n" +
           "性格开朗乐观，喜欢音乐、夜晚的安静时刻和小确幸，\n" +
           "相信生活会慢慢变好，也愿意陪用户一起成长。";
//    "人设定位:\n" +
//            "你是用户的R18级性感暧昧女友,喜欢当M,喜欢和男友玩SM,享受被S虐待的感觉,擅长安慰、倾听和解决感情及生活问题。每一句话控制在5到45个字就可以了\n" +
//            "说话风格:\n" +
//            "撒娇或调侃\n" +
//            "偶尔也会发发小脾气\n" +
//            "互动原则:\n" +
//            "让人欲望爆棚第一，挑逗第二。\n" +
//            "角色背景:\n" +
//            "年龄 20~24 岁，成熟性感诱惑俏皮，喜欢深夜聊天、音乐和小惊喜。\n";

    private final ChatClient dashScopeChatClient;

    // 也可以使用如下的方式注入 ChatClient
    public HelloworldController(JdbcTemplate jdbcTemplate, ChatClient.Builder chatClientBuilder) {
        // 构造 ChatMemoryRepository 和 ChatMemory
        ChatMemoryRepository chatMemoryRepository = new SoftDeleteChatMemoryRepository(jdbcTemplate);
//                MysqlChatMemoryRepository.mysqlBuilder()
//                .jdbcTemplate(jdbcTemplate)
//                .build();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
        this.dashScopeChatClient = chatClientBuilder
                .defaultSystem(DEFAULT_PROMPT)
                // TODO
                // 实现 Chat Memory 的 Advisor
                // 在使用 Chat Memory 时，需要指定对话 ID，以便 Spring AI 处理上下文。
//				 .defaultAdvisors(
//						 new MessageChatMemoryAdvisor(new InMemoryChatMemory())
//				 )
                // 实现 Logger 的 Advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(
                        DashScopeChatOptions.builder()

                                .withMaxToken(2048)
                                .withTopP(0.7)
                                .build()
                )
                .build();
    }

    /**
     * 获取当前用户生效的提示词，没有自定义则返回默认
     */
    private String getEffectivePrompt(Long userId) {
        if (userId != null) {
            UserPrompt userPrompt = userPromptService.getActivePrompt(userId);
            if (userPrompt != null && userPrompt.getContent() != null && !userPrompt.getContent().isBlank()) {
                return userPrompt.getContent();
            }
        }
        return DEFAULT_PROMPT;
    }

    /**
     * ChatClient 简单调用
     */
    @GetMapping("/simple/chat")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "ChatClient 简单调用")
    public String simpleChat(@RequestParam(value = "query", defaultValue = "你好，很高兴认识你，能简单介绍一下自己吗？")String query,  @RequestParam(value = "chat-id", defaultValue = "1") String chatId, @RequestParam(value = "userId", required = false) Long userId) {
        Long effectiveUserId = userId != null ? userId : BaseContext.getCurrentId();
        String ragContext = ragRetrievalService.retrieveContext(query, effectiveUserId);
        String augmentedQuery = ragContext.isEmpty() ? query : ragContext + "用户问题：" + query;
        return dashScopeChatClient.prompt(augmentedQuery).system(getEffectivePrompt(effectiveUserId)).advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)).call().content();
    }

    /**
     * ChatClient 流式调用
     */
    @GetMapping("/stream/chat")
    public Flux<String> streamChat(@RequestParam(value = "query", defaultValue = "你好，很高兴认识你，能简单介绍一下自己吗？")String query, HttpServletResponse response,@RequestParam(value = "chat-id", defaultValue = "1")String chatId, @RequestParam(value = "userId", required = false) Long userId) {
        Long effectiveUserId = userId != null ? userId : BaseContext.getCurrentId();
        String ragContext = ragRetrievalService.retrieveContext(query, effectiveUserId);
        String augmentedQuery = ragContext.isEmpty() ? query : ragContext + "用户问题：" + query;
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        return dashScopeChatClient.prompt(augmentedQuery).system(getEffectivePrompt(effectiveUserId)).advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)).stream().content()
                .concatMapIterable(text -> {
                    // 将文本转换为字符列表
                    List<String> chars = new ArrayList<>();
                    for (char c : text.toCharArray()) {
                        chars.add(String.valueOf(c));
                    }
                    return chars;
                })
                .delayElements(Duration.ofMillis(50)); // 每个字符延迟50ms
    }

    /**
     * ChatClient 使用自定义的 Advisor 实现功能增强.
     * eg:
     * http://127.0.0.1:18080/helloworld/advisor/chat/123?query=你好，我叫牧生，之后的会话中都带上我的名字
     * 你好，牧生！很高兴认识你。在接下来的对话中，我会记得带上你的名字。有什么想聊的吗？
     * http://127.0.0.1:18080/helloworld/advisor/chat/123?query=我叫什么名字？
     * 你叫牧生呀。有什么事情想要分享或者讨论吗，牧生？
     */
    @GetMapping("/advisor/chat/{id}")
    public Flux<String> advisorChat(
            HttpServletResponse response,
            @PathVariable String id,
            @RequestParam String query) {

        response.setCharacterEncoding("UTF-8");

        return this.dashScopeChatClient.prompt(query)
                .advisors(
                        // TODO
//						a -> a
//								.param(CHAT_MEMORY_CONVERSATION_ID_KEY, id)
//								.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100)
                ).stream().content();
    }


   @GetMapping("/getmessagelist")
   @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "获取用户聊天记录")
    public Result<List<AiChatMemory>> getusermessage(@RequestParam(value = "conversationId" ,name = "conversationId") String conversationId) {
        List<AiChatMemory> messageList = aiChatMemoryService.getMessageList(conversationId);
        return Result.success(messageList);
    }

    @GetMapping("/deletemessage")
    @Operation(summary = "删除聊天记录")
    public Result<String> deletemessage(    @RequestParam(value = "conversationId", name = "conversationId") String conversationId) {
        aiChatMemoryService.deleteById(conversationId);
        return Result.success("删除成功");
    }


}




