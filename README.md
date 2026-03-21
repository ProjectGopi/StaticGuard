# StaticGuard v2.0

**AST-Based Static Code Analysis & Automated Defect Detection Framework**

StaticGuard is a modular static code analysis framework built in Java using AST-based parsing (JavaParser). It analyzes Java source code to detect **code smells**, **dead code**, **security vulnerabilities**, **naming convention violations**, **duplicate code**, and computes **software metrics** such as cyclomatic complexity, cognitive complexity, and maintainability index.

Each defect is classified by severity (CRITICAL / MAJOR / MINOR), assigned a unique rule ID, and accompanied by a fix suggestion. Reports are generated in both **JSON** and **interactive HTML dashboard** formats.

---

## Table of Contents

- [Features](#features)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [How to Build and Run](#how-to-build-and-run)
- [CLI Options](#cli-options)
- [Configuration](#configuration)
- [Output Reports](#output-reports)
- [Detection Rules Reference](#detection-rules-reference)
- [Technologies Used](#technologies-used)
- [License](#license)
- [Author](#author)

---

## Features

- **39 detection rules** across 6 categories
- **Configurable thresholds** via JSON config file
- **Interactive HTML dashboard** with charts, filters, search, sorting, dark/light mode, and CSV export
- **Structured JSON report** for programmatic consumption
- **CLI interface** with flags for path, output, severity filter, config, and verbose mode
- **Exit codes** for CI/CD integration (0 = clean, 1 = critical issues, 2 = error)
- **Deduplication** of defects to avoid duplicate reporting
- **Per-file and per-category statistics** in summary output

---

## Project Structure

```
StaticCodeAnalyzer/
|- pom.xml                              # Maven build configuration
|- README.md                            # This file
|- LICENSE                              # MIT License
|- .gitignore                           # Git ignore rules
|- test-input/                          # Sample Java files with intentional defects
|   |- EnterpriseUserService.java
|   |- SampleTest.java
|- src/
    |- main/java/com/staticanalysis/
    |   |- Main.java                    # Entry point with CLI argument parsing
    |   |- config/
    |   |   |- AnalysisConfig.java      # Configurable thresholds and feature toggles
    |   |- analyzer/
    |   |   |- ClassAnalyzer.java       # Class structure info (verbose mode)
    |   |   |- MethodAnalyzer.java      # Method structure info (verbose mode)
    |   |   |- SmellDetector.java       # 12 code smell detection rules
    |   |   |- DeadCodeDetector.java    # 5 dead code detection rules
    |   |   |- SecurityAnalyzer.java    # 9 security vulnerability rules
    |   |   |- NamingConventionAnalyzer.java  # 7 naming convention rules
    |   |   |- DuplicateCodeDetector.java     # Clone detection via hashing
    |   |- metrics/
    |   |   |- ComplexityAnalyzer.java         # Cyclomatic complexity
    |   |   |- CognitiveComplexityAnalyzer.java # Cognitive complexity (SonarQube)
    |   |   |- LOCCounter.java                # Lines of code and comment ratio
    |   |   |- MaintainabilityIndex.java      # Microsoft MI formula
    |   |- model/
    |   |   |- Defect.java              # Defect data model
    |   |   |- DefectCollector.java     # Collection, dedup, filtering, stats
    |   |- parser/
    |   |   |- CodeParser.java          # JavaParser AST parsing
    |   |   |- FileScanner.java         # Recursive .java file discovery
    |   |- report/
    |       |- ReportGenerator.java     # JSON and HTML report generation
    |- test/java/                       # Unit tests (placeholder)
```

---

## Prerequisites

- **Java 17** or higher
- **Apache Maven 3.6+**

Verify your setup:
```bash
java -version
mvn -version
```

---

## How to Build and Run

### 1. Clone the repository
```bash
git clone https://github.com/<your-username>/StaticGuard.git
cd StaticGuard
```

### 2. Compile the project
```bash
mvn clean compile
```

### 3. Run analysis on the included test-input
```bash
mvn exec:java -Dexec.mainClass="com.staticanalysis.Main"
```

### 4. Run analysis on your own Java project
```bash
mvn exec:java -Dexec.mainClass="com.staticanalysis.Main" -Dexec.args="--path /path/to/your/java/project"
```

### 5. Build an executable JAR (fat JAR with all dependencies)
```bash
mvn clean package
java -jar target/StaticCodeAnalyzer-2.0-SNAPSHOT.jar --path /path/to/project
```

### 6. View the reports
After running, two report files are generated in the current directory:
- `report.json` - structured JSON with all defect details
- `report.html` - open in any web browser for the interactive dashboard

---

## CLI Options

```
Usage: java -jar StaticGuard.jar [options] [path]

Options:
  -p, --path <dir>        Path to analyze (default: test-input)
  -o, --output <dir>      Output directory for reports (default: .)
  -s, --severity <level>  Filter by severity: CRITICAL, MAJOR, MINOR
  -c, --config <file>     Path to configuration JSON file
  -v, --verbose           Enable verbose structural output
  --generate-config       Generate default config file (staticguard-config.json)
  -h, --help              Show help message

Exit Codes:
  0 - No critical issues found
  1 - Critical issues found
  2 - Analysis error (no files found, parse failure)
```

---

## Configuration

Generate a default configuration file:
```bash
java -jar StaticGuard.jar --generate-config
```

This creates `staticguard-config.json` which you can edit to customize thresholds:

```json
{
  "maxParameters": 3,
  "maxMethodLines": 30,
  "maxClassMethods": 10,
  "maxNestingDepth": 3,
  "maxClassFields": 15,
  "complexityMinor": 10,
  "complexityMajor": 20,
  "complexityCritical": 50,
  "cognitiveComplexityThreshold": 15,
  "maxFileLOC": 500,
  "minCommentRatio": 0.1,
  "enableCodeSmells": true,
  "enableDeadCode": true,
  "enableSecurity": true,
  "enableMetrics": true,
  "enableNaming": true,
  "enableDuplication": true
}
```

Run with a custom config:
```bash
java -jar StaticGuard.jar --config staticguard-config.json --path src/
```

---

## Output Reports

### JSON Report (report.json)

Each defect is a JSON object with these fields:

| Field | Description |
|-------|-------------|
| type | Category (Code Smell, Dead Code, Security, Metrics, Naming, Duplication) |
| message | Human-readable description of the issue |
| severity | CRITICAL, MAJOR, or MINOR |
| fileName | Name of the file where the issue was found |
| lineNumber | Line number of the issue |
| ruleId | Unique rule identifier (e.g., SEC-001, SMELL-003) |
| category | Enum category (CODE_SMELL, DEAD_CODE, SECURITY, METRICS, NAMING, DUPLICATION) |
| suggestion | Recommended fix for the issue |

### HTML Dashboard (report.html)

The interactive dashboard includes:
- **Summary cards** showing total, critical, major, and minor counts
- **SVG donut chart** for severity distribution
- **Bar charts** for issues grouped by file and by category
- **Search bar** to filter defects by keyword
- **Dropdown filters** for severity, type, and file
- **Sortable table columns** (click any header to sort)
- **Dark/Light mode toggle**
- **CSV export button** to download the data

---

## Detection Rules Reference

### Code Smells (12 rules)

| Rule ID | Detection | Severity |
|---------|-----------|----------|
| SMELL-001 | Large class (too many methods) | MAJOR |
| SMELL-002 | Too many parameters in method | MINOR |
| SMELL-003 | Long method (excessive lines) | MAJOR |
| SMELL-004 | Empty catch block | MAJOR |
| SMELL-005 | Deeply nested code | MAJOR |
| SMELL-006 | Too many fields in class | MAJOR |
| SMELL-007 | God Class (high methods + high fields) | CRITICAL |
| SMELL-008 | Data Class (only getters/setters) | MINOR |
| SMELL-009 | Feature Envy (method uses external data excessively) | MINOR |
| SMELL-010 | Boolean parameter in method signature | MINOR |
| SMELL-011 | Magic numbers in code | MINOR |
| SMELL-012 | Catching generic Exception/Throwable | MINOR |

### Dead Code (5 rules)

| Rule ID | Detection | Severity |
|---------|-----------|----------|
| DEAD-001 | Unreachable code after return/throw | MAJOR |
| DEAD-002 | Unused local variables | MINOR |
| DEAD-003 | Unused imports | MINOR |
| DEAD-004 | Unused private methods | MAJOR |
| DEAD-005 | Unused private fields | MINOR |

### Security (9 rules)

| Rule ID | Detection | Severity |
|---------|-----------|----------|
| SEC-001 | Hardcoded passwords, API keys, tokens, secrets | CRITICAL |
| SEC-002 | SQL Injection via string concatenation | CRITICAL |
| SEC-003 | System.exit() misuse | MAJOR |
| SEC-004 | Insecure random number generator (java.util.Random) | MAJOR |
| SEC-005 | Resource leaks (streams/connections not in try-with-resources) | MAJOR |
| SEC-006 | Weak cryptographic algorithms (MD5, SHA1) | CRITICAL |
| SEC-007 | LDAP Injection patterns | CRITICAL |
| SEC-008 | Path Traversal detection | CRITICAL |
| SEC-009 | Command Injection via Runtime.exec() | CRITICAL |

### Naming Conventions (7 rules)

| Rule ID | Detection | Severity |
|---------|-----------|----------|
| NAME-001 | Class names not in PascalCase | MINOR |
| NAME-002 | Method names not in camelCase | MINOR |
| NAME-003 | Variable names not in camelCase | MINOR |
| NAME-004 | Parameter names not in camelCase | MINOR |
| NAME-005 | Constants not in UPPER_SNAKE_CASE | MINOR |
| NAME-006 | Single-letter variable names (non-loop) | MINOR |
| NAME-007 | Non-descriptive variable names (temp, data, obj, etc.) | MINOR |

### Metrics (5 rules)

| Rule ID | Detection | Severity |
|---------|-----------|----------|
| MET-001 | High cyclomatic complexity | MINOR / MAJOR / CRITICAL |
| MET-002 | High cognitive complexity (SonarQube algorithm) | MINOR / MAJOR / CRITICAL |
| MET-003 | Large file (excessive lines of code) | MAJOR |
| MET-004 | Low comment-to-code ratio | MINOR |
| MET-005 | Low Maintainability Index (Microsoft formula) | MINOR / MAJOR |

### Duplication (1 rule)

| Rule ID | Detection | Severity |
|---------|-----------|----------|
| DUP-001 | Duplicate method bodies across files (SHA-256 hash) | MAJOR |

---

## Technologies Used

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 17 | Core programming language |
| JavaParser | 3.25.8 | AST-based source code parsing |
| Jackson | 2.17.0 | JSON serialization for reports |
| Maven | 3.6+ | Build automation and dependency management |
| JUnit | 5.10.2 | Unit testing framework |

---

## License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## Author

**Pokala Gopi Lakshman**
- Email: pokala.gopilakshman@gmail.com
- GitHub: [github.com/pokala-gopi-lakshman](https://github.com/pokala-gopi-lakshman)
