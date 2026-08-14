package com.xzh.friendxxx.ai.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式多消息解析器（无状态工厂）。
 *
 * <p>本类不持有任何请求级可变状态，只负责创建 {@link Session}。每次 AI 聊天或
 * 预览请求都必须创建独立的 Session，禁止把状态存在单例中，避免不同用户/会话串流。
 */
@Component
public class IncrementalMessageParser {

    public static final String OPEN_TAG = "<message>";
    public static final String CLOSE_TAG = "</message>";
    public static final int MAX_MESSAGES = 4;

    /**
     * 创建一个请求级解析会话。
     */
    public Session createSession() {
        return new Session();
    }

    /**
     * 请求级多消息解析状态机。
     *
     * <p>把 DeepSeek 文本流中的 <code>&lt;message&gt;...&lt;/message&gt;</code>
     * 边界转换为明确的解析事件（Start/Delta/End）。为保证 start/end 严格配对且
     * 空消息/超限消息被完整丢弃，采用"每条消息闭合时一次性产出"策略：
     * 消息正文在缓冲中累积，识别到完整闭合标签后一次性发出
     * START + DELTA(全文) + END；标签绝不进入正文。
     *
     * <p>无任何标签迹象的纯文本安全降级为一条流式消息；整轮累计硬上限 4 条（跨 feed）。
     */
    public static final class Session {

        public enum EventKind { START, DELTA, END }

        /** 解析事件。index 为整轮内消息序号（从 1 起）。 */
        public record Event(EventKind kind, int index, String text, boolean completed) {
            public static Event start(int index) { return new Event(EventKind.START, index, null, false); }
            public static Event delta(int index, String text) { return new Event(EventKind.DELTA, index, text, false); }
            public static Event end(int index, boolean completed) { return new Event(EventKind.END, index, null, completed); }
        }

        private final StringBuilder buffer = new StringBuilder();
        private boolean inMessage = false;
        private int currentIndex = 0;
        private int closedCount = 0;
        private boolean sawAnyTag = false;
        private boolean plainMode = false;
        private boolean finished = false;

        public int closedCount() {
            return closedCount;
        }

        public boolean sawAnyTag() {
            return sawAnyTag;
        }

        /**
         * 喂入一段增量文本，返回本次产生的事件序列。
         */
        public List<Event> feed(String delta) {
            if (finished) {
                throw new IllegalStateException("解析会话已结束，禁止再喂入");
            }
            if (delta == null || delta.isEmpty()) {
                return List.of();
            }
            buffer.append(delta);
            List<Event> events = new ArrayList<>();
            consume(events);
            return events;
        }

        /**
         * 流结束收尾：未闭合消息按一个完整消息发出；无任何输出则无事件。
         */
        public List<Event> finish() {
            if (finished) {
                return List.of();
            }
            finished = true;
            List<Event> events = new ArrayList<>();
            if (plainMode) {
                String text = buffer.toString();
                if (!text.isBlank()) {
                    events.add(Event.start(1));
                    events.add(Event.delta(1, text));
                    events.add(Event.end(1, true));
                }
                return events;
            }
            if (inMessage && currentIndex > 0) {
                // 残留未闭合消息：把累积正文作为一个完整气泡
                String content = buffer.toString();
                if (!content.isBlank()) {
                    events.add(Event.start(currentIndex));
                    events.add(Event.delta(currentIndex, content));
                    events.add(Event.end(currentIndex, true));
                }
            }
            return events;
        }

        // ---------- 核心状态机 ----------

        private void consume(List<Event> events) {
            while (buffer.length() > 0) {
                if (plainMode) {
                    return; // 纯文本：整段等 finish 统一输出，避免中途切换
                }
                if (!inMessage) {
                    int open = buffer.indexOf(OPEN_TAG);
                    if (open >= 0) {
                        // 丢弃开标签前的游离文本（协议模式下游离文本不展示）
                        sawAnyTag = true;
                        buffer.delete(0, open + OPEN_TAG.length());
                        if (closedCount < MAX_MESSAGES) {
                            currentIndex = closedCount + 1;
                            closedCount++;
                            inMessage = true;
                        } else {
                            // 超限：进入"跳过"模式直到闭合
                            inMessage = true;
                            currentIndex = -1;
                        }
                        continue;
                    }
                    // 无完整开标签
                    if (buffer.indexOf("<") >= 0) {
                        if (isPotentialTagPrefix(buffer)) {
                            return; // 可能是开标签前缀，等待补齐
                        }
                        if (!sawAnyTag) {
                            // 出现 < 但构不成标签且从未见过协议标签 → 纯文本降级
                            enterPlain();
                            return;
                        }
                        // 已见过协议标签，< 后不是合法标签：丢弃游离文本
                        int keep = potentialTagPrefixLength(buffer);
                        int cut = buffer.length() - keep;
                        if (cut > 0) {
                            buffer.delete(0, cut);
                        } else if (keep == 0) {
                            buffer.setLength(0);
                        }
                        return;
                    }
                    // 完全无 <
                    if (!sawAnyTag) {
                        enterPlain();
                        return;
                    }
                    // 已见过协议标签但出现无标签文本：丢弃
                    buffer.setLength(0);
                    return;
                }
                // inMessage：查找闭合标签
                int close = buffer.indexOf(CLOSE_TAG);
                if (close >= 0) {
                    String content = buffer.substring(0, close);
                    buffer.delete(0, close + CLOSE_TAG.length());
                    if (currentIndex > 0 && !content.isBlank()) {
                        events.add(Event.start(currentIndex));
                        events.add(Event.delta(currentIndex, content));
                        events.add(Event.end(currentIndex, true));
                    }
                    inMessage = false;
                    currentIndex = 0;
                    continue;
                }
                // 无完整闭合标签：检查尾部是否为闭合标签前缀
                int keep = partialClosePrefixLength(buffer);
                if (keep > 0) {
                    // 尾部可能是闭合标签前缀，保留等待补齐
                    return;
                }
                // 全为正文（无标签前缀迹象），累积等待闭合
                return;
            }
        }

        private void enterPlain() {
            plainMode = true;
        }

        private static boolean isPotentialTagPrefix(StringBuilder b) {
            return potentialTagPrefixLength(b) > 0 && potentialTagPrefixLength(b) == b.length();
        }

        private static int potentialTagPrefixLength(StringBuilder b) {
            String s = b.toString();
            for (String p : PARTIAL_TAGS) {
                if (s.endsWith(p)) {
                    return p.length();
                }
            }
            return 0;
        }

        private static int partialClosePrefixLength(StringBuilder b) {
            String s = b.toString();
            for (String p : PARTIAL_CLOSE) {
                if (s.endsWith(p)) {
                    return p.length();
                }
            }
            return 0;
        }

        private static final String[] PARTIAL_CLOSE = {"</message", "</messag", "</messa", "</mess", "</mes", "</me", "</m"};
        private static final String[] PARTIAL_TAGS = {
                "</message", "</messag", "</messa", "</mess", "</mes", "</me", "</m",
                "<message", "<messag", "<messa", "<mess", "<mes", "<me", "<m", "<"
        };
    }
}
