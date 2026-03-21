package com.staticanalysis.metrics;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.config.AnalysisConfig;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

/**
 * Implements SonarQube's Cognitive Complexity metric.
 * Unlike Cyclomatic Complexity, Cognitive Complexity weights nesting depth,
 * making deeply nested code score higher than flat branching.
 */
public class CognitiveComplexityAnalyzer extends VoidVisitorAdapter<Void> {

    private String fileName;

    public CognitiveComplexityAnalyzer(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);

        if (md.getBody().isPresent()) {
            int cognitiveComplexity = calculateCognitiveComplexity(md.getBody().get(), 0);

            int line = md.getBegin().map(p -> p.line).orElse(-1);
            AnalysisConfig config = AnalysisConfig.getInstance();

            if (cognitiveComplexity > config.getCognitiveComplexityThreshold()) {
                String severity = cognitiveComplexity > config.getCognitiveComplexityThreshold() * 3
                    ? "CRITICAL"
                    : cognitiveComplexity > config.getCognitiveComplexityThreshold() * 2
                        ? "MAJOR"
                        : "MINOR";

                DefectCollector.addDefect(new Defect(
                    "Metrics",
                    "High cognitive complexity (" + cognitiveComplexity + ") in method -> " + md.getName(),
                    severity, fileName, line,
                    "MET-002", Defect.Category.METRICS,
                    "Simplify this method by reducing nesting. Use early returns, extract methods, and flatten conditionals. Threshold: " + config.getCognitiveComplexityThreshold()
                ));
            }
        }
    }

    private int calculateCognitiveComplexity(BlockStmt block, int nestingLevel) {
        int complexity = 0;

        for (Statement stmt : block.getStatements()) {
            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                complexity += 1 + nestingLevel; // increment + nesting penalty

                // Count logical operators in condition
                complexity += countLogicalOperators(ifStmt);

                if (ifStmt.getThenStmt() instanceof BlockStmt) {
                    complexity += calculateCognitiveComplexity((BlockStmt) ifStmt.getThenStmt(), nestingLevel + 1);
                }
                if (ifStmt.getElseStmt().isPresent()) {
                    Statement elseStmt = ifStmt.getElseStmt().get();
                    if (elseStmt instanceof BlockStmt) {
                        complexity += 1; // else is a +1 (no nesting penalty)
                        complexity += calculateCognitiveComplexity((BlockStmt) elseStmt, nestingLevel + 1);
                    } else if (elseStmt instanceof IfStmt) {
                        complexity += 1; // else-if is a +1 (no nesting penalty, treated as flat)
                    }
                }
            } else if (stmt instanceof ForStmt) {
                complexity += 1 + nestingLevel;
                ForStmt forStmt = (ForStmt) stmt;
                if (forStmt.getBody() instanceof BlockStmt) {
                    complexity += calculateCognitiveComplexity((BlockStmt) forStmt.getBody(), nestingLevel + 1);
                }
            } else if (stmt instanceof ForEachStmt) {
                complexity += 1 + nestingLevel;
                ForEachStmt forEachStmt = (ForEachStmt) stmt;
                if (forEachStmt.getBody() instanceof BlockStmt) {
                    complexity += calculateCognitiveComplexity((BlockStmt) forEachStmt.getBody(), nestingLevel + 1);
                }
            } else if (stmt instanceof WhileStmt) {
                complexity += 1 + nestingLevel;
                WhileStmt whileStmt = (WhileStmt) stmt;
                if (whileStmt.getBody() instanceof BlockStmt) {
                    complexity += calculateCognitiveComplexity((BlockStmt) whileStmt.getBody(), nestingLevel + 1);
                }
            } else if (stmt instanceof DoStmt) {
                complexity += 1 + nestingLevel;
                DoStmt doStmt = (DoStmt) stmt;
                if (doStmt.getBody() instanceof BlockStmt) {
                    complexity += calculateCognitiveComplexity((BlockStmt) doStmt.getBody(), nestingLevel + 1);
                }
            } else if (stmt instanceof SwitchStmt) {
                complexity += 1 + nestingLevel;
            } else if (stmt instanceof TryStmt) {
                TryStmt tryStmt = (TryStmt) stmt;
                complexity += calculateCognitiveComplexity(tryStmt.getTryBlock(), nestingLevel + 1);
                for (CatchClause cc : tryStmt.getCatchClauses()) {
                    complexity += 1 + nestingLevel;
                    complexity += calculateCognitiveComplexity(cc.getBody(), nestingLevel + 1);
                }
            } else if (stmt instanceof BreakStmt || stmt instanceof ContinueStmt) {
                // break/continue to label adds complexity
                if (stmt instanceof BreakStmt && ((BreakStmt) stmt).getLabel().isPresent()) {
                    complexity += 1;
                }
                if (stmt instanceof ContinueStmt && ((ContinueStmt) stmt).getLabel().isPresent()) {
                    complexity += 1;
                }
            }

            // Ternary operators
            complexity += stmt.findAll(ConditionalExpr.class).size() * (1 + nestingLevel);
        }

        return complexity;
    }

    private int countLogicalOperators(IfStmt ifStmt) {
        int count = 0;
        for (BinaryExpr expr : ifStmt.getCondition().findAll(BinaryExpr.class)) {
            if (expr.getOperator() == BinaryExpr.Operator.AND ||
                expr.getOperator() == BinaryExpr.Operator.OR) {
                count++;
            }
        }
        return count;
    }
}
