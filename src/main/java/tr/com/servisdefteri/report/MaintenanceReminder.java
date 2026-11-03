package tr.com.servisdefteri.report;

import java.time.LocalDate;

public record MaintenanceReminder(Long customerId, String customerName, String phone,
                                  Long vehicleId, String plate, String vehicleName,
                                  LocalDate lastServiceDate, int daysSince, int kmSince,
                                  String reason) {
}
