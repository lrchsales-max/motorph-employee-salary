package payroll;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

// Simple  MotorPH payroll app
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

        // read employee file
        try {
            BufferedReader br = new BufferedReader(new FileReader(empFile));
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts[0].equals(inputEmpNo)) {
                    empNo = parts[0];
                    lastName = parts[1];
                    firstName = parts[2];
                    birthday = parts[3];
                    String rateStr = parts[parts.length - 1].trim();
                    try { hourlyRate = Double.parseDouble(rateStr); } catch (Exception e) {}
                    found = true;
                    break;
                }
            }
            br.close();
        } catch (Exception e) {
            System.out.println("Error reading employee file.");
            return;
        }

        if (!found) {
            System.out.println("Employee does not exist.");
            return;
        }

        System.out.println();
        System.out.println("Employee # : " + empNo);
        System.out.println("Employee Name : " + lastName + ", " + firstName);
        System.out.println("Birthday : " + birthday);

        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("H:mm");

        // aggregate hours by year->month
        Map<Integer, Map<Integer, double[]>> data = new TreeMap<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(attFile));
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (!parts[0].equals(empNo)) continue;

                String[] dparts = parts[3].split("/");
                int mon = Integer.parseInt(dparts[0]);
                int d = Integer.parseInt(dparts[1]);
                int yr = Integer.parseInt(dparts[2]);

                LocalTime in = LocalTime.parse(parts[4].trim(), timeFormat);
                LocalTime out = LocalTime.parse(parts[5].trim(), timeFormat);
                double h = computeHours(in, out);

                if (!data.containsKey(yr)) data.put(yr, new TreeMap<Integer, double[]>());
                Map<Integer, double[]> months = data.get(yr);
                if (!months.containsKey(mon)) months.put(mon, new double[2]);
                double[] cut = months.get(mon);
                if (d <= 15) cut[0] += h; else cut[1] += h;
            }
            br.close();
        } catch (Exception e) {
            System.out.println("Error reading attendance file.");
            e.printStackTrace();
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

                double grossFirst = firstHalf * hourlyRate;
                double grossSecond = secondHalf * hourlyRate;
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

    // simple hours calculation
    static double computeHours(LocalTime login, LocalTime logout) {
        LocalTime grace = LocalTime.of(8,10);
        LocalTime cutoff = LocalTime.of(17,0);
        if (logout.isAfter(cutoff)) logout = cutoff;
        long minutes = Duration.between(login, logout).toMinutes();
        if (minutes > 60) minutes -= 60; else minutes = 0;
        double hours = minutes / 60.0;
        if (!login.isAfter(grace)) return 8.0;
        if (hours > 8.0) return 8.0;
        return hours;
    }
}

