# StaticGuard — AST-Based Static Code Analysis Framework
# Version 2.0

## Quick Start (Other Device)

### You only need: Java JDK 17

Download from: https://adoptium.net  (free, open-source)
IMPORTANT: Install the JDK (not just JRE) so you can also rebuild from source.

---

## Option 1 — Run Immediately (No Build Needed)

The pre-built JAR is already included.

### Windows
  Double-click:  run.bat
  OR in Command Prompt:
  > run.bat

### macOS / Linux
  In terminal:
  > chmod +x run.sh
  > ./run.sh

Then open browser: http://localhost:8080

---

## Option 2 — Rebuild from Source (Then Run)

### Windows (Command Prompt)
  > mvnw.cmd clean package -DskipTests
  > run.bat

### macOS / Linux
  > chmod +x mvnw
  > ./mvnw clean package -DskipTests
  > ./run.sh

The first run will auto-download Maven (~10 MB). After that, it works offline.

---

## CLI Mode (Scan a Folder)

### Windows
  > java -jar target\StaticCodeAnalyzer-2.0-SNAPSHOT.jar --path C:\path\to\java\project

### macOS / Linux
  > java -jar target/StaticCodeAnalyzer-2.0-SNAPSHOT.jar --path /path/to/java/project

Reports saved as: report.json and report.html

---

## Project Structure

  src/                         <- Full Java source code
  src/main/java/com/staticanalysis/
    Main.java                  <- Entry point (CLI + Web mode)
    analyzer/                  <- Code smell, security, dead code, naming analyzers
    metrics/                   <- Complexity, LOC, Maintainability index
    model/                     <- Defect data model + collector
    parser/                    <- JavaParser wrapper
    report/                    <- JSON + HTML report generators
    web/                       <- Embedded HTTP server + Web UI
  pom.xml                      <- Maven build file
  mvnw / mvnw.cmd              <- Maven wrapper (no Maven install needed)
  run.bat                      <- Windows launcher
  run.sh                       <- macOS/Linux launcher
  target/StaticCodeAnalyzer-2.0-SNAPSHOT.jar  <- Pre-built fat JAR

---

## What It Analyzes (47 Rules)

  Code Smells (17)  : Magic numbers, deep nesting, raw types, null returns, empty methods...
  Dead Code   ( 6)  : Unused imports, variables, parameters, methods...
  Security    (13)  : SQL injection, hardcoded secrets, weak crypto, deserialization...
  Naming      ( 9)  : PascalCase, camelCase, Exception naming, test method naming...
  Metrics     ( 5)  : Cyclomatic complexity, cognitive complexity, LOC, Javadoc coverage
  Duplication ( 1)  : Cross-file method clone detection

---

## Troubleshooting

  "java not found"       -> Install JDK 17 from adoptium.net, restart terminal
  Port 8080 in use       -> Another app is on that port; restart your PC
  run.sh permission denied -> Run: chmod +x run.sh
  mvnw.cmd not working   -> Run as Administrator or use: java -jar target\...jar --web
