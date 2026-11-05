package tr.com.servisdefteri.appointment;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.vehicle.VehicleService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final Clock clock;

    public AppointmentController(AppointmentService appointmentService,
                                 CustomerService customerService,
                                 VehicleService vehicleService,
                                 Clock clock) {
        this.appointmentService = appointmentService;
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.clock = clock;
    }

    @GetMapping
    public String day(@RequestParam(required = false)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      Model model) {
        LocalDate selected = date == null ? LocalDate.now(clock) : date;
        List<Appointment> appointments = appointmentService.day(selected);
        model.addAttribute("appointments", appointments);
        model.addAttribute("date", selected);
        model.addAttribute("previousDate", selected.minusDays(1));
        model.addAttribute("nextDate", selected.plusDays(1));
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("plannedCount", appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.PLANLANDI).count());
        model.addAttribute("arrivedCount", appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.GELDI).count());
        model.addAttribute("activeMenu", "appointments");
        return "appointments/index";
    }

    @GetMapping("/week")
    public String week(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Model model) {
        LocalDate today = LocalDate.now(clock);
        LocalDate selected = date == null ? today : date;
        LocalDate weekStart = selected.with(DayOfWeek.MONDAY);
        List<Appointment> appointments = appointmentService.week(weekStart);
        List<AppointmentDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            days.add(new AppointmentDay(day, appointments.stream()
                    .filter(a -> a.getStartsAt().toLocalDate().equals(day))
                    .toList()));
        }
        model.addAttribute("days", days);
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd", weekStart.plusDays(6));
        model.addAttribute("previousWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("today", today);
        model.addAttribute("activeMenu", "appointments");
        return "appointments/week";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String createForm(@RequestParam(required = false) Long customerId,                             @RequestParam(required = false) Long vehicleId,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             Model model) {
        AppointmentForm form = new AppointmentForm();
        form.setCustomerId(customerId);
        form.setVehicleId(vehicleId);
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        form.setStartsAt(day.atTime(9, 0));
        model.addAttribute("appointmentForm", form);
        model.addAttribute("editing", false);
        populateOptions(model);
        model.addAttribute("activeMenu", "appointments");
        return "appointments/form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String create(@Valid @ModelAttribute("appointmentForm") AppointmentForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", false);
            populateOptions(model);
            model.addAttribute("activeMenu", "appointments");
            return "appointments/form";
        }
        try {
            Appointment appointment = appointmentService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Randevu oluşturuldu.");
            return "redirect:/appointments?date=" + appointment.getStartsAt().toLocalDate();
        } catch (BusinessRuleException ex) {
            bindingResult.reject("appointment.failed", ex.getMessage());
            model.addAttribute("editing", false);
            populateOptions(model);
            model.addAttribute("activeMenu", "appointments");
            return "appointments/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String editForm(@PathVariable Long id, Model model) {
        Appointment appointment = appointmentService.findById(id);
        model.addAttribute("appointmentForm", appointmentService.toForm(appointment));
        model.addAttribute("appointment", appointment);
        model.addAttribute("editing", true);
        populateOptions(model);
        model.addAttribute("activeMenu", "appointments");
        return "appointments/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("appointmentForm") AppointmentForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("appointment", appointmentService.findById(id));
            model.addAttribute("editing", true);
            populateOptions(model);
            model.addAttribute("activeMenu", "appointments");
            return "appointments/form";
        }
        try {
            Appointment appointment = appointmentService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Randevu güncellendi.");
            return "redirect:/appointments?date=" + appointment.getStartsAt().toLocalDate();
        } catch (BusinessRuleException ex) {
            bindingResult.reject("appointment.failed", ex.getMessage());
            model.addAttribute("appointment", appointmentService.findById(id));
            model.addAttribute("editing", true);
            populateOptions(model);
            model.addAttribute("activeMenu", "appointments");
            return "appointments/form";
        }
    }

    @PostMapping("/{id}/arrived")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String arrived(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Appointment appointment = appointmentService.markArrived(id);
        redirectAttributes.addFlashAttribute("successMessage", "Randevu geldi olarak işaretlendi.");
        return "redirect:/appointments?date=" + appointment.getStartsAt().toLocalDate();
    }

    /**
     * Randevuyu geldi olarak işaretler ve plakası ön dolu hızlı iş emri ekranını açar.
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String startWorkOrder(@PathVariable Long id) {
        appointmentService.markArrived(id);
        return "redirect:/work-orders/quick?plate=" + appointmentService.vehiclePlate(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Appointment appointment = appointmentService.cancel(id);
        redirectAttributes.addFlashAttribute("successMessage", "Randevu iptal edildi.");
        return "redirect:/appointments?date=" + appointment.getStartsAt().toLocalDate();
    }

    private void populateOptions(Model model) {
        model.addAttribute("customers", customerService.activeCustomers());
        model.addAttribute("vehicles", vehicleService.activeVehicles());
        model.addAttribute("statuses", AppointmentStatus.values());
    }
}
