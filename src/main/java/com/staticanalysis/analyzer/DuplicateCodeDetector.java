package com.staticanalysis.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.staticanalysis.model.Defect;
import com.staticanalysis.model.DefectCollector;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class DuplicateCodeDetector extends VoidVisitorAdapter<Void> {

    private String fileName;

    // Shared across files — static map of method body hashes to detect cross-file duplicates
    private static Map<String, List<MethodLocation>> methodBodyHashes = new LinkedHashMap<>();

    public DuplicateCodeDetector(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);

        if (md.getBody().isPresent()) {
            BlockStmt body = md.getBody().get();
            String normalizedBody = normalizeCode(body.toString());

            // Only consider methods with meaningful body (> 3 statements)
            if (body.getStatements().size() >= 3) {
                String hash = computeHash(normalizedBody);

                MethodLocation location = new MethodLocation(
                    fileName,
                    md.getNameAsString(),
                    md.getBegin().map(p -> p.line).orElse(-1)
                );

                if (!methodBodyHashes.containsKey(hash)) {
                    methodBodyHashes.put(hash, new ArrayList<>());
                }

                List<MethodLocation> locations = methodBodyHashes.get(hash);
                locations.add(location);

                // If we now have 2+ duplicates, report them
                if (locations.size() == 2) {
                    // Report for the original
                    MethodLocation original = locations.get(0);
                    DefectCollector.addDefect(new Defect(
                        "Duplication",
                        "Duplicate code block in method '" + original.methodName
                            + "' (duplicated in " + location.fileName + ":" + location.methodName + ")",
                        "MAJOR", original.fileName, original.line,
                        "DUP-001", Defect.Category.DUPLICATION,
                        "Extract the duplicated logic into a shared utility method."
                    ));
                    // Report for the duplicate
                    DefectCollector.addDefect(new Defect(
                        "Duplication",
                        "Duplicate code block in method '" + location.methodName
                            + "' (duplicated from " + original.fileName + ":" + original.methodName + ")",
                        "MAJOR", location.fileName, location.line,
                        "DUP-001", Defect.Category.DUPLICATION,
                        "Extract the duplicated logic into a shared utility method."
                    ));
                } else if (locations.size() > 2) {
                    // Report for additional duplicates
                    DefectCollector.addDefect(new Defect(
                        "Duplication",
                        "Duplicate code block in method '" + location.methodName
                            + "' (" + locations.size() + " copies found across files)",
                        "MAJOR", location.fileName, location.line,
                        "DUP-001", Defect.Category.DUPLICATION,
                        "Extract the duplicated logic into a shared utility method."
                    ));
                }
            }
        }
    }

    /**
     * Normalize code for comparison:
     * - Remove all whitespace variations
     * - Replace variable names with placeholders (Type-2 clone detection)
     */
    private String normalizeCode(String code) {
        return code
            .replaceAll("\\s+", " ")           // normalize whitespace
            .replaceAll("//.*", "")             // remove single-line comments
            .replaceAll("/\\*.*?\\*/", "")      // remove multi-line comments
            .trim();
    }

    private String computeHash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    // Reset state between analysis runs
    public static void reset() {
        methodBodyHashes.clear();
    }

    private static class MethodLocation {
        String fileName;
        String methodName;
        int line;

        MethodLocation(String fileName, String methodName, int line) {
            this.fileName = fileName;
            this.methodName = methodName;
            this.line = line;
        }
    }
}
