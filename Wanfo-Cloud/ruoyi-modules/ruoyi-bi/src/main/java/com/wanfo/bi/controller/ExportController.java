package com.wanfo.bi.controller;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.ExportJobService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/bi/export")
public class ExportController {
 private final ExportJobService service;
 public ExportController(ExportJobService service){this.service=service;}
 @GetMapping @BiPermission("bi:export:list") public List<Map<String,Object>> list(){return service.list();}
 @GetMapping("/{id}") @BiPermission("bi:export:list") public Map<String,Object> get(@PathVariable Long id){return service.get(id);}
 @PostMapping @BiPermission("bi:export:add") public Map<String,Object> submit(@RequestBody ExportJobService.Submit request){return service.submit(request);}
 @PostMapping("/{id}/cancel") @BiPermission("bi:export:cancel") public Map<String,Object> cancel(@PathVariable Long id){return service.cancel(id);}
 @GetMapping("/{id}/file") @BiPermission("bi:export:download") public ResponseEntity<byte[]> file(@PathVariable Long id){
  var file=service.download(id);
  String encoded=URLEncoder.encode(file.name(),StandardCharsets.UTF_8).replace("+","%20");
  return ResponseEntity.ok()
    .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+file.name().replace("\"","")+"\"; filename*=UTF-8''"+encoded)
    .contentType(MediaType.parseMediaType(file.type()))
    .contentLength(file.bytes().length)
    .body(file.bytes());
 }
}
