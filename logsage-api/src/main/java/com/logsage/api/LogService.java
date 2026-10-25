package com.logsage.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class LogService {
    final JdbcTemplate db;
    public LogService(JdbcTemplate db) { this.db = db; }
    public JdbcTemplate jdbc() { return db; }
    @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> ingest(String text, String source) {
        if (text.length() > 5_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum log size is 5 MB");
        source = source == null || source.isBlank() ? "application" : source.trim();
        if (source.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name must be at most 100 characters");
        var entries = new LogParser().parse(text, source);
        if (entries.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No supported log entries found. Use timestamp LEVEL [service] logger : message format.");
        String batch = UUID.randomUUID().toString(); int errors = 0;
        for (var e : entries) {
            String incident = null;
            if (List.of("ERROR", "FATAL").contains(e.level())) {
                errors++; incident = LogParser.fingerprint(e);
                if (db.queryForObject("SELECT COUNT(*) FROM incidents WHERE id=?", Integer.class, incident)==0) {
                    String type = LogParser.exception(e);
                    String title = e.message().contains("Hikari") || e.message().contains("Connection is not available") ? "Database connection pool exhausted" : e.message().contains("Read timed out") ? "Downstream service timeout" : type.equals("NullPointerException") ? "Null reference in request handler" : type.equals("ApplicationError") ? e.message() : type.replaceAll("([a-z])([A-Z])", "$1 $2");
                    db.update("INSERT INTO incidents(id,title,service,exception_type,severity,status) VALUES(?,?,?,?,?,'OPEN')", incident, title.substring(0,Math.min(250,title.length())), e.service(), type, type.contains("OutOfMemory") || e.message().contains("Hikari") ? "CRITICAL" : "HIGH");
                }
            }
            db.update("INSERT INTO entries VALUES(?,?,?,?,?,?,?,?,?)", e.id(), batch, e.time(), e.service(), e.level(), e.logger(), e.message(), e.stack(), incident);
        }
        return Map.of("batchId",batch,"totalEntries",entries.size(),"errors",errors);
    }
    public List<Map<String,Object>> incidents() {
        return db.query("SELECT i.*,COUNT(e.id) occurrences,MIN(e.event_time) first_seen,MAX(e.event_time) last_seen FROM incidents i JOIN entries e ON e.incident_id=i.id GROUP BY i.id ORDER BY last_seen DESC", (rs,n) -> {
            var m = new LinkedHashMap<String,Object>();
            for (String key: List.of("id","title","service","exception_type","severity","status","first_seen","last_seen")) m.put(key,rs.getString(key));
            m.put("occurrences",rs.getInt("occurrences")); return m;
        });
    }
    public Map<String,Object> incident(String id) { return incidents().stream().filter(i -> i.get("id").equals(id)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident not found")); }
    public List<Map<String,Object>> logs(String incident) {
        String where = incident == null ? "" : " WHERE incident_id=?";
        Object[] args = incident==null ? new Object[]{} : new Object[]{incident};
        return db.query("SELECT * FROM entries"+where+" ORDER BY event_time DESC LIMIT 500",(rs,n)-> {
            var m = new LinkedHashMap<String,Object>(); for(String k: List.of("id","event_time","service","level","logger","message","stack","incident_id")) m.put(k,rs.getString(k)); return m;
        },args);
    }
    public Map<String,Object> stats() { return Map.of("logs",db.queryForObject("SELECT COUNT(*) FROM entries",Long.class),"errors",db.queryForObject("SELECT COUNT(*) FROM entries WHERE level IN ('ERROR','FATAL')",Long.class),"incidents",db.queryForObject("SELECT COUNT(*) FROM incidents WHERE status='OPEN'",Long.class),"services",db.queryForObject("SELECT COUNT(DISTINCT service) FROM entries",Long.class)); }
}
