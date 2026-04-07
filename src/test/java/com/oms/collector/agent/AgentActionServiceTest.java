package com.oms.collector.agent;

import com.oms.collector.agent.dto.AgentActionProposal;
import com.oms.collector.controller.AllocationController;
import com.oms.collector.controller.CancelController;
import com.oms.collector.controller.CsMemoController;
import com.oms.collector.controller.InvoiceController;
import com.oms.collector.controller.StockMatchingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AgentActionServiceTest {

    @Mock private InvoiceController invoiceController;
    @Mock private AllocationController allocationController;
    @Mock private CancelController cancelController;
    @Mock private StockMatchingController stockMatchingController;
    @Mock private CsMemoController csMemoController;

    @Test
    @DisplayName("송장번호 부여 문장은 송장 자동부여 제안으로 해석된다")
    void invoiceAssignPhraseIsRecognized() {
        AgentActionService service = new AgentActionService(
            invoiceController, allocationController, cancelController, stockMatchingController, csMemoController
        );

        AgentActionProposal proposal = service.propose("OMS-20260330-0434 송장번호 부여해줘", "관리자");

        assertThat(proposal).isNotNull();
        assertThat(proposal.actionType()).isEqualTo("AUTO_ASSIGN_INVOICE");
        assertThat(proposal.title()).contains("송장 자동부여");
    }

    @Test
    @DisplayName("창고 변경 문장은 할당 창고 변경 제안으로 해석된다")
    void warehouseChangePhraseIsRecognized() {
        AgentActionService service = new AgentActionService(
            invoiceController, allocationController, cancelController, stockMatchingController, csMemoController
        );

        AgentActionProposal proposal = service.propose("할당 창고를 ANYANG으로 설정해줘", "관리자");

        assertThat(proposal).isNotNull();
        assertThat(proposal.actionType()).isEqualTo("SET_ALLOCATION_WAREHOUSE");
        assertThat(proposal.params()).containsEntry("warehouseCode", "ANYANG");
    }
}
