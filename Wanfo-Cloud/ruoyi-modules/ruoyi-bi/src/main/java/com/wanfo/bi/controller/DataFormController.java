package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;

import com.wanfo.bi.domain.form.DataFormField;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.DataFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 数据填报")
@RestController
@RequestMapping("/bi/form")
public class DataFormController
{
    private final DataFormService service;
    private final com.wanfo.bi.service.DataFormReviewService reviews;

    public DataFormController(DataFormService service,com.wanfo.bi.service.DataFormReviewService reviews)
    {
        this.service = service;
        this.reviews=reviews;
    }

    public record FormBody(String name, Long datasetId, List<DataFormField> fields, String remark, String status) { }

    @GetMapping
    @BiPermission("bi:form:list")
    public List<Map<String, Object>> list()
    {
        return service.list();
    }

    @GetMapping("/{id}")
    @BiPermission("bi:form:list")
    public Map<String, Object> detail(@PathVariable Long id)
    {
        return service.detail(id);
    }

    @PostMapping
    @BiPermission("bi:form:add")
    public Map<String, Object> create(@RequestBody FormBody body)
    {
        return service.create(body.name(), body.datasetId(), body.fields(), body.remark());
    }

    @PutMapping("/{id}")
    @BiPermission("bi:form:edit")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody FormBody body)
    {
        return service.update(id, body.name(), body.fields(), body.remark(), body.status());
    }

    @DeleteMapping("/{id}")
    @BiPermission("bi:form:remove")
    public Map<String, Object> delete(@PathVariable Long id)
    {
        return service.delete(id);
    }

    @PostMapping("/{id}/submit")
    @BiPermission("bi:form:submit")
    public Map<String, Object> submit(@PathVariable Long id, @RequestBody Map<String, Object> values, @org.springframework.web.bind.annotation.RequestHeader(value="X-BI-Submission-Id",required=false) String requestKey) throws Exception
    {
        return service.submit(id, values, requestKey);
    }

    @GetMapping("/{id}/records")
    @BiPermission("bi:form:list")
    public List<Map<String, Object>> records(@PathVariable Long id)
    {
        return service.records(id);
    }
    @PostMapping("/{id}/records/{recordId}/review")
    @BiPermission("bi:form:edit")
    public Map<String,Object> review(@PathVariable Long id,@PathVariable Long recordId,@RequestBody com.wanfo.bi.service.DataFormReviewService.Review request){return reviews.review(id,recordId,request);}
    @GetMapping("/{id}/records/{recordId}/reviews")
    @BiPermission("bi:form:list")
    public List<Map<String,Object>> reviews(@PathVariable Long id,@PathVariable Long recordId){return reviews.history(id,recordId);}
}
