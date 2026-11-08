package tr.com.servisdefteri.workorder;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.account.WorkOrderDebt;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.FormTokenService;
import tr.com.servisdefteri.payment.AllocationForm;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentForm;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.payment.PaymentService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Servis ve muhasebe için iş emrine özel tahsilat ekranı.
 * Yalnızca ilgili iş emrinin kalan borcunu gösterir.
 */
@Controller
@RequestMapping("/work-orders/{orderId}/payment")
@PreAuthorize("hasAnyRole('PATRON','MUHASEBE','SERVIS')")
public class WorkOrderPaymentController {

    private final WorkOrderService workOrderService;
    private final AccountService accountService;
    private final PaymentService paymentService;
    private final FormTokenService formTokenService;
    private final Clock clock;

    public WorkOrderPaymentController(WorkOrderService workOrderService,
                                      AccountService accountService,
                                      PaymentService paymentService,
                                      FormTokenService formTokenService,
                                      Clock clock) {
        this.workOrderService = workOrderService;
        this.accountService = accountService;
        this.paymentService = paymentService;
        this.formTokenService = formTokenService;
        this.clock = clock;
    }

    @GetMapping
    public String form(@PathVariable Long orderId, HttpSession session, Model model) {
        return render(orderId, session, model, null, null, null);
    }

    @PostMapping
    public String pay(@PathVariable Long orderId,
                      @RequestParam(required = false) String formToken,
                      @RequestParam(required = false) BigDecimal amount,
                      @RequestParam(required = false) PaymentMethod method,
                      @RequestParam(required = false) String description,
                      HttpSession session,
                      Model model,
                      RedirectAttributes redirectAttributes) {
        try {
            formTokenService.consume(session, formToken);
        } catch (BusinessRuleException ex) {
            return render(orderId, session, model, amount, description, ex.getMessage());
        }

        WorkOrder order = workOrderService.findById(orderId);
        WorkOrderDebt debt = accountService.workOrderDebt(orderId);
        if (!order.getStatus().isCompleted()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Tahsilat için iş emri tamamlanmış veya teslim edilmiş olmalıdır.");
            return "redirect:/work-orders/" + orderId;
        }
        if (debt == null || debt.remaining().signum() <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bu iş emrinde açık borç yok.");
            return "redirect:/work-orders/" + orderId;
        }
        if (amount == null || amount.signum() <= 0) {
            return render(orderId, session, model, amount, description,
                    "Tahsilat tutarı sıfırdan büyük olmalıdır.");
        }
        if (amount.compareTo(debt.remaining()) > 0) {
            return render(orderId, session, model, amount, description,
                    "Tahsilat tutarı kalan borçtan fazla olamaz. Kalan: "
                            + debt.remaining().setScale(2) + " ₺");
        }
        if (method == null) {
            return render(orderId, session, model, amount, description, "Ödeme yöntemi seçilmelidir.");
        }
        if (description != null && description.length() > 500) {
            return render(orderId, session, model, amount, description,
                    "Açıklama en fazla 500 karakter olabilir.");
        }

        PaymentForm form = new PaymentForm();
        form.setCustomerId(order.getCustomer().getId());
        form.setAmount(amount);
        form.setPaymentDate(LocalDate.now(clock));
        form.setMethod(method);
        form.setDescription(description == null || description.isBlank()
                ? "İş emri " + order.getOrderNo() + " tahsilatı" : description.trim());
        AllocationForm allocation = new AllocationForm();
        allocation.setTransactionId(debt.transactionId());
        allocation.setAmount(amount);
        form.setAllocations(List.of(allocation));

        try {
            Payment payment = paymentService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tahsilat alındı: " + payment.getReceiptNo());
            return "redirect:/work-orders/" + orderId;
        } catch (BusinessRuleException ex) {
            return render(orderId, session, model, amount, description, ex.getMessage());
        }
    }

    private String render(Long orderId, HttpSession session, Model model, BigDecimal amount,
                          String description, String error) {
        WorkOrder order = workOrderService.findById(orderId);
        WorkOrderDebt debt = accountService.workOrderDebt(orderId);
        if (!order.getStatus().isCompleted() || debt == null || debt.remaining().signum() <= 0) {
            return "redirect:/work-orders/" + orderId;
        }
        model.addAttribute("order", order);
        model.addAttribute("debt", debt);
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("formToken", formTokenService.issue(session));
        model.addAttribute("amountValue", amount == null ? debt.remaining() : amount);
        model.addAttribute("descriptionValue", description);
        model.addAttribute("activeMenu", "workorders");
        if (error != null) {
            model.addAttribute("errorMessage", error);
        }
        return "workorders/payment-form";
    }
}
