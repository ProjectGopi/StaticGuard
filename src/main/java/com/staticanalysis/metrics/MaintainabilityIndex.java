package com.staticanalysis.metrics;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.config.AnalysisConfig;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

/**
 * Computes the Maintainability Index (MI) using the Microsoft formula:
 * MI = 171 - 5.2 * ln(HV) - 0.23 * CC - 16.2 * ln(LOC)
 *
 * Simplified: Uses avg cyclomatic complexity and LOC as proxies.
 * Grading: A (>85), B (65-85), C (<65)
 */
public class MaintainabilityIndex extends VoidVisitorAdapter<Void> {

    private String fileName;

    public MaintainabilityIndex(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);

        if (md.getBody().isPresent()) {
            BlockStmt body = md.getBody().get();

            // Calculate LOC for this method
            int loc = body.toString().split("\n").length;
            if (loc < 1) loc = 1;

            // Calculate cyclomatic complexity
            int cc = calculateCyclomaticComplexity(md);

            // Simplified Halstead Volume approximation (based on token count)
            int tokenCount = body.toString().split("\\s+|[;{}()\\[\\],.]").length;
            double halsteadVolume = tokenCount > 0 ? tokenCount * Math.log(tokenCount) / Math.log(2) : 1;
            if (halsteadVolume < 1) halsteadVolume = 1;

            // Compute Maintainability Index
            double mi = 171.0 - 5.2 * Math.log(halsteadVolume) - 0.23 * cc - 16.2 * Math.log(loc);

            // Clamp to 0-171
            mi = Math.max(0, Math.min(171, mi));

            // Normalize to 0-100
            double normalizedMI = (mi / 171.0) * 100.0;

            int line = md.getBegin().map(p -> p.line).orElse(-1);
            AnalysisConfig config = AnalysisConfig.getInstance();

            String grade;
            String severity;
            if (normalizedMI >= config.getMaintainabilityGradeA()) {
                grade = "A";
                return; // Don't report well-maintained code
            } else if (normalizedMI >= config.getMaintainabilityGradeB()) {
                grade = "B";
                severity = "MINOR";
            } else {
                grade = "C";
                severity = "MAJOR";
            }

            DefectCollector.addDefect(new Defect(
                "Metrics",
                "Low Maintainability Index (MI=" + String.format("%.1f", normalizedMI)
                    + ", Grade=" + grade + ") in method -> " + md.getName(),
                severity, fileName, line,
                "MET-005", Defect.Category.METRICS,
                "Improve maintainability by reducing complexity, method length, and improving documentation."
            ));
        }
    }

    private int calculateCyclomaticComplexity(MethodDeclaration md) {
        int complexity = 1;
        complexity += md.findAll(IfStmt.class).size();
        complexity += md.findAll(ForStmt.class).size();
        complexity += md.findAll(ForEachStmt.class).size();
        complexity += md.findAll(WhileStmt.class).size();
        complexity += md.findAll(DoStmt.class).size();
        complexity += md.findAll(SwitchEntry.class).size();
        complexity += md.findAll(CatchClause.class).size();

        for (BinaryExpr expr : md.findAll(BinaryExpr.class)) {
            if (expr.getOperator() == BinaryExpr.Operator.AND ||
                expr.getOperator() == BinaryExpr.Operator.OR) {
                complexity++;
            }
        }
        return complexity;
    }
}
