package ru.itmo.nemat.weezzy.common.dto;

import java.util.List;

public record CursorPageResponse<T>(
		List<T> content,
		String nextCursor
) {
}
