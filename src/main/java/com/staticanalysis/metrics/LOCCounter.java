package com.staticanalysis.metrics;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.config.AnalysisConfig;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

import java.util.HashMap;
import java.util.Map;

/**
 * Lines of Code (LOC) Counter.
 * Computes: total lines, code lines, comment lines, blank lines,
 * and comment-to-code ratio per file.
 */
public class LOCCounter extends VoidVisitorAdapter<Void> {

    private String fileName;

    // Store metrics per file for use by MaintainabilityIndex
    private static Map<String, FileMetrics> fileMetricsMap = new HashMap<>();

    public LOCCounter(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void visit(CompilationUnit cu, Void arg) {
        super.visit(cu, arg);

        String source = cu.toString();
        String[] lines = source.split("\n");

        int totalLines = lines.length;
        int blankLines = 0;
        int commentLines = 0;
        int codeLines = 0;
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                blankLines++;
                continue;
            }

            if (inBlockComment) {
                commentLines++;
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }

            if (trimmed.startsWith("/*")) {
                commentLines++;
                if (!trimmed.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }

            if (trimmed.startsWith("//")) {
                commentLines++;
                continue;
            }

            codeLines++;
        }

        double commentRatio = codeLines > 0 ? (double) commentLines / codeLines : 0;

        // Store metrics for MaintainabilityIndex
        FileMetrics metrics = new FileMetrics(totalLines, codeLines, commentLines, blankLines, commentRatio);
        fileMetricsMap.put(fileName, metrics);

        AnalysisConfig config = AnalysisConfig.getInstance();

        // Report file size issues
        if (codeLines > config.getMaxFileLOC()) {
            DefectCollector.addDefect(new Defect(
                "Metrics",
                "Large file -> " + fileName + " (" + codeLines + " code lines)",
                "MAJOR", fileName,
                1,
                "MET-003", Defect.Category.METRICS,
                "Consider splitting this file into smaller, focused modules. Max recommended: " + config.getMaxFileLOC() + " lines."
            ));
        }

        // Report low comment ratio
        if (commentRatio < config.getMinCommentRatio() && codeLines > 50) {
            DefectCollector.addDefect(new Defect(
                "Metrics",
                "Low comment-to-code ratio in " + fileName + " (" + String.format("%.1f%%", commentRatio * 100) + ")",
                "MINOR", fileName,
                1,
                "MET-004", Defect.Category.METRICS,
                "Add documentation comments to improve code maintainability. Recommended minimum: " + String.format("%.0f%%", config.getMinCommentRatio() * 100)
            ));
        }
    }

    // Getter for MaintainabilityIndex
    public static Map<String, FileMetrics> getFileMetricsMap() {
        return fileMetricsMap;
    }

    public static void reset() {
        fileMetricsMap.clear();
    }

    public static class FileMetrics {
        public final int totalLines;
        public final int codeLines;
        public final int commentLines;
        public final int blankLines;
        public final double commentRatio;

        public FileMetrics(int totalLines, int codeLines, int commentLines, int blankLines, double commentRatio) {
            this.totalLines = totalLines;
            this.codeLines = codeLines;
            this.commentLines = commentLines;
            this.blankLines = blankLines;
            this.commentRatio = commentRatio;
        }
    }
}
