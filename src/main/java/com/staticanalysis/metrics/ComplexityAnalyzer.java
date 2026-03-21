package com.staticanalysis.metrics;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.config.AnalysisConfig;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

public class ComplexityAnalyzer extends VoidVisitorAdapter<Void> {

    private String fileName;

    public ComplexityAnalyzer(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);

        int complexity = 1; // base complexity

        // Count if statements
        complexity += md.findAll(IfStmt.class).size();

        // Count for loops
        complexity += md.findAll(ForStmt.class).size();

        // Count for-each loops
        complexity += md.findAll(ForEachStmt.class).size();

        // Count while loops
        complexity += md.findAll(WhileStmt.class).size();

        // Count do-while loops
        complexity += md.findAll(DoStmt.class).size();

        // Count switch cases (non-default)
        complexity += md.findAll(SwitchEntry.class).size();

        // Count catch blocks
        complexity += md.findAll(CatchClause.class).size();

        // Count ternary operators
        complexity += md.findAll(ConditionalExpr.class).size();

        // Count logical operators (&& and ||)
        for (BinaryExpr expr : md.findAll(BinaryExpr.class)) {
            if (expr.getOperator() == BinaryExpr.Operator.AND ||
                expr.getOperator() == BinaryExpr.Operator.OR) {
                complexity++;
            }
        }

        int line = md.getBegin().map(p -> p.line).orElse(-1);
        AnalysisConfig config = AnalysisConfig.getInstance();

        // Generate severity-based defects
        if (complexity > config.getComplexityCritical()) {
            DefectCollector.addDefect(new Defect(
                "Metrics",
                "Very high cyclomatic complexity (" + complexity + ") in method -> " + md.getName(),
                "CRITICAL",
                fileName,
                line,
                "MET-001",
                Defect.Category.METRICS,
                "Refactor this method into smaller, focused sub-methods. Complexity: " + complexity + " (threshold: " + config.getComplexityCritical() + ")"
            ));
        } else if (complexity > config.getComplexityMajor()) {
            DefectCollector.addDefect(new Defect(
                "Metrics",
                "High cyclomatic complexity (" + complexity + ") in method -> " + md.getName(),
                "MAJOR",
                fileName,
                line,
                "MET-001",
                Defect.Category.METRICS,
                "Consider breaking this method into smaller methods. Complexity: " + complexity + " (threshold: " + config.getComplexityMajor() + ")"
            ));
        } else if (complexity > config.getComplexityMinor()) {
            DefectCollector.addDefect(new Defect(
                "Metrics",
                "Moderate cyclomatic complexity (" + complexity + ") in method -> " + md.getName(),
                "MINOR",
                fileName,
                line,
                "MET-001",
                Defect.Category.METRICS,
                "Method is becoming complex. Complexity: " + complexity + " (threshold: " + config.getComplexityMinor() + ")"
            ));
        }
    }
}
