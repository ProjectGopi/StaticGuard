package com.staticanalysis.analyzer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

public class DeadCodeDetector extends VoidVisitorAdapter<Void> {

    private String fileName;

    public DeadCodeDetector(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void visit(CompilationUnit cu, Void arg) {
        super.visit(cu, arg);
        detectUnusedImports(cu);
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
        super.visit(cid, arg);
        detectUnusedPrivateFields(cid);
        detectUnusedPrivateMethods(cid);
    }

    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);

        if (md.getBody().isPresent()) {

            BlockStmt body = md.getBody().get();

            // Detect Unreachable Code after return
            detectUnreachableAfter(md, body, ReturnStmt.class, "return");

            // Detect Unreachable Code after throw
            detectUnreachableAfter(md, body, ThrowStmt.class, "throw");

            // Detect Unused Variables
            Set<String> declaredVars = new HashSet<>();
            Set<String> usedVars = new HashSet<>();

            body.findAll(VariableDeclarator.class)
                    .forEach(v -> declaredVars.add(v.getNameAsString()));

            body.findAll(NameExpr.class)
                    .forEach(n -> usedVars.add(n.getNameAsString()));

            for (String var : declaredVars) {
                if (!usedVars.contains(var)) {
                    DefectCollector.addDefect(new Defect(
                        "Dead Code",
                        "Unused variable -> " + var + " in method -> " + md.getName(),
                        "MINOR", fileName,
                        md.getBegin().map(p -> p.line).orElse(-1),
                        "DEAD-002", Defect.Category.DEAD_CODE,
                        "Remove this unused variable or use it in the method logic."
                    ));
                }
            }
        }
    }

    private <T extends Statement> void detectUnreachableAfter(
            MethodDeclaration md, BlockStmt body, Class<T> stmtClass, String stmtType) {

        md.findAll(stmtClass).forEach(exitStmt -> {
            exitStmt.getParentNode().ifPresent(parent -> {
                if (parent instanceof BlockStmt) {
                    BlockStmt block = (BlockStmt) parent;
                    boolean foundExit = false;

                    for (Statement stmt : block.getStatements()) {
                        if (foundExit) {
                            DefectCollector.addDefect(new Defect(
                                "Dead Code",
                                "Unreachable statement after '" + stmtType + "' in method -> " + md.getName(),
                                "MAJOR", fileName,
                                stmt.getBegin().map(p -> p.line).orElse(-1),
                                "DEAD-001", Defect.Category.DEAD_CODE,
                                "Remove this unreachable code or restructure the control flow."
                            ));
                            break;
                        }
                        if (stmt == exitStmt) {
                            foundExit = true;
                        }
                    }
                }
            });
        });
    }

    private void detectUnusedImports(CompilationUnit cu) {
        // Get all the source code as string to check if import types are used
        String sourceCode = cu.toString();

        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk()) continue; // skip wildcard imports

            String importName = imp.getNameAsString();
            String simpleName = importName.substring(importName.lastIndexOf('.') + 1);

            // Check if the simple name appears anywhere in the source (excluding the import itself)
            String sourceWithoutImports = sourceCode.replaceAll("import\\s+.*?;", "");
            if (!sourceWithoutImports.contains(simpleName)) {
                DefectCollector.addDefect(new Defect(
                    "Dead Code",
                    "Unused import -> " + importName,
                    "MINOR", fileName,
                    imp.getBegin().map(p -> p.line).orElse(-1),
                    "DEAD-003", Defect.Category.DEAD_CODE,
                    "Remove this unused import to keep the code clean."
                ));
            }
        }
    }

    private void detectUnusedPrivateMethods(ClassOrInterfaceDeclaration cid) {
        List<MethodDeclaration> privateMethods = cid.getMethods().stream()
                .filter(m -> m.isPrivate())
                .collect(Collectors.toList());

        // Collect all method call names in the class
        Set<String> calledMethods = new HashSet<>();
        cid.findAll(MethodCallExpr.class)
                .forEach(call -> calledMethods.add(call.getNameAsString()));

        for (MethodDeclaration pm : privateMethods) {
            if (!calledMethods.contains(pm.getNameAsString())) {
                DefectCollector.addDefect(new Defect(
                    "Dead Code",
                    "Unused private method -> " + pm.getName() + " in class -> " + cid.getName(),
                    "MAJOR", fileName,
                    pm.getBegin().map(p -> p.line).orElse(-1),
                    "DEAD-004", Defect.Category.DEAD_CODE,
                    "Remove this unused private method or make it part of the class logic."
                ));
            }
        }
    }

    private void detectUnusedPrivateFields(ClassOrInterfaceDeclaration cid) {
        // Collect all name expressions used in the class body
        Set<String> usedNames = new HashSet<>();
        cid.findAll(NameExpr.class)
                .forEach(n -> usedNames.add(n.getNameAsString()));

        // Also check method calls with scope (e.g., this.field)
        cid.findAll(MethodCallExpr.class).forEach(call -> {
            call.getScope().ifPresent(scope -> usedNames.add(scope.toString()));
        });

        for (FieldDeclaration field : cid.getFields()) {
            if (field.isPrivate()) {
                for (VariableDeclarator var : field.getVariables()) {
                    String fieldName = var.getNameAsString();
                    if (!usedNames.contains(fieldName)) {
                        DefectCollector.addDefect(new Defect(
                            "Dead Code",
                            "Unused private field -> " + fieldName + " in class -> " + cid.getName(),
                            "MINOR", fileName,
                            field.getBegin().map(p -> p.line).orElse(-1),
                            "DEAD-005", Defect.Category.DEAD_CODE,
                            "Remove this unused private field to reduce class clutter."
                        ));
                    }
                }
            }
        }
    }
}
