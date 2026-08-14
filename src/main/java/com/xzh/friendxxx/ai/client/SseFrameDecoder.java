package com.xzh.friendxxx.ai.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Incrementally decodes Server-Sent Event frames from arbitrary network chunks.
 * A frame may span chunks and a chunk may contain multiple frames.
 */
final class SseFrameDecoder {

    private final StringBuilder pending = new StringBuilder();

    List<String> accept(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        pending.append(text);

        List<String> dataItems = new ArrayList<>();
        Boundary boundary;
        while ((boundary = findBoundary()) != null) {
            String frame = pending.substring(0, boundary.index());
            pending.delete(0, boundary.index() + boundary.length());
            String data = extractData(frame);
            if (data != null) {
                dataItems.add(data);
            }
        }
        return dataItems;
    }

    private Boundary findBoundary() {
        int lf = pending.indexOf("\n\n");
        int crlf = pending.indexOf("\r\n\r\n");
        if (lf < 0 && crlf < 0) {
            return null;
        }
        if (crlf >= 0 && (lf < 0 || crlf <= lf)) {
            return new Boundary(crlf, 4);
        }
        return new Boundary(lf, 2);
    }

    private String extractData(String frame) {
        StringBuilder data = new StringBuilder();
        for (String line : frame.split("\\r?\\n", -1)) {
            if (line.startsWith(":")) {
                continue;
            }
            if (line.equals("data:")) {
                appendLine(data, "");
            } else if (line.startsWith("data:")) {
                String value = line.substring(5);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                appendLine(data, value);
            }
        }
        return data.length() == 0 ? null : data.toString();
    }

    private void appendLine(StringBuilder target, String value) {
        if (target.length() > 0) {
            target.append('\n');
        }
        target.append(value);
    }

    private record Boundary(int index, int length) {
    }
}
