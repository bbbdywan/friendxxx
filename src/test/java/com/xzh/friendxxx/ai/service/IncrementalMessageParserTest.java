package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.service.IncrementalMessageParser.Session;
import com.xzh.friendxxx.ai.service.IncrementalMessageParser.Session.Event;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多消息解析器 Session 测试：
 * 事件序列、start/end 严格配对、标签零泄漏、跨 feed 上限、并发隔离、降级单条。
 */
class IncrementalMessageParserTest {

    private final IncrementalMessageParser factory = new IncrementalMessageParser();

    private List<Event> feedAll(Session s, String... chunks) {
        List<Event> all = new ArrayList<>();
        for (String c : chunks) {
            all.addAll(s.feed(c));
        }
        all.addAll(s.finish());
        return all;
    }

    private String concatDelta(List<Event> events) {
        StringBuilder sb = new StringBuilder();
        for (Event e : events) {
            if (e.kind() == Session.EventKind.DELTA) {
                sb.append(e.text());
            }
        }
        return sb.toString();
    }

    @Test
    void 单条消息完整闭合startEnd配对() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s, "<message>刚刚在发呆</message>");
        List<Event> starts = events.stream().filter(e -> e.kind() == Session.EventKind.START).toList();
        List<Event> ends = events.stream().filter(e -> e.kind() == Session.EventKind.END).toList();
        assertEquals(1, starts.size());
        assertEquals(1, ends.size());
        assertEquals("刚刚在发呆", concatDelta(events));
        assertTrue(noTagLeak(events));
    }

    @Test
    void 多条消息一个chunk() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s,
                "<message>刚刚在发呆</message><message>然后就被你抓到了～</message><message>你呢</message>");
        long starts = events.stream().filter(e -> e.kind() == Session.EventKind.START).count();
        long ends = events.stream().filter(e -> e.kind() == Session.EventKind.END).count();
        assertEquals(3, starts);
        assertEquals(3, ends);
        assertEquals("刚刚在发呆然后就被你抓到了～你呢", concatDelta(events));
    }

    @Test
    void 开标签逐字符切分() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s,
                "<", "m", "e", "s", "s", "a", "g", "e", ">", "刚", "刚", "在", "发", "呆", "</message>");
        long starts = events.stream().filter(e -> e.kind() == Session.EventKind.START).count();
        long ends = events.stream().filter(e -> e.kind() == Session.EventKind.END).count();
        assertEquals(1, starts);
        assertEquals(1, ends);
        assertEquals("刚刚在发呆", concatDelta(events));
        assertTrue(noTagLeak(events));
    }

    @Test
    void 闭合标签逐字符切分() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s,
                "<message>", "发", "呆", "<", "/", "m", "e", "s", "s", "a", "g", "e", ">");
        long starts = events.stream().filter(e -> e.kind() == Session.EventKind.START).count();
        long ends = events.stream().filter(e -> e.kind() == Session.EventKind.END).count();
        assertEquals(1, starts);
        assertEquals(1, ends);
        assertEquals("发呆", concatDelta(events));
        assertTrue(noTagLeak(events));
    }

    @Test
    void 空消息自动丢弃() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s, "<message>  </message><message>有内容</message>");
        assertEquals(1, events.stream().filter(e -> e.kind() == Session.EventKind.START).count());
        assertEquals("有内容", concatDelta(events));
    }

    @Test
    void 标签外文本被丢弃() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s, "这是解释说明，不要展示 <message>你好</message> 后面的也不要");
        assertEquals("你好", concatDelta(events));
        assertTrue(noTagLeak(events));
    }

    @Test
    void 超过4条跨feed也生效() {
        Session s = factory.createSession();
        List<Event> all = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            all.addAll(s.feed("<message>消息" + i + "</message>"));
        }
        all.addAll(s.finish());
        long starts = all.stream().filter(e -> e.kind() == Session.EventKind.START).count();
        long ends = all.stream().filter(e -> e.kind() == Session.EventKind.END).count();
        assertEquals(4, starts);
        assertEquals(4, ends);
        assertEquals("消息1消息2消息3消息4", concatDelta(all));
    }

    @Test
    void 全程无标签降级为单条() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s, "普通文本，没有标记，一句话说完");
        long starts = events.stream().filter(e -> e.kind() == Session.EventKind.START).count();
        long ends = events.stream().filter(e -> e.kind() == Session.EventKind.END).count();
        assertEquals(1, starts);
        assertEquals(1, ends);
        assertEquals("普通文本，没有标记，一句话说完", concatDelta(events));
    }

    @Test
    void 只缺开标签内容不丢() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s, "<message>前半句");
        long starts = events.stream().filter(e -> e.kind() == Session.EventKind.START).count();
        long ends = events.stream().filter(e -> e.kind() == Session.EventKind.END).count();
        assertEquals(1, starts);
        assertEquals(1, ends);
        assertEquals("前半句", concatDelta(events));
    }

    @Test
    void 内容含中文换行() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s, "<message>第一行\n第二行</message>");
        assertEquals("第一行\n第二行", concatDelta(events));
    }

    @Test
    void 两个会话并发交错互不污染() {
        Session a = factory.createSession();
        Session b = factory.createSession();
        List<Event> ea = new ArrayList<>();
        List<Event> eb = new ArrayList<>();
        ea.addAll(a.feed("<message>A1</m"));
        eb.addAll(b.feed("<message>B1</message>"));
        ea.addAll(a.feed("essage>"));
        eb.addAll(b.feed("<message>B2</message>"));
        ea.addAll(a.feed("<message>A2</message>"));
        ea.addAll(a.finish());
        eb.addAll(b.finish());
        assertEquals("A1A2", concatDelta(ea));
        assertEquals("B1B2", concatDelta(eb));
    }

    @Test
    void 标签绝不泄漏到正文() {
        Session s = factory.createSession();
        List<Event> events = feedAll(s,
                "<message>你好</message><message>在干嘛</message>");
        for (Event e : events) {
            if (e.kind() == Session.EventKind.DELTA && e.text() != null) {
                assertFalse(e.text().contains("<message"), "正文泄漏开标签: " + e.text());
                assertFalse(e.text().contains("</message"), "正文泄漏闭标签: " + e.text());
                assertFalse(e.text().contains("<"), "正文泄漏残缺标签: " + e.text());
            }
        }
    }

    @Test
    void finish后不允许再feed() {
        Session s = factory.createSession();
        s.feed("<message>a</message>");
        s.finish();
        assertThrows(IllegalStateException.class, () -> s.feed("x"));
    }

    @Test
    void 无输出finish无事件() {
        Session s = factory.createSession();
        assertTrue(s.finish().isEmpty());
    }

    private boolean noTagLeak(List<Event> events) {
        for (Event e : events) {
            if (e.kind() == Session.EventKind.DELTA && e.text() != null) {
                if (e.text().contains("<") || e.text().contains(">")) {
                    return false;
                }
            }
        }
        return true;
    }
}
