/*
 * MotorPH Payroll App - MO-IT101-Group22
 */

package com.motorph.payroll;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.Map;
import java.util.TreeMap;

/**
 * Basic MotorPH employee payroll: lookup, hours per cutoff, gross/net salary.
 */
public class MotorPhPayrollApp {

    public static void main(String[] args) {
        String empFile = "resources/MotorPH_Employee Data - Employee Details.csv";
        String attFile = "resources/MotorPH_Employee Data - Attendance Record.csv";

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter Employee #: ");
        String inputEmpNo = sc.nextLine();

        String empNo = "";
        String firstName = "";
        String lastName = "";
        String birthday = "";
        double hourlyRate = 0;
        boolean found = false;

        // Read Employee Details CSV
        try (BufferedReader br = new BufferedReader(new FileReader(empFile))) {

            br.readLine(); // Skip Header
            String line;

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] data = line.split(",");

                if (data[0].equals(inputEmpNo)) {
                    empNo = data[0];
                    lastName = data[1];
                    firstName = data[2];
                    birthday = data[3];
                    String rateStr = data[data.length - 1].trim();
                    try {
                        hourlyRate = Double.parseDouble(rateStr);
                    } catch (NumberFormatException ignored) { }
                    found = true;
                    break;
                }
            }

        } catch (Exception e) {
            System.out.println("Error reading employee file.");
            return;
        }

        if (!found) {
            System.out.println("Employee does not exist.");
            return;
        }

        System.out.println("\n===================================");
        System.out.println("Employee # : " + empNo);
        System.out.println("Employee Name : " + lastName + ", " + firstName);
        System.out.println("Birthday : " + birthday);
        System.out.println("===================================");

        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("H:mm");

        // Read Attendance Records CSV (aggregate by year -> month -> cutoff)
        Map<Integer, Map<Integer, double[]>> hoursByYearMonth = new TreeMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(attFile))) {
            br.readLine(); // Skip Header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] data = line.split(",");
                if (!data[0].equals(empNo)) continue;

                String[] dateParts = data[3].split(\"/\");
                int recordMonth = Integer.parseInt(dateParts[0]);
                int day = Integer.parseInt(dateParts[1]);
                int year = Integer.parseInt(dateParts[2]);

                LocalTime login = LocalTime.parse(data[4].trim(), timeFormat);
                LocalTime logout = LocalTime.parse(data[5].trim(), timeFormat);
                double hours = computeHours(login, logout);

                hoursByYearMonth
                    .computeIfAbsent(year, y -> new TreeMap<>())
                    .computeIfAbsent(recordMonth, m -> new double[2]);

                double[] cutoffs = hoursByYearMonth.get(year).get(recordMonth);
                if (day <= 15) cutoffs[0] += hours;
                else cutoffs[1] += hours;
            }
        } catch (Exception e) {
            System.out.println(\"Error reading attendance file.\");
            e.printStackTrace();
        }

        // Display results for each year -> month found in the attendance file
        for (Map.Entry<Integer, Map<Integer, double[]>> yearEntry : hoursByYearMonth.entrySet()) {
            int year = yearEntry.getKey();
            for (Map.Entry<Integer, double[]> monthEntry : yearEntry.getValue().entrySet()) {
                int month = monthEntry.getKey();
                double firstHalf = monthEntry.getValue()[0];
                double secondHalf = monthEntry.getValue()[1];
                int daysInMonth = YearMonth.of(year, month).lengthOfMonth();

                String monthName = switch (month) {
                    case 1 -> \"January\";
                    case 2 -> \"February\";
                    case 3 -> \"March\";
                    case 4 -> \"April\";
                    case 5 -> \"May\";
                    case 6 -> \"June\";
                    case 7 -> \"July\";
                    case 8 -> \"August\";
                    case 9 -> \"September\";
                    case 10 -> \"October\";
                    case 11 -> \"November\";
                    case 12 -> \"December\";
                    default -> \"Month \" + month;
                };

                // Gross Salary = Total Hours Worked * Hourly Rate
                double grossFirst = firstHalf * hourlyRate;
                double grossSecond = secondHalf * hourlyRate;
                // Deductions only on 2nd cutoff; Net = Gross - Total Deductions (placeholders for now)
                double totalDeductions = 0; // TODO: SSS + PhilHealth + Pag-IBIG + Tax based on one-month gross
                double netFirst = grossFirst; // No deductions on 1st cutoff
                double netSecond = grossSecond - totalDeductions;

                System.out.println(\"\\n\" + monthName + \" \" + year + \" - Cutoff Date: 1 to 15\");\n+                System.out.println(\"Total Hours Worked : \" + firstHalf);\n+                System.out.println(\"Gross Salary: \" + String.format(\"%.2f\", grossFirst));\n+                System.out.println(\"Net Salary: \" + String.format(\"%.2f\", netFirst));\n+\n+                System.out.println(\"\\n\" + monthName + \" \" + year + \" - Cutoff Date: 16 to \" + daysInMonth);\n+                System.out.println(\"Total Hours Worked : \" + secondHalf);\n+                System.out.println(\"Gross Salary: \" + String.format(\"%.2f\", grossSecond));\n+                // Deductions (SSS, PhilHealth, Pag-IBIG, Tax) based on one-month gross salary; applied on second cutoff only\n+                System.out.println(\"Deductions: \");\n+                System.out.println(\"    SSS: \");\n+                System.out.println(\"    PhilHealth: \");\n+                System.out.println(\"    Pag-IBIG: \");\n+                System.out.println(\"    Tax: \");\n+                System.out.println(\"Net Salary: \" + String.format(\"%.2f\", netSecond));\n+            }\n+        }\n*** End Patch"}
    }

    // Calculate Hours Worked
    static double computeHours(LocalTime login, LocalTime logout) {

        LocalTime graceTime = LocalTime.of(8, 10);
        LocalTime cutoffTime = LocalTime.of(17, 0);

        // Apply 17:00 cutoff
        if (logout.isAfter(cutoffTime)) {
            logout = cutoffTime;
        }

        long minutesWorked = Duration.between(login, logout).toMinutes();

        // Deduct lunch (if total worked is more than 1 hour)
        if (minutesWorked > 60) {
            minutesWorked -= 60;
        } else {
            minutesWorked = 0;
        }

        double hours = minutesWorked / 60.0;

        // Grace period rule
        if (!login.isAfter(graceTime)) {
            return 8.0;
        }

        // Return hours worked, capped at 8
        return Math.min(hours, 8.0);
    }
}
