package tr.com.servisdefteri.workorder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderStatusTest {

    @Test
    void allowedTransitionsFollowTheServiceFlow() {
        assertThat(WorkOrderStatus.ISLEMDE.canTransitionTo(WorkOrderStatus.TAMAMLANDI)).isTrue();
        assertThat(WorkOrderStatus.ISLEMDE.canTransitionTo(WorkOrderStatus.TESLIM_EDILDI)).isFalse();
        assertThat(WorkOrderStatus.TAMAMLANDI.canTransitionTo(WorkOrderStatus.TESLIM_EDILDI)).isTrue();
        assertThat(WorkOrderStatus.TESLIM_EDILDI.canTransitionTo(WorkOrderStatus.IPTAL)).isFalse();
        assertThat(WorkOrderStatus.IPTAL.canTransitionTo(WorkOrderStatus.ISLEMDE)).isFalse();
        assertThat(WorkOrderStatus.ISLEMDE.canTransitionTo(WorkOrderStatus.IPTAL)).isTrue();
    }

    @Test
    void waitingStatusIsNotPartOfTheFlow() {
        assertThat(WorkOrderStatus.values()).extracting(Enum::name).doesNotContain("BEKLIYOR");
    }

    @Test
    void cssNamesAreAsciiLowerCase() {
        assertThat(WorkOrderStatus.ISLEMDE.getCssName()).isEqualTo("islemde");
        assertThat(WorkOrderStatus.TAMAMLANDI.getCssName()).isEqualTo("tamamlandi");
        assertThat(WorkOrderStatus.TESLIM_EDILDI.getCssName()).isEqualTo("teslim_edildi");
        assertThat(WorkOrderStatus.IPTAL.getCssName()).isEqualTo("iptal");
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
        assertThat(WorkOrderStatus.ISLEMDE.isOpen()).isTrue();
        assertThat(WorkOrderStatus.TAMAMLANDI.isOpen()).isFalse();
        assertThat(WorkOrderStatus.TAMAMLANDI.isCompleted()).isTrue();
        assertThat(WorkOrderStatus.TESLIM_EDILDI.isCompleted()).isTrue();
        assertThat(WorkOrderStatus.IPTAL.isOpen()).isFalse();
    }
}
