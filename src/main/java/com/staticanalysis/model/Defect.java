package com.staticanalysis.model;

public class Defect {

    public enum Category {
        CODE_SMELL, DEAD_CODE, SECURITY, METRICS, NAMING, DUPLICATION
    }

    private String type;
    private String message;
    private String severity;
    private String fileName;
    private int lineNumber;
    private String ruleId;
    private Category category;
    private String suggestion;

    public Defect(String type, String message, String severity, String fileName, int lineNumber,
                  String ruleId, Category category, String suggestion) {
        this.type = type;
        this.message = message;
        this.severity = severity;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.ruleId = ruleId;
        this.category = category;
        this.suggestion = suggestion;
    }

    public Defect(String type, String message, String severity, String fileName, int lineNumber) {
        this(type, message, severity, fileName, lineNumber, null, inferCategory(type), null);
    }

    private static Category inferCategory(String type) {
        if (type == null) return Category.CODE_SMELL;
        switch (type) {
            case "Security": return Category.SECURITY;
            case "Dead Code": return Category.DEAD_CODE;
            case "Metrics": return Category.METRICS;
            case "Naming": return Category.NAMING;
            case "Duplication": return Category.DUPLICATION;
            default: return Category.CODE_SMELL;
        }
    }

    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getSeverity() { return severity; }
    public String getFileName() { return fileName; }
    public int getLineNumber() { return lineNumber; }
    public String getRuleId() { return ruleId; }
    public Category getCategory() { return category; }
    public String getSuggestion() { return suggestion; }

    public String getDeduplicationKey() {
        return fileName + ":" + lineNumber + ":" + type + ":" + ruleId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ").append(severity).append(" ] ");
        if (ruleId != null) sb.append("[").append(ruleId).append("] ");
        sb.append(type).append(" | ")
          .append(fileName).append(" | Line ")
          .append(lineNumber).append(" -> ")
          .append(message);
        if (suggestion != null) {
            sb.append("\n   Fix: ").append(suggestion);
        }
        return sb.toString();
    }
}
