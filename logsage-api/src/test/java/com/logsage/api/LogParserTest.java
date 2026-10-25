package com.logsage.api;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LogParserTest {
    @Test void groupsVariableIdsAndPreservesStack() {
        var entries=new LogParser().parse("2026-10-22T10:32:01.000Z ERROR [order-service] Controller : Order 1042 failed java.lang.IllegalStateException\n\tat app.Order.save(Order.java:42)\n2026-10-22T10:33:01.000Z ERROR [order-service] Controller : Order 1043 failed java.lang.IllegalStateException\n\tat app.Order.save(Order.java:43)","test");
        assertEquals(2,entries.size()); assertEquals("order-service",entries.getFirst().service());
        assertTrue(entries.getFirst().stack().contains("Order.save"));
        assertEquals(LogParser.fingerprint(entries.get(0)),LogParser.fingerprint(entries.get(1)));
        assertEquals("IllegalStateException",LogParser.exception(entries.getFirst()));
    }
    @Test void redactsCommonSecrets() { assertEquals("password=[REDACTED] token: [REDACTED] Bearer [REDACTED] [EMAIL REDACTED]",LogParser.redact("password=hunter2 token: abc Bearer jwt.value user@example.com")); }
    @Test void rejectsUnknownEvidence() {
        Map<String,Object> d=Map.of("summary","Hypothesis","confidence","MEDIUM","probableCauses",List.of("Cause"),"recommendedActions",List.of("Inspect"),"evidenceIds",List.of("invented"));
        assertThrows(IllegalArgumentException.class,()->AnalysisService.validate(d,Set.of("real")));
    }
    @Test void ignoresUnsupportedInput() { assertTrue(new LogParser().parse("not a log","test").isEmpty()); }
}
