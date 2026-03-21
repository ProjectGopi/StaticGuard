package com.staticanalysis.report;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

public class ReportGenerator {

    public static void generateJsonReport(List<Defect> defects, String outputPath) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            mapper.writeValue(new File(outputPath), defects);
            System.out.println("JSON report generated: " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void generateJsonReport(List<Defect> defects) {
        generateJsonReport(defects, "report.json");
    }

    public static void generateHtmlReport(List<Defect> defects, String outputPath) {
        try {
            PrintWriter w = new PrintWriter(new File(outputPath));

            int critical = DefectCollector.getCriticalCount();
            int major = DefectCollector.getMajorCount();
            int minor = DefectCollector.getMinorCount();
            int total = defects.size();

            Map<String, Long> byType = defects.stream()
                .collect(Collectors.groupingBy(Defect::getType, Collectors.counting()));
            Map<String, Long> byFile = defects.stream()
                .collect(Collectors.groupingBy(Defect::getFileName, Collectors.counting()));

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            w.println("<!DOCTYPE html>");
            w.println("<html lang='en'>");
            w.println("<head>");
            w.println("<meta charset='UTF-8'>");
            w.println("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
            w.println("<title>StaticGuard Analysis Report</title>");
            w.println("<style>");
            writeCSS(w);
            w.println("</style>");
            w.println("</head>");
            w.println("<body>");
            w.println("<div class='container'>");

            // Header
            w.println("<header class='header'>");
            w.println("<div class='header-content'>");
            w.println("<div class='logo-section'>");
            w.println("<h1>StaticGuard</h1>");
            w.println("<p class='subtitle'>AST-Based Static Code Analysis Report</p>");
            w.println("</div>");
            w.println("<div class='header-meta'>");
            w.println("<span class='timestamp'>Generated: " + timestamp + "</span>");
            w.println("<button class='btn-toggle' onclick='toggleDarkMode()'>Toggle Theme</button>");
            w.println("<button class='btn-export' onclick='exportCSV()'>Export CSV</button>");
            w.println("</div>");
            w.println("</div>");
            w.println("</header>");

            // Summary Cards
            w.println("<section class='summary-cards'>");
            writeSummaryCard(w, "Total Issues", String.valueOf(total), "total");
            writeSummaryCard(w, "Critical", String.valueOf(critical), "critical");
            writeSummaryCard(w, "Major", String.valueOf(major), "major");
            writeSummaryCard(w, "Minor", String.valueOf(minor), "minor");
            w.println("</section>");

            // Charts Section
            w.println("<section class='charts-section'>");

            w.println("<div class='chart-card'>");
            w.println("<h3>Severity Distribution</h3>");
            writeSeverityPieChart(w, critical, major, minor);
            w.println("</div>");

            w.println("<div class='chart-card'>");
            w.println("<h3>Issues by File</h3>");
            writeFileBarChart(w, byFile);
            w.println("</div>");

            w.println("<div class='chart-card'>");
            w.println("<h3>Issues by Category</h3>");
            writeCategoryChart(w, byType);
            w.println("</div>");

            w.println("</section>");

            // Filters
            w.println("<section class='filters-section'>");
            w.println("<div class='filter-bar'>");
            w.println("<input type='text' id='searchBox' class='search-input' placeholder='Search defects...' onkeyup='filterTable()'>");
            w.println("<select id='severityFilter' class='filter-select' onchange='filterTable()'>");
            w.println("<option value=''>All Severities</option>");
            w.println("<option value='CRITICAL'>Critical</option>");
            w.println("<option value='MAJOR'>Major</option>");
            w.println("<option value='MINOR'>Minor</option>");
            w.println("</select>");
            w.println("<select id='typeFilter' class='filter-select' onchange='filterTable()'>");
            w.println("<option value=''>All Types</option>");
            for (String type : byType.keySet()) {
                w.println("<option value='" + type + "'>" + type + "</option>");
            }
            w.println("</select>");
            w.println("<select id='fileFilter' class='filter-select' onchange='filterTable()'>");
            w.println("<option value=''>All Files</option>");
            for (String file : byFile.keySet()) {
                w.println("<option value='" + file + "'>" + file + "</option>");
            }
            w.println("</select>");
            w.println("<span id='resultCount' class='result-count'>" + total + " results</span>");
            w.println("</div>");
            w.println("</section>");

            // Data Table
            w.println("<section class='table-section'>");
            w.println("<table id='defectTable'>");
            w.println("<thead>");
            w.println("<tr>");
            w.println("<th onclick='sortTable(0)'>Severity</th>");
            w.println("<th onclick='sortTable(1)'>Rule ID</th>");
            w.println("<th onclick='sortTable(2)'>Type</th>");
            w.println("<th onclick='sortTable(3)'>File</th>");
            w.println("<th onclick='sortTable(4)'>Line</th>");
            w.println("<th onclick='sortTable(5)'>Message</th>");
            w.println("<th>Suggestion</th>");
            w.println("</tr>");
            w.println("</thead>");
            w.println("<tbody>");

            for (Defect d : defects) {
                String severityClass = d.getSeverity().toLowerCase();
                String ruleId = d.getRuleId() != null ? d.getRuleId() : "-";
                String suggestion = d.getSuggestion() != null ? escapeHtml(d.getSuggestion()) : "-";

                w.println("<tr class='severity-" + severityClass + "' data-severity='" + d.getSeverity()
                    + "' data-type='" + d.getType() + "' data-file='" + d.getFileName() + "'>");
                w.println("<td><span class='badge badge-" + severityClass + "'>" + d.getSeverity() + "</span></td>");
                w.println("<td class='rule-id'>" + ruleId + "</td>");
                w.println("<td>" + d.getType() + "</td>");
                w.println("<td class='file-name'>" + d.getFileName() + "</td>");
                w.println("<td class='line-num'>" + d.getLineNumber() + "</td>");
                w.println("<td>" + escapeHtml(d.getMessage()) + "</td>");
                w.println("<td class='suggestion'>" + suggestion + "</td>");
                w.println("</tr>");
            }

            w.println("</tbody>");
            w.println("</table>");
            w.println("</section>");

            // Footer
            w.println("<footer class='footer'>");
            w.println("<p>Generated by <strong>StaticGuard v2.0</strong> - AST-Based Static Code Analysis Framework</p>");
            w.println("</footer>");

            w.println("</div>");

            // JavaScript
            w.println("<script>");
            writeJavaScript(w);
            w.println("</script>");

            w.println("</body>");
            w.println("</html>");

            w.close();
            System.out.println("HTML report generated: " + outputPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void generateHtmlReport(List<Defect> defects) {
        generateHtmlReport(defects, "report.html");
    }

    private static void writeSummaryCard(PrintWriter w, String title, String value, String type) {
        w.println("<div class='card card-" + type + "'>");
        w.println("<div class='card-value'>" + value + "</div>");
        w.println("<div class='card-title'>" + title + "</div>");
        w.println("</div>");
    }

    private static void writeSeverityPieChart(PrintWriter w, int critical, int major, int minor) {
        int total = critical + major + minor;
        if (total == 0) {
            w.println("<p class='no-data'>No issues found!</p>");
            return;
        }

        double critPct = (double) critical / total * 100;
        double majPct = (double) major / total * 100;
        double minPct = (double) minor / total * 100;

        w.println("<svg viewBox='0 0 200 200' class='pie-chart'>");
        double startAngle = 0;
        if (critical > 0) {
            writeArc(w, startAngle, critPct, "#ef4444", "Critical: " + critical);
            startAngle += critPct;
        }
        if (major > 0) {
            writeArc(w, startAngle, majPct, "#f59e0b", "Major: " + major);
            startAngle += majPct;
        }
        if (minor > 0) {
            writeArc(w, startAngle, minPct, "#eab308", "Minor: " + minor);
        }
        w.println("<circle cx='100' cy='100' r='50' fill='var(--bg-primary)'/>");
        w.println("<text x='100' y='105' text-anchor='middle' class='chart-center-text'>" + total + "</text>");
        w.println("</svg>");

        w.println("<div class='legend'>");
        if (critical > 0) w.println("<span class='legend-item'><span class='dot dot-critical'></span> Critical (" + critical + ")</span>");
        if (major > 0) w.println("<span class='legend-item'><span class='dot dot-major'></span> Major (" + major + ")</span>");
        if (minor > 0) w.println("<span class='legend-item'><span class='dot dot-minor'></span> Minor (" + minor + ")</span>");
        w.println("</div>");
    }

    private static void writeArc(PrintWriter w, double startPct, double pct, String color, String title) {
        double startAngle = startPct / 100 * 360 - 90;
        double endAngle = (startPct + pct) / 100 * 360 - 90;

        double startRad = Math.toRadians(startAngle);
        double endRad = Math.toRadians(endAngle);

        double x1 = 100 + 80 * Math.cos(startRad);
        double y1 = 100 + 80 * Math.sin(startRad);
        double x2 = 100 + 80 * Math.cos(endRad);
        double y2 = 100 + 80 * Math.sin(endRad);

        int largeArcFlag = pct > 50 ? 1 : 0;

        w.println("<path d='M 100 100 L " + x1 + " " + y1
            + " A 80 80 0 " + largeArcFlag + " 1 " + x2 + " " + y2
            + " Z' fill='" + color + "'><title>" + title + "</title></path>");
    }

    private static void writeFileBarChart(PrintWriter w, Map<String, Long> byFile) {
        if (byFile.isEmpty()) return;

        long maxCount = byFile.values().stream().mapToLong(Long::longValue).max().orElse(1);

        w.println("<div class='bar-chart'>");
        for (Map.Entry<String, Long> entry : byFile.entrySet()) {
            double widthPct = (double) entry.getValue() / maxCount * 100;
            w.println("<div class='bar-row'>");
            w.println("<span class='bar-label'>" + entry.getKey() + "</span>");
            w.println("<div class='bar-track'>");
            w.println("<div class='bar-fill' style='width:" + widthPct + "%'>" + entry.getValue() + "</div>");
            w.println("</div>");
            w.println("</div>");
        }
        w.println("</div>");
    }

    private static void writeCategoryChart(PrintWriter w, Map<String, Long> byType) {
        if (byType.isEmpty()) return;

        String[] colors = {"#6366f1", "#ec4899", "#14b8a6", "#f59e0b", "#8b5cf6", "#06b6d4"};
        int colorIndex = 0;

        long maxCount = byType.values().stream().mapToLong(Long::longValue).max().orElse(1);

        w.println("<div class='bar-chart'>");
        for (Map.Entry<String, Long> entry : byType.entrySet()) {
            double widthPct = (double) entry.getValue() / maxCount * 100;
            String color = colors[colorIndex % colors.length];
            colorIndex++;
            w.println("<div class='bar-row'>");
            w.println("<span class='bar-label'>" + entry.getKey() + "</span>");
            w.println("<div class='bar-track'>");
            w.println("<div class='bar-fill' style='width:" + widthPct + "%; background:" + color + "'>" + entry.getValue() + "</div>");
            w.println("</div>");
            w.println("</div>");
        }
        w.println("</div>");
    }

    private static void writeCSS(PrintWriter w) {
        w.println(":root {");
        w.println("  --bg-primary: #0f172a;");
        w.println("  --bg-secondary: #1e293b;");
        w.println("  --bg-card: #1e293b;");
        w.println("  --text-primary: #f1f5f9;");
        w.println("  --text-secondary: #94a3b8;");
        w.println("  --border: #334155;");
        w.println("  --accent: #6366f1;");
        w.println("  --critical: #ef4444;");
        w.println("  --major: #f59e0b;");
        w.println("  --minor: #eab308;");
        w.println("  --success: #22c55e;");
        w.println("}");
        w.println(".light-mode {");
        w.println("  --bg-primary: #f8fafc;");
        w.println("  --bg-secondary: #ffffff;");
        w.println("  --bg-card: #ffffff;");
        w.println("  --text-primary: #1e293b;");
        w.println("  --text-secondary: #64748b;");
        w.println("  --border: #e2e8f0;");
        w.println("}");
        w.println("* { margin: 0; padding: 0; box-sizing: border-box; }");
        w.println("body { font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; background: var(--bg-primary); color: var(--text-primary); line-height: 1.6; }");
        w.println(".container { max-width: 1400px; margin: 0 auto; padding: 24px; }");

        w.println(".header { background: linear-gradient(135deg, #6366f1, #8b5cf6, #a855f7); border-radius: 16px; padding: 32px; margin-bottom: 24px; }");
        w.println(".header-content { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; }");
        w.println(".header h1 { font-size: 2rem; color: white; }");
        w.println(".subtitle { color: rgba(255,255,255,0.8); font-size: 1rem; margin-top: 4px; }");
        w.println(".header-meta { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }");
        w.println(".timestamp { color: rgba(255,255,255,0.7); font-size: 0.85rem; }");
        w.println(".btn-toggle, .btn-export { padding: 8px 16px; border: 2px solid rgba(255,255,255,0.3); border-radius: 8px; background: rgba(255,255,255,0.1); color: white; cursor: pointer; font-size: 0.85rem; transition: all 0.2s; }");
        w.println(".btn-toggle:hover, .btn-export:hover { background: rgba(255,255,255,0.2); border-color: rgba(255,255,255,0.5); }");

        w.println(".summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 24px; }");
        w.println(".card { background: var(--bg-card); border-radius: 12px; padding: 24px; text-align: center; border: 1px solid var(--border); transition: transform 0.2s, box-shadow 0.2s; }");
        w.println(".card:hover { transform: translateY(-2px); box-shadow: 0 8px 25px rgba(0,0,0,0.3); }");
        w.println(".card-value { font-size: 2.5rem; font-weight: 700; }");
        w.println(".card-title { color: var(--text-secondary); font-size: 0.9rem; margin-top: 4px; text-transform: uppercase; letter-spacing: 1px; }");
        w.println(".card-total .card-value { color: var(--accent); }");
        w.println(".card-critical .card-value { color: var(--critical); }");
        w.println(".card-major .card-value { color: var(--major); }");
        w.println(".card-minor .card-value { color: var(--minor); }");

        w.println(".charts-section { display: grid; grid-template-columns: repeat(auto-fit, minmax(350px, 1fr)); gap: 16px; margin-bottom: 24px; }");
        w.println(".chart-card { background: var(--bg-card); border-radius: 12px; padding: 24px; border: 1px solid var(--border); }");
        w.println(".chart-card h3 { margin-bottom: 16px; color: var(--text-primary); }");
        w.println(".pie-chart { width: 200px; height: 200px; margin: 0 auto; display: block; }");
        w.println(".chart-center-text { font-size: 1.5rem; font-weight: 700; fill: var(--text-primary); }");
        w.println(".legend { display: flex; justify-content: center; gap: 16px; margin-top: 16px; flex-wrap: wrap; }");
        w.println(".legend-item { display: flex; align-items: center; gap: 6px; font-size: 0.85rem; color: var(--text-secondary); }");
        w.println(".dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }");
        w.println(".dot-critical { background: var(--critical); }");
        w.println(".dot-major { background: var(--major); }");
        w.println(".dot-minor { background: var(--minor); }");
        w.println(".no-data { text-align: center; padding: 40px; color: var(--success); font-size: 1.2rem; }");

        w.println(".bar-chart { display: flex; flex-direction: column; gap: 10px; }");
        w.println(".bar-row { display: flex; align-items: center; gap: 12px; }");
        w.println(".bar-label { min-width: 160px; font-size: 0.85rem; color: var(--text-secondary); text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }");
        w.println(".bar-track { flex: 1; background: var(--bg-primary); border-radius: 6px; height: 28px; overflow: hidden; }");
        w.println(".bar-fill { height: 100%; background: linear-gradient(90deg, #6366f1, #8b5cf6); border-radius: 6px; display: flex; align-items: center; justify-content: flex-end; padding-right: 8px; font-size: 0.8rem; font-weight: 600; color: white; min-width: 30px; transition: width 0.5s ease; }");

        w.println(".filters-section { margin-bottom: 16px; }");
        w.println(".filter-bar { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }");
        w.println(".search-input { flex: 1; min-width: 200px; padding: 10px 16px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-secondary); color: var(--text-primary); font-size: 0.9rem; outline: none; }");
        w.println(".search-input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px rgba(99,102,241,0.2); }");
        w.println(".filter-select { padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-secondary); color: var(--text-primary); font-size: 0.9rem; cursor: pointer; }");
        w.println(".result-count { color: var(--text-secondary); font-size: 0.85rem; margin-left: auto; }");

        w.println(".table-section { background: var(--bg-card); border-radius: 12px; border: 1px solid var(--border); overflow: hidden; }");
        w.println("table { width: 100%; border-collapse: collapse; }");
        w.println("thead th { background: var(--bg-secondary); padding: 14px 16px; text-align: left; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 1px; color: var(--text-secondary); cursor: pointer; border-bottom: 2px solid var(--border); user-select: none; }");
        w.println("thead th:hover { color: var(--accent); }");
        w.println("tbody td { padding: 12px 16px; border-bottom: 1px solid var(--border); font-size: 0.9rem; }");
        w.println("tbody tr { transition: background 0.15s; }");
        w.println("tbody tr:hover { background: rgba(99,102,241,0.05); }");
        w.println(".badge { padding: 4px 10px; border-radius: 20px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }");
        w.println(".badge-critical { background: rgba(239,68,68,0.15); color: #ef4444; }");
        w.println(".badge-major { background: rgba(245,158,11,0.15); color: #f59e0b; }");
        w.println(".badge-minor { background: rgba(234,179,8,0.15); color: #eab308; }");
        w.println(".rule-id { font-family: monospace; color: var(--accent); font-weight: 600; }");
        w.println(".file-name { font-family: monospace; color: var(--text-secondary); }");
        w.println(".line-num { font-family: monospace; text-align: center; }");
        w.println(".suggestion { font-size: 0.85rem; color: var(--text-secondary); font-style: italic; }");

        w.println(".footer { text-align: center; padding: 24px; margin-top: 24px; color: var(--text-secondary); font-size: 0.85rem; }");

        w.println("@media (max-width: 768px) {");
        w.println("  .header-content { flex-direction: column; text-align: center; }");
        w.println("  .bar-label { min-width: 80px; }");
        w.println("  .table-section { overflow-x: auto; }");
        w.println("}");
    }

    private static void writeJavaScript(PrintWriter w) {
        w.println("function toggleDarkMode() {");
        w.println("  document.body.classList.toggle('light-mode');");
        w.println("  var btn = document.querySelector('.btn-toggle');");
        w.println("  btn.textContent = document.body.classList.contains('light-mode') ? 'Dark Mode' : 'Light Mode';");
        w.println("}");

        w.println("function filterTable() {");
        w.println("  var search = document.getElementById('searchBox').value.toLowerCase();");
        w.println("  var severity = document.getElementById('severityFilter').value;");
        w.println("  var type = document.getElementById('typeFilter').value;");
        w.println("  var file = document.getElementById('fileFilter').value;");
        w.println("  var rows = document.querySelectorAll('#defectTable tbody tr');");
        w.println("  var count = 0;");
        w.println("  rows.forEach(function(row) {");
        w.println("    var text = row.textContent.toLowerCase();");
        w.println("    var rowSeverity = row.getAttribute('data-severity');");
        w.println("    var rowType = row.getAttribute('data-type');");
        w.println("    var rowFile = row.getAttribute('data-file');");
        w.println("    var matchSearch = !search || text.indexOf(search) >= 0;");
        w.println("    var matchSeverity = !severity || rowSeverity === severity;");
        w.println("    var matchType = !type || rowType === type;");
        w.println("    var matchFile = !file || rowFile === file;");
        w.println("    var show = matchSearch && matchSeverity && matchType && matchFile;");
        w.println("    row.style.display = show ? '' : 'none';");
        w.println("    if (show) count++;");
        w.println("  });");
        w.println("  document.getElementById('resultCount').textContent = count + ' results';");
        w.println("}");

        w.println("var sortDirections = {};");
        w.println("function sortTable(colIndex) {");
        w.println("  var table = document.getElementById('defectTable');");
        w.println("  var rows = Array.prototype.slice.call(table.querySelectorAll('tbody tr'));");
        w.println("  sortDirections[colIndex] = !sortDirections[colIndex];");
        w.println("  var dir = sortDirections[colIndex] ? 1 : -1;");
        w.println("  rows.sort(function(a, b) {");
        w.println("    var aVal = a.cells[colIndex].textContent.trim();");
        w.println("    var bVal = b.cells[colIndex].textContent.trim();");
        w.println("    if (!isNaN(aVal) && !isNaN(bVal)) return (Number(aVal) - Number(bVal)) * dir;");
        w.println("    return aVal.localeCompare(bVal) * dir;");
        w.println("  });");
        w.println("  var tbody = table.querySelector('tbody');");
        w.println("  rows.forEach(function(row) { tbody.appendChild(row); });");
        w.println("}");

        w.println("function exportCSV() {");
        w.println("  var table = document.getElementById('defectTable');");
        w.println("  var rows = table.querySelectorAll('tr');");
        w.println("  var csv = [];");
        w.println("  rows.forEach(function(row) {");
        w.println("    if (row.style.display === 'none') return;");
        w.println("    var cols = row.querySelectorAll('th, td');");
        w.println("    var rowData = Array.prototype.slice.call(cols).map(function(col) { return '\"' + col.textContent.trim().replace(/\"/g, '\"\"') + '\"'; });");
        w.println("    csv.push(rowData.join(','));");
        w.println("  });");
        w.println("  var blob = new Blob([csv.join('\\n')], { type: 'text/csv' });");
        w.println("  var url = URL.createObjectURL(blob);");
        w.println("  var a = document.createElement('a');");
        w.println("  a.href = url;");
        w.println("  a.download = 'staticguard-report.csv';");
        w.println("  a.click();");
        w.println("  URL.revokeObjectURL(url);");
        w.println("}");
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
