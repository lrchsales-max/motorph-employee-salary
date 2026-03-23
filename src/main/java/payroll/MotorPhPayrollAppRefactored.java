package payroll;

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
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

// Refactored MotorPH payroll app using Apache Commons CSV and attendance caching.
public class MotorPhPayrollAppRefactored {

    private static final Logger logger = LoggerFactory.getLogger(MotorPhPayrollAppRefactored.class);

    private static final String[] MONTHS_NAMES = {
        "", "January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December"
    };

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
        String credPathEnv = System.getenv("MOTORPH_CREDENTIALS_PATH");
        String credJsonEnv = System.getenv("MOTORPH_CREDENTIALS_JSON");
        String credFile = credPathEnv != null && !credPathEnv.isBlank() ? credPathEnv : "resources/credentials_hashed.csv";

        Scanner sc = new Scanner(System.in);
        Credential session = loginRubric(credFile, credJsonEnv, sc);
        if (session == null) {
            return;
        }

        // Rubric: capabilities follow login username, not the role column (avoids wrong CSV sending
        // "employee" users to payroll output with cutoffs).
        logger.info("Authenticated as user: {}", session.username);
        if (session.username.equalsIgnoreCase("employee")) {
            runEmployeeSession(empFile, sc);
        } else if (session.username.equalsIgnoreCase("payroll_staff")) {
            runPayrollStaffSession(empFile, attFile, sc);
        } else {
            String r = normalizeStaffRole(session.role);
            if (r != null && r.equalsIgnoreCase("payroll_staff")) {
                runPayrollStaffSession(empFile, attFile, sc);
            } else if (r != null && r.equalsIgnoreCase("employee")) {
                runEmployeeSession(empFile, sc);
            } else {
                logger.warn("Unknown account '{}'. Exiting.", session.username);
            }
        }
    }

    /** Rubric: one login attempt; exact failure message. Returns verified credential or null. */
    static Credential loginRubric(String credFile, String credJsonEnv, Scanner sc) {
        Map<String, Credential> creds;
        if (credJsonEnv != null && !credJsonEnv.isBlank()) {
            creds = loadCredentialsFromJson(credJsonEnv);
        } else {
            creds = loadCredentials(credFile);
        }
        if (creds == null || creds.isEmpty()) {
            System.out.println("Incorrect username and/or password.");
            return null;
        }
        System.out.print("Username: ");
        String u = sc.nextLine().trim();
        System.out.print("Password: ");
        String p = sc.nextLine().trim();
        Credential c = creds.get(u.toLowerCase(Locale.ROOT));
        if (c == null || !verifyPassword(c, p)) {
            System.out.println("Incorrect username and/or password.");
            return null;
        }
        return c;
    }

    static String normalizeStaffRole(String role) {
        if (role != null && role.equalsIgnoreCase("admin")) {
            return "payroll_staff";
        }
        return role;
    }

    static boolean verifyPassword(Credential c, String plaintext) {
        String stored = c.password;
        if (stored.startsWith("$2a$") || stored.startsWith("$2y$") || stored.startsWith("$2b$")) {
            return BCrypt.checkpw(plaintext, stored);
        }
        if (stored.startsWith("SHA256$")) {
            return verifySha256(stored, plaintext);
        }
        return stored.equals(plaintext);
    }

    static void runEmployeeSession(String empFile, Scanner sc) {
        String[] opts = { "Enter your employee number", "Exit the program" };
        while (true) {
            int choice = promptMenu(sc, "Select an option:", opts);
            if (choice == 2) {
                return;
            }
            System.out.print("Enter your employee number: ");
            String id = sc.nextLine().trim();
            Employee emp = readEmployee(empFile, id);
            if (emp == null) {
                System.out.println("Employee number does not exist.");
                continue;
            }
            System.out.println();
            System.out.println("Employee # : " + emp.empNo);
            System.out.println("Employee Name : " + emp.lastName + ", " + emp.firstName);
            System.out.println("Birthday : " + emp.birthday);
        }
    }

    static void runPayrollStaffSession(String empFile, String attFile, Scanner sc) {
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("H:mm");
        Map<String, List<AttendanceRecord>> attendanceMap = readAllAttendance(attFile, timeFormat);
        String[] mainOpts = { "Process Payroll", "Exit the program" };
        String[] subOpts = { "One employee", "All employees", "Exit the program" };

        while (true) {
            int c = promptMenu(sc, "Select an option:", mainOpts);
            if (c == 2) {
                return;
            }
            while (true) {
                int s = promptMenu(sc, "Process Payroll — select an option:", subOpts);
                if (s == 3) {
                    break;
                }
                if (s == 1) {
                    System.out.print("Enter the employee number: ");
                    String id = sc.nextLine().trim();
                    Employee emp = readEmployee(empFile, id);
                    if (emp == null) {
                        System.out.println("Employee number does not exist.");
                        continue;
                    }
                    printStaffPayroll(emp, attendanceMap);
                } else {
                    List<Employee> all = readAllEmployees(empFile);
                    for (Employee emp : all) {
                        printStaffPayroll(emp, attendanceMap);
                    }
                }
            }
        }
    }

    static int promptMenu(Scanner sc, String title, String[] options) {
        while (true) {
            System.out.println();
            System.out.println(title);
            for (int i = 0; i < options.length; i++) {
                System.out.println((i + 1) + ". " + options[i]);
            }
            System.out.print("Enter choice: ");
            String line = sc.nextLine().trim();
            try {
                int c = Integer.parseInt(line);
                if (c >= 1 && c <= options.length) {
                    return c;
                }
            } catch (NumberFormatException ignored) {
                // retry
            }
            System.out.println("Invalid choice. Try again.");
        }
    }

    /** Plain decimal string for output (guideline: do not round off for display). */
    static String plainDouble(double v) {
        return new BigDecimal(Double.toString(v)).stripTrailingZeros().toPlainString();
    }

    /**
     * Guideline: add 1st- and 2nd-cutoff gross for the month before computing government deductions.
     * Amounts are placeholders (0) until contribution tables are implemented.
     */
    static double[] computeMonthlyDeductions(double monthlyGross) {
        double sss = 0;
        double philHealth = 0;
        double pagIbig = 0;
        double tax = 0;
        if (Double.isNaN(monthlyGross) || Double.isInfinite(monthlyGross)) {
            return new double[] { 0, 0, 0, 0 };
        }
        // TODO: use monthlyGross for SSS / PhilHealth / Pag-IBIG / tax tables
        return new double[] { sss, philHealth, pagIbig, tax };
    }

    /** June–December only; semimonthly cutoffs for payroll_staff view (rubric layout). */
    static void printStaffPayroll(Employee emp, Map<String, List<AttendanceRecord>> attendanceMap) {
        Map<Integer, Map<Integer, double[]>> data = aggregateHoursJuneToDecember(emp.empNo, attendanceMap);
        if (data.isEmpty()) {
            System.out.println();
            System.out.println("No attendance records found for employee " + emp.empNo + " (June–December).");
            return;
        }

        for (Integer yr : data.keySet()) {
            Map<Integer, double[]> months = data.get(yr);
            for (Integer mon : months.keySet()) {
                double firstHalf = months.get(mon)[0];
                double secondHalf = months.get(mon)[1];
                String monthName = (mon >= 1 && mon <= 12) ? MONTHS_NAMES[mon] : ("Month " + mon);

                double grossFirst = firstHalf * emp.hourlyRate;
                double grossSecond = secondHalf * emp.hourlyRate;
                double monthlyGross = grossFirst + grossSecond;
                double[] ded = computeMonthlyDeductions(monthlyGross);
                double totalDeductions = ded[0] + ded[1] + ded[2] + ded[3];
                double netFirst = grossFirst;
                double netSecond = grossSecond - totalDeductions;

                int daysInMonth = YearMonth.of(yr, mon).lengthOfMonth();
                System.out.println();
                System.out.println("Employee # : " + emp.empNo);
                System.out.println("Employee Name : " + emp.lastName + ", " + emp.firstName);
                System.out.println("Birthday : " + emp.birthday);
                System.out.println(monthName + " " + yr + " - Cutoff Date: 1 to 15");
                System.out.println("Total Hours Worked : " + plainDouble(firstHalf));
                System.out.println("Gross Salary: " + plainDouble(grossFirst));
                System.out.println("Net Salary: " + plainDouble(netFirst));

                System.out.println();
                System.out.println(monthName + " " + yr + " - Cutoff Date: 16 to " + daysInMonth);
                System.out.println("Total Hours Worked : " + plainDouble(secondHalf));
                System.out.println("Gross Salary: " + plainDouble(grossSecond));
                System.out.println("Deductions: ");
                System.out.println("  SSS: " + plainDouble(ded[0]));
                System.out.println("  PhilHealth: " + plainDouble(ded[1]));
                System.out.println("  Pag-IBIG: " + plainDouble(ded[2]));
                System.out.println("  Tax: " + plainDouble(ded[3]));
                System.out.println("Total Deductions: " + plainDouble(totalDeductions));
                System.out.println("Net Salary: " + plainDouble(netSecond));
            }
        }
    }

    static Map<Integer, Map<Integer, double[]>> aggregateHoursJuneToDecember(
            String empNo, Map<String, List<AttendanceRecord>> attendanceMap) {
        Map<Integer, Map<Integer, double[]>> data = new TreeMap<>();
        for (AttendanceRecord r : attendanceMap.getOrDefault(empNo, new ArrayList<>())) {
            if (r.month < 6 || r.month > 12) {
                continue;
            }
            double h = computeHours(r.in, r.out);
            data.computeIfAbsent(r.year, y -> new TreeMap<>());
            Map<Integer, double[]> months = data.get(r.year);
            months.computeIfAbsent(r.month, m -> new double[2]);
            double[] cut = months.get(r.month);
            if (r.day <= 15) {
                cut[0] += h;
            } else {
                cut[1] += h;
            }
        }
        return data;
    }

    static List<Employee> readAllEmployees(String empFile) {
        List<Employee> list = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Paths.get(empFile));
             CSVParser csv = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (CSVRecord rec : csv) {
                String id = rec.size() > 0 ? rec.get(0).trim() : "";
                if (id.isEmpty()) {
                    continue;
                }
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
                list.add(new Employee(id, firstName, lastName, birthday, hourlyRate));
            }
        } catch (IOException e) {
            logger.error("Error reading employee file: {}", e.getMessage(), e);
        }
        return list;
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
            if (txt.length() > 5) {
                try {
                    return LocalTime.parse(txt.substring(0, 5), fmt);
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
                creds.put(u.toLowerCase(Locale.ROOT), new Credential(u, p, r));
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
                    String pass = null;
                    String role = null;
                    if (body.contains("\"password\"")) {
                        pass = extractJsonField(body, "password");
                    }
                    if (body.contains("\"role\"")) {
                        role = extractJsonField(body, "role");
                    }
                    if (pass != null && role != null) {
                        creds.put(username.toLowerCase(Locale.ROOT), new Credential(username, pass, role));
                    }
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

    /**
     * Class guideline: work counted 8:00–17:00 only; logout after 17:00 ignored.
     * Arrival on or before 8:05 treated as 8:00. Lunch: −1 hour when span ≥ 4 hours.
     * Examples: 8:30–17:30 → 7.5 h; 8:05–17:00 → 8 h; 8:05–16:30 → 7.5 h.
     */
    static double computeHours(LocalTime login, LocalTime logout) {
        final LocalTime graceEnd = LocalTime.of(8, 5);
        final LocalTime dayStart = LocalTime.of(8, 0);
        final LocalTime dayEnd = LocalTime.of(17, 0);

        LocalTime effectiveIn = login;
        if (!login.isAfter(graceEnd)) {
            effectiveIn = dayStart;
        } else if (login.isBefore(dayStart)) {
            effectiveIn = dayStart;
        }

        LocalTime out = logout;
        if (out.isAfter(dayEnd)) {
            out = dayEnd;
        }

        long minutes = Duration.between(effectiveIn, out).toMinutes();
        if (minutes < 0) {
            minutes = 0;
        }
        if (minutes >= 240) {
            minutes -= 60;
        }
        if (minutes < 0) {
            minutes = 0;
        }

        double hours = minutes / 60.0;
        if (hours > 8.0) {
            return 8.0;
        }
        return hours;
    }
}
