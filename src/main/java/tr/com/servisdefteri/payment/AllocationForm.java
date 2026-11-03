package tr.com.servisdefteri.payment;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AllocationForm {

    private Long transactionId;
    private BigDecimal amount;
}
