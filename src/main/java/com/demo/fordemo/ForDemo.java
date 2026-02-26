/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.demo.fordemo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 *
 * @author aldrinjohntamayo
 */
public class ForDemo {

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

        // Read Attendance Records CSV
        // Nested loop: month ---> cutoff (1-15, 16-end-of-month)
        for (int month = 6; month <= 12; month++) { // June to December 2024
            double firstHalf = 0;
            double secondHalf = 0;
            int daysInMonth = YearMonth.of(2024, month).lengthOfMonth();

            try (BufferedReader br = new BufferedReader(new FileReader(attFile))) {

                br.readLine(); // Skip Header
                String line;

                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    String[] data = line.split(",");

                    if (!data[0].equals(empNo)) continue;

                    String[] dateParts = data[3].split("/");
                    int recordMonth = Integer.parseInt(dateParts[0]);
                    int day = Integer.parseInt(dateParts[1]);
                    int year = Integer.parseInt(dateParts[2]);

                    if (year != 2024 || recordMonth != month) continue;

                    LocalTime login = LocalTime.parse(data[4].trim(), timeFormat);
                    LocalTime logout = LocalTime.parse(data[5].trim(), timeFormat);

                    double hours = computeHours(login, logout);

                    if (day <= 15) firstHalf += hours;
                    else secondHalf += hours;
                }

            } catch (Exception e) {
                System.out.println("Error reading attendance file for month " + month);
                e.printStackTrace();
                continue;
            }

            String monthName = switch (month) {
                case 6 -> "June";
                case 7 -> "July";
                case 8 -> "August";
                case 9 -> "September";
                case 10 -> "October";
                case 11 -> "November";
                case 12 -> "December";
                default -> "Month " + month;
            };

            System.out.println("\nCutoff Date: " + monthName + " 1 to 15");
            System.out.println("Total Hours Worked : " + firstHalf);
            System.out.println("Gross Salary: ");
            System.out.println("Net Salary: ");

            System.out.println("\nCutoff Date: " + monthName + " 16 to " + daysInMonth);
            System.out.println("Total Hours Worked : " + secondHalf);
            System.out.println("Gross Salary: ");
            System.out.println("Deductions: ");
            System.out.println("    SSS: ");
            System.out.println("    PhilHealth: ");
            System.out.println("    Pag-IBIG: ");
            System.out.println("    Tax: ");
            System.out.println("Net Salary: ");
        }
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
