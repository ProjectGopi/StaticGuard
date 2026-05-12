package com.staticanalysis;

import java.io.File;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.staticanalysis.analyzer.*;
import com.staticanalysis.config.AnalysisConfig;
import com.staticanalysis.metrics.CognitiveComplexityAnalyzer;
import com.staticanalysis.metrics.ComplexityAnalyzer;
import com.staticanalysis.metrics.LOCCounter;
import com.staticanalysis.metrics.MaintainabilityIndex;
import com.staticanalysis.model.DefectCollector;
import com.staticanalysis.parser.CodeParser;
import com.staticanalysis.parser.FileScanner;
import com.staticanalysis.report.ReportGenerator;
import com.staticanalysis.web.WebServer;

public class Main {

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private static boolean verbose = false;
    private static boolean webMode = false;
    private static String severityFilter = null;
    private static String configPath = null;
    private static String outputDir = null;

    public static void main(String[] args) {

        String projectPath = "test-input";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help":
                case "-h":
                    printHelp();
                    return;
                case "--web":
                case "-w":
                    webMode = true;
                    break;
                case "--path":
                case "-p":
                    if (i + 1 < args.length) projectPath = args[++i];
                    break;
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                case "--severity":
                case "-s":
                    if (i + 1 < args.length) severityFilter = args[++i].toUpperCase();
                    break;
                case "--config":
                case "-c":
                    if (i + 1 < args.length) configPath = args[++i];
                    break;
                case "--output":
                case "-o":
                    if (i + 1 < args.length) outputDir = args[++i];
                    break;
                case "--generate-config":
                    AnalysisConfig.getInstance().saveToFile("staticguard-config.json");
                    System.out.println(GREEN + "Default configuration written to staticguard-config.json" + RESET);
                    return;
                default:
                    if (!args[i].startsWith("-")) {
                        projectPath = args[i];
                    }
                    break;
            }
        }

        // Web UI mode — start the embedded HTTP server and block
        if (webMode) {
            try {
                new WebServer().start();
                // Keep the main thread alive until Ctrl+C
                Thread.currentThread().join();
            } catch (Exception e) {
                System.out.println(RED + "Failed to start web server: " + e.getMessage() + RESET);
                System.exit(1);
            }
            return;
        }

        AnalysisConfig config;
        if (configPath != null) {
            config = AnalysisConfig.loadFromFile(configPath);
        } else {
            config = AnalysisConfig.getInstance();
        }

        if (outputDir != null) {
            config.setOutputDirectory(outputDir);
        }

        printBanner();

        DefectCollector.reset();
        DuplicateCodeDetector.reset();
        LOCCounter.reset();

        CodeParser parser = new CodeParser();
        List<File> javaFiles = FileScanner.scanJavaFiles(projectPath);

        if (javaFiles.isEmpty()) {
            System.out.println(RED + "No Java files found in: " + projectPath + RESET);
            System.exit(2);
        }

        System.out.println(CYAN + "Found " + javaFiles.size() + " Java file(s) to analyze in: " + projectPath + RESET);
        System.out.println();

        int fileCount = 0;
        for (File file : javaFiles) {
            fileCount++;
            String fileName = file.getName();

            System.out.print(BLUE + "[" + fileCount + "/" + javaFiles.size() + "] " + RESET);
            System.out.println("Analyzing: " + BOLD + fileName + RESET);

            CompilationUnit cu = parser.parseFile(file.getAbsolutePath());

            if (cu != null) {
                if (verbose) {
                    new ClassAnalyzer().visit(cu, null);
                    new MethodAnalyzer().visit(cu, null);
                }

                if (config.isEnableCodeSmells()) {
                    new SmellDetector(fileName).visit(cu, null);
                }

                if (config.isEnableDeadCode()) {
                    new DeadCodeDetector(fileName).visit(cu, null);
                }

                if (config.isEnableSecurity()) {
                    new SecurityAnalyzer(fileName).visit(cu, null);
                }

                if (config.isEnableNaming()) {
                    new NamingConventionAnalyzer(fileName).visit(cu, null);
                }

                if (config.isEnableDuplication()) {
                    new DuplicateCodeDetector(fileName).visit(cu, null);
                }

                if (config.isEnableMetrics()) {
                    new ComplexityAnalyzer(fileName).visit(cu, null);
                    new CognitiveComplexityAnalyzer(fileName).visit(cu, null);
                    new LOCCounter(fileName).visit(cu, null);
                    new MaintainabilityIndex(fileName).visit(cu, null);
                }
            } else {
                System.out.println(RED + "  WARNING: Failed to parse: " + fileName + RESET);
            }
        }

        System.out.println();

        DefectCollector.printReport();
        DefectCollector.printSummary();

        String outDir = config.getOutputDirectory();
        ReportGenerator.generateJsonReport(DefectCollector.getDefects(), outDir + "/" + config.getJsonReportName());
        ReportGenerator.generateHtmlReport(DefectCollector.getDefects(), outDir + "/" + config.getHtmlReportName());

        int criticalCount = DefectCollector.getCriticalCount();
        int majorCount = DefectCollector.getMajorCount();

        if (criticalCount > 0) {
            System.out.println(RED + BOLD + "\nAnalysis complete: " + criticalCount + " CRITICAL issue(s) found!" + RESET);
            System.exit(1);
        } else if (majorCount > 0) {
            System.out.println(YELLOW + BOLD + "\nAnalysis complete: " + majorCount + " MAJOR issue(s) found." + RESET);
            System.exit(0);
        } else {
            System.out.println(GREEN + BOLD + "\nAnalysis complete: No critical or major issues!" + RESET);
            System.exit(0);
        }
    }

    private static void printBanner() {
        System.out.println();
        System.out.println(CYAN + BOLD + "+--------------------------------------------------+" + RESET);
        System.out.println(CYAN + BOLD + "|     StaticGuard v2.0                             |" + RESET);
        System.out.println(CYAN + BOLD + "|     AST-Based Static Code Analysis Framework     |" + RESET);
        System.out.println(CYAN + BOLD + "+--------------------------------------------------+" + RESET);
        System.out.println();
    }

    private static void printHelp() {
        printBanner();
        System.out.println(BOLD + "Usage:" + RESET + " java -jar StaticGuard.jar [options] [path]");
        System.out.println();
        System.out.println(BOLD + "Options:" + RESET);
        System.out.println("  -p, --path <dir>        Path to analyze (default: test-input)");
        System.out.println("  -o, --output <dir>      Output directory for reports (default: .)");
        System.out.println("  -s, --severity <level>  Filter by severity: CRITICAL, MAJOR, MINOR");
        System.out.println("  -c, --config <file>     Path to configuration JSON file");
        System.out.println("  -v, --verbose           Enable verbose output");
        System.out.println("  -w, --web               Start the Web UI server at http://localhost:8080");
        System.out.println("  --generate-config       Generate default config file");
        System.out.println("  -h, --help              Show this help message");
        System.out.println();
        System.out.println(BOLD + "Exit Codes:" + RESET);
        System.out.println("  0 - No critical issues found");
        System.out.println("  1 - Critical issues found");
        System.out.println("  2 - Analysis error (no files found, parse failure)");
        System.out.println();
        System.out.println(BOLD + "Analysis Categories:" + RESET);
        System.out.println("  * Code Smells     - Large class, long method, deep nesting, god class, etc.");
        System.out.println("  * Dead Code       - Unused variables, imports, private methods/fields");
        System.out.println("  * Security        - SQL injection, hardcoded secrets, weak crypto, etc.");
        System.out.println("  * Naming          - PascalCase, camelCase, UPPER_SNAKE_CASE conventions");
        System.out.println("  * Duplication     - Clone detection across methods and files");
        System.out.println("  * Metrics         - Cyclomatic/Cognitive complexity, LOC, Maintainability Index");
    }
}
