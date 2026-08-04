package com.huazaiki.purchase.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.purchase.entity.ApprovalFlow;
import com.huazaiki.purchase.entity.ApprovalRecord;
import com.huazaiki.purchase.entity.ApprovalTask;
import com.huazaiki.purchase.mapper.ApprovalFlowMapper;
import com.huazaiki.purchase.mapper.ApprovalRecordMapper;
import com.huazaiki.purchase.mapper.ApprovalTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalEngine")
class ApprovalEngineTest {

    private static final String RULE_JSON = """
            {
              "flowCode": "PR_DEFAULT",
              "bizType": "PR",
              "nodes": [
                {"key":"dept_manager","name":"部门经理审批","approver":{"type":"ROLE","value":"DEPT_MANAGER","scope":"APPLICANT_DEPT"},"enabled":{"field":"totalAmount","op":"gte","value":0}},
                {"key":"purchase_manager","name":"采购经理审批","approver":{"type":"ROLE","value":"PURCHASE_MANAGER","scope":"ANY"},"enabled":{"field":"totalAmount","op":"gte","value":50000}}
              ],
              "rejectStrategy": "BACK_TO_SUBMITTER",
              "transferAllowed": true
            }
            """;

    @Mock private ApprovalFlowMapper flowMapper;
    @Mock private ApprovalTaskMapper taskMapper;
    @Mock private ApprovalRecordMapper recordMapper;
    @Mock private BizApprovalCallback callback;

    private ApprovalEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new ApprovalEngineImpl(flowMapper, taskMapper, recordMapper, new ObjectMapper());
        engine.registerCallback("PR", callback);
    }

    private ApprovalFlow flow() {
        ApprovalFlow flow = new ApprovalFlow();
        flow.setId(1L);
        flow.setBizType("PR");
        flow.setStatus("ACTIVE");
        flow.setRuleJson(RULE_JSON);
        return flow;
    }

    @Test
    @DisplayName("submit creates first pending task and notifies submitted")
    void shouldCreateFirstTaskOnSubmit() {
        when(flowMapper.selectOne(any())).thenReturn(flow());
        when(taskMapper.insert((ApprovalTask) any())).thenAnswer(inv -> {
            ApprovalTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });
        when(recordMapper.insert((ApprovalRecord) any())).thenReturn(1);

        Map<String, Object> snapshot = Map.of("totalAmount", 10000, "applicantDeptId", 10L);
        ApprovalEngine.ApprovalContext ctx = engine.submit("PR", 100L, snapshot, callback);

        assertNotNull(ctx.firstTaskId());
        verify(taskMapper).insert((ApprovalTask) argThat((ApprovalTask t) -> "PENDING".equals(t.getStatus())
                && "DEPT_MANAGER".equals(t.getApproverRole())
                && Long.valueOf(10L).equals(t.getScopeDeptId())));
        verify(callback).onSubmitted("PR", 100L);
    }

    @Test
    @DisplayName("approve last node completes and notifies approved")
    void shouldCompleteOnLastNodeApprove() {
        ApprovalTask task = new ApprovalTask();
        task.setId(1L);
        task.setFlowId(1L);
        task.setBizType("PR");
        task.setBizId(100L);
        task.setNodeKey("purchase_manager");
        task.setApproverRole("PURCHASE_MANAGER");
        task.setStatus("PENDING");
        task.setSnapshotJson("{\"totalAmount\":60000,\"applicantDeptId\":10}");

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(flowMapper.selectById(1L)).thenReturn(flow());
        when(taskMapper.updateById((ApprovalTask) any())).thenReturn(1);
        when(recordMapper.insert((ApprovalRecord) any())).thenReturn(1);

        engine.approve(1L, 9L, List.of("PURCHASE_MANAGER"), 10L, "同意");

        assertEquals("APPROVED", task.getStatus());
        verify(callback).onApproved("PR", 100L);
    }

    @Test
    @DisplayName("approve advances to next node when condition met")
    void shouldAdvanceToNextNode() {
        ApprovalTask task = new ApprovalTask();
        task.setId(1L);
        task.setFlowId(1L);
        task.setBizType("PR");
        task.setBizId(100L);
        task.setNodeKey("dept_manager");
        task.setApproverRole("DEPT_MANAGER");
        task.setScopeDeptId(10L);
        task.setStatus("PENDING");
        task.setSnapshotJson("{\"totalAmount\":60000,\"applicantDeptId\":10}");

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(flowMapper.selectById(1L)).thenReturn(flow());
        when(taskMapper.updateById((ApprovalTask) any())).thenReturn(1);
        when(taskMapper.insert((ApprovalTask) any())).thenReturn(1);
        when(recordMapper.insert((ApprovalRecord) any())).thenReturn(1);

        engine.approve(1L, 9L, List.of("DEPT_MANAGER"), 10L, "同意");

        verify(taskMapper).insert((ApprovalTask) argThat((ApprovalTask t) -> "PURCHASE_MANAGER".equals(t.getApproverRole())));
        verify(callback, never()).onApproved(any(), any());
    }

    @Test
    @DisplayName("reject with BACK_TO_SUBMITTER notifies rejectedBack")
    void shouldRejectBack() {
        ApprovalTask task = new ApprovalTask();
        task.setId(1L);
        task.setFlowId(1L);
        task.setBizType("PR");
        task.setBizId(100L);
        task.setNodeKey("dept_manager");
        task.setApproverRole("DEPT_MANAGER");
        task.setScopeDeptId(10L);
        task.setStatus("PENDING");

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(flowMapper.selectById(1L)).thenReturn(flow());
        when(taskMapper.updateById((ApprovalTask) any())).thenReturn(1);
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.insert((ApprovalRecord) any())).thenReturn(1);

        engine.reject(1L, 9L, List.of("DEPT_MANAGER"), 10L, "不通过");

        assertEquals("REJECTED", task.getStatus());
        verify(callback).onRejectedBack("PR", 100L);
    }

    @Test
    @DisplayName("approve with wrong role throws 403")
    void shouldForbidWrongRole() {
        ApprovalTask task = new ApprovalTask();
        task.setId(1L);
        task.setFlowId(1L);
        task.setBizType("PR");
        task.setBizId(100L);
        task.setNodeKey("dept_manager");
        task.setApproverRole("DEPT_MANAGER");
        task.setScopeDeptId(10L);
        task.setStatus("PENDING");

        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThrows(BusinessException.class, () ->
                engine.approve(1L, 9L, List.of("PURCHASER"), 10L, "x"));
    }
}