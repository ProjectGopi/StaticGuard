import java.io.*;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Enterprise-grade user management service.
 * This file intentionally contains various code issues for static analysis testing.
 */
public class EnterpriseUserService {

    private String password = "admin123";  // SEC-001: Hardcoded password
    private String apiKey = "sk-abc123xyz";  // SEC-001: Hardcoded API key
    private String secretToken = "Bearer eyJhbGciOi";  // SEC-001: Hardcoded token
    private int unusedField = 42;  // DEAD-005: Unused private field (if made private)

    // SMELL-002: Too many parameters (>3)
    // SMELL-010: Boolean parameter smell
    public List<String> getUsers(int roleId, String sortOrder, boolean includeInactive, int limit, int offset) {

        List<String> users = new ArrayList<>();
        int unusedCounter = 0;  // DEAD-002: Unused variable
        int temp = 5;  // NAME-007: Non-descriptive name

        try {
            Connection conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/app", "root", password);

            Statement stmt = conn.createStatement();

            // SEC-002: SQL Injection via string concatenation
            String query = "SELECT * FROM users WHERE role=" + roleId;

            if (includeInactive) {
                query += " OR status='inactive'";
            }

            if (sortOrder != null && sortOrder.equals("DESC")) {
                query += " ORDER BY created_at DESC";
            } else {
                query += " ORDER BY created_at ASC";
            }

            query += " LIMIT " + limit + " OFFSET " + offset;

            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {

                String name = rs.getString("name");

                // SMELL-005: Deeply nested code (4+ levels)
                if (name != null && !name.isEmpty()) {

                    if (name.startsWith("A")) {
                        if (name.length() > 3) {
                            if (name.contains("admin")) {
                                users.add(name.toUpperCase());
                            }
                        }
                    } else if (name.startsWith("B")) {
                        users.add(name.toLowerCase());
                    } else {
                        users.add(name);
                    }

                } else {
                    continue;
                }
            }

            conn.close();

        } catch (Exception e) { }  // SMELL-004: Empty catch block + SMELL-012: Generic Exception

        return users;
    }

    public void deleteUser(int userId) {

        if (userId <= 0) {
            return;
        }

        if (userId == 999) {
            return;
            // DEAD-001: Unreachable code after return
            System.out.println("This is unreachable");
        }

        System.out.println("Deleting user: " + userId);

        if (userId > 1000) {
            System.exit(1);  // SEC-003: System.exit misuse
        }
    }

    // SMELL-002: Too many parameters + SMELL-010: Boolean parameter
    public boolean authenticate(String username, String password, String ipAddress, boolean rememberMe) {

        // SEC-004: Insecure Random usage
        Random random = new Random();
        int sessionId = random.nextInt(100000);

        if (username == null || password == null) {
            return false;
        }

        // SMELL-011: Magic numbers (100000, 999)
        if (username.equals("admin") && password.equals("admin")) {
            return true;
        }

        if (rememberMe) {
            if (ipAddress != null && ipAddress.startsWith("192.")) {
                return true;
            }
        }

        return false;
    }

    // SEC-006: Weak cryptographic algorithm
    public String hashPassword(String pwd) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(pwd.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // SEC-005: Resource leak (FileInputStream not in try-with-resources)
    public String readConfig(String path) {
        try {
            FileInputStream fis = new FileInputStream(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String content = reader.readLine();
            return content;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // DEAD-004: Unused private method
    private void legacyCleanup() {
        System.out.println("Cleaning up...");
    }

    // DEAD-004: Another unused private method
    private int computeHash(String input) {
        return input.hashCode();
    }

    // SMELL-008: Data class pattern (only getters/setters)
    // This inner class acts as a data class
    public String getUserName() { return "test"; }
    public void setUserName(String name) { }
    public String getEmail() { return "test@test.com"; }
    public void setEmail(String email) { }
    public int getAge() { return 25; }
    public void setAge(int age) { }
    public String getRole() { return "user"; }
    public void setRole(String role) { }
    public String getStatus() { return "active"; }
    public void setStatus(String status) { }
    public String getDepartment() { return "IT"; }
    public void setDepartment(String dept) { }
    public String getPhone() { return "1234567890"; }
    public void setPhone(String phone) { }
    public String getAddress() { return "123 Main St"; }
    public void setAddress(String addr) { }
}
