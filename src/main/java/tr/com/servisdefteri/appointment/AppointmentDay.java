package tr.com.servisdefteri.appointment;

import java.time.LocalDate;
import java.util.List;

public record AppointmentDay(LocalDate date, List<Appointment> appointments) {
}
