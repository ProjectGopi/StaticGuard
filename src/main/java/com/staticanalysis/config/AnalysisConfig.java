package com.staticanalysis.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AnalysisConfig {

    // Code Smell Thresholds
    private int maxParameters = 3;
    private int maxMethodLines = 30;
    private int maxClassMethods = 10;
    private int maxNestingDepth = 3;
    private int maxClassFields = 15;

    // Complexity Thresholds
    private int complexityMinor = 10;
    private int complexityMajor = 20;
    private int complexityCritical = 50;
    private int cognitiveComplexityThreshold = 15;

    // LOC Thresholds
    private int maxFileLOC = 500;
    private double minCommentRatio = 0.1;

    // Maintainability Index Thresholds
    private int maintainabilityGradeA = 85;
    private int maintainabilityGradeB = 65;

    // Feature Toggles
    private boolean enableCodeSmells = true;
    private boolean enableDeadCode = true;
    private boolean enableSecurity = true;
    private boolean enableMetrics = true;
    private boolean enableNaming = true;
    private boolean enableDuplication = true;

    // Duplicate Code
    private int minDuplicateTokens = 50;

    // Output settings
    private String outputDirectory = ".";
    private String jsonReportName = "report.json";
    private String htmlReportName = "report.html";

    private static volatile AnalysisConfig instance;

    public static AnalysisConfig getInstance() {
        if (instance == null) {
            synchronized (AnalysisConfig.class) {
                if (instance == null) {
                    instance = new AnalysisConfig();
                }
            }
        }
        return instance;
    }

    public static AnalysisConfig loadFromFile(String configPath) {
        File configFile = new File(configPath);
        if (configFile.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                instance = mapper.readValue(configFile, AnalysisConfig.class);
                System.out.println("Configuration loaded from: " + configPath);
            } catch (IOException e) {
                System.out.println("Warning: Could not load config file, using defaults. " + e.getMessage());
                instance = new AnalysisConfig();
            }
        } else {
            instance = new AnalysisConfig();
        }
        return instance;
    }

    public void saveToFile(String configPath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(configPath), this);
        } catch (IOException e) {
            System.out.println("Warning: Could not save config file. " + e.getMessage());
        }
    }

    public static void reset() {
        instance = null;
    }

    // Getters and Setters
    public int getMaxParameters() { return maxParameters; }
    public void setMaxParameters(int v) { this.maxParameters = v; }

    public int getMaxMethodLines() { return maxMethodLines; }
    public void setMaxMethodLines(int v) { this.maxMethodLines = v; }

    public int getMaxClassMethods() { return maxClassMethods; }
    public void setMaxClassMethods(int v) { this.maxClassMethods = v; }

    public int getMaxNestingDepth() { return maxNestingDepth; }
    public void setMaxNestingDepth(int v) { this.maxNestingDepth = v; }

    public int getMaxClassFields() { return maxClassFields; }
    public void setMaxClassFields(int v) { this.maxClassFields = v; }

    public int getComplexityMinor() { return complexityMinor; }
    public void setComplexityMinor(int v) { this.complexityMinor = v; }

    public int getComplexityMajor() { return complexityMajor; }
    public void setComplexityMajor(int v) { this.complexityMajor = v; }

    public int getComplexityCritical() { return complexityCritical; }
    public void setComplexityCritical(int v) { this.complexityCritical = v; }

    public int getCognitiveComplexityThreshold() { return cognitiveComplexityThreshold; }
    public void setCognitiveComplexityThreshold(int v) { this.cognitiveComplexityThreshold = v; }

    public int getMaxFileLOC() { return maxFileLOC; }
    public void setMaxFileLOC(int v) { this.maxFileLOC = v; }

    public double getMinCommentRatio() { return minCommentRatio; }
    public void setMinCommentRatio(double v) { this.minCommentRatio = v; }

    public int getMaintainabilityGradeA() { return maintainabilityGradeA; }
    public void setMaintainabilityGradeA(int v) { this.maintainabilityGradeA = v; }

    public int getMaintainabilityGradeB() { return maintainabilityGradeB; }
    public void setMaintainabilityGradeB(int v) { this.maintainabilityGradeB = v; }

    public boolean isEnableCodeSmells() { return enableCodeSmells; }
    public void setEnableCodeSmells(boolean v) { this.enableCodeSmells = v; }

    public boolean isEnableDeadCode() { return enableDeadCode; }
    public void setEnableDeadCode(boolean v) { this.enableDeadCode = v; }

    public boolean isEnableSecurity() { return enableSecurity; }
    public void setEnableSecurity(boolean v) { this.enableSecurity = v; }

    public boolean isEnableMetrics() { return enableMetrics; }
    public void setEnableMetrics(boolean v) { this.enableMetrics = v; }

    public boolean isEnableNaming() { return enableNaming; }
    public void setEnableNaming(boolean v) { this.enableNaming = v; }

    public boolean isEnableDuplication() { return enableDuplication; }
    public void setEnableDuplication(boolean v) { this.enableDuplication = v; }

    public int getMinDuplicateTokens() { return minDuplicateTokens; }
    public void setMinDuplicateTokens(int v) { this.minDuplicateTokens = v; }

    public String getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(String v) { this.outputDirectory = v; }

    public String getJsonReportName() { return jsonReportName; }
    public void setJsonReportName(String v) { this.jsonReportName = v; }

    public String getHtmlReportName() { return htmlReportName; }
    public void setHtmlReportName(String v) { this.htmlReportName = v; }
}
