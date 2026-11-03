package tr.com.servisdefteri.payment;

import tr.com.servisdefteri.account.OpenDebt;

/**
 * Odeme formunda borc satirini ve ilgili form indeksini birlikte tasir.
 */
public record AllocationView(int index, OpenDebt debt) {
}
