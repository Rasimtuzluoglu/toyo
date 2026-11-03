package tr.com.servisdefteri.workorder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderStatusTest {

    @Test
    void allowedTransitionsFollowTheServiceFlow() {
        assertThat(WorkOrderStatus.BEKLIYOR.canTransitionTo(WorkOrderStatus.ISLEMDE)).isTrue();
        assertThat(WorkOrderStatus.BEKLIYOR.canTransitionTo(WorkOrderStatus.TAMAMLANDI)).isFalse();
        assertThat(WorkOrderStatus.ISLEMDE.canTransitionTo(WorkOrderStatus.TAMAMLANDI)).isTrue();
        assertThat(WorkOrderStatus.TAMAMLANDI.canTransitionTo(WorkOrderStatus.TESLIM_EDILDI)).isTrue();
        assertThat(WorkOrderStatus.TESLIM_EDILDI.canTransitionTo(WorkOrderStatus.IPTAL)).isFalse();
        assertThat(WorkOrderStatus.IPTAL.canTransitionTo(WorkOrderStatus.ISLEMDE)).isFalse();
        assertThat(WorkOrderStatus.BEKLIYOR.canTransitionTo(WorkOrderStatus.IPTAL)).isTrue();
    }

    @Test
    void statusCannotTransitionToItself() {
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
            assertThat(status.canTransitionTo(null)).isFalse();
        }
    }

    @Test
    void openAndCompletedFlagsAreConsistent() {
        assertThat(WorkOrderStatus.BEKLIYOR.isOpen()).isTrue();
        assertThat(WorkOrderStatus.ISLEMDE.isOpen()).isTrue();
        assertThat(WorkOrderStatus.TAMAMLANDI.isCompleted()).isTrue();
        assertThat(WorkOrderStatus.TESLIM_EDILDI.isCompleted()).isTrue();
        assertThat(WorkOrderStatus.IPTAL.isOpen()).isFalse();
    }
}
