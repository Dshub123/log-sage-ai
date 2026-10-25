package com.logsage.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:workflow;DB_CLOSE_DELAY=-1"})
@Transactional
class WorkflowTest {
    @Autowired LogService logs;
    @Autowired AnalysisService ai;
    @Autowired ApiController api;
    @Test void sampleWorkflowPersistsDiagnosisAndFeedback() {
        api.demo();
        assertEquals(128L,logs.stats().get("logs"));
        assertEquals(4,logs.incidents().size());
        String id=logs.incidents().getFirst().get("id").toString();
        var result=ai.analyze(id,true);
        assertEquals("GUIDED_DEMO",result.get("mode"));
        assertNotNull(ai.latest(id));
        assertEquals(Map.of("saved",true),api.feedback(result.get("id").toString(),Map.of("rating","USEFUL")));
        api.status(id,Map.of("status","RESOLVED"));
        assertEquals("RESOLVED",logs.incident(id).get("status"));
        api.demo(); assertEquals(128L,logs.stats().get("logs"));
    }
    @Test void unsupportedInputLeavesDatabaseUnchanged() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->logs.ingest("garbage","test"));
        assertEquals(0L,logs.stats().get("logs"));
    }
}
