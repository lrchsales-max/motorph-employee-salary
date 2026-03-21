package com.motorph.payroll;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

// Refactored MotorPH payroll app using Apache Commons CSV and attendance caching.
public class MotorPhPayrollAppRefactored {

    private static final Logger logger = LoggerFactory.getLogger(MotorPhPayrollAppRefactored.class);

    static class Employee {
        String empNo;
        String firstName;
        String lastName;
        String birthday;
        double hourlyRate;
        Employee(String empNo, String firstName, String lastName, String birthday, double hourlyRate) {
            this.empNo = empNo;
            this.firstName = firstName;
            this.lastName = lastName;
            this.birthday = birthday;
            this.hourlyRate = hourlyRate;
        }
    }

    static class AttendanceRecord {
        String empNo;
        int year, month, day;
        LocalTime in, out;
        AttendanceRecord(String empNo, int year, int month, int day, LocalTime in, LocalTime out) {
            this.empNo = empNo;
            this.year = year;
            this.month = month;
            this.day = day;
            this.in = in;
            this.out = out;
        }
    }

    static class Credential {
        String username;
        String password;
        String role;
        Credential(String u, String p, String r) { username = u; password = p; role = r; }
    }

    public static void main(String[] args) {
        String empFile = "resources/MotorPH_Employee Data - Employee Details.csv";
        String attFile = "resources/MotorPH_Employee Data - Attendance Record.csv";
        // credential source: prefer env path, then env JSON, then bundled file
        String credPathEnv = System.getenv("MOTORPH_CREDENTIALS_PATH");
        String credJsonEnv = System.getenv("MOTORPH_CREDENTIALS_JSON");
        String credFile = credPathEnv != null && !credPathEnv.isBlank() ? credPathEnv : "resources/credentials_hashed.csv";

        Scanner sc = new Scanner(System.in);
        String role = null;
        if (credJsonEnv != null && !credJsonEnv.isBlank()) {
            Map<String, Credential> creds = loadCredentialsFromJson(credJsonEnv);
            role = loginWithMap(creds, sc);
        } else {
            role = login(credFile, sc);
        }
        if (role == null) {
            logger.warn("Authentication failed after maximum attempts. Exiting.");
            return;
        }

        logger.info("Authenticated as: {}", role);
        System.out.print("Enter Employee #: ");
        String inputEmpNo = sc.nextLine();

        Employee emp = readEmployee(empFile, inputEmpNo);
        if (emp == null) {
            System.out.println("Employee does not exist.");
            return;
        }

        System.out.println();
        System.out.println("Employee # : " + emp.empNo);
        System.out.println("Employee Name : " + emp.lastName + ", " + emp.firstName);
        System.out.println("Birthday : " + emp.birthday);

        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("H:mm");

        Map<String, List<AttendanceRecord>> attendanceMap = readAllAttendance(attFile, timeFormat);
        List<AttendanceRecord> empRecords = attendanceMap.getOrDefault(emp.empNo, new ArrayList<>());
        if (empRecords.isEmpty()) {
            System.out.println("No attendance records found for employee " + emp.empNo);
            return;
        }

        // aggregate hours by year->month
        Map<Integer, Map<Integer, double[]>> data = new TreeMap<>();
        for (AttendanceRecord r : empRecords) {
            double h = computeHours(r.in, r.out);
            data.computeIfAbsent(r.year, y -> new TreeMap<>());
            Map<Integer, double[]> months = data.get(r.year);
            months.computeIfAbsent(r.month, m -> new double[2]);
            double[] cut = months.get(r.month);
            if (r.day <= 15) cut[0] += h; else cut[1] += h;
        }

        String[] monthsNames = {"", "January","February","March","April","May","June","July","August","September","October","November","December"};

        // print payroll per month/year
        for (Integer yr : data.keySet()) {
            Map<Integer, double[]> months = data.get(yr);
            for (Integer mon : months.keySet()) {
                double firstHalf = months.get(mon)[0];
                double secondHalf = months.get(mon)[1];
                int daysInMonth = YearMonth.of(yr, mon).lengthOfMonth();

                String monthName = (mon >=1 && mon <=12) ? monthsNames[mon] : ("Month " + mon);

                double grossFirst = firstHalf * emp.hourlyRate;
                double grossSecond = secondHalf * emp.hourlyRate;
                double totalDeductions = 0; // TODO: compute deductions
                double netFirst = grossFirst;
                double netSecond = grossSecond - totalDeductions;

                System.out.println();
                System.out.println(monthName + " " + yr + " - Cutoff Date: 1 to 15");
                System.out.println("Total Hours Worked : " + firstHalf);
                System.out.println("Gross Salary: " + String.format("%.2f", grossFirst));
                System.out.println("Net Salary: " + String.format("%.2f", netFirst));

                System.out.println();
                System.out.println(monthName + " " + yr + " - Cutoff Date: 16 to " + daysInMonth);
                System.out.println("Total Hours Worked : " + secondHalf);
                System.out.println("Gross Salary: " + String.format("%.2f", grossSecond));
                System.out.println("Deductions: ");
                System.out.println("  SSS: ");
                System.out.println("  PhilHealth: ");
                System.out.println("  Pag-IBIG: ");
                System.out.println("  Tax: ");
                System.out.println("Net Salary: " + String.format("%.2f", netSecond));
            }
        }
    }

