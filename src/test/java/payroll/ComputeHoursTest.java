package payroll;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComputeHoursTest {

    @Test
    void grace805To500IsEightHours() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(8, 5), LocalTime.of(17, 0));
        assertEquals(8.0, h, 0.0001);
    }

    @Test
    void grace805To430IsSevenPointFive() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(8, 5), LocalTime.of(16, 30));
        assertEquals(7.5, h, 0.0001);
    }

    @Test
    void eightThirtyToFiveThirtyIsSevenPointFive() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(8, 30), LocalTime.of(17, 30));
        assertEquals(7.5, h, 0.0001);
    }

    @Test
    void afterGraceFullDay() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(8, 11), LocalTime.of(17, 0));
        assertEquals((529.0 - 60.0) / 60.0, h, 0.0001);
    }

    @Test
    void shortShiftNoLunchDeduction() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(13, 0), LocalTime.of(14, 0));
        assertEquals(1.0, h, 0.0001);
    }

    @Test
    void eightToFiveWithLunchIsEightHours() {
        double h = MotorPhPayrollAppRefactored.computeHours(LocalTime.of(8, 0), LocalTime.of(17, 0));
        assertEquals(8.0, h, 0.0001);
    }
}
