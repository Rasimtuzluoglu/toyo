package tr.com.servisdefteri.workorder;

import lombok.Getter;

@Getter
public enum WorkOrderItemType {

    HIZMET("Hizmet"),
    PARCA("Parça");

    private final String label;

    WorkOrderItemType(String label) {
        this.label = label;
    }
}
