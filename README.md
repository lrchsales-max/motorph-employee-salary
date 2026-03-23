# MotorPH Payroll — README

Quick guide to run and secure credentials.

**Project root:** work only inside this **`MotorPh/`** repository (sources, `resources/`, `pom.xml`). A parent folder may aggregate this module, but the submission app lives here.

**Class rubric (summary):** `employee` sees menu + employee info only; `payroll_staff` sees Process Payroll (one / all employees) with June–December semimonthly cutoffs. Login uses usernames `employee` and `payroll_staff` with password **`12345`** (hashed in `credentials_hashed.csv`). See `MotorPhPayrollAppRefactored` for the implemented flow.

Prerequisites
- Java 17+ (JDK 21 recommended)
- Maven (for build & tests)

Credentials
- A hashed credentials file is required: `resources/credentials_hashed.csv`.
- Format: CSV with header `username,password,role`. Password column uses `SHA256$<saltHex>$<hashHex>` (or bcrypt).
- To generate a hashed file from the plaintext sample, run the helper:

Windows (PowerShell):
```
javac -d out src/main/java/payroll/CredentialHashUtil.java
java -cp out payroll.CredentialHashUtil
```
This writes `resources/credentials_hashed.csv`.

- Usage
- By default the app looks for `resources/credentials_hashed.csv`. After generating the hashed file, either rename it to that path or update the `credFile` variable in `MotorPhPayrollAppRefactored`.
- Run tests:
```
mvn test
```
- Run the app (Maven exec or from IDE):
```
mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass="payroll.MotorPhPayrollAppRefactored"
```

Security notes
- Do NOT commit plaintext credentials to source control. Use only the hashed credentials file in the repo.
- Consider moving credentials to a secure store (env vars, vault, or DB) for production.

# MO-IT101-Group22
CP1 - MS2 Source Code

Basic Payroll Program

**QA:** See [`docs/QA_CASE_DOCUMENT.md`](docs/QA_CASE_DOCUMENT.md) (use this **`MotorPh`** folder as the project root for runs).

## Camu Submission

**Repository Name:** MO-IT101-Group22  
**Date Added:** 3/7/2025

## Program Details

This system is a Java payroll application that looks up employees and summarizes their attendance and pay by cutoff. It reads employee details and attendance from CSV files in the `resources` folder, prompts for an employee number, and for that employee it computes total hours worked per payroll cutoff (1–15 and 16–end of month) for June–December 2025. Hours are calculated with an 8:10 grace period, 5:00 PM cutoff, and a 1-hour lunch deduction. The program displays the employee’s info and a salary summary per cutoff, with placeholders for gross salary, SSS, PhilHealth, Pag-IBIG, tax, and net salary.

### Simple formula

- **Total Hours Worked** = Time Out − Time In, with rulings/conditions applied (grace period 8:10, cutoff 17:00, 1-hour lunch deduction, cap 8 hours).
- **Gross Salary** = Total Hours Worked × Hourly Rate.
- **Net Salary** = Gross Salary − Total Deductions. (Deductions apply only on the **2nd cutoff**; on the 1st cutoff, Net Salary = Gross Salary.)

### Deductions (team alignment)

All deductions—SSS, PhilHealth, Pag-IBIG, and tax—are based on the employee’s **one month gross salary**. These deductions are applied only on the employee’s **second cutoff** (16 to end of month) each month.

## Project Plan Link

[Project Plan (Google Doc)](https://docs.google.com/document/d/1TEosbn2ek39xev6Xo5ozyiKlUm8oBL6GdkAfJoELEks/edit?usp=sharing)

---

## How the Program Works
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
public class MotorPhPayrollApp {
    public static void main(String[] args) {

-MotorPhPayrollApp is the main class.
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
    int daysInMonth = YearMonth.of(2025, month).lengthOfMonth();

-Loops from June to December 2025.
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

if (year != 2025 || recordMonth != month) continue;

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
-Works only for year 2025 in this version.
-Cutoffs are 1–15 and 16–end of month.
