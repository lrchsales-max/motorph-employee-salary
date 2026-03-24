package payroll;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * MotorPH payroll — single-file, procedural style (no domain classes).
 * Reads employee and attendance data from CSV only; does not modify those files.
 */
public class MotorPhPayrollApp {

    private static final String[] MONTHS_NAMES = {
        "", "January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December"
    };

    private static final String RUBRIC_PASSWORD = "12345";
    private static final int MAX_ATTENDANCE_ROWS = 20000;
    private static final int MAX_YEAR_MONTH_PAIRS = 512;

    public static void main(String[] args) {
        String empFile = "resources/MotorPH_Employee Data - Employee Details.csv";
        String attFile = "resources/MotorPH_Employee Data - Attendance Record.csv";

        Scanner sc = new Scanner(System.in);
        String role = loginRubric(sc);
        if (role == null) {
            return;
        }

        if (role.equals("employee")) {
            runEmployeeSession(empFile, sc);
        } else {
            runPayrollStaffSession(empFile, attFile, sc);
        }
    }

    /**
     * Rubric: only usernames {@code employee} and {@code payroll_staff}, password {@code 12345};
     * one attempt; exact failure message; exit on failure.
     */
    static String loginRubric(Scanner sc) {
        System.out.print("Username: ");
        String u = sc.nextLine().trim();
        System.out.print("Password: ");
        String p = sc.nextLine().trim();

        if (!RUBRIC_PASSWORD.equals(p)) {
            System.out.println("Incorrect username and/or password.");
            return null;
        }
        if (u.equalsIgnoreCase("employee")) {
            return "employee";
        }
        if (u.equalsIgnoreCase("payroll_staff")) {
            return "payroll_staff";
        }
        System.out.println("Incorrect username and/or password.");
        return null;
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
            String[] row = new String[5];
            if (!findEmployeeRow(empFile, id, row)) {
                System.out.println("Employee number does not exist.");
                continue;
            }
            System.out.println();
            System.out.println("Employee # : " + row[0]);
            System.out.println("Employee Name : " + row[1] + ", " + row[2]);
            System.out.println("Birthday : " + row[3]);
        }
    }

    static void runPayrollStaffSession(String empFile, String attFile, Scanner sc) {
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("H:mm");
        String[] attEmp = new String[MAX_ATTENDANCE_ROWS];
        int[] attY = new int[MAX_ATTENDANCE_ROWS];
        int[] attM = new int[MAX_ATTENDANCE_ROWS];
        int[] attD = new int[MAX_ATTENDANCE_ROWS];
        LocalTime[] attIn = new LocalTime[MAX_ATTENDANCE_ROWS];
        LocalTime[] attOut = new LocalTime[MAX_ATTENDANCE_ROWS];
        int attN;
        try {
            attN = loadAttendance(attFile, timeFormat, attEmp, attY, attM, attD, attIn, attOut);
        } catch (IOException e) {
            System.out.println("Error reading attendance file.");
            return;
        }

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
                    return;
                }
                if (s == 1) {
                    System.out.print("Enter the employee number: ");
                    String id = sc.nextLine().trim();
                    String[] row = new String[5];
                    if (!findEmployeeRow(empFile, id, row)) {
                        System.out.println("Employee number does not exist.");
                        continue;
                    }
                    double rate = parseHourlyRate(row[4]);
                    printStaffPayroll(row[0], row[1], row[2], row[3], rate, attN, attEmp, attY, attM, attD, attIn, attOut);
                } else {
                    String[][] all;
                    try {
                        all = loadAllEmployeeRows(empFile);
                    } catch (IOException e) {
                        System.out.println("Error reading employee file.");
                        continue;
                    }
                    for (int i = 0; i < all.length; i++) {
                        double rate = parseHourlyRate(all[i][4]);
                        printStaffPayroll(all[i][0], all[i][1], all[i][2], all[i][3], rate,
                                attN, attEmp, attY, attM, attD, attIn, attOut);
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

    static String plainDouble(double v) {
        return new BigDecimal(Double.toString(v)).stripTrailingZeros().toPlainString();
    }

    /**
     * Same numeric value as {@link #plainDouble(double)} (no extra rounding), with comma grouping on the integer part
     * (e.g. {@code 15250.5} → {@code 15,250.5}).
     */
    static String plainDoubleWithGrouping(double v) {
        String s = new BigDecimal(Double.toString(v)).stripTrailingZeros().toPlainString();
        boolean neg = s.startsWith("-");
        if (neg) {
            s = s.substring(1);
        }
        int dot = s.indexOf('.');
        String intPart = dot < 0 ? s : s.substring(0, dot);
        String frac = dot < 0 ? "" : s.substring(dot);
        if (intPart.isEmpty()) {
            intPart = "0";
        }
        String grouped = groupIntegerWithCommas(intPart);
        if (neg) {
            grouped = "-" + grouped;
        }
        return grouped + frac;
    }

    static String groupIntegerWithCommas(String intPart) {
        int n = intPart.length();
        if (n <= 3) {
            return intPart;
        }
        int first = n % 3;
        if (first == 0) {
            first = 3;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(intPart, 0, first);
        for (int i = first; i < n; i += 3) {
            sb.append(',').append(intPart, i, i + 3);
        }
        return sb.toString();
    }

    /**
     * Uses combined 1st + 2nd cutoff gross ({@code monthlyGross}) for SSS/PhilHealth/Pag-IBIG brackets.
     * Withholding tax is on taxable income = monthly gross minus those three (per rubric sample).
     *
     * @return {@code [ SSS, PhilHealth employee share, Pag-IBIG employee share, withholding tax ]}
     */
    static double[] computeMonthlyDeductions(double monthlyGross) {
        if (Double.isNaN(monthlyGross) || Double.isInfinite(monthlyGross) || monthlyGross < 0) {
            return new double[] { 0, 0, 0, 0 };
        }
        double sss = sssEmployeeContribution(monthlyGross);
        double philHealth = philHealthEmployeeShare(monthlyGross);
        double pagIbig = pagIbigEmployeeShare(monthlyGross);
        double taxable = monthlyGross - sss - philHealth - pagIbig;
        if (taxable < 0) {
            taxable = 0;
        }
        double tax = withholdingTaxOnTaxable(taxable);
        return new double[] { sss, philHealth, pagIbig, tax };
    }

    /** SSS employee share from compensation brackets (MotorPH table). */
    static double sssEmployeeContribution(double monthlyGross) {
        if (monthlyGross < 3250) {
            return 135.0;
        }
        for (int k = 1; k <= 43; k++) {
            if (monthlyGross < 3250 + 500 * k) {
                return 157.5 + 22.5 * (k - 1);
            }
        }
        return 1125.0;
    }

    /** PhilHealth: 3% monthly premium, 50% employee; tiers per MotorPH table. */
    static double philHealthEmployeeShare(double monthlyGross) {
        double totalPremium;
        if (monthlyGross <= 10000) {
            totalPremium = 300.0;
        } else if (monthlyGross < 60000) {
            totalPremium = monthlyGross * 0.03;
            if (totalPremium > 1800) {
                totalPremium = 1800;
            }
        } else {
            totalPremium = 1800.0;
        }
        return totalPremium / 2.0;
    }

    /** Pag-IBIG employee: 1% (₱1,000–₱1,500), 2% (over ₱1,500), max ₱100. */
    static double pagIbigEmployeeShare(double monthlyGross) {
        if (monthlyGross < 1000) {
            return 0;
        }
        double raw;
        if (monthlyGross <= 1500) {
            raw = monthlyGross * 0.01;
        } else {
            raw = monthlyGross * 0.02;
        }
        return Math.min(raw, 100.0);
    }

    /** BIR-style monthly withholding on taxable income (after SSS, PhilHealth, Pag-IBIG). */
    static double withholdingTaxOnTaxable(double taxable) {
        if (taxable <= 20832) {
            return 0;
        }
        if (taxable < 33333) {
            return (taxable - 20833) * 0.20;
        }
        if (taxable < 66667) {
            return 2500.0 + (taxable - 33333) * 0.25;
        }
        if (taxable < 166667) {
            return 10833.0 + (taxable - 66667) * 0.30;
        }
        if (taxable < 666667) {
            return 40833.33 + (taxable - 166667) * 0.32;
        }
        return 200833.33 + (taxable - 666667) * 0.35;
    }

    static void printStaffPayroll(String empNo, String lastName, String firstName, String birthday, double hourlyRate,
            int attN, String[] attEmp, int[] attY, int[] attM, int[] attD, LocalTime[] attIn, LocalTime[] attOut) {
        int[] pairYears = new int[MAX_YEAR_MONTH_PAIRS];
        int[] pairMonths = new int[MAX_YEAR_MONTH_PAIRS];
        double[] firstHalf = new double[MAX_YEAR_MONTH_PAIRS];
        double[] secondHalf = new double[MAX_YEAR_MONTH_PAIRS];
        int pairCount = aggregateJuneToDecember(empNo, attN, attEmp, attY, attM, attD, attIn, attOut,
                pairYears, pairMonths, firstHalf, secondHalf);

        if (pairCount == 0) {
            System.out.println();
            System.out.println("No attendance records found for employee " + empNo + " (June–December).");
            return;
        }

        sortYearMonthPairs(pairYears, pairMonths, firstHalf, secondHalf, pairCount);

        for (int p = 0; p < pairCount; p++) {
            int yr = pairYears[p];
            int mon = pairMonths[p];
            double fh = firstHalf[p];
            double sh = secondHalf[p];
            String monthName = (mon >= 1 && mon <= 12) ? MONTHS_NAMES[mon] : ("Month " + mon);

            double grossFirst = fh * hourlyRate;
            double grossSecond = sh * hourlyRate;
            double monthlyGross = grossFirst + grossSecond;
            double[] ded = computeMonthlyDeductions(monthlyGross);
            double totalDeductions = ded[0] + ded[1] + ded[2] + ded[3];
            double netFirst = grossFirst;
            double netSecond = grossSecond - totalDeductions;

            int daysInMonth = YearMonth.of(yr, mon).lengthOfMonth();
            System.out.println();
            System.out.println("Employee # : " + empNo);
            System.out.println("Employee Name : " + lastName + ", " + firstName);
            System.out.println("Birthday : " + birthday);
            System.out.println(monthName + " " + yr + " - Cutoff Date: 1 to 15");
            System.out.println("Total Hours Worked : " + plainDouble(fh));
            System.out.println("Gross Salary: " + plainDoubleWithGrouping(grossFirst));
            System.out.println("Net Salary: " + plainDoubleWithGrouping(netFirst));

            System.out.println();
            System.out.println(monthName + " " + yr + " - Cutoff Date: 16 to " + daysInMonth);
            System.out.println("Total Hours Worked : " + plainDouble(sh));
            System.out.println("Gross Salary: " + plainDoubleWithGrouping(grossSecond));
            System.out.println("Deductions: ");
            System.out.println("  SSS: " + plainDoubleWithGrouping(ded[0]));
            System.out.println("  PhilHealth: " + plainDoubleWithGrouping(ded[1]));
            System.out.println("  Pag-IBIG: " + plainDoubleWithGrouping(ded[2]));
            System.out.println("  Tax: " + plainDoubleWithGrouping(ded[3]));
            System.out.println("Total Deductions: " + plainDoubleWithGrouping(totalDeductions));
            System.out.println("Net Salary: " + plainDoubleWithGrouping(netSecond));
        }
    }

    static int aggregateJuneToDecember(String empNo, int attN, String[] attEmp, int[] attY, int[] attM, int[] attD,
            LocalTime[] attIn, LocalTime[] attOut,
            int[] pairYears, int[] pairMonths, double[] firstHalf, double[] secondHalf) {
        int pairCount = 0;
        for (int i = 0; i < attN; i++) {
            if (!empNo.equals(attEmp[i])) {
                continue;
            }
            int m = attM[i];
            if (m < 6 || m > 12) {
                continue;
            }
            int y = attY[i];
            int idx = findPairIndex(pairYears, pairMonths, pairCount, y, m);
            if (idx < 0) {
                if (pairCount >= MAX_YEAR_MONTH_PAIRS) {
                    continue;
                }
                idx = pairCount;
                pairYears[idx] = y;
                pairMonths[idx] = m;
                firstHalf[idx] = 0;
                secondHalf[idx] = 0;
                pairCount++;
            }
            double h = computeHours(attIn[i], attOut[i]);
            int d = attD[i];
            if (d <= 15) {
                firstHalf[idx] += h;
            } else {
                secondHalf[idx] += h;
            }
        }
        return pairCount;
    }

    static int findPairIndex(int[] years, int[] months, int count, int y, int m) {
        for (int i = 0; i < count; i++) {
            if (years[i] == y && months[i] == m) {
                return i;
            }
        }
        return -1;
    }

    static void sortYearMonthPairs(int[] years, int[] months, double[] firstHalf, double[] secondHalf, int n) {
        for (int a = 0; a < n - 1; a++) {
            for (int b = 0; b < n - 1 - a; b++) {
                if (years[b] > years[b + 1] || (years[b] == years[b + 1] && months[b] > months[b + 1])) {
                    swapInt(years, b, b + 1);
                    swapInt(months, b, b + 1);
                    swapDouble(firstHalf, b, b + 1);
                    swapDouble(secondHalf, b, b + 1);
                }
            }
        }
    }

    static void swapInt(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    static void swapDouble(double[] a, int i, int j) {
        double t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    static int loadAttendance(String path, DateTimeFormatter timeFormat,
            String[] empNos, int[] ys, int[] ms, int[] ds, LocalTime[] ins, LocalTime[] outs) throws IOException {
        int i = 0;
        try (Reader reader = Files.newBufferedReader(Paths.get(path));
             CSVParser csv = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (CSVRecord rec : csv) {
                if (rec.size() < 6 || i >= MAX_ATTENDANCE_ROWS) {
                    continue;
                }
                String id = rec.get(0).trim();
                String dateStr = rec.get(3).trim();
                try {
                    String[] dparts = dateStr.split("/");
                    int mon = Integer.parseInt(dparts[0]);
                    int d = Integer.parseInt(dparts[1]);
                    int yr = Integer.parseInt(dparts[2]);
                    LocalTime in = parseTimeSafe(rec.get(4).trim(), timeFormat);
                    LocalTime out = parseTimeSafe(rec.get(5).trim(), timeFormat);
                    if (in == null || out == null) {
                        continue;
                    }
                    empNos[i] = id;
                    ys[i] = yr;
                    ms[i] = mon;
                    ds[i] = d;
                    ins[i] = in;
                    outs[i] = out;
                    i++;
                } catch (Exception ignored) {
                    // skip malformed row
                }
            }
        }
        return i;
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

    /**
     * row[0]=emp#, [1]=last, [2]=first, [3]=birthday, [4]=hourly rate string from CSV last column.
     */
    static boolean findEmployeeRow(String empFile, String inputEmpNo, String[] row) {
        try (Reader reader = Files.newBufferedReader(Paths.get(empFile));
             CSVParser csv = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (CSVRecord rec : csv) {
                String id = rec.size() > 0 ? rec.get(0).trim() : "";
                if (!id.equals(inputEmpNo)) {
                    continue;
                }
                row[0] = id;
                row[1] = rec.size() > 1 ? rec.get(1).trim() : "";
                row[2] = rec.size() > 2 ? rec.get(2).trim() : "";
                row[3] = rec.size() > 3 ? rec.get(3).trim() : "";
                row[4] = rec.size() > 0 ? rec.get(rec.size() - 1).trim() : "0";
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    static String[][] loadAllEmployeeRows(String empFile) throws IOException {
        int count = 0;
        try (Reader reader = Files.newBufferedReader(Paths.get(empFile));
             CSVParser csv = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (CSVRecord rec : csv) {
                String id = rec.size() > 0 ? rec.get(0).trim() : "";
                if (!id.isEmpty()) {
                    count++;
                }
            }
        }
        String[][] out = new String[count][5];
        int i = 0;
        try (Reader reader = Files.newBufferedReader(Paths.get(empFile));
             CSVParser csv = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (CSVRecord rec : csv) {
                String id = rec.size() > 0 ? rec.get(0).trim() : "";
                if (id.isEmpty()) {
                    continue;
                }
                out[i][0] = id;
                out[i][1] = rec.size() > 1 ? rec.get(1).trim() : "";
                out[i][2] = rec.size() > 2 ? rec.get(2).trim() : "";
                out[i][3] = rec.size() > 3 ? rec.get(3).trim() : "";
                out[i][4] = rec.size() > 0 ? rec.get(rec.size() - 1).trim() : "0";
                i++;
            }
        }
        return out;
    }

    static double parseHourlyRate(String rateStr) {
        try {
            return Double.parseDouble(rateStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

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