    // Read employee details for a given employee number using Apache Commons CSV.
    static Employee readEmployee(String empFile, String inputEmpNo) {
        try (Reader reader = Files.newBufferedReader(Paths.get(empFile));
             CSVParser csv = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (CSVRecord rec : csv) {
                String id = rec.size() > 0 ? rec.get(0).trim() : "";
                if (id.equals(inputEmpNo)) {
                    String lastName = rec.size() > 1 ? rec.get(1).trim() : "";
                    String firstName = rec.size() > 2 ? rec.get(2).trim() : "";
                    String birthday = rec.size() > 3 ? rec.get(3).trim() : "";
                    String rateStr = rec.size() > 0 ? rec.get(rec.size() - 1).trim() : "0";
                    double hourlyRate = 0;
                    try {
                        hourlyRate = Double.parseDouble(rateStr);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid hourly rate '{}' for employee {}. Defaulting to 0.0", rateStr, id);
                    }
                    return new Employee(id, firstName, lastName, birthday, hourlyRate);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading employee file: {}", e.getMessage(), e);
        }
        return null;
    }

    // Read entire attendance CSV into a map keyed by empNo (attendance caching).
    static Map<String, List<AttendanceRecord>> readAllAttendance(String attFile, DateTimeFormatter timeFormat) {
        Map<String, List<AttendanceRecord>> map = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(Paths.get(attFile));
             CSVParser csv = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (CSVRecord rec : csv) {
                if (rec.size() < 6) continue;
                String id = rec.get(0).trim();
                String dateStr = rec.get(3).trim();
                try {
                    String[] dparts = dateStr.split("/");
                    int mon = Integer.parseInt(dparts[0]);
                    int d = Integer.parseInt(dparts[1]);
                    int yr = Integer.parseInt(dparts[2]);
                    LocalTime in = parseTimeSafe(rec.get(4).trim(), timeFormat);
                    LocalTime out = parseTimeSafe(rec.get(5).trim(), timeFormat);
                    if (in == null || out == null) continue;
                    AttendanceRecord ar = new AttendanceRecord(id, yr, mon, d, in, out);
                    map.computeIfAbsent(id, k -> new ArrayList<>()).add(ar);
                } catch (Exception e) {
                    logger.warn("Skipping malformed attendance row: {}", rec.toString());
                }
            }
        } catch (IOException e) {
            logger.error("Error reading attendance file: {}", e.getMessage(), e);
        }
        return map;
    }

    static LocalTime parseTimeSafe(String txt, DateTimeFormatter fmt) {
        try {
            return LocalTime.parse(txt, fmt);
        } catch (DateTimeParseException e) {
            // try trimming seconds if present (e.g., H:mm:ss -> H:mm)
            if (txt.length() > 5) {
                try {
                    return LocalTime.parse(txt.substring(0,5), fmt);
                } catch (Exception ex) {
                    return null;
                }
            }
            return null;
        }
    }

    // Load credentials from CSV into a map username -> Credential
    static Map<String, Credential> loadCredentials(String credFile) {
        Map<String, Credential> creds = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(Paths.get(credFile));
             CSVParser csv = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (CSVRecord rec : csv) {
            if (rec.size() < 3) continue;
                String u = rec.get(0).trim();
                String p = rec.get(1).trim();
                String r = rec.get(2).trim();
                creds.put(u, new Credential(u, p, r));
            }
        } catch (IOException e) {
            logger.warn("Could not read credentials file: {}", e.getMessage());
        }
        return creds;
    }

