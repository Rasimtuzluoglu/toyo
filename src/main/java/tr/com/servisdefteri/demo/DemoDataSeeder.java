package tr.com.servisdefteri.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.account.OpenDebt;
import tr.com.servisdefteri.appointment.AppointmentForm;
import tr.com.servisdefteri.appointment.AppointmentService;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerForm;
import tr.com.servisdefteri.customer.CustomerRepository;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.customer.CustomerType;
import tr.com.servisdefteri.payment.AllocationForm;
import tr.com.servisdefteri.payment.PaymentForm;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.payment.PaymentService;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.security.UserRepository;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleForm;
import tr.com.servisdefteri.vehicle.VehicleService;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderForm;
import tr.com.servisdefteri.workorder.WorkOrderItemForm;
import tr.com.servisdefteri.workorder.WorkOrderItemType;
import tr.com.servisdefteri.workorder.WorkOrderRepository;
import tr.com.servisdefteri.workorder.WorkOrderService;
import tr.com.servisdefteri.workorder.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Yalnizca "demo" profilinde calisir. Bos demo veritabanini gercekci ornek
 * musteriler, araclar, is emirleri, tahsilatlar ve randevularla doldurur.
 */
@Component
@Profile("demo")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final WorkOrderService workOrderService;
    private final WorkOrderRepository workOrderRepository;
    private final AccountService accountService;
    private final PaymentService paymentService;
    private final AppointmentService appointmentService;
    private final Clock clock;

    public DemoDataSeeder(CustomerRepository customerRepository, UserRepository userRepository,
                          PasswordEncoder passwordEncoder, CustomerService customerService,
                          VehicleService vehicleService, WorkOrderService workOrderService,
                          WorkOrderRepository workOrderRepository,
                          AccountService accountService, PaymentService paymentService,
                          AppointmentService appointmentService, Clock clock) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.workOrderService = workOrderService;
        this.workOrderRepository = workOrderRepository;
        this.accountService = accountService;
        this.paymentService = paymentService;
        this.appointmentService = appointmentService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        User patron = demoUser();
        demoRoleUser("servis", "Demo Servis", Role.SERVIS, "ServisPass1234");
        demoRoleUser("muhasebe", "Demo Muhasebe", Role.MUHASEBE, "MuhasebePass1234");
        if (customerRepository.count() > 0) {
            log.info("Demo verisi atlandı: veritabanında zaten müşteri kaydı var.");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                AppUserPrincipal.from(patron), patron.getPasswordHash(),
                AppUserPrincipal.from(patron).getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            seed();
            log.info("Demo verisi oluşturuldu. Kullanıcı: demo / DemoPass1234");
        } catch (RuntimeException ex) {
            log.error("Demo verisi oluşturulamadı: {}", ex.getMessage(), ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void seed() {
        List<Customer> customers = new ArrayList<>();
        customers.add(customer("Ahmet Yılmaz", "0532 410 20 10", CustomerType.BIREYSEL, "ahmet.yilmaz@example.com"));
        customers.add(customer("Ayşe Demir", "0533 411 20 11", CustomerType.BIREYSEL, "ayse.demir@example.com"));
        customers.add(customer("Mehmet Kaya", "0534 412 20 12", CustomerType.BIREYSEL, null));
        customers.add(customer("Öztoprak Lojistik A.Ş.", "0212 555 20 13", CustomerType.FIRMA, "filo@oztopraklojistik.example"));
        customers.add(customer("Fatma Şahin", "0535 413 20 14", CustomerType.BIREYSEL, null));
        customers.add(customer("Emre Çelik", "0536 414 20 15", CustomerType.BIREYSEL, "emre.celik@example.com"));
        customers.add(customer("Zeynep Arslan", "0537 415 20 16", CustomerType.BIREYSEL, null));
        customers.add(customer("Marmara Turizm Ltd.", "0216 555 20 17", CustomerType.FIRMA, "servis@marmaraturizm.example"));
        customers.add(customer("Hasan Koç", "0538 416 20 18", CustomerType.BIREYSEL, null));
        customers.add(customer("Elif Aydın", "0539 417 20 19", CustomerType.BIREYSEL, "elif.aydin@example.com"));
        customers.add(customer("Burak Yıldız", "0541 418 20 20", CustomerType.BIREYSEL, null));
        customers.add(customer("Deniz Otomotiv Ltd.", "0212 555 20 21", CustomerType.FIRMA, "info@denizoto.example"));

        List<Vehicle> vehicles = new ArrayList<>();
        vehicles.add(vehicle(customers.get(0), "34 TP 1001", "Toyota", "Corolla", 2021, 62400, "JTDBR32E720123401"));
        vehicles.add(vehicle(customers.get(1), "34 TP 1002", "Toyota", "Yaris", 2022, 31800, "NMTBB3BE10R123402"));
        vehicles.add(vehicle(customers.get(2), "34 TP 1003", "Toyota", "C-HR", 2020, 84500, "NMTKZ3BX10R123403"));
        vehicles.add(vehicle(customers.get(3), "34 TP 1004", "Toyota", "Hilux", 2019, 142000, "MR0HA8CD200123404"));
        vehicles.add(vehicle(customers.get(4), "34 TP 1005", "Toyota", "RAV4", 2023, 18700, "JTMB3REV10J123405"));
        vehicles.add(vehicle(customers.get(5), "34 TP 1006", "Toyota", "Corolla Cross", 2023, 22400, "JTMB3REV20J123406"));
        vehicles.add(vehicle(customers.get(6), "34 TP 1007", "Toyota", "Proace City", 2022, 49700, "NMTBB3BE20R123407"));
        vehicles.add(vehicle(customers.get(7), "34 TP 1008", "Toyota", "Corolla", 2018, 176000, "JTDBR32E720123408"));
        vehicles.add(vehicle(customers.get(8), "34 TP 1009", "Toyota", "Yaris Cross", 2024, 9800, "NMTBB3BE30R123409"));
        vehicles.add(vehicle(customers.get(9), "34 TP 1010", "Toyota", "C-HR", 2021, 57300, "NMTKZ3BX20R123410"));
        vehicles.add(vehicle(customers.get(10), "34 TP 1011", "Toyota", "Hilux", 2020, 118000, "MR0HA8CD300123411"));
        vehicles.add(vehicle(customers.get(11), "34 TP 1012", "Toyota", "Proace", 2021, 93000, "NMTBB3BE40R123412"));

        WorkOrder waitingToday = order(customers.get(0), vehicles.get(0), 0, "Motor yağı ve filtre bakımı");
        addItem(waitingToday, WorkOrderItemType.HIZMET, "Periyodik bakım", "1", "1850.00");
        addItem(waitingToday, WorkOrderItemType.PARCA, "Motor yağı 5W-30", "4", "480.00");

        WorkOrder waitingYesterday = order(customers.get(1), vehicles.get(1), 1, "Fren sesi ve balata kontrolü");
        addItem(waitingYesterday, WorkOrderItemType.HIZMET, "Fren balata kontrolü", "1", "950.00");

        WorkOrder inProgress = order(customers.get(2), vehicles.get(2), 0, "Klima soğutmuyor");
        addItem(inProgress, WorkOrderItemType.HIZMET, "Klima bakımı ve gaz dolumu", "1", "2400.00");
        transition(inProgress, WorkOrderStatus.ISLEMDE);

        WorkOrder inProgressOld = order(customers.get(3), vehicles.get(3), 2, "Periyodik bakım ve lastik kontrolü");
        addItem(inProgressOld, WorkOrderItemType.HIZMET, "Periyodik bakım", "1", "3200.00");
        addItem(inProgressOld, WorkOrderItemType.PARCA, "Hava filtresi", "1", "720.00");
        transition(inProgressOld, WorkOrderStatus.ISLEMDE);

        WorkOrder readyOne = order(customers.get(4), vehicles.get(4), 0, "Yağ bakımı");
        addItem(readyOne, WorkOrderItemType.HIZMET, "Periyodik bakım", "1", "2100.00");
        addItem(readyOne, WorkOrderItemType.PARCA, "Polen filtresi", "1", "650.00");
        transition(readyOne, WorkOrderStatus.ISLEMDE, WorkOrderStatus.TAMAMLANDI);

        WorkOrder readyTwo = order(customers.get(5), vehicles.get(5), 0, "Akü ve şarj kontrolü");
        addItem(readyTwo, WorkOrderItemType.HIZMET, "Akü kontrolü", "1", "450.00");
        addItem(readyTwo, WorkOrderItemType.PARCA, "Akü", "1", "4200.00");
        transition(readyTwo, WorkOrderStatus.ISLEMDE, WorkOrderStatus.TAMAMLANDI);

        WorkOrder deliveredOne = order(customers.get(6), vehicles.get(6), 0, "Polen filtresi ve iç temizlik");
        addItem(deliveredOne, WorkOrderItemType.HIZMET, "Polen filtresi değişimi", "1", "1100.00");
        addItem(deliveredOne, WorkOrderItemType.PARCA, "Polen filtresi", "1", "780.00");
        transition(deliveredOne, WorkOrderStatus.ISLEMDE, WorkOrderStatus.TAMAMLANDI, WorkOrderStatus.TESLIM_EDILDI);

        WorkOrder deliveredTwo = order(customers.get(7), vehicles.get(7), 0, "Fren balatası değişimi");
        addItem(deliveredTwo, WorkOrderItemType.HIZMET, "Fren balatası değişimi", "1", "1600.00");
        addItem(deliveredTwo, WorkOrderItemType.PARCA, "Fren balatası takımı", "1", "2900.00");
        transition(deliveredTwo, WorkOrderStatus.ISLEMDE, WorkOrderStatus.TAMAMLANDI, WorkOrderStatus.TESLIM_EDILDI);

        WorkOrder completedYesterday = order(customers.get(8), vehicles.get(8), 1, "İlk bakım");
        addItem(completedYesterday, WorkOrderItemType.HIZMET, "İlk bakım", "1", "1750.00");
        transition(completedYesterday, WorkOrderStatus.ISLEMDE, WorkOrderStatus.TAMAMLANDI);

        WorkOrder waitingOld = order(customers.get(9), vehicles.get(9), 4, "Rot-balans ve lastik");
        addItem(waitingOld, WorkOrderItemType.HIZMET, "Rot-balans ayarı", "1", "1250.00");
        addItem(waitingOld, WorkOrderItemType.PARCA, "Balans ağırlığı", "4", "90.00");

        WorkOrder oldService = order(customers.get(10), vehicles.get(10), 220, "Geçmiş dönem bakımı");
        addItem(oldService, WorkOrderItemType.HIZMET, "Periyodik bakım", "1", "1600.00");
        transition(oldService, WorkOrderStatus.ISLEMDE, WorkOrderStatus.TAMAMLANDI);
        backdateClosed(oldService, 219);

        WorkOrder highMileage = order(customers.get(11), vehicles.get(11), 60, "Filo bakımı");
        highMileage.setMileage(Math.max(0, vehicles.get(11).getCurrentMileage() - 15_000));
        workOrderRepository.save(highMileage);
        addItem(highMileage, WorkOrderItemType.HIZMET, "Filo bakım kontrolü", "1", "1900.00");
        transition(highMileage, WorkOrderStatus.ISLEMDE, WorkOrderStatus.TAMAMLANDI);
        backdateClosed(highMileage, 59);

        pay(customers.get(6), "1800.00", 0, PaymentMethod.KART);
        pay(customers.get(7), "2500.00", 0, PaymentMethod.NAKIT);
        pay(customers.get(8), "1500.00", 1, PaymentMethod.BANKA);
        pay(customers.get(4), "1000.00", 2, PaymentMethod.NAKIT);
        pay(customers.get(5), "2000.00", 4, PaymentMethod.KART);
        pay(customers.get(0), "500.00", 6, PaymentMethod.NAKIT);

        appointment(customers.get(10), vehicles.get(10), 30, "Periyodik bakım randevusu");
        appointment(customers.get(11), vehicles.get(11), 90, "Fren kontrolü");
        appointment(customers.get(1), vehicles.get(1), 150, "Klima kontrolü");
        appointment(customers.get(2), vehicles.get(2), 24 * 60 + 60, "Lastik değişimi");
    }

    private User demoUser() {
        return demoRoleUser("demo", "Demo İşletme Sahibi", Role.PATRON, "DemoPass1234");
    }

    private User demoRoleUser(String username, String fullName, Role role, String password) {
        User user = userRepository.findByUsernameIgnoreCase(username).orElseGet(User::new);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(role);
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode(password));
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now(clock));
        }
        return userRepository.save(user);
    }

    private Customer customer(String name, String phone, CustomerType type, String email) {
        CustomerForm form = new CustomerForm();
        form.setName(name);
        form.setPhone(phone);
        form.setType(type);
        form.setEmail(email);
        form.setActive(true);
        return customerService.create(form);
    }

    private Vehicle vehicle(Customer customer, String plate, String brand, String model,
                            int year, int mileage, String vin) {
        VehicleForm form = new VehicleForm();
        form.setCustomerId(customer.getId());
        form.setPlate(plate);
        form.setBrand(brand);
        form.setModel(model);
        form.setModelYear(year);
        form.setCurrentMileage(mileage);
        form.setVin(vin);
        form.setActive(true);
        return vehicleService.create(form);
    }

    private WorkOrder order(Customer customer, Vehicle vehicle, int daysAgo, String complaint) {
        WorkOrderForm form = new WorkOrderForm();
        form.setCustomerId(customer.getId());
        form.setVehicleId(vehicle.getId());
        LocalDateTime openedAt = daysAgo == 0
                ? LocalDateTime.now(clock).minusHours(2).withSecond(0).withNano(0)
                : LocalDateTime.now(clock).minusDays(daysAgo).withHour(9).withMinute(30).withSecond(0).withNano(0);
        form.setOpenedAt(openedAt);
        form.setMileage(vehicle.getCurrentMileage());
        form.setComplaint(complaint);
        return workOrderService.create(form);
    }

    private void addItem(WorkOrder order, WorkOrderItemType type, String description,
                         String quantity, String unitPrice) {
        WorkOrderItemForm form = new WorkOrderItemForm();
        form.setType(type);
        form.setDescription(description);
        form.setQuantity(new BigDecimal(quantity));
        form.setUnitPrice(new BigDecimal(unitPrice));
        workOrderService.addItem(order.getId(), form);
    }

    private void transition(WorkOrder order, WorkOrderStatus... statuses) {
        for (WorkOrderStatus status : statuses) {
            workOrderService.changeStatus(order.getId(), status);
        }
    }

    private void backdateClosed(WorkOrder order, int daysAgo) {
        WorkOrder stored = workOrderRepository.findById(order.getId()).orElse(null);
        if (stored != null) {
            stored.setClosedAt(LocalDateTime.now(clock).minusDays(daysAgo)
                    .withHour(16).withMinute(30).withSecond(0).withNano(0));
            workOrderRepository.save(stored);
        }
    }

    private void pay(Customer customer, String amount, int daysAgo, PaymentMethod method) {
        List<OpenDebt> debts = accountService.openDebts(customer.getId());
        if (debts.isEmpty()) {
            return;
        }
        BigDecimal remaining = new BigDecimal(amount);
        List<AllocationForm> allocations = new ArrayList<>();
        for (OpenDebt debt : debts) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal allocation = remaining.min(debt.getRemaining());
            AllocationForm form = new AllocationForm();
            form.setTransactionId(debt.id());
            form.setAmount(allocation);
            allocations.add(form);
            remaining = remaining.subtract(allocation);
        }
        PaymentForm form = new PaymentForm();
        form.setCustomerId(customer.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(LocalDate.now(clock).minusDays(daysAgo));
        form.setMethod(method);
        form.setDescription("Demo tahsilatı");
        form.setAllocations(allocations);
        paymentService.create(form);
    }

    private void appointment(Customer customer, Vehicle vehicle, int minutesFromNow, String note) {
        AppointmentForm form = new AppointmentForm();
        form.setCustomerId(customer.getId());
        form.setVehicleId(vehicle.getId());
        form.setStartsAt(LocalDateTime.now(clock).plusMinutes(minutesFromNow).withSecond(0).withNano(0));
        form.setNotes(note);
        appointmentService.create(form);
    }
}
