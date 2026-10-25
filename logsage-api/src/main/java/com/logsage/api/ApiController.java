package com.logsage.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    final LogService logs; final AnalysisService ai;
    public ApiController(LogService logs, AnalysisService ai) { this.logs=logs; this.ai=ai; }
    @GetMapping("/dashboard/stats") public Object stats() { return logs.stats(); }
    @GetMapping("/incidents") public Object incidents() { return logs.incidents(); }
    @GetMapping("/logs") public Object entries(@RequestParam(required=false) String incidentId) { return logs.logs(incidentId); }
    @GetMapping("/incidents/{id}") public Object incident(@PathVariable String id) { return Map.of("incident",logs.incident(id),"evidence",logs.logs(id)); }
    @PostMapping("/logs/upload") public Object upload(@RequestParam MultipartFile file,@RequestParam(defaultValue="application") String source) throws Exception {
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        if (!name.endsWith(".log") && !name.endsWith(".txt")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a .log or .txt file");
        return logs.ingest(new String(file.getBytes(),StandardCharsets.UTF_8),source);
    }
    @PostMapping("/logs/ingest") public Object ingest(@RequestBody Map<String,String> body) { return logs.ingest(body.getOrDefault("text",""),body.get("source")); }
    @PostMapping("/incidents/{id}/analyze") public Object analyze(@PathVariable String id,@RequestParam(defaultValue="false") boolean demo) { return ai.analyze(id,demo); }
    @GetMapping("/incidents/{id}/analysis") public Object latest(@PathVariable String id) { logs.incident(id); Object result=ai.latest(id); return result==null? Map.of():result; }
    @PatchMapping("/incidents/{id}") public Object status(@PathVariable String id,@RequestBody Map<String,String> body) {
        logs.incident(id); String status=body.get("status");
        if (!List.of("OPEN","RESOLVED").contains(status==null?"":status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status");
        logs.jdbc().update("UPDATE incidents SET status=? WHERE id=?",status,id); return logs.incident(id);
    }
    @PostMapping("/analyses/{id}/feedback") public Object feedback(@PathVariable String id,@RequestBody Map<String,String> body) {
        if(logs.jdbc().queryForObject("SELECT COUNT(*) FROM analyses WHERE id=?",Integer.class,id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Analysis not found");
        String rating=body.getOrDefault("rating","");
        if (!List.of("USEFUL","INCORRECT").contains(rating)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid rating");
        logs.jdbc().update("INSERT INTO feedback VALUES(?,?,?,?)",UUID.randomUUID().toString(),id,rating,Instant.now().toString()); return Map.of("saved",true);
    }
    @PostMapping("/demo") public Object demo() {
        if(logs.jdbc().queryForObject("SELECT COUNT(*) FROM entries WHERE batch_id='demo-seeded'",Integer.class)>0) return Map.of("message","Sample data already loaded");
        StringBuilder text=new StringBuilder();
        String[] messages={"HikariPool-1 - Connection is not available, request timed out after 30000ms. java.sql.SQLTransientConnectionException", "Request failed: java.lang.NullPointerException: Cannot invoke getEmail() because customer is null", "Payment request failed: java.net.SocketTimeoutException: Read timed out", "Authentication failed: io.jsonwebtoken.ExpiredJwtException: JWT expired"};
        String[] services={"order-service","customer-service","payment-service","gateway-service"};
        for(int i=0;i<128;i++) {
            int group=i%4; boolean error=i%3==0;
            text.append("2026-10-22T10:").append(String.format("%02d:%02d.000Z",20+i/6,i%60)).append(error?" ERROR ":" INFO ").append("[").append(services[group]).append("] com.logsage.").append(error?"RequestHandler":"RequestFilter").append(" : ").append(error?messages[group]:"Request completed successfully in 24ms").append("\n");
            if(error) text.append("\tat com.example.").append(services[group].replace("-service","")).append(".RequestHandler.handle(RequestHandler.java:84)\n");
        }
        var result=logs.ingest(text.toString(),"demo"); logs.jdbc().update("UPDATE entries SET batch_id='demo-seeded' WHERE batch_id=?",result.get("batchId")); return result;
    }
}
