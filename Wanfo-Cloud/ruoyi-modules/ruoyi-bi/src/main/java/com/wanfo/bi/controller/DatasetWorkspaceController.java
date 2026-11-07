package com.wanfo.bi.controller;

import java.util.*;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.domain.dataset.DatasetJoinModel;
import com.wanfo.bi.service.*;
import com.wanfo.bi.security.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bi/dataset/{id}/workspace")
public class DatasetWorkspaceController {
    public record Request(BiDatasetTableDO dataset,DatasetJoinModel model,List<DatasetService.FieldSelection> fields,
                          Map<String,List<String>> parameterValues,String revision,String impactToken) {
        DatasetWorkspaceService.Draft draft(){return new DatasetWorkspaceService.Draft(dataset,model,fields,parameterValues,revision,impactToken);}
    }
    private final DatasetWorkspaceService workspace;
    private final CurrentActorProvider actors;
    public DatasetWorkspaceController(DatasetWorkspaceService workspace,CurrentActorProvider actors){this.workspace=workspace;this.actors=actors;}
    @GetMapping
    @BiPermission("bi:datasource:model")
    public Map<String,Object> load(@PathVariable Long id){return workspace.load(id);}
    @PostMapping("/check")
    @BiPermission("bi:datasource:model")
    public DatasetWorkspaceService.Impact check(@PathVariable Long id,@RequestBody Request request) throws Exception {return workspace.check(id,request.draft());}
    @PutMapping
    @BiPermission("bi:datasource:model")
    public Map<String,Object> save(@PathVariable Long id,@RequestBody Request request) throws Exception {return workspace.save(id,request.draft(),actors.currentActor().username());}
}
