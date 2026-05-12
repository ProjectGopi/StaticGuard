package com.staticanalysis.analyzer;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.config.AnalysisConfig;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SmellDetector extends VoidVisitorAdapter<Void> {

    private String fileName;

    public SmellDetector(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
        super.visit(cid, arg);

        AnalysisConfig config = AnalysisConfig.getInstance();
        int methodCount = cid.getMethods().size();
        int fieldCount = cid.getFields().size();
        int line = cid.getBegin().map(p -> p.line).orElse(-1);

        if (methodCount > config.getMaxClassMethods()) {
            DefectCollector.addDefect(new Defect(
                "Code Smell", "Large class detected -> " + cid.getName() + " (" + methodCount + " methods)",
                "MAJOR", fileName, line,
                "SMELL-001", Defect.Category.CODE_SMELL,
                "Consider splitting this class using the Single Responsibility Principle. Max allowed: " + config.getMaxClassMethods()
            ));
        }

        if (fieldCount > config.getMaxClassFields()) {
            DefectCollector.addDefect(new Defect(
                "Code Smell", "Too many fields in class -> " + cid.getName() + " (" + fieldCount + " fields)",
                "MAJOR", fileName, line,
                "SMELL-006", Defect.Category.CODE_SMELL,
                "Consider grouping related fields into separate classes or using composition."
            ));
        }

        if (methodCount > config.getMaxClassMethods() && fieldCount > config.getMaxClassFields()) {
            DefectCollector.addDefect(new Defect(
                "Code Smell", "God Class detected -> " + cid.getName() + " (" + methodCount + " methods, " + fieldCount + " fields)",
                "CRITICAL", fileName, line,
                "SMELL-007", Defect.Category.CODE_SMELL,
                "This class has too many responsibilities. Apply the Extract Class refactoring pattern."
            ));
        }

        if (methodCount > 0) {
            long getterSetterCount = cid.getMethods().stream()
                .filter(m -> {
                    String name = m.getNameAsString();
                    return (name.startsWith("get") || name.startsWith("set") || name.startsWith("is"))
                           && m.getBody().isPresent()
                           && m.getBody().get().getStatements().size() <= 1;
                }).count();

            if (getterSetterCount == methodCount && methodCount >= 4) {
                DefectCollector.addDefect(new Defect(
                    "Code Smell", "Data Class detected -> " + cid.getName() + " (only getters/setters)",
                    "MINOR", fileName, line,
                    "SMELL-008", Defect.Category.CODE_SMELL,
                    "Consider adding behavior to this class or using a Record type (Java 16+)."
                ));
            }
        }

        for (MethodDeclaration md : cid.getMethods()) {
            detectFeatureEnvy(cid, md);
        }
    }

    private void detectFeatureEnvy(ClassOrInterfaceDeclaration cid, MethodDeclaration md) {
        Set<String> ownFields = new HashSet<>();
        cid.getFields().forEach(f -> f.getVariables().forEach(v -> ownFields.add(v.getNameAsString())));

        List<MethodCallExpr> methodCalls = md.findAll(MethodCallExpr.class);
        long externalCalls = methodCalls.stream()
            .filter(call -> call.getScope().isPresent())
            .count();

        List<NameExpr> nameUsages = md.findAll(NameExpr.class);
        long ownFieldUsages = nameUsages.stream()
            .filter(n -> ownFields.contains(n.getNameAsString()))
            .count();

        if (externalCalls > 5 && externalCalls > ownFieldUsages * 2) {
            DefectCollector.addDefect(new Defect(
                "Code Smell",
                "Feature Envy in method -> " + md.getName() + " (uses external data more than own)",
                "MINOR", fileName,
                md.getBegin().map(p -> p.line).orElse(-1),
                "SMELL-009", Defect.Category.CODE_SMELL,
                "This method might belong in another class. Consider moving it closer to the data it uses."
            ));
        }
    }

    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);

        AnalysisConfig config = AnalysisConfig.getInstance();
        int line = md.getBegin().map(p -> p.line).orElse(-1);

        if (md.getParameters().size() > config.getMaxParameters()) {
            DefectCollector.addDefect(new Defect(
                "Code Smell", "Too many parameters in method -> " + md.getName() + " (" + md.getParameters().size() + " params)",
                "MINOR", fileName, line,
                "SMELL-002", Defect.Category.CODE_SMELL,
                "Use a parameter object or builder pattern. Max allowed: " + config.getMaxParameters()
            ));
        }

        for (Parameter param : md.getParameters()) {
            if (param.getTypeAsString().equals("boolean") || param.getTypeAsString().equals("Boolean")) {
                DefectCollector.addDefect(new Defect(
                    "Code Smell",
                    "Boolean parameter '" + param.getNameAsString() + "' in method -> " + md.getName(),
                    "MINOR", fileName, line,
                    "SMELL-010", Defect.Category.CODE_SMELL,
                    "Boolean params often indicate a method doing two things. Consider splitting into two methods."
                ));
                break;
            }
        }

        if (md.getBody().isPresent()) {
            int lineCount = md.getBody().get().toString().split("\n").length;
            if (lineCount > config.getMaxMethodLines()) {
                DefectCollector.addDefect(new Defect(
                    "Code Smell", "Long method detected -> " + md.getName() + " (" + lineCount + " lines)",
                    "MAJOR", fileName, line,
                    "SMELL-003", Defect.Category.CODE_SMELL,
                    "Extract smaller methods with descriptive names. Max allowed: " + config.getMaxMethodLines() + " lines"
                ));
            }

            // SMELL-017: Empty method body (non-abstract, non-interface)
            if (md.getBody().get().getStatements().isEmpty() && !md.isAbstract()) {
                DefectCollector.addDefect(new Defect(
                    "Code Smell",
                    "Empty method body -> " + md.getName(),
                    "MINOR", fileName, line,
                    "SMELL-017", Defect.Category.CODE_SMELL,
                    "Either implement this method or remove it if it is unnecessary."
                ));
            }

            detectDeepNesting(md);
            detectMagicNumbers(md);
            detectStringConcatInLoop(md);
            detectNullReturns(md);
            detectUnusedParameters(md);
        }
    }

    private void detectDeepNesting(MethodDeclaration md) {
        AnalysisConfig config = AnalysisConfig.getInstance();
        int maxDepth = calculateMaxNesting(md.getBody().orElse(null), 0);

        if (maxDepth > config.getMaxNestingDepth()) {
            DefectCollector.addDefect(new Defect(
                "Code Smell",
                "Deeply nested code in method -> " + md.getName() + " (depth: " + maxDepth + ")",
                "MAJOR", fileName,
                md.getBegin().map(p -> p.line).orElse(-1),
                "SMELL-005", Defect.Category.CODE_SMELL,
                "Reduce nesting by using early returns, guard clauses, or extracting inner blocks. Max depth: " + config.getMaxNestingDepth()
            ));
        }
    }

    private int calculateMaxNesting(BlockStmt block, int currentDepth) {
        if (block == null) return currentDepth;

        int maxDepth = currentDepth;
        for (Statement stmt : block.getStatements()) {
            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                if (ifStmt.getThenStmt() instanceof BlockStmt) {
                    maxDepth = Math.max(maxDepth, calculateMaxNesting((BlockStmt) ifStmt.getThenStmt(), currentDepth + 1));
                }
                if (ifStmt.getElseStmt().isPresent() && ifStmt.getElseStmt().get() instanceof BlockStmt) {
                    maxDepth = Math.max(maxDepth, calculateMaxNesting((BlockStmt) ifStmt.getElseStmt().get(), currentDepth + 1));
                }
            } else if (stmt instanceof ForStmt) {
                ForStmt forStmt = (ForStmt) stmt;
                if (forStmt.getBody() instanceof BlockStmt) {
                    maxDepth = Math.max(maxDepth, calculateMaxNesting((BlockStmt) forStmt.getBody(), currentDepth + 1));
                }
            } else if (stmt instanceof ForEachStmt) {
                // B3: was missing — for-each loops add nesting depth
                ForEachStmt forEachStmt = (ForEachStmt) stmt;
                if (forEachStmt.getBody() instanceof BlockStmt) {
                    maxDepth = Math.max(maxDepth, calculateMaxNesting((BlockStmt) forEachStmt.getBody(), currentDepth + 1));
                }
            } else if (stmt instanceof WhileStmt) {
                WhileStmt whileStmt = (WhileStmt) stmt;
                if (whileStmt.getBody() instanceof BlockStmt) {
                    maxDepth = Math.max(maxDepth, calculateMaxNesting((BlockStmt) whileStmt.getBody(), currentDepth + 1));
                }
            } else if (stmt instanceof DoStmt) {
                DoStmt doStmt = (DoStmt) stmt;
                if (doStmt.getBody() instanceof BlockStmt) {
                    maxDepth = Math.max(maxDepth, calculateMaxNesting((BlockStmt) doStmt.getBody(), currentDepth + 1));
                }
            } else if (stmt instanceof SwitchStmt) {
                // B3: was missing — switch blocks add nesting depth
                maxDepth = Math.max(maxDepth, currentDepth + 1);
            } else if (stmt instanceof TryStmt) {
                TryStmt tryStmt = (TryStmt) stmt;
                maxDepth = Math.max(maxDepth, calculateMaxNesting(tryStmt.getTryBlock(), currentDepth + 1));
            }
        }
        return maxDepth;
    }

    private void detectMagicNumbers(MethodDeclaration md) {
        // P3: single traversal for all numeric literal types
        md.findAll(com.github.javaparser.ast.expr.LiteralStringValueExpr.class).forEach(lit -> {
            try {
                String raw = lit.getValue();
                double value = Double.parseDouble(raw);
                if (value != 0.0 && value != 1.0 && value != -1.0) {
                    DefectCollector.addDefect(new Defect(
                        "Code Smell",
                        "Magic number '" + raw + "' in method -> " + md.getName(),
                        "MINOR", fileName,
                        lit.getBegin().map(p -> p.line).orElse(-1),
                        "SMELL-011", Defect.Category.CODE_SMELL,
                        "Extract magic numbers into named constants for better readability."
                    ));
                }
            } catch (NumberFormatException e) {
                // skip non-numeric literals (e.g. string values)
            }
        });
    }

    // N4: Detect String concatenation inside a loop — O(n^2) performance anti-pattern
    private void detectStringConcatInLoop(MethodDeclaration md) {
        List<Statement> loopStatements = new java.util.ArrayList<>();
        loopStatements.addAll(md.findAll(ForStmt.class)
            .stream().map(s -> (Statement) s).collect(java.util.stream.Collectors.toList()));
        loopStatements.addAll(md.findAll(ForEachStmt.class)
            .stream().map(s -> (Statement) s).collect(java.util.stream.Collectors.toList()));
        loopStatements.addAll(md.findAll(WhileStmt.class)
            .stream().map(s -> (Statement) s).collect(java.util.stream.Collectors.toList()));

        for (Statement loop : loopStatements) {
            // Look for string += or string = string + ...
            loop.findAll(AssignExpr.class).forEach(assign -> {
                if (assign.getOperator() == AssignExpr.Operator.PLUS) {
                    DefectCollector.addDefect(new Defect(
                        "Code Smell",
                        "String concatenation with '+=' inside a loop in method -> " + md.getName(),
                        "MAJOR", fileName,
                        assign.getBegin().map(p -> p.line).orElse(-1),
                        "SMELL-013", Defect.Category.CODE_SMELL,
                        "Use StringBuilder instead of String += inside loops to avoid O(n^2) memory allocations."
                    ));
                }
            });

            loop.findAll(BinaryExpr.class).forEach(bin -> {
                if (bin.getOperator() == BinaryExpr.Operator.PLUS
                        && (bin.getLeft() instanceof StringLiteralExpr
                            || bin.getRight() instanceof StringLiteralExpr)) {
                    DefectCollector.addDefect(new Defect(
                        "Code Smell",
                        "String literal concatenation with '+' inside a loop in method -> " + md.getName(),
                        "MINOR", fileName,
                        bin.getBegin().map(p -> p.line).orElse(-1),
                        "SMELL-013", Defect.Category.CODE_SMELL,
                        "Use StringBuilder.append() inside loops instead of String + literal."
                    ));
                }
            });
        }
    }

    @Override
    public void visit(CatchClause cc, Void arg) {
        super.visit(cc, arg);

        if (cc.getBody().getStatements().isEmpty()) {
            DefectCollector.addDefect(new Defect(
                "Code Smell", "Empty catch block detected",
                "MAJOR", fileName,
                cc.getBegin().map(p -> p.line).orElse(-1),
                "SMELL-004", Defect.Category.CODE_SMELL,
                "Log the exception or handle it properly. Never swallow exceptions silently."
            ));
        }

        String exceptionType = cc.getParameter().getTypeAsString();
        if (exceptionType.equals("Exception") || exceptionType.equals("Throwable")) {
            DefectCollector.addDefect(new Defect(
                "Code Smell",
                "Catching generic " + exceptionType + " is too broad",
                "MINOR", fileName,
                cc.getBegin().map(p -> p.line).orElse(-1),
                "SMELL-012", Defect.Category.CODE_SMELL,
                "Catch specific exception types to handle different error cases appropriately."
            ));
        }
    }

    // SMELL-014: switch statement without a default case
    @Override
    public void visit(SwitchStmt ss, Void arg) {
        super.visit(ss, arg);

        boolean hasDefault = ss.getEntries().stream()
            .anyMatch(entry -> entry.getLabels().isEmpty());

        if (!hasDefault) {
            DefectCollector.addDefect(new Defect(
                "Code Smell",
                "switch statement missing 'default' case",
                "MINOR", fileName,
                ss.getBegin().map(p -> p.line).orElse(-1),
                "SMELL-014", Defect.Category.CODE_SMELL,
                "Add a 'default' case to handle unexpected values and prevent silent failures."
            ));
        }
    }

    // SMELL-015: Raw type usage (e.g. List list instead of List<Type>)
    @Override
    public void visit(FieldDeclaration fd, Void arg) {
        super.visit(fd, arg);

        fd.getVariables().forEach(v -> {
            if (v.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType cit = (ClassOrInterfaceType) v.getType();
                String typeName = cit.getNameAsString();
                boolean isCollectionLike = typeName.equals("List") || typeName.equals("Map")
                    || typeName.equals("Set") || typeName.equals("Collection")
                    || typeName.equals("ArrayList") || typeName.equals("HashMap")
                    || typeName.equals("HashSet") || typeName.equals("LinkedList")
                    || typeName.equals("Queue") || typeName.equals("Deque")
                    || typeName.equals("Optional");
                if (isCollectionLike && !cit.getTypeArguments().isPresent()) {
                    DefectCollector.addDefect(new Defect(
                        "Code Smell",
                        "Raw type usage -> '" + typeName + "' used without type parameter in field '" + v.getNameAsString() + "'",
                        "MAJOR", fileName,
                        fd.getBegin().map(p -> p.line).orElse(-1),
                        "SMELL-015", Defect.Category.CODE_SMELL,
                        "Specify the generic type (e.g., List<String>) to ensure type safety and avoid ClassCastException."
                    ));
                }
            }
        });
    }

    // SMELL-016: Returning null from a typed (non-void) method
    private void detectNullReturns(MethodDeclaration md) {
        String returnType = md.getTypeAsString();
        if (returnType.equals("void") || returnType.equals("Void")) return;

        md.findAll(ReturnStmt.class).forEach(ret -> {
            if (ret.getExpression().isPresent()
                    && ret.getExpression().get() instanceof NullLiteralExpr) {
                DefectCollector.addDefect(new Defect(
                    "Code Smell",
                    "Returning null from method -> '" + md.getName() + "' (return type: " + returnType + ")",
                    "MAJOR", fileName,
                    ret.getBegin().map(p -> p.line).orElse(-1),
                    "SMELL-016", Defect.Category.CODE_SMELL,
                    "Return Optional<" + returnType + "> instead of null to force callers to handle the absence case."
                ));
            }
        });
    }

    // Unused method parameters detection
    private void detectUnusedParameters(MethodDeclaration md) {
        if (md.getParameters().isEmpty() || !md.getBody().isPresent()) return;

        // Collect all names used in the body
        Set<String> usedNames = new HashSet<>();
        md.getBody().get().findAll(NameExpr.class)
            .forEach(n -> usedNames.add(n.getNameAsString()));

        for (Parameter param : md.getParameters()) {
            String paramName = param.getNameAsString();
            // Skip common placeholder names (e.g. arg, args)
            if (paramName.equals("arg") || paramName.equals("args")) continue;
            if (!usedNames.contains(paramName)) {
                DefectCollector.addDefect(new Defect(
                    "Dead Code",
                    "Unused parameter '" + paramName + "' in method -> " + md.getName(),
                    "MINOR", fileName,
                    param.getBegin().map(p -> p.line).orElse(-1),
                    "DEAD-006", Defect.Category.DEAD_CODE,
                    "Remove this parameter or use it in the method body."
                ));
            }
        }
    }
}
