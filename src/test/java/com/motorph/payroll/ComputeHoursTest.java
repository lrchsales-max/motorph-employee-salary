package com.motorph.payroll;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComputeHoursTest {

    @Test
    void gracePeriodReturnsFullDay() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(8, 10), LocalTime.of(17, 0));
        assertEquals(8.0, h, 0.001);
    }

    @Test
    void afterGracePartial() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(8, 11), LocalTime.of(17, 0));
        // arrival 8:11 -> worked ~8h49m -> minus 1h lunch -> capped to 8.0
        assertEquals(8.0, h, 0.001);
    }

    @Test
    void shortShiftNoLunchDeduction() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(13, 0), LocalTime.of(14, 0));
        assertEquals(1.0, h, 0.001);
    }

    @Test
    void longShiftSubtractsLunch() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(8, 0), LocalTime.of(17, 0));
        assertEquals(8.0, h, 0.001);
    }
}
