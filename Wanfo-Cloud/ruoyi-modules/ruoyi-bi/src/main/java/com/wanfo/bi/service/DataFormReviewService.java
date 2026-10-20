package com.wanfo.bi.service;

import java.util.*;
import java.util.function.Supplier;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manual reconciliation never executes source DML or retries a submission. */
@Service
public class DataFormReviewService {
    public record Review(String expectedStatus,String decision,String evidence) { }
    private final BiDataFormMapper forms;
    private final BiDataFormRecordMapper records;
    private final BiResourceAuthorizer resources;
    private final LiveActorResolver live;
    private final CurrentActorProvider actors;
    public DataFormReviewService(BiDataFormMapper forms,BiDataFormRecordMapper records,BiResourceAuthorizer resources,LiveActorResolver live,CurrentActorProvider actors){this.forms=forms;this.records=records;this.resources=resources;this.live=live;this.actors=actors;}

    @Transactional
    public Map<String,Object> review(Long form,Long id,Review request) {
        return authorized(form,id,()->{
            var target=records.reviewTarget(form,id);
            if(request==null||request.expectedStatus()==null||!Set.of("pending","uncertain").contains(request.expectedStatus())||request.decision()==null||!Set.of("note","succeeded","failed").contains(request.decision()))throw new IllegalArgumentException("请选择有效的核对状态和处理方式");
            String note=request.evidence()==null?"":request.evidence().trim();
            if(note.length()<5||note.length()>500)throw new IllegalArgumentException("请填写5至500字核对依据，包括业务唯一标识和源库核对结果");
            if(!request.expectedStatus().equals(target.get("status")))throw new BiAccessException(409,"记录状态已改变，请刷新后核对");
            if("pending".equals(request.expectedStatus())&&!"note".equals(request.decision()))throw new BiAccessException(409,"待核对记录可能仍在执行，只能补充核对记录，不能直接判定终态");
            String next="note".equals(request.decision())?request.expectedStatus():request.decision();Long reviewer=actors.currentActor().userId();
            if(records.reviewSubmission(form,id,request.expectedStatus(),next,reviewer,note)!=1)throw new BiAccessException(409,"记录状态已改变，请刷新后核对");
            if(records.appendReview(id,request.expectedStatus(),next,reviewer,note)!=1)throw new IllegalStateException("核对审计保存失败");
            return Map.of("reviewed",true,"status",next);
        });
    }
    public List<Map<String,Object>> history(Long form,Long id){return authorized(form,id,()->records.reviewHistory(id));}
    @SuppressWarnings("unchecked") private <T> T authorized(Long form,Long id,Supplier<T> action){
        var current=live.resolve(actors.currentActor());current.require("bi:resource:admin");current.require("bi:form:edit");current.require("bi:form:list");Object[] result=new Object[1];
        ActorScope.run(current.actor(),current.capabilities(),()->{
            var definition=forms.selectById(form);if(definition==null)throw new IllegalArgumentException("表单不存在");
            for(String permission:List.of("view","query","write"))resources.require("dataset",definition.getDatasetId(),permission);
            if(records.reviewTarget(form,id)==null)throw new IllegalArgumentException("填报记录不存在");
            result[0]=action.get();
        });return (T)result[0];
    }
}
