package ru.itmo.nemat.weezzy.common.pagination;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Component
public class CursorTokenCodec {
	private static final String VERSION = "v1";
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder()
			.withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	public String encode(String type, List<String> values) {
		String rawCursor = String.join("|", VERSION, type, String.join("|", values));
		return ENCODER.encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
	}

	public List<String> decode(String cursor, String expectedType, int valueCount) {
		String rawCursor = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
		String[] parts = rawCursor.split("\\|", -1);

		if (parts.length != valueCount + 2
				|| !VERSION.equals(parts[0])
				|| !expectedType.equals(parts[1])) {
			throw new IllegalArgumentException("Invalid cursor token");
		}

		return Arrays.asList(parts).subList(2, parts.length);
	}
}