    // Load credentials from a JSON string where format is {"username":{"password":"...","role":"..."}, ...}
    static Map<String, Credential> loadCredentialsFromJson(String json) {
        Map<String, Credential> creds = new HashMap<>();
        try {
            // Basic JSON parsing without adding new deps: expect simple flat JSON
            // Example: {"payroll_staff":{"password":"SHA256$...","role":"admin"}, "employee":{...}}
            String s = json.trim();
            if (s.startsWith("{") && s.endsWith("}")) {
                s = s.substring(1, s.length() - 1).trim();
                String[] entries = s.split("},");
                for (String e : entries) {
                    if (!e.contains(":")) continue;
                    String[] kv = e.split(":", 2);
                    String username = kv[0].trim().replaceAll("^\"|\"$", "");
                    String body = kv[1].trim();
                    if (body.endsWith("}")) body = body.substring(0, body.length());
                    // find password and role fields
                    String pass = null, role = null;
                    if (body.contains("\"password\"")) {
                        pass = extractJsonField(body, "password");
                    }
                    if (body.contains("\"role\"")) {
                        role = extractJsonField(body, "role");
                    }
                    if (pass != null && role != null) creds.put(username, new Credential(username, pass, role));
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to parse credentials JSON: {}", ex.getMessage());
        }
        return creds;
    }

    static String extractJsonField(String body, String field) {
        int idx = body.indexOf("\"" + field + "\"");
        if (idx < 0) return null;
        int colon = body.indexOf(':', idx);
        if (colon < 0) return null;
        int start = body.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = body.indexOf('"', start + 1);
        if (end < 0) return null;
        return body.substring(start + 1, end);
    }

    // Login using an in-memory credential map (used when credentials provided via JSON env)
    static String loginWithMap(Map<String, Credential> creds, Scanner sc) {
        if (creds == null || creds.isEmpty()) {
            logger.error("No credentials available.");
            return null;
        }
        int attempts = 0;
        while (attempts < 3) {
            System.out.print("Username: ");
            String u = sc.nextLine().trim();
            System.out.print("Password: ");
            String p = sc.nextLine().trim();
            Credential c = creds.get(u);
            if (c != null) {
                String stored = c.password;
                if (stored.startsWith("SHA256$")) {
                    if (verifySha256(stored, p)) return c.role;
                } else if (stored.startsWith("$2a$") || stored.startsWith("$2y$") || stored.startsWith("$2b$")) {
                    if (BCrypt.checkpw(p, stored)) return c.role;
                } else {
                    logger.warn("Credential format for user '{}' is unsupported; skipping.", u);
                }
            } else {
                System.out.println("Invalid username or password. Try again.");
            }
            attempts++;
        }
        return null;
    }

    // Simple console login: 3 attempts
    static String login(String credFile, Scanner sc) {
        Map<String, Credential> creds = loadCredentials(credFile);
        if (creds.isEmpty()) {
            logger.error("No credentials available.");
            return null;
        }
        int attempts = 0;
        while (attempts < 3) {
            System.out.print("Username: ");
            String u = sc.nextLine().trim();
            System.out.print("Password: ");
            String p = sc.nextLine().trim();
            Credential c = creds.get(u);
            if (c != null) {
                String stored = c.password;
                if (stored.startsWith("$2a$") || stored.startsWith("$2y$") || stored.startsWith("$2b$")) {
                    if (BCrypt.checkpw(p, stored)) return c.role;
                } else if (stored.startsWith("SHA256$")) {
                    // format: SHA256$saltHex$hashHex where hash = SHA256(salt + password)
                    if (verifySha256(stored, p)) return c.role;
                } else {
                    logger.warn("Credential format for user '{}' is unsupported; skipping.", u);
                }
            } else {
                System.out.println("Invalid username or password. Try again.");
            }
            attempts++;
        }
        return null;
    }

    static boolean verifySha256(String stored, String candidate) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 3) return false;
            String saltHex = parts[1];
            String hashHex = parts[2];
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((saltHex + candidate).getBytes("UTF-8"));
            String candHex = bytesToHex(digest);
            return candHex.equalsIgnoreCase(hashHex);
        } catch (Exception e) {
            logger.error("Error verifying SHA256 credential: {}", e.getMessage(), e);
            return false;
        }
    }

    static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    // compute hours with windowing, grace, and only deduct lunch for sufficiently long shifts
    static double computeHours(LocalTime login, LocalTime logout) {
        LocalTime grace = LocalTime.of(8,10);
        LocalTime cutoff = LocalTime.of(17,0);
        if (logout.isAfter(cutoff)) logout = cutoff;
        long minutes = Duration.between(login, logout).toMinutes();
        // Only apply 60-minute lunch deduction if the employee worked at least 4 hours (240 minutes)
        if (minutes >= 240) minutes -= 60;
        else if (minutes < 0) minutes = 0;
        double hours = minutes / 60.0;
        if (!login.isAfter(grace)) return 8.0;
        if (hours > 8.0) return 8.0;
        return hours;
    }
}

