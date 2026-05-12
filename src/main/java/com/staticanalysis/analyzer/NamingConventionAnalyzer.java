package com.staticanalysis.analyzer;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NamingConventionAnalyzer extends VoidVisitorAdapter<Void> {

    private String fileName;

    private static final Set<String> NON_DESCRIPTIVE_NAMES = new HashSet<>(Arrays.asList(
        "temp", "tmp", "data", "obj", "val", "var", "item", "element",
        "thing", "stuff", "foo", "bar", "baz", "test", "result", "res",
        "str", "num", "arr", "lst", "map"
    ));

    private static final Set<String> ALLOWED_SHORT_NAMES = new HashSet<>(Arrays.asList(
        "i", "j", "k", "x", "y", "z", "e", "ex"
    ));

    public NamingConventionAnalyzer(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
        super.visit(cid, arg);

        String name = cid.getNameAsString();
        int line = cid.getBegin().map(p -> p.line).orElse(-1);

        // NAME-001: Classes should be PascalCase
        if (!isPascalCase(name)) {
            DefectCollector.addDefect(new Defect(
                "Naming", "Class name '" + name + "' does not follow PascalCase convention",
                "MINOR", fileName, line,
                "NAME-001", Defect.Category.NAMING,
                "Rename class to PascalCase (e.g., MyClassName)."
            ));
        }

        // NAME-008: Exception subclasses should end with 'Exception'
        cid.getExtendedTypes().forEach(ext -> {
            String parentName = ext.getNameAsString();
            boolean extendsException = parentName.endsWith("Exception")
                || parentName.equals("RuntimeException")
                || parentName.equals("Error");
            if (extendsException && !name.endsWith("Exception") && !name.endsWith("Error")) {
                DefectCollector.addDefect(new Defect(
                    "Naming",
                    "Exception class '" + name + "' does not end with 'Exception'",
                    "MINOR", fileName, line,
                    "NAME-008", Defect.Category.NAMING,
                    "Rename to '" + name + "Exception' to follow Java exception naming conventions."
                ));
            }
        });
    }

    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);

        String name = md.getNameAsString();
        int line = md.getBegin().map(p -> p.line).orElse(-1);

        // NAME-002: Methods should be camelCase
        if (!isCamelCase(name)) {
            DefectCollector.addDefect(new Defect(
                "Naming", "Method name '" + name + "' does not follow camelCase convention",
                "MINOR", fileName, line,
                "NAME-002", Defect.Category.NAMING,
                "Rename method to camelCase (e.g., myMethodName)."
            ));
        }

        // NAME-009: Test methods should follow test naming conventions
        boolean looksLikeTest =
            md.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Test")
                || a.getNameAsString().equals("ParameterizedTest")
                || a.getNameAsString().equals("RepeatedTest"));
        if (looksLikeTest) {
            boolean followsTestNaming = name.startsWith("test")
                || name.startsWith("should")
                || name.startsWith("given")
                || name.startsWith("when")
                || name.contains("_should")
                || name.contains("_when");
            if (!followsTestNaming) {
                DefectCollector.addDefect(new Defect(
                    "Naming",
                    "Test method '" + name + "' does not follow a descriptive naming convention",
                    "MINOR", fileName, line,
                    "NAME-009", Defect.Category.NAMING,
                    "Name test methods to describe behaviour, e.g., 'shouldReturnEmptyWhenInputIsNull' or 'testCalculateSum'."
                ));
            }
        }

        // Check parameter names
        md.getParameters().forEach(param -> {
            String paramName = param.getNameAsString();

            // Parameters should be camelCase
            if (paramName.length() > 1 && !isCamelCase(paramName)) {
                DefectCollector.addDefect(new Defect(
                    "Naming", "Parameter '" + paramName + "' in method '" + name + "' does not follow camelCase",
                    "MINOR", fileName, line,
                    "NAME-004", Defect.Category.NAMING,
                    "Rename parameter to camelCase."
                ));
            }
        });

        // Check local variable names
        md.findAll(com.github.javaparser.ast.body.VariableDeclarator.class).forEach(vd -> {
            checkVariableName(vd, false);
        });
    }

    @Override
    public void visit(FieldDeclaration fd, Void arg) {
        super.visit(fd, arg);

        for (VariableDeclarator vd : fd.getVariables()) {
            boolean isConstant = fd.isStatic() && fd.isFinal();
            checkVariableName(vd, isConstant);
        }
    }

    private void checkVariableName(VariableDeclarator vd, boolean isConstant) {
        String name = vd.getNameAsString();
        int line = vd.getBegin().map(p -> p.line).orElse(-1);

        if (isConstant) {
            // Constants should be UPPER_SNAKE_CASE
            if (!isUpperSnakeCase(name)) {
                DefectCollector.addDefect(new Defect(
                    "Naming", "Constant '" + name + "' does not follow UPPER_SNAKE_CASE convention",
                    "MINOR", fileName, line,
                    "NAME-005", Defect.Category.NAMING,
                    "Rename constant to UPPER_SNAKE_CASE (e.g., MAX_SIZE)."
                ));
            }
        } else {
            // Variables should be camelCase
            if (name.length() > 1 && !isCamelCase(name)) {
                DefectCollector.addDefect(new Defect(
                    "Naming", "Variable '" + name + "' does not follow camelCase convention",
                    "MINOR", fileName, line,
                    "NAME-003", Defect.Category.NAMING,
                    "Rename variable to camelCase (e.g., myVariable)."
                ));
            }

            // Single-letter names (except loop vars)
            if (name.length() == 1 && !ALLOWED_SHORT_NAMES.contains(name)) {
                DefectCollector.addDefect(new Defect(
                    "Naming", "Single-letter variable name '" + name + "' is not descriptive",
                    "MINOR", fileName, line,
                    "NAME-006", Defect.Category.NAMING,
                    "Use a descriptive name that conveys the variable's purpose."
                ));
            }

            // Non-descriptive names
            if (NON_DESCRIPTIVE_NAMES.contains(name.toLowerCase())) {
                DefectCollector.addDefect(new Defect(
                    "Naming", "Non-descriptive variable name '" + name + "'",
                    "MINOR", fileName, line,
                    "NAME-007", Defect.Category.NAMING,
                    "Choose a name that describes the purpose of this variable."
                ));
            }
        }
    }

    private boolean isPascalCase(String name) {
        return name.length() > 0
                && Character.isUpperCase(name.charAt(0))
                && !name.contains("_")
                && !name.equals(name.toUpperCase());
    }

    private boolean isCamelCase(String name) {
        return name.length() > 0
                && Character.isLowerCase(name.charAt(0))
                && !name.contains("_");
    }

    private boolean isUpperSnakeCase(String name) {
        return name.equals(name.toUpperCase()) && name.matches("[A-Z][A-Z0-9_]*");
    }
}
