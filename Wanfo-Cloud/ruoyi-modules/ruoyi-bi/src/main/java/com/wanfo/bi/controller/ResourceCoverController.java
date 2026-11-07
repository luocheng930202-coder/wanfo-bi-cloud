package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.ResourceCoverService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResourceCoverController {
    private final ResourceCoverService covers;

    public ResourceCoverController(ResourceCoverService covers) { this.covers = covers; }

    public record CoverUpload(Integer version, String dataUrl) {}

    @GetMapping("/bi/resource-cover/{kind}/{id}")
    @BiPermission
    public ResponseEntity<byte[]> read(@PathVariable String kind, @PathVariable Long id, @RequestParam int v) {
        var image = covers.read(kind, id, v);
        if (image == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(CacheControl.noCache()).body(image.body());
    }

    @PostMapping("/bi/resource-cover/{kind}/{id}")
    @BiPermission
    public MapResult save(@PathVariable String kind, @PathVariable Long id, @RequestBody CoverUpload body) {
        if (body == null || body.version() == null) throw new IllegalArgumentException("封面版本不能为空");
        covers.save(kind, id, body.version(), body.dataUrl());
        return new MapResult(true);
    }

    public record MapResult(boolean saved) {}
}
