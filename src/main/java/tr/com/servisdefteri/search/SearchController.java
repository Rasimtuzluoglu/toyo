package tr.com.servisdefteri.search;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleService;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderService;

import java.util.List;

@Controller
public class SearchController {

    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final WorkOrderService workOrderService;

    public SearchController(CustomerService customerService, VehicleService vehicleService,
                            WorkOrderService workOrderService) {
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.workOrderService = workOrderService;
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q, Model model) {
        String query = q == null ? "" : q.trim();
        model.addAttribute("q", query);
        if (query.length() >= 2) {
            List<Customer> customers = customerService.search(query, null, null,
                    PageRequest.of(0, 20, Sort.by("name"))).getContent();
            List<Vehicle> vehicles = vehicleService.search(query, null, null,
                    PageRequest.of(0, 20, Sort.by("plate"))).getContent();
            List<WorkOrder> orders = workOrderService.search(null, null, null, null, null, query, 0, 20)
                    .getContent();
            model.addAttribute("customers", customers);
            model.addAttribute("vehicles", vehicles);
            model.addAttribute("orders", orders);
            model.addAttribute("searched", true);
        }
        return "search/results";
    }
}
