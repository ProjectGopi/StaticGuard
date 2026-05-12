package com.staticanalysis.analyzer;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

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

    // SEC-012: Matches IPv4 addresses, excluding loopback (127.x.x.x) and wildcard (0.0.0.0)
    private static final Pattern IP_PATTERN =
        Pattern.compile("^(?!127\\.\\d+\\.\\d+\\.\\d+$)(?!0\\.0\\.0\\.0$)\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

    public SecurityAnalyzer(String fileName) {
        this.fileName = fileName;
    }

    // -----------------------------------------------------------------------
    // SEC-001: Hardcoded sensitive variable values
    // -----------------------------------------------------------------------
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

    // -----------------------------------------------------------------------
    // SEC-002: SQL Injection | SEC-007: LDAP Injection | SEC-008: Path Traversal
    // -----------------------------------------------------------------------
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

    // -----------------------------------------------------------------------
    // SEC-003: System.exit() | SEC-006: Weak crypto | SEC-009: Runtime.exec()
    // SEC-010: printStackTrace() | SEC-011: Thread.sleep()
    // -----------------------------------------------------------------------
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

        // SEC-010: printStackTrace() leaks internal stack trace
        if (methodName.equals("printStackTrace")) {
            DefectCollector.addDefect(new Defect(
                "Security",
                "printStackTrace() detected - stack trace may be exposed",
                "MAJOR", fileName, line,
                "SEC-010", Defect.Category.SECURITY,
                "Replace printStackTrace() with a logger (e.g. log.error(\"msg\", e)) to avoid leaking internal details."
            ));
        }

        // SEC-011: Thread.sleep() indicates bad timing design in production code
        if (methodName.equals("sleep")
                && mce.getScope().isPresent()
                && mce.getScope().get().toString().equals("Thread")) {
            DefectCollector.addDefect(new Defect(
                "Security",
                "Thread.sleep() detected in production code",
                "MAJOR", fileName, line,
                "SEC-011", Defect.Category.SECURITY,
                "Thread.sleep() wastes threads and causes unpredictable delays. " +
                "Use ScheduledExecutorService or CompletableFuture.delayedExecutor() instead."
            ));
        }
    }

    // -----------------------------------------------------------------------
    // SEC-004: Insecure Random | SEC-013: ObjectInputStream deserialization
    // -----------------------------------------------------------------------
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

        // SEC-013: Deserializing ObjectInputStream without class validation is OWASP A8
        if (typeName.equals("ObjectInputStream")) {
            DefectCollector.addDefect(new Defect(
                "Security",
                "Unsafe deserialization -> ObjectInputStream usage detected",
                "CRITICAL", fileName, line,
                "SEC-013", Defect.Category.SECURITY,
                "Deserialization of untrusted data is a critical vulnerability (OWASP A8). " +
                "Validate/whitelist classes with a custom ObjectInputStream.resolveClass() filter."
            ));
        }
    }

    // -----------------------------------------------------------------------
    // SEC-005: Resource leak (not in try-with-resources)
    // -----------------------------------------------------------------------
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

    // -----------------------------------------------------------------------
    // SEC-012: Hardcoded IP address in string literals
    // -----------------------------------------------------------------------
    @Override
    public void visit(StringLiteralExpr sle, Void arg) {
        super.visit(sle, arg);

        String value = sle.asString().trim();
        if (IP_PATTERN.matcher(value).matches()) {
            DefectCollector.addDefect(new Defect(
                "Security",
                "Hardcoded IP address -> \"" + value + "\"",
                "MAJOR", fileName,
                sle.getBegin().map(p -> p.line).orElse(-1),
                "SEC-012", Defect.Category.SECURITY,
                "Hardcoded IP addresses make deployment brittle. Use configuration files, DNS names, or environment variables."
            ));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
