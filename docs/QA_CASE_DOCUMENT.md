# MotorPH Payroll — QA Case Document

**Repository location:** `ForDemo/MotorPh/` (use this folder as the project root for QA)  
**Project:** MotorPH Employee Salary / Payroll  
**Document type:** Test plan + test case matrix (fill during execution)  
**Version:** 1.2 (MotorPh copy)  
**Date:** _______________  
**Prepared by (Dev):** _______________  
**Executed by (QA):** _______________

---

## 0. Source alignment (read first)

| Location | Role |
|----------|------|
| **`ForDemo/MotorPh/`** | Intended **submission / QA root** (this document). |
| **`ForDemo/`** (parent) | May contain a fuller Maven tree (`com.motorph.payroll.*`, refactored payroll, tests). **If your instructor grades only `MotorPh`,** ensure the same entry point and sources you want tested are **present under `MotorPh/src`** and that `pom.xml` `exec.mainClass` matches. |

**Current `MotorPh` tree note:** `pom.xml` declares `exec.mainClass=com.demo.fordemo.ForDemo`. If `ForDemo.java` is not in `src`, use **`com.demo.fordemo.EmployeeLookup`** for smoke tests until the main class exists, or fix `exec.mainClass` in `pom.xml` to match the class you run.

---

## 1. Purpose

This document defines **what** the QA team will verify, **how** to run the application, **test data** to use, and **where** to record results (Pass/Fail) and defects.

---

## 2. Scope

### 2.1 In scope (when full payroll app is present under `MotorPh`)

Use this matrix when the submission includes the refactored payroll entry point (e.g. `com.motorph.payroll.MotorPhPayrollAppRefactored`) and matching `pom.xml` dependencies.

| Area | Description |
|------|-------------|
| Authentication | Login via credentials file or environment (`MOTORPH_CREDENTIALS_PATH`, `MOTORPH_CREDENTIALS_JSON`) |
| Employee lookup | Load employee from CSV; display ID, name, birthday |
| Attendance | Read attendance CSV; aggregate hours by year/month and cutoff (1–15 vs 16–end) |
| Hours rules | Grace period (≤ 8:10 per `computeHours`), 5:00 PM cap, lunch deduction when shift ≥ 4 hours |
| Payroll summary | Gross from hours × hourly rate; net/deduction placeholders per current code |
| Data integrity | CSV parsing (quoted fields with Commons CSV); malformed rows skipped or handled without crash |

### 2.2 Minimal scope (employee lookup only — `EmployeeLookup`)

If QA only has `com.demo.fordemo.EmployeeLookup`:

| ID | Title | Steps | Expected |
|----|--------|-------|----------|
| EL-01 | Valid ID | Run `EmployeeLookup`, enter `10001` | Employee found; ID, name, birthday printed |
| EL-02 | Invalid ID | Enter `99999` | Not found message; no crash |
| EL-03 | Missing CSV | Rename employee CSV | IOException handled; user-visible error |

### 2.3 Out of scope (unless specified by instructor)

- Production deployment, load/performance testing  
- Full legal accuracy of SSS/PhilHealth/Pag-IBIG/tax (if still TODO in code)  
- Penetration testing beyond basic auth negative cases  

### 2.4 QA build target (important)

| Class | In full matrix below? |
|-------|------------------------|
| **`MotorPhPayrollAppRefactored`** | **Yes** — when present under `MotorPh` |
| **`MotorPhPayrollApp`** (original) | **No** unless instructor requires both |
| **`EmployeeLookup`** | Use **§2.2** only unless merged into full app |

---

## 3. Test environment (`MotorPh` — match this folder’s `pom.xml`)

| Item | Value / instructions |
|------|----------------------|
| OS | _______________ |
| JDK | **`MotorPh/pom.xml`** uses `maven.compiler.release` **21** → use **JDK 21** (or change `release` to match your JDK). |
| Build | From **`MotorPh`**: `mvn test` (if tests exist) then run main class |
| Main class | As in **`MotorPh/pom.xml`** → `exec.mainClass` (e.g. `com.demo.fordemo.ForDemo` or align to `MotorPhPayrollAppRefactored` after merge) |
| Working directory | **`MotorPh` project root** (so `resources/...` resolves) |
| Data files | `resources/MotorPH_Employee Data - Employee Details.csv`, `resources/MotorPH_Employee Data - Attendance Record.csv` |
| Credentials | Only if using refactored payroll: `resources/credentials_hashed.csv` or env vars — see parent `ForDemo/README.md` if copied |

**Run (MotorPh — `exec-maven-plugin` is declared in `pom.xml`):**

```text
cd MotorPh
mvn test
mvn exec:java
```

**If `exec.mainClass` must be overridden for one run:**

```text
mvn exec:java -Dexec.mainClass=com.demo.fordemo.EmployeeLookup
```

**Full payroll refactored class (when present in this repo):**

```text
mvn exec:java -Dexec.mainClass=com.motorph.payroll.MotorPhPayrollAppRefactored
```

**Logging:** If using refactored app with SLF4J, expect `[main] INFO/ERROR/...` on the console.

