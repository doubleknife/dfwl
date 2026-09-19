package com.dfwl.fleet.attachment.controller;

import com.dfwl.fleet.attachment.dto.response.AttachmentResponse;

import com.dfwl.fleet.attachment.service.AttachmentService;
import com.dfwl.fleet.attachment.domain.AttachmentFile;
import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public ApiResponse<List<AttachmentResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String purpose) {
        return ApiResponse.success(attachmentService.list(ownerType, ownerId, purpose, user), RequestIdHolder.get());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String ownerType,
            @RequestParam long ownerId,
            @RequestParam String purpose,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(attachmentService.upload(ownerType, ownerId, purpose, file, user), RequestIdHolder.get());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        AttachmentFile download = attachmentService.download(id, user);
        MediaType mediaType = MediaType.parseMediaType(download.attachment().contentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.attachment().originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.attachment().fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-File-Sha256", download.attachment().fileHash())
                .body(download.resource());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        attachmentService.rejectOrdinaryDelete(id, user);
        return ApiResponse.success(RequestIdHolder.get());
    }
}
