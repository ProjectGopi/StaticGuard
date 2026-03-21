package com.staticanalysis.analyzer;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SecurityAnalyzer extends VoidVisitorAdapter<Void> {

    private String fileName;

    private static final Set<String> SENSITIVE_VAR_NAMES = new HashSet<>(Arrays.asList(
        "password", "passwd", "pwd", "pass", "secret", "apikey", "api_key",
        "token", "accesstoken", "access_token", "authtoken", "auth_token",
        "privatekey", "private_key", "credentials", "secretkey", "secret_key"
    ));

    private static final Set<String> WEAK_HASH_ALGORITHMS = new HashSet<>(Arrays.asList(
        "MD5", "SHA1", "SHA-1", "md5", "sha1", "sha-1"
    ));

    public SecurityAnalyzer(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void visit(VariableDeclarator vd, Void arg) {
        super.visit(vd, arg);

        String varName = vd.getNameAsString().toLowerCase();
        int line = vd.getBegin().map(p -> p.line).orElse(-1);

        for (String sensitive : SENSITIVE_VAR_NAMES) {
            if (varName.contains(sensitive)
                    && vd.getInitializer().isPresent()
                    && vd.getInitializer().get() instanceof StringLiteralExpr) {

                DefectCollector.addDefect(new Defect(
                    "Security",
                    "Hardcoded sensitive value -> " + vd.getNameAsString(),
                    "CRITICAL", fileName, line,
                    "SEC-001", Defect.Category.SECURITY,
                    "Store sensitive values in environment variables, a secrets vault, or configuration files outside the codebase."
                ));
                break;
            }
        }
    }

    @Override
    public void visit(BinaryExpr be, Void arg) {
        super.visit(be, arg);

        if (be.getOperator() == BinaryExpr.Operator.PLUS) {
            String expression = be.toString().toLowerCase();

            if (expression.contains("select") || expression.contains("insert")
                    || expression.contains("update") || expression.contains("delete")
                    || expression.contains("drop") || expression.contains("alter")
                    || expression.contains("truncate")) {

                DefectCollector.addDefect(new Defect(
                    "Security",
                    "Possible SQL Injection -> " + truncate(be.toString(), 80),
                    "CRITICAL", fileName,
                    be.getBegin().map(p -> p.line).orElse(-1),
                    "SEC-002", Defect.Category.SECURITY,
                    "Use PreparedStatement with parameterized queries instead of string concatenation."
                ));
            }

            if (expression.contains("ldap") || expression.contains("(cn=") || expression.contains("(uid=")) {
                DefectCollector.addDefect(new Defect(
                    "Security",
                    "Possible LDAP Injection -> " + truncate(be.toString(), 80),
                    "CRITICAL", fileName,
                    be.getBegin().map(p -> p.line).orElse(-1),
                    "SEC-007", Defect.Category.SECURITY,
                    "Sanitize user input before constructing LDAP queries."
                ));
            }

            if (expression.contains("file") && (expression.contains("..") || expression.contains("/"))) {
                DefectCollector.addDefect(new Defect(
                    "Security",
                    "Possible Path Traversal -> " + truncate(be.toString(), 80),
                    "CRITICAL", fileName,
                    be.getBegin().map(p -> p.line).orElse(-1),
                    "SEC-008", Defect.Category.SECURITY,
                    "Validate and sanitize file paths. Use canonical path comparison to prevent directory traversal."
                ));
            }
        }
    }

    @Override
    public void visit(MethodCallExpr mce, Void arg) {
        super.visit(mce, arg);

        String methodName = mce.getNameAsString();
        int line = mce.getBegin().map(p -> p.line).orElse(-1);

        if (methodName.equals("exit")
                && mce.getScope().isPresent()
                && mce.getScope().get().toString().equals("System")) {

            DefectCollector.addDefect(new Defect(
                "Security",
                "System.exit() usage detected",
                "MAJOR", fileName, line,
                "SEC-003", Defect.Category.SECURITY,
                "Avoid System.exit() - it terminates the entire JVM. Use exceptions or return codes instead."
            ));
        }

        if (methodName.equals("getInstance") && mce.getArguments().size() > 0) {
            String firstArg = mce.getArguments().get(0).toString().replace("\"", "");
            if (WEAK_HASH_ALGORITHMS.contains(firstArg)) {
                DefectCollector.addDefect(new Defect(
                    "Security",
                    "Weak cryptographic algorithm used -> " + firstArg,
                    "CRITICAL", fileName, line,
                    "SEC-006", Defect.Category.SECURITY,
                    "Use SHA-256 or SHA-512 instead of " + firstArg + ". Consider bcrypt/scrypt for passwords."
                ));
            }
        }

        if (methodName.equals("exec") && mce.getScope().isPresent()) {
            String scope = mce.getScope().get().toString();
            if (scope.contains("Runtime") || scope.contains("runtime")) {
                DefectCollector.addDefect(new Defect(
                    "Security",
                    "Runtime.exec() usage - potential command injection",
                    "CRITICAL", fileName, line,
                    "SEC-009", Defect.Category.SECURITY,
                    "Validate and sanitize all input to Runtime.exec(). Consider using ProcessBuilder with a whitelist."
                ));
            }
        }
    }

    @Override
    public void visit(ObjectCreationExpr oce, Void arg) {
        super.visit(oce, arg);

        String typeName = oce.getTypeAsString();
        int line = oce.getBegin().map(p -> p.line).orElse(-1);

        if (typeName.equals("Random")) {
            DefectCollector.addDefect(new Defect(
                "Security",
                "Insecure random number generator -> java.util.Random",
                "MAJOR", fileName, line,
                "SEC-004", Defect.Category.SECURITY,
                "Use java.security.SecureRandom for security-sensitive operations (tokens, keys, salts)."
            ));
        }
    }

    @Override
    public void visit(TryStmt tryStmt, Void arg) {
        super.visit(tryStmt, arg);

        tryStmt.getTryBlock().findAll(ObjectCreationExpr.class).forEach(oce -> {
            String typeName = oce.getTypeAsString();
            if (isAutoCloseableType(typeName) && tryStmt.getResources().isEmpty()) {
                DefectCollector.addDefect(new Defect(
                    "Security",
                    "Potential resource leak -> " + typeName + " not in try-with-resources",
                    "MAJOR", fileName,
                    oce.getBegin().map(p -> p.line).orElse(-1),
                    "SEC-005", Defect.Category.SECURITY,
                    "Use try-with-resources to ensure " + typeName + " is properly closed."
                ));
            }
        });
    }

    private boolean isAutoCloseableType(String typeName) {
        Set<String> closeableTypes = new HashSet<>(Arrays.asList(
            "FileInputStream", "FileOutputStream", "BufferedReader", "BufferedWriter",
            "FileReader", "FileWriter", "Scanner", "PrintWriter",
            "Connection", "Statement", "PreparedStatement", "ResultSet",
            "Socket", "ServerSocket", "DataInputStream", "DataOutputStream",
            "ObjectInputStream", "ObjectOutputStream", "InputStream", "OutputStream"
        ));
        return closeableTypes.contains(typeName);
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
