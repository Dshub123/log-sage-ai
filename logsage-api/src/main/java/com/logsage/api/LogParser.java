package com.logsage.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public class LogParser {
    private static final Pattern HEADER = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)\\s+(?:\\d+\\s+)?(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+(.*)$");
    private static final Pattern EXCEPTION = Pattern.compile("(?:[a-zA-Z_$][\\w$]*\\.)*([A-Z][\\w$]*(?:Exception|Error))\\b");
    public record Entry(String id, String time, String service, String level, String logger, String message, String stack) {}
    public List<Entry> parse(String text, String source) {
        var result = new ArrayList<Entry>();
        for (String line : text.replace("\r", "").split("\n")) {
            var matcher = HEADER.matcher(line);
            if (matcher.matches()) {
                String rest = matcher.group(3), service = source;
                var tag = Pattern.compile("\\[([\\w-]+-service)\\]").matcher(rest);
                if (tag.find()) service = tag.group(1);
                String logger = "application", message = rest;
                int split = rest.indexOf(" : ");
                if (split >= 0) { logger = rest.substring(0, split).replaceAll("^.*?---\\s*", "").replaceAll("\\[[^]]*]", "").trim(); message = rest.substring(split + 3); }
                result.add(new Entry(UUID.randomUUID().toString(), matcher.group(1).replace(',', '.'), service, matcher.group(2), redact(logger), redact(message), ""));
            } else if (!line.isBlank() && !result.isEmpty()) {
                int i = result.size()-1; var old = result.get(i);
                result.set(i, new Entry(old.id, old.time, old.service, old.level, old.logger, old.message, old.stack + redact(line) + "\n"));
            }
        }
        return result;
    }
    public static String exception(Entry e) { var m = EXCEPTION.matcher(e.message + "\n" + e.stack); return m.find() ? m.group(1) : "ApplicationError"; }
    public static String fingerprint(Entry e) {
        String frame = e.stack.lines().filter(l -> l.trim().startsWith("at ")).findFirst().orElse("").replaceAll(":\\d+", ":#");
        String normalized = e.message.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}", "<id>").replaceAll("\\b\\d+\\b", "#");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((e.service+"|"+exception(e)+"|"+frame+"|"+normalized).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public static String redact(String text) {
        return text.replaceAll("(?i)(password|token|secret|api[_-]?key)(\\s*[=:]\\s*)[^\\s,;]+", "$1$2[REDACTED]")
            .replaceAll("(?i)Bearer\\s+[\\w.\\-]+", "Bearer [REDACTED]")
            .replaceAll("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}", "[EMAIL REDACTED]");
    }
}
