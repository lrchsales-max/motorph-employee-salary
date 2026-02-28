# ForDemo
CP1 - MS2 Source Code

Basic Payroll Program

This program reads employee information and attendance records from CSV files, calculates the total hours worked per payroll cutoff, and displays a simple salary summary.

How the Program Works
1. Imports
import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

-BufferedReader and FileReader are used to read files line by line.
-LocalTime, Duration are used to handle time and compute hours worked.
-YearMonth helps find the number of days in a month.
-DateTimeFormatter formats time strings.
-Scanner is used to get input from the user.

2. Main Class and Method
public class ForDemo {
    public static void main(String[] args) {

-ForDemo is the main class.
-main method is the entry point of the program.

3. File Paths and Scanner
String empFile = "resources/MotorPH_Employee Data - Employee Details.csv";
String attFile = "resources/MotorPH_Employee Data - Attendance Record.csv";
Scanner sc = new Scanner(System.in);

-empFile and attFile store paths to CSV files.
-Scanner sc is used to read user input from the keyboard.

4. Get Employee Number
System.out.print("Enter Employee #: ");
String inputEmpNo = sc.nextLine();

-Program asks the user to enter an employee number.

5. Prepare Variables
String empNo = "";
String firstName = "";
String lastName = "";
String birthday = "";
boolean found = false;

-Variables to store employee information.
-found will check if the employee exists in the file.

6. Read Employee Details
try (BufferedReader br = new BufferedReader(new FileReader(empFile))) {
    br.readLine(); // Skip header
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

-Reads employee CSV line by line.
-Skips the first line (header).
-Splits each line by comma.
-Compares employee number to user input.
-Stores employee info if found.

7. Check if Employee Exists
if (!found) {
    System.out.println("Employee does not exist.");
    return;
}

-If employee not found, program stops.

8. Display Employee Info
System.out.println("\n===================================");
System.out.println("Employee # : " + empNo);
System.out.println("Employee Name : " + lastName + ", " + firstName);
System.out.println("Birthday : " + birthday);
System.out.println("===================================");

-Shows employee number, name, and birthday.

9. Prepare Time Format
DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("H:mm");

-Tells Java how to read time from the CSV (hours:minutes).

10. Process Attendance by Month
for (int month = 6; month <= 12; month++) {
    double firstHalf = 0;
    double secondHalf = 0;
    int daysInMonth = YearMonth.of(2024, month).lengthOfMonth();

-Loops from June to December 2024.
-firstHalf and secondHalf store total hours for payroll cutoffs.
-daysInMonth finds how many days are in the month.

11. Read Attendance File
try (BufferedReader br = new BufferedReader(new FileReader(attFile))) {
    br.readLine(); // Skip Header
    String line;
    while ((line = br.readLine()) != null) {
        if (line.trim().isEmpty()) continue;
        String[] data = line.split(",");
        if (!data[0].equals(empNo)) continue;

-Reads the attendance CSV line by line.
-Skips empty lines.
-Only processes lines that match the employee number.

12. Parse Date and Time
String[] dateParts = data[3].split("/");
int recordMonth = Integer.parseInt(dateParts[0]);
int day = Integer.parseInt(dateParts[1]);
int year = Integer.parseInt(dateParts[2]);

if (year != 2024 || recordMonth != month) continue;

LocalTime login = LocalTime.parse(data[4].trim(), timeFormat);
LocalTime logout = LocalTime.parse(data[5].trim(), timeFormat);

double hours = computeHours(login, logout);

-Splits date into month, day, year.
-Only uses records from the correct month and year.
-Reads login and logout time.
-Calls computeHours to calculate hours worked.

13. Add Hours to Cutoffs
if (day <= 15) firstHalf += hours;
else secondHalf += hours;

-Adds hours to first or second half of the month.

14. Display Payroll Summary
String monthName = switch (month) { ... };

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

-Shows hours worked for both cutoffs.
-Placeholder lines for gross salary, deductions, and net salary.

15. Compute Hours Method
static double computeHours(LocalTime login, LocalTime logout) {
    LocalTime graceTime = LocalTime.of(8, 10);
    LocalTime cutoffTime = LocalTime.of(17, 0);

    if (logout.isAfter(cutoffTime)) {
        logout = cutoffTime;
    }

    long minutesWorked = Duration.between(login, logout).toMinutes();

    if (minutesWorked > 60) {
        minutesWorked -= 60; // Lunch break
    } else {
        minutesWorked = 0;
    }

    double hours = minutesWorked / 60.0;

    if (!login.isAfter(graceTime)) {
        return 8.0; // Full 8 hours if on time
    }

    return Math.min(hours, 8.0);
}

-Sets grace period and cutoff time.
-Limits logout time to 5 PM.
-Subtracts 1 hour for lunch if work is more than 1 hour.
-Returns total hours worked, capped at 8 hours.

Notes
-CSV files must exist in the resources folder.
-The program currently shows placeholders for salary calculation; you can add formulas later.
-Works only for year 2024 in this version.
-Cutoffs are 1–15 and 16–end of month.