---

## 4. Test accounts & data (obtain from team)

*(Full payroll + login — skip if testing `EmployeeLookup` only.)*

| Role | Username | Password | Notes |
|------|----------|----------|--------|
| Admin | *(from team / hashed CSV)* | *(plaintext used when hashing)* | Role `admin` |
| Employee | *(from team)* | *(same)* | Role `employee` |

**Sample employee IDs** (bundled employee CSV):

| Employee # | Use for |
|------------|---------|
| 10001 | Happy path |
| 99999 | Negative |

---

## 5. Test case matrix (full payroll — refactored app)

**Instructions:** Record **Actual**, **Status** (Pass/Fail), **Tester**, **Date**, **Evidence**.

### 5.1 Authentication (AUTH)

| ID | Title | Preconditions | Steps | Expected result | Actual | Status | Notes |
|----|--------|---------------|-------|-----------------|--------|--------|-------|
| AUTH-01 | Valid login | Valid creds file or env | Run app; enter valid username/password | Auth succeeds; role logged; prompt for Employee # | | | |
| AUTH-02 | Invalid password | Same | Wrong password once | Invalid; retry allowed | | | |
| AUTH-03 | Max attempts | Same | Wrong password 3 times | Exit / auth failed | | | |
| AUTH-04 | Unknown user | Same | Random username | Invalid login | | | |
| AUTH-05 | Missing creds file | No file, no env JSON | Run | No hang; SLF4J ERROR/WARN; failed login path | | | |
| AUTH-06 | `MOTORPH_CREDENTIALS_PATH` | Set to valid CSV | Run | Login uses that file | | | |
| AUTH-07 | `MOTORPH_CREDENTIALS_JSON` | Single-line JSON map | Run | Login uses map | | | |

**`MOTORPH_CREDENTIALS_JSON` example:**

```json
{"payroll_staff":{"password":"SHA256$...","role":"admin"},"employee":{"password":"SHA256$...","role":"employee"}}
```

### 5.2 Employee & profile (EMP)

| ID | Title | Preconditions | Steps | Expected result | Actual | Status | Notes |
|----|--------|---------------|-------|-----------------|--------|--------|-------|
| EMP-01 | Valid employee | Logged in (if applicable) | Enter `10001` | ID, name, birthday; rate used later | | | |
| EMP-02 | Invalid employee | Same | `99999` | “Employee does not exist.” | | | |
| EMP-03 | Empty ID | Same | Blank input | No crash | | | |

### 5.3 Attendance & hours (ATT)

| ID | Title | Preconditions | Steps | Expected result | Actual | Status | Notes |
|----|--------|---------------|-------|-----------------|--------|--------|-------|
| ATT-01 | Has records | EMP-01 | Full flow | Months/years; hours ≥ 0 | | | |
| ATT-02 | No attendance | ID with no rows | Full flow | Message / no crash | | | |
| ATT-03 | Cutoff split | Data on day 10 & 20 | Compare | 1–15 vs 16–end | | | |

### 5.4 Hours rules (HRULE)

| ID | Title | Expected (refactored `computeHours`) | Actual | Status | Notes |
|----|--------|--------------------------------------|--------|--------|-------|
| HRULE-01 | Grace ≤ 8:10 | Full-day branch per code | | | |
| HRULE-02 | Late ≥ 8:11 | Paid hours reflect lateness | | | |
| HRULE-03 | Out > 17:00 | Capped at 17:00 | | | |
| HRULE-04 | Shift < 4h | No lunch −60 | | | |
| HRULE-05 | Shift ≥ 4h | Lunch −60 | | | |

### 5.5 Payroll (PAY)

| ID | Title | Expected | Actual | Status | Notes |
|----|--------|----------|--------|--------|-------|
| PAY-01 | 1st cutoff gross | hours × rate | | | |
| PAY-02 | 2nd cutoff gross | same | | | |
| PAY-03 | Deductions | Per spec / TODO | | | |

### 5.6 Errors (ERR)

| ID | Title | Expected | Actual | Status | Notes |
|----|--------|----------|--------|--------|-------|
| ERR-01 | Missing employee CSV | Handled | | | |
| ERR-02 | Missing attendance CSV | Handled | | | |
| ERR-03 | Bad attendance row | Skip/log; continue | | | |

---

## 6. Regression checklist

- [ ] EL-01, EL-02 *(if only EmployeeLookup)*  
- [ ] AUTH-01, AUTH-03, EMP-01, ATT-01 *(if full payroll)*  
- [ ] `mvn test` *(when tests exist)*  

---

## 7. Defect template

| Field | Value |
|-------|--------|
| **ID** | DEF-___ |
| **Test case** | |
| **Severity** | Critical / High / Medium / Low |
| **Steps** | |
| **Expected** | |
| **Actual** | |
| **Environment** | OS, JDK, commit |
| **Evidence** | |

---

## 8. Sign-off

| Role | Name | Date |
|------|------|------|
| QA Lead | | |
| Developer | | |

---

*End of QA Case Document (MotorPh)*
