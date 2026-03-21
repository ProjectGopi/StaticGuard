package com.staticanalysis.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefectCollector {

    private static List<Defect> defects = new ArrayList<>();
    private static Set<String> deduplicationKeys = new HashSet<>();

    public static void addDefect(Defect defect) {
        String key = defect.getDeduplicationKey();
        if (!deduplicationKeys.contains(key)) {
            deduplicationKeys.add(key);
            defects.add(defect);
        }
    }

    public static List<Defect> getDefects() {
        return defects;
    }

    public static void reset() {
        defects.clear();
        deduplicationKeys.clear();
    }

    // Filter by severity
    public static List<Defect> getDefectsBySeverity(String severity) {
        return defects.stream()
                .filter(d -> d.getSeverity().equalsIgnoreCase(severity))
                .collect(Collectors.toList());
    }

    // Filter by category
    public static List<Defect> getDefectsByCategory(Defect.Category category) {
        return defects.stream()
                .filter(d -> d.getCategory() == category)
                .collect(Collectors.toList());
    }

    // Filter by file
    public static List<Defect> getDefectsByFile(String fileName) {
        return defects.stream()
                .filter(d -> d.getFileName().equals(fileName))
                .collect(Collectors.toList());
    }

    // Statistics: defects per file
    public static Map<String, Integer> getDefectsPerFile() {
        Map<String, Integer> map = new HashMap<>();
        for (Defect d : defects) {
            map.merge(d.getFileName(), 1, Integer::sum);
        }
        return map;
    }

    // Statistics: defects per category
    public static Map<Defect.Category, Integer> getDefectsPerCategory() {
        Map<Defect.Category, Integer> map = new HashMap<>();
        for (Defect d : defects) {
            map.merge(d.getCategory(), 1, Integer::sum);
        }
        return map;
    }

    // Statistics: defects per severity
    public static Map<String, Integer> getDefectsPerSeverity() {
        Map<String, Integer> map = new HashMap<>();
        for (Defect d : defects) {
            map.merge(d.getSeverity(), 1, Integer::sum);
        }
        return map;
    }

    // Count methods
    public static int getCriticalCount() {
        return (int) defects.stream().filter(d -> "CRITICAL".equals(d.getSeverity())).count();
    }

    public static int getMajorCount() {
        return (int) defects.stream().filter(d -> "MAJOR".equals(d.getSeverity())).count();
    }

    public static int getMinorCount() {
        return (int) defects.stream().filter(d -> "MINOR".equals(d.getSeverity())).count();
    }

    public static void printReport() {
        System.out.println("\n===== DEFECT REPORT =====");
        for (Defect defect : defects) {
            System.out.println(defect);
        }
    }

    public static void printSummary() {
        int critical = getCriticalCount();
        int major = getMajorCount();
        int minor = getMinorCount();

        System.out.println("\n===== SUMMARY =====");
        System.out.println("CRITICAL: " + critical);
        System.out.println("MAJOR:    " + major);
        System.out.println("MINOR:    " + minor);
        System.out.println("TOTAL:    " + defects.size());

        // Category breakdown
        Map<Defect.Category, Integer> categoryMap = getDefectsPerCategory();
        if (!categoryMap.isEmpty()) {
            System.out.println("\n--- By Category ---");
            for (Map.Entry<Defect.Category, Integer> entry : categoryMap.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }

        // Per-file breakdown
        Map<String, Integer> fileMap = getDefectsPerFile();
        if (!fileMap.isEmpty()) {
            System.out.println("\n--- By File ---");
            for (Map.Entry<String, Integer> entry : fileMap.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }
    }
}
