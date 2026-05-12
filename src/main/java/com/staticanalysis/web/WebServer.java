package com.staticanalysis.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.ast.CompilationUnit;
import com.staticanalysis.analyzer.*;
import com.staticanalysis.config.AnalysisConfig;
import com.staticanalysis.metrics.CognitiveComplexityAnalyzer;
import com.staticanalysis.metrics.ComplexityAnalyzer;
import com.staticanalysis.metrics.LOCCounter;
import com.staticanalysis.metrics.MaintainabilityIndex;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;
import com.staticanalysis.parser.CodeParser;
import com.staticanalysis.report.ReportGenerator;
import com.staticanalysis.web.MultipartParser.UploadedFile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server that provides the StaticGuard Web UI.
 *
 * Routes:
 *   GET  /           - Serves the upload UI page
 *   POST /analyze    - Accepts .java file uploads, runs analysis, returns JSON
 *   GET  /download   - Downloads report.json or report.html  (?type=json|html)
 */
public class WebServer {

    private static final int PORT = 8080;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Last generated reports held in memory for the download endpoint
    private volatile byte[] lastJsonReport = null;
    private volatile byte[] lastHtmlReport = null;

    // -----------------------------------------------------------------------
    // Start
    // -----------------------------------------------------------------------

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/analyze", this::handleAnalyze);
        server.createContext("/download", this::handleDownload);
        server.createContext("/health", this::handleHealth);  // W1
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println();
        System.out.println("+--------------------------------------------------+");
        System.out.println("|  StaticGuard Web UI                              |");
        System.out.println("+--------------------------------------------------+");
        System.out.println("  Server started at: http://localhost:" + PORT);
        System.out.println("  Open this URL in your browser to begin.");
        System.out.println("  Press Ctrl+C to stop.");
        System.out.println();
    }

    // -----------------------------------------------------------------------
    // Route: GET /
    // -----------------------------------------------------------------------

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, 405, "Method Not Allowed");
            return;
        }
        byte[] body = buildUiPage().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, body.length);
        ex.getResponseBody().write(body);
        ex.getResponseBody().close();
    }

    // -----------------------------------------------------------------------
    // Route: POST /analyze
    // -----------------------------------------------------------------------

    private void handleAnalyze(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, 405, "Method Not Allowed");
            return;
        }

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        List<UploadedFile> uploads = MultipartParser.parse(contentType, ex.getRequestBody());

        if (uploads.isEmpty()) {
            sendJsonError(ex, 400, "No Java files were uploaded.");
            return;
        }

        // Filter .java files only
        List<UploadedFile> javaFiles = new ArrayList<>();
        for (UploadedFile f : uploads) {
            if (f.filename != null && f.filename.toLowerCase().endsWith(".java")) {
                javaFiles.add(f);
            }
        }

        if (javaFiles.isEmpty()) {
            sendJsonError(ex, 400, "Please upload at least one .java file.");
            return;
        }

        // Write files to a temp directory and run the analysis pipeline
        Path tempDir = Files.createTempDirectory("staticguard_");
        try {
            for (UploadedFile f : javaFiles) {
                Path dest = tempDir.resolve(f.filename);
                Files.write(dest, f.content);
            }

            // Run analysis (synchronised on class to avoid static state collisions)
            Map<String, Object> result;
            synchronized (WebServer.class) {
                DefectCollector.reset();
                DuplicateCodeDetector.reset();
                LOCCounter.reset();

                CodeParser parser = new CodeParser();
                AnalysisConfig config = AnalysisConfig.getInstance();

                for (UploadedFile f : javaFiles) {
                    Path filePath = tempDir.resolve(f.filename);
                    CompilationUnit cu = parser.parseFile(filePath.toAbsolutePath().toString());
                    if (cu != null) {
                        if (config.isEnableCodeSmells())   new SmellDetector(f.filename).visit(cu, null);
                        if (config.isEnableDeadCode())     new DeadCodeDetector(f.filename).visit(cu, null);
                        if (config.isEnableSecurity())     new SecurityAnalyzer(f.filename).visit(cu, null);
                        if (config.isEnableNaming())       new NamingConventionAnalyzer(f.filename).visit(cu, null);
                        if (config.isEnableDuplication())  new DuplicateCodeDetector(f.filename).visit(cu, null);
                        if (config.isEnableMetrics()) {
                            new ComplexityAnalyzer(f.filename).visit(cu, null);
                            new CognitiveComplexityAnalyzer(f.filename).visit(cu, null);
                            new LOCCounter(f.filename).visit(cu, null);
                            new MaintainabilityIndex(f.filename).visit(cu, null);
                        }
                    }
                }

                List<Defect> defects = DefectCollector.getDefects();

                // Capture JSON report into memory
                ByteArrayOutputStream jsonBuf = new ByteArrayOutputStream();
                JSON.writeValue(jsonBuf, defects);
                lastJsonReport = jsonBuf.toByteArray();

                // Capture HTML report into memory (ReportGenerator writes to a temp file)
                Path htmlTemp = Files.createTempFile("sg_report_", ".html");
                ReportGenerator.generateHtmlReport(defects, htmlTemp.toAbsolutePath().toString());
                lastHtmlReport = Files.readAllBytes(htmlTemp);
                Files.deleteIfExists(htmlTemp);

                // Build response payload
                result = buildResultPayload(defects);
            }

            byte[] responseBody = JSON.writeValueAsBytes(result);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            ex.sendResponseHeaders(200, responseBody.length);
            ex.getResponseBody().write(responseBody);
            ex.getResponseBody().close();

        } catch (Exception e) {
            e.printStackTrace();
            sendJsonError(ex, 500, "Analysis failed: " + e.getMessage());
        } finally {
            // Clean up temp directory
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }

    // -----------------------------------------------------------------------
    // Route: GET /download?type=json|html
    // -----------------------------------------------------------------------

    private void handleDownload(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, 405, "Method Not Allowed");
            return;
        }

        String query = ex.getRequestURI().getQuery();
        String type = "json";
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("type=")) {
                    type = param.substring(5);
                }
            }
        }

        byte[] data;
        String mimeType;
        String filename;

        if ("html".equalsIgnoreCase(type)) {
            data = lastHtmlReport;
            mimeType = "text/html";
            filename = "staticguard-report.html";
        } else {
            data = lastJsonReport;
            mimeType = "application/json";
            filename = "staticguard-report.json";
        }

        if (data == null) {
            sendError(ex, 404, "No report available yet. Please analyze a file first.");
            return;
        }

        ex.getResponseHeaders().set("Content-Type", mimeType);
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.getResponseBody().close();
    }

    // -----------------------------------------------------------------------
    // Route: GET /health  (W1)
    // -----------------------------------------------------------------------

    private void handleHealth(HttpExchange ex) throws IOException {
        byte[] body = "{\"status\":\"ok\",\"service\":\"StaticGuard Web UI\",\"version\":\"2.0\"}".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, body.length);
        ex.getResponseBody().write(body);
        ex.getResponseBody().close();
    }

    // -----------------------------------------------------------------------
    // Build structured result payload from defects
    // -----------------------------------------------------------------------

    private Map<String, Object> buildResultPayload(List<Defect> defects) {
        int critical = (int) defects.stream().filter(d -> "CRITICAL".equals(d.getSeverity())).count();
        int major    = (int) defects.stream().filter(d -> "MAJOR".equals(d.getSeverity())).count();
        int minor    = (int) defects.stream().filter(d -> "MINOR".equals(d.getSeverity())).count();

        // Count by type
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Defect d : defects) {
            byType.merge(d.getType(), 1L, Long::sum);
        }

        // Count by file
        Map<String, Long> byFile = new LinkedHashMap<>();
        for (Defect d : defects) {
            byFile.merge(d.getFileName(), 1L, Long::sum);
        }

        // Defect list for table
        List<Map<String, Object>> defectList = new ArrayList<>();
        for (Defect d : defects) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("severity",   d.getSeverity());
            item.put("ruleId",     d.getRuleId() != null ? d.getRuleId() : "-");
            item.put("type",       d.getType());
            item.put("file",       d.getFileName());
            item.put("line",       d.getLineNumber());
            item.put("message",    d.getMessage());
            item.put("suggestion", d.getSuggestion() != null ? d.getSuggestion() : "-");
            defectList.add(item);
        }

        // W5: Compute code health score (A=excellent, F=failing)
        // Weights: critical=10pts, major=3pts, minor=1pt
        int rawScore = Math.max(0, 100 - (critical * 10) - (major * 3) - minor);
        String grade;
        if      (rawScore >= 90) grade = "A";
        else if (rawScore >= 75) grade = "B";
        else if (rawScore >= 55) grade = "C";
        else if (rawScore >= 35) grade = "D";
        else                     grade = "F";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", Map.of(
            "total", defects.size(),
            "critical", critical,
            "major", major,
            "minor", minor,
            "score", rawScore,
            "grade", grade
        ));
        result.put("byType", byType);
        result.put("byFile", byFile);
        result.put("defects", defectList);
        return result;
    }

    // -----------------------------------------------------------------------
    // Helper utilities
    // -----------------------------------------------------------------------

    private void sendError(HttpExchange ex, int code, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, body.length);
        ex.getResponseBody().write(body);
        ex.getResponseBody().close();
    }

    private void sendJsonError(HttpExchange ex, int code, String message) throws IOException {
        Map<String, String> err = Map.of("error", message);
        byte[] body = JSON.writeValueAsBytes(err);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, body.length);
        ex.getResponseBody().write(body);
        ex.getResponseBody().close();
    }

    // -----------------------------------------------------------------------
    // UI Page HTML
    // -----------------------------------------------------------------------

    private String buildUiPage() {
        return "<!DOCTYPE html>\n" +
        "<html lang='en'>\n" +
        "<head>\n" +
        "<meta charset='UTF-8'>\n" +
        "<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
        "<title>StaticGuard - Web UI</title>\n" +
        "<style>\n" +
        buildCSS() +
        "</style>\n" +
        "</head>\n" +
        "<body>\n" +
        buildHTML() +
        "<script>\n" +
        buildJS() +
        "</script>\n" +
        "</body>\n" +
        "</html>\n";
    }

    private String buildCSS() {
        return
        ":root{--bg:#0f172a;--card:#1e293b;--border:#334155;--accent:#6366f1;--accent2:#8b5cf6;" +
        "--text:#f1f5f9;--muted:#94a3b8;--critical:#ef4444;--major:#f59e0b;--minor:#eab308;--success:#22c55e;}" +
        "*{margin:0;padding:0;box-sizing:border-box;}" +
        "body{font-family:'Segoe UI',system-ui,sans-serif;background:var(--bg);color:var(--text);min-height:100vh;}" +
        "a{color:var(--accent);}" +

        /* Header */
        ".header{background:linear-gradient(135deg,#6366f1,#8b5cf6,#a855f7);padding:32px 40px;" +
        "display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:16px;}" +
        ".header-brand h1{font-size:2rem;color:#fff;letter-spacing:-0.5px;}" +
        ".header-brand p{color:rgba(255,255,255,.75);font-size:.9rem;margin-top:4px;}" +
        ".header-badge{background:rgba(255,255,255,.15);border:1px solid rgba(255,255,255,.3);" +
        "border-radius:20px;padding:6px 16px;color:#fff;font-size:.8rem;font-weight:600;}" +

        /* Layout */
        ".wrap{max-width:1300px;margin:0 auto;padding:32px 24px;}" +

        /* Upload zone */
        ".upload-card{background:var(--card);border:2px dashed var(--border);border-radius:20px;" +
        "padding:48px 40px;text-align:center;cursor:pointer;transition:all .25s;position:relative;}" +
        ".upload-card:hover,.upload-card.drag-over{border-color:var(--accent);" +
        "box-shadow:0 0 0 4px rgba(99,102,241,.15);}" +
        ".upload-icon{font-size:3.5rem;margin-bottom:16px;opacity:.8;}" +
        ".upload-card h2{font-size:1.4rem;margin-bottom:8px;}" +
        ".upload-card p{color:var(--muted);font-size:.9rem;}" +
        ".upload-card input[type=file]{position:absolute;inset:0;opacity:0;cursor:pointer;}" +
        ".file-list{margin-top:20px;display:flex;flex-wrap:wrap;gap:8px;justify-content:center;}" +
        ".file-tag{background:rgba(99,102,241,.15);border:1px solid var(--accent);color:var(--accent);" +
        "border-radius:20px;padding:4px 14px;font-size:.8rem;display:flex;align-items:center;gap:6px;}" +
        ".file-tag button{background:none;border:none;color:var(--accent);cursor:pointer;font-size:1rem;line-height:1;}" +
        // W4: file count badge
        ".file-count{margin-top:12px;font-size:.82rem;color:var(--muted);}" +
        // W3: clear button
        ".btn-clear{background:none;border:1px solid var(--border);border-radius:8px;color:var(--muted);" +
        "padding:7px 16px;font-size:.82rem;cursor:pointer;margin-top:10px;transition:all .2s;}" +
        ".btn-clear:hover{border-color:var(--critical);color:var(--critical);}" +

        /* Action button */
        ".btn-analyze{display:block;width:100%;max-width:380px;margin:24px auto 0;padding:16px;background:" +
        "linear-gradient(135deg,var(--accent),var(--accent2));color:#fff;border:none;border-radius:12px;" +
        "font-size:1rem;font-weight:700;cursor:pointer;transition:all .2s;letter-spacing:.5px;}" +
        ".btn-analyze:hover:not(:disabled){transform:translateY(-2px);box-shadow:0 8px 25px rgba(99,102,241,.4);}" +
        ".btn-analyze:disabled{opacity:.5;cursor:not-allowed;}" +

        /* Progress */
        ".progress-wrap{margin-top:24px;display:none;}" +
        ".progress-bar{height:6px;background:var(--border);border-radius:3px;overflow:hidden;}" +
        ".progress-fill{height:100%;width:0;background:linear-gradient(90deg,var(--accent),var(--accent2));" +
        "border-radius:3px;transition:width .4s ease;}" +
        ".progress-label{text-align:center;font-size:.85rem;color:var(--muted);margin-top:8px;}" +

        /* Error banner */
        ".error-banner{background:rgba(239,68,68,.12);border:1px solid var(--critical);border-radius:10px;" +
        "padding:14px 20px;color:var(--critical);margin-top:20px;display:none;}" +

        /* Results section */
        "#results{margin-top:36px;display:none;}" +
        ".section-title{font-size:1.15rem;font-weight:700;margin-bottom:16px;color:var(--text);}" +

        /* Summary cards — includes W5 score card */
        ".cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:14px;margin-bottom:28px;}" +
        ".card{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:22px;text-align:center;" +
        "transition:transform .2s,box-shadow .2s;}" +
        ".card:hover{transform:translateY(-3px);box-shadow:0 10px 30px rgba(0,0,0,.35);}" +
        ".card-val{font-size:2.4rem;font-weight:800;}" +
        ".card-lbl{font-size:.8rem;text-transform:uppercase;letter-spacing:1px;color:var(--muted);margin-top:4px;}" +
        ".card.total .card-val{color:var(--accent);}" +
        ".card.critical .card-val{color:var(--critical);}" +
        ".card.major .card-val{color:var(--major);}" +
        ".card.minor .card-val{color:var(--minor);}" +
        ".card.score .card-val{font-size:2.8rem;}" +
        ".grade-A{color:#22c55e;}.grade-B{color:#84cc16;}.grade-C{color:#f59e0b;}.grade-D{color:#f97316;}.grade-F{color:#ef4444;}" +

        /* Charts */
        ".charts{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px;margin-bottom:28px;}" +
        ".chart-card{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:24px;}" +
        ".chart-card h3{font-size:.95rem;font-weight:700;margin-bottom:16px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px;}" +
        ".bar-row{display:flex;align-items:center;gap:10px;margin-bottom:9px;}" +
        ".bar-lbl{min-width:130px;font-size:.8rem;color:var(--muted);text-align:right;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}" +
        ".bar-track{flex:1;background:rgba(255,255,255,.05);border-radius:6px;height:24px;overflow:hidden;}" +
        ".bar-fill{height:100%;border-radius:6px;display:flex;align-items:center;justify-content:flex-end;" +
        "padding-right:8px;font-size:.75rem;font-weight:700;color:#fff;min-width:28px;transition:width .6s ease;}" +
        ".pie-wrap{display:flex;flex-direction:column;align-items:center;gap:14px;}" +
        ".pie-legend{display:flex;gap:14px;flex-wrap:wrap;justify-content:center;}" +
        ".legend-dot{width:10px;height:10px;border-radius:50%;display:inline-block;margin-right:5px;}" +
        ".legend-item{font-size:.82rem;color:var(--muted);display:flex;align-items:center;}" +

        /* Filters */
        ".filter-bar{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin-bottom:14px;}" +
        ".filter-bar input,.filter-bar select{padding:9px 14px;background:var(--card);border:1px solid var(--border);" +
        "border-radius:8px;color:var(--text);font-size:.88rem;outline:none;}" +
        ".filter-bar input{flex:1;min-width:180px;}.filter-bar input:focus{border-color:var(--accent);}" +
        ".result-count{font-size:.82rem;color:var(--muted);margin-left:auto;}" +

        /* Table */
        ".table-wrap{background:var(--card);border:1px solid var(--border);border-radius:14px;overflow:hidden;overflow-x:auto;}" +
        "table{width:100%;border-collapse:collapse;}" +
        "thead th{background:rgba(255,255,255,.04);padding:13px 15px;text-align:left;" +
        "font-size:.75rem;text-transform:uppercase;letter-spacing:1px;color:var(--muted);" +
        "cursor:pointer;border-bottom:1px solid var(--border);user-select:none;white-space:nowrap;}" +
        "thead th:hover{color:var(--accent);}" +
        "tbody td{padding:11px 15px;border-bottom:1px solid var(--border);font-size:.87rem;}" +
        "tbody tr:last-child td{border-bottom:none;}" +
        "tbody tr:hover{background:rgba(99,102,241,.05);}" +
        ".badge{padding:3px 9px;border-radius:20px;font-size:.7rem;font-weight:700;text-transform:uppercase;}" +
        ".badge.critical{background:rgba(239,68,68,.15);color:var(--critical);}" +
        ".badge.major{background:rgba(245,158,11,.15);color:var(--major);}" +
        ".badge.minor{background:rgba(234,179,8,.15);color:var(--minor);}" +
        ".rule-id{font-family:monospace;color:var(--accent);font-size:.8rem;}" +
        ".file-cell{font-family:monospace;color:var(--muted);font-size:.82rem;}" +
        ".msg-cell{max-width:280px;}" +
        ".sug-cell{font-size:.82rem;color:var(--muted);font-style:italic;max-width:220px;}" +

        /* Download buttons */
        ".download-row{display:flex;gap:14px;justify-content:center;flex-wrap:wrap;margin-top:28px;" +
        "padding-top:24px;border-top:1px solid var(--border);}" +
        ".btn-dl{padding:13px 32px;border-radius:10px;font-size:.95rem;font-weight:700;cursor:pointer;" +
        "transition:all .2s;text-decoration:none;display:inline-flex;align-items:center;gap:8px;}" +
        ".btn-dl.json{background:rgba(99,102,241,.15);border:2px solid var(--accent);color:var(--accent);}" +
        ".btn-dl.html{background:rgba(139,92,246,.15);border:2px solid var(--accent2);color:var(--accent2);}" +
        ".btn-dl:hover{transform:translateY(-2px);box-shadow:0 6px 20px rgba(0,0,0,.3);}" +

        /* Footer */
        ".footer{text-align:center;padding:28px;color:var(--muted);font-size:.82rem;margin-top:16px;}" +

        /* Responsive */
        "@media(max-width:768px){.header{padding:24px;}.wrap{padding:20px 14px;}.bar-lbl{min-width:80px;}}";
    }

    private String buildHTML() {
        return
        "<header class='header'>" +
          "<div class='header-brand'>" +
            "<h1>StaticGuard</h1>" +
            "<p>AST-Based Static Code Analysis Framework</p>" +
          "</div>" +
          "<span class='header-badge'>Web UI v2.0</span>" +
        "</header>" +

        "<main class='wrap'>" +

          "<!-- Upload Zone -->" +
          "<div id='uploadCard' class='upload-card'>" +
            "<input type='file' id='fileInput' multiple accept='.java' onchange='onFilePicked()'>" +
            "<div class='upload-icon'>&#128196;</div>" +
            "<h2>Drop your Java files here</h2>" +
            "<p>Or click anywhere in this box to browse &mdash; multiple files supported</p>" +
            "<div class='file-list' id='fileList'></div>" +
          "</div>" +
          // W4: file count + W3: clear button outside the card (no z-index conflict)
          "<div style='text-align:center;margin-top:10px;'>" +
            "<span class='file-count' id='fileCount'></span>" +
            "<button class='btn-clear' id='clearBtn' style='display:none;margin-left:12px;' onclick='clearAll()'>Clear all</button>" +
          "</div>" +

          "<div class='error-banner' id='errorBanner'></div>" +

          "<div class='progress-wrap' id='progressWrap'>" +
            "<div class='progress-bar'><div class='progress-fill' id='progressFill'></div></div>" +
            "<p class='progress-label' id='progressLabel'>Preparing analysis...</p>" +
          "</div>" +

          "<button class='btn-analyze' id='analyzeBtn' onclick='runAnalysis()' disabled>Analyze Files</button>" +

          "<!-- Results -->" +
          "<section id='results'>" +

            "<p class='section-title'>Analysis Results</p>" +

            "<!-- Summary Cards -->" +
            "<div class='cards' id='summaryCards'></div>" +

            "<!-- Charts -->" +
            "<div class='charts'>" +
              "<div class='chart-card'>" +
                "<h3>Severity Distribution</h3>" +
                "<div class='pie-wrap'>" +
                  "<canvas id='pieChart' width='180' height='180'></canvas>" +
                  "<div class='pie-legend' id='pieLegend'></div>" +
                "</div>" +
              "</div>" +
              "<div class='chart-card'>" +
                "<h3>Issues by File</h3>" +
                "<div id='fileChart'></div>" +
              "</div>" +
              "<div class='chart-card'>" +
                "<h3>Issues by Category</h3>" +
                "<div id='typeChart'></div>" +
              "</div>" +
            "</div>" +

            "<!-- Defect Table -->" +
            "<div class='filter-bar'>" +
              "<input type='text' id='searchBox' placeholder='Search defects...' oninput='filterTable()'>" +
              "<select id='sevFilter' onchange='filterTable()'>" +
                "<option value=''>All Severities</option>" +
                "<option value='CRITICAL'>Critical</option>" +
                "<option value='MAJOR'>Major</option>" +
                "<option value='MINOR'>Minor</option>" +
              "</select>" +
              "<select id='typeFilter' onchange='filterTable()'><option value=''>All Types</option></select>" +
              "<select id='fileFilter' onchange='filterTable()'><option value=''>All Files</option></select>" +
              "<span class='result-count' id='resultCount'></span>" +
            "</div>" +

            "<div class='table-wrap'>" +
              "<table id='defectTable'>" +
                "<thead><tr>" +
                  "<th onclick='sortCol(0)'>Severity</th>" +
                  "<th onclick='sortCol(1)'>Rule ID</th>" +
                  "<th onclick='sortCol(2)'>Type</th>" +
                  "<th onclick='sortCol(3)'>File</th>" +
                  "<th onclick='sortCol(4)'>Line</th>" +
                  "<th onclick='sortCol(5)'>Message</th>" +
                  "<th>Suggestion</th>" +
                "</tr></thead>" +
                "<tbody id='tableBody'></tbody>" +
              "</table>" +
            "</div>" +

            "<!-- Download Buttons -->" +
            "<div class='download-row'>" +
              "<a class='btn-dl json' href='/download?type=json' download='staticguard-report.json'>" +
                "&#8659; Download JSON Report" +
              "</a>" +
              "<a class='btn-dl html' href='/download?type=html' download='staticguard-report.html'>" +
                "&#8659; Download HTML Report" +
              "</a>" +
            "</div>" +

          "</section>" +
        "</main>" +

        "<footer class='footer'>StaticGuard v2.0 &mdash; AST-Based Static Code Analysis Framework</footer>";
    }

    private String buildJS() {
        return
        "var selectedFiles = [];" +
        "var sortDirs = {};" +

        // Drag and drop
        "var card = document.getElementById('uploadCard');" +
        "card.addEventListener('dragover', function(e){e.preventDefault();card.classList.add('drag-over');});" +
        "card.addEventListener('dragleave', function(){card.classList.remove('drag-over');});" +
        "card.addEventListener('drop', function(e){" +
          "e.preventDefault();card.classList.remove('drag-over');" +
          "addFiles(e.dataTransfer.files);" +
        "});" +

        "function onFilePicked(){" +
          "addFiles(document.getElementById('fileInput').files);" +
          "document.getElementById('fileInput').value='';" +
        "}" +

        "function addFiles(fileList){" +
          "for(var i=0;i<fileList.length;i++){" +
            "var f=fileList[i];" +
            "if(f.name.endsWith('.java')&&!selectedFiles.find(function(x){return x.name===f.name;})){" +
              "selectedFiles.push(f);" +
            "}" +
          "}" +
          "renderFileList();" +
        "}" +

        "function removeFile(name){" +
          "selectedFiles=selectedFiles.filter(function(f){return f.name!==name;});" +
          "renderFileList();" +
        "}" +

        "function renderFileList(){" +
          "var list=document.getElementById('fileList');" +
          "list.innerHTML='';" +
          "selectedFiles.forEach(function(f){" +
            "var tag=document.createElement('div');" +
            "tag.className='file-tag';" +
            "tag.innerHTML='<span>'+f.name+'</span><button onclick=\"removeFile(\\'' + f.name + '\\')\">x</button>';" +
            "list.appendChild(tag);" +
          "});" +
          // W4: update file count badge
          "var n=selectedFiles.length;" +
          "var countEl=document.getElementById('fileCount');" +
          "countEl.textContent=n>0?(n+' file'+(n!==1?'s':'')+' selected'):'';" +
          // W3: show/hide clear button
          "document.getElementById('clearBtn').style.display=n>0?'inline-block':'none';" +
          "document.getElementById('analyzeBtn').disabled=n===0;" +
        "}" +

        // W3: clear all files and reset results
        "function clearAll(){" +
          "selectedFiles=[];" +
          "renderFileList();" +
          "document.getElementById('results').style.display='none';" +
          "document.getElementById('errorBanner').style.display='none';" +
          "document.getElementById('progressWrap').style.display='none';" +
        "}" +

        // Progress animation
        "function animateProgress(label, targetPct) {" +
          "document.getElementById('progressLabel').textContent = label;" +
          "var fill = document.getElementById('progressFill');" +
          "fill.style.width = targetPct + '%';" +
        "}" +

        // Analysis
        "function runAnalysis(){" +
          "document.getElementById('errorBanner').style.display='none';" +
          "document.getElementById('results').style.display='none';" +
          "document.getElementById('progressWrap').style.display='block';" +
          "document.getElementById('analyzeBtn').disabled=true;" +
          "animateProgress('Uploading files...', 20);" +

          "var form=new FormData();" +
          "selectedFiles.forEach(function(f){form.append('files',f,f.name);});" +

          "var xhr=new XMLHttpRequest();" +
          "xhr.open('POST','/analyze',true);" +
          "xhr.onload=function(){" +
            "document.getElementById('progressWrap').style.display='none';" +
            "document.getElementById('analyzeBtn').disabled=false;" +
            "if(xhr.status===200){" +
              "var data=JSON.parse(xhr.responseText);" +
              "renderResults(data);" +
            "}else{" +
              "showError(JSON.parse(xhr.responseText).error||'Analysis failed.');" +
            "}" +
          "};" +
          "xhr.onerror=function(){" +
            "document.getElementById('progressWrap').style.display='none';" +
            "document.getElementById('analyzeBtn').disabled=false;" +
            "showError('Network error. Is the server running?');" +
          "};" +
          "xhr.upload.onprogress=function(e){" +
            "if(e.lengthComputable) animateProgress('Uploading... '+Math.round(e.loaded/e.total*50)+'%', Math.round(e.loaded/e.total*50));" +
          "};" +
          "" +
          "setTimeout(function(){animateProgress('Running analysis pipeline...', 65);},500);" +
          "setTimeout(function(){animateProgress('Generating reports...', 85);},900);" +
          "xhr.send(form);" +
        "}" +

        "function showError(msg){" +
          "var b=document.getElementById('errorBanner');" +
          "b.textContent=msg;b.style.display='block';" +
        "}" +

        // Render full results
        "function renderResults(data){" +
          "renderCards(data.summary);" +
          "renderPie(data.summary);" +
          "renderBarChart('fileChart', data.byFile, ['#6366f1','#8b5cf6','#a855f7','#06b6d4','#14b8a6']);" +
          "renderBarChart('typeChart', data.byType, ['#ec4899','#f59e0b','#eab308','#22c55e','#6366f1','#ef4444']);" +
          "renderTable(data);" +
          "document.getElementById('results').style.display='block';" +
          "document.getElementById('results').scrollIntoView({behavior:'smooth'});" +
        "}" +

        "function renderCards(s){" +
          "var gradeClass='grade-'+s.grade;" +
          "var c=document.getElementById('summaryCards');" +
          "c.innerHTML=[" +
            "{label:'Total Issues',val:s.total,cls:'total',extra:''}," +
            "{label:'Critical',val:s.critical,cls:'critical',extra:''}," +
            "{label:'Major',val:s.major,cls:'major',extra:''}," +
            "{label:'Minor',val:s.minor,cls:'minor',extra:''}," +
            // W5: health score card
            "{label:'Health Score',val:s.grade,cls:'score '+gradeClass,extra:'<br><small style=\"color:var(--muted);font-size:.75rem\">'+s.score+'/100</small>'}" +
          "].map(function(x){" +
            "return '<div class=\"card '+x.cls+'\"><div class=\"card-val\">'+x.val+x.extra+'</div><div class=\"card-lbl\">'+x.label+'</div></div>';" +
          "}).join('');" +
        "}" +

        "function renderPie(s){" +
          "var canvas=document.getElementById('pieChart');" +
          "var ctx=canvas.getContext('2d');" +
          "var total=s.total||1;" +
          "var slices=[" +
            "{val:s.critical,color:'#ef4444',label:'Critical'}," +
            "{val:s.major,color:'#f59e0b',label:'Major'}," +
            "{val:s.minor,color:'#eab308',label:'Minor'}" +
          "];" +
          "ctx.clearRect(0,0,180,180);" +
          "var startAngle=-Math.PI/2;var cx=90;var cy=90;var r=75;" +
          "slices.forEach(function(sl){" +
            "if(sl.val===0)return;" +
            "var sweep=sl.val/total*2*Math.PI;" +
            "ctx.beginPath();ctx.moveTo(cx,cy);" +
            "ctx.arc(cx,cy,r,startAngle,startAngle+sweep);" +
            "ctx.closePath();ctx.fillStyle=sl.color;ctx.fill();" +
            "startAngle+=sweep;" +
          "});" +
          // Donut hole
          "ctx.beginPath();ctx.arc(cx,cy,40,0,2*Math.PI);ctx.fillStyle='#1e293b';ctx.fill();" +
          "ctx.fillStyle='#f1f5f9';ctx.font='bold 20px sans-serif';ctx.textAlign='center';ctx.textBaseline='middle';" +
          "ctx.fillText(total,cx,cy);" +
          "var leg=document.getElementById('pieLegend');" +
          "leg.innerHTML=slices.filter(function(x){return x.val>0;}).map(function(x){" +
            "return '<span class=\"legend-item\"><span class=\"legend-dot\" style=\"background:'+x.color+'\"></span>'+x.label+' ('+x.val+')</span>';" +
          "}).join('');" +
        "}" +

        "function renderBarChart(id, data, colors){" +
          "var el=document.getElementById(id);" +
          "var entries=Object.entries(data);" +
          "if(entries.length===0){el.innerHTML='<p style=\"color:var(--muted);text-align:center;\">No data</p>';return;}" +
          "var max=Math.max.apply(null,entries.map(function(e){return e[1];}));" +
          "el.innerHTML=entries.map(function(e,i){" +
            "var pct=(e[1]/max*100).toFixed(1);" +
            "var col=colors[i%colors.length];" +
            "return '<div class=\"bar-row\">'+" +
              "'<span class=\"bar-lbl\" title=\"'+e[0]+'\">'+e[0]+'</span>'+" +
              "'<div class=\"bar-track\">'+" +
                "'<div class=\"bar-fill\" style=\"width:'+pct+'%;background:'+col+'\">'+e[1]+'</div>'+" +
              "'</div>'+" +
            "'</div>';" +
          "}).join('');" +
        "}" +

        "function renderTable(data){" +
          "var types=Object.keys(data.byType);" +
          "var files=Object.keys(data.byFile);" +
          "var tf=document.getElementById('typeFilter');" +
          "tf.innerHTML='<option value=\"\">All Types</option>'+" +
            "types.map(function(t){return '<option value=\"'+t+'\">'+t+'</option>';}).join('');" +
          "var ff=document.getElementById('fileFilter');" +
          "ff.innerHTML='<option value=\"\">All Files</option>'+" +
            "files.map(function(f){return '<option value=\"'+f+'\">'+f+'</option>';}).join('');" +

          "var tbody=document.getElementById('tableBody');" +
          "tbody.innerHTML=data.defects.map(function(d){" +
            "var sev=d.severity.toLowerCase();" +
            "return '<tr data-sev=\"'+d.severity+'\" data-type=\"'+d.type+'\" data-file=\"'+d.file+'\">'+" +
              "'<td><span class=\"badge '+sev+'\">'+d.severity+'</span></td>'+" +
              "'<td class=\"rule-id\">'+d.ruleId+'</td>'+" +
              "'<td>'+d.type+'</td>'+" +
              "'<td class=\"file-cell\">'+d.file+'</td>'+" +
              "'<td style=\"text-align:center;font-family:monospace\">'+d.line+'</td>'+" +
              "'<td class=\"msg-cell\">'+esc(d.message)+'</td>'+" +
              "'<td class=\"sug-cell\">'+esc(d.suggestion)+'</td>'+" +
            "'</tr>';" +
          "}).join('');" +
          "filterTable();" +
        "}" +

        "function esc(s){" +
          "return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');" +
        "}" +

        "function filterTable(){" +
          "var search=document.getElementById('searchBox').value.toLowerCase();" +
          "var sev=document.getElementById('sevFilter').value;" +
          "var type=document.getElementById('typeFilter').value;" +
          "var file=document.getElementById('fileFilter').value;" +
          "var rows=document.querySelectorAll('#tableBody tr');" +
          "var count=0;" +
          "rows.forEach(function(r){" +
            "var ok=(!search||r.textContent.toLowerCase().includes(search))" +
              "&&(!sev||r.dataset.sev===sev)" +
              "&&(!type||r.dataset.type===type)" +
              "&&(!file||r.dataset.file===file);" +
            "r.style.display=ok?'':'none';if(ok)count++;" +
          "});" +
          "document.getElementById('resultCount').textContent=count+' result'+(count!==1?'s':'');" +
        "}" +

        "function sortCol(i){" +
          "sortDirs[i]=!sortDirs[i];" +
          "var rows=Array.from(document.querySelectorAll('#tableBody tr'));" +
          "rows.sort(function(a,b){" +
            "var av=a.cells[i].textContent.trim();" +
            "var bv=b.cells[i].textContent.trim();" +
            "var n=Number(av)-Number(bv);" +
            "if(!isNaN(n))return sortDirs[i]?n:-n;" +
            "return sortDirs[i]?av.localeCompare(bv):bv.localeCompare(av);" +
          "});" +
          "rows.forEach(function(r){document.getElementById('tableBody').appendChild(r);});" +
        "}";
    }
}
