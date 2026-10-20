package com.wanfo.bi.service;
import com.wanfo.bi.mapper.BiDataFormRecordMapper;
import com.wanfo.bi.domain.BiDataFormRecordDO;
import com.wanfo.bi.security.BiAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
/** Durable intent before external DML. Never rolls back with a submission failure. */
@Service
public class DataFormAuditService {
 private final BiDataFormRecordMapper mapper;
 public DataFormAuditService(BiDataFormRecordMapper mapper){this.mapper=mapper;}
 public record Reservation(BiDataFormRecordDO record,boolean fresh){}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public Reservation reserve(BiDataFormRecordDO row){
  try{mapper.insert(row);return new Reservation(row,true);}catch(DuplicateKeyException ex){
   var stored=mapper.findSubmission(row.getFormId(),row.getOwnerPrincipal(),row.getRequestKey());
   if(stored==null || !row.getPayloadHash().equals(stored.getPayloadHash()))throw new BiAccessException(409,"提交标识已用于其他内容，请刷新后重新填写");
   return new Reservation(stored,false);
  }
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void finish(Long id,String status,String message){if(mapper.finishSubmission(id,status,message)!=1)throw new BiAccessException(409,"提交记录状态已改变，请核对记录");}
}
