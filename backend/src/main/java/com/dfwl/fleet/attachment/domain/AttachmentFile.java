package com.dfwl.fleet.attachment.domain;

import org.springframework.core.io.Resource;

public record AttachmentFile(FileAttachment attachment, Resource resource) {
}
