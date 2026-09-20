package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

public record RequestAttachmentUploadRequest(String fileName, String contentType, long sizeBytes) {
}
