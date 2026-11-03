package tr.com.servisdefteri.vehicle;

import java.time.LocalDateTime;

public record VehicleOwnershipView(LocalDateTime changedAt, String previousOwner, String newOwner, String note) {
}
