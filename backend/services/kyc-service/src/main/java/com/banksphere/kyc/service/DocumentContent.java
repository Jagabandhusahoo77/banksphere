package com.banksphere.kyc.service;

/**
 * Internal transport type for streaming a document's bytes back through
 * the employee document-content endpoint — never serialized as JSON, and
 * deliberately does not carry {@code storageReference}.
 */
public record DocumentContent(byte[] content, String contentType, String fileName) {
}
