package com.wanfo.bi.controller;

import com.wanfo.bi.service.SnapshotImportService;
import com.wanfo.bi.security.BiPermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

/** Service enforces create/edit capability and individual resource access for every operation. */
@RestController
@RequestMapping("/bi/datasource/import")
public class BiSnapshotController {
    private final SnapshotImportService service;
    public BiSnapshotController(SnapshotImportService service){this.service=service;}
    @BiPermission(anyOf={"bi:datasource:add","bi:datasource:edit"})
    @PostMapping("/api") public Map<String,Object> api(@RequestBody SnapshotImportService.ApiRequest request)throws Exception{return service.api(request);}
    @BiPermission("bi:datasource:edit")
    @PostMapping("/{id}/refresh") public Map<String,Object> refresh(@PathVariable Long id)throws Exception{return service.refresh(id);}
    @BiPermission("bi:datasource:edit")
    @GetMapping("/{id}/info") public Map<String,Object> info(@PathVariable Long id){return service.info(id);}
    @BiPermission(anyOf={"bi:datasource:add","bi:datasource:edit"})
    @PostMapping("/file") public Map<String,Object> file(@RequestParam(required=false) Long id,@RequestParam(required=false) String name,
            @RequestParam(required=false) String schemaName,@RequestParam(required=false) String sheet,@RequestParam(defaultValue="replace") String mode,@RequestParam(required=false) String columnTypes,@RequestParam(defaultValue="reject") String invalidMode,@RequestParam MultipartFile file)throws Exception {
        if(file.getSize()>com.wanfo.bi.datasource.snapshot.SnapshotParser.MAX_BYTES)throw new IllegalArgumentException("文件不能超过 5 MB");
        return service.file(id,name,schemaName,file.getOriginalFilename()==null?"upload":file.getOriginalFilename(),sheet,file.getBytes(),mode,columnTypes,invalidMode);
    }
    @PostMapping("/file/preview")
    @BiPermission(anyOf={"bi:datasource:add","bi:datasource:edit"})
    public Map<String,Object> preview(@RequestParam(required=false) Long id,@RequestParam(required=false) String sheet,@RequestParam MultipartFile file)throws Exception {
        if(file.getSize()>com.wanfo.bi.datasource.snapshot.SnapshotParser.MAX_BYTES)throw new IllegalArgumentException("文件不能超过 5 MB");
        return service.previewFile(id,file.getOriginalFilename()==null?"upload":file.getOriginalFilename(),sheet,file.getBytes());
    }
}
