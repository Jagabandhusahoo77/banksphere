package com.banksphere.employee.dto;

import java.util.List;

/** Deserialization target for a downstream service's {@code PageResponse<T>} — only {@code content} is needed here. */
public record RemotePage<T>(List<T> content) {
}
