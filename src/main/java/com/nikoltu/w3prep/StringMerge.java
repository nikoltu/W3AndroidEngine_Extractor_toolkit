package com.nikoltu.w3prep;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts an older game's translation into a newer game's string file key by key. The newer file stays the frame: its
 * sections, keys and order are kept, and a key takes the old value where the old file has the same key in the same
 * section. Keys the old game did not have stay as they are, so they show in the newer file's language.
 */
public final class StringMerge {
	private static final Pattern FDF_STRING = Pattern.compile("^(\\s*)(\"?)([A-Za-z0-9_]+)(\"?)(\\s+)\"(.*)\"(.*)$");

	private StringMerge() {
	}

	public static final class Result {
		private final byte[] text;
		private final int keys;
		private final int translated;

		Result(final byte[] text, final int keys, final int translated) {
			this.text = text;
			this.keys = keys;
			this.translated = translated;
		}

		public byte[] getText() {
			return this.text;
		}

		public int getKeys() {
			return this.keys;
		}

		public int getTranslated() {
			return this.translated;
		}
	}

	/** Merges by the file's kind: an .fdf string list or a .txt with [sections] and key=value lines. */
	public static Result merge(final String path, final byte[] current, final byte[] old) {
		final boolean fdf = path.toLowerCase(Locale.ROOT).endsWith(".fdf");
		final Map<String, String> oldValues = fdf ? fdfValues(text(old)) : txtValues(text(old));
		final String frame = text(current);
		final StringBuilder out = new StringBuilder(frame.length() + 1024);
		if (hasBom(current)) {
			out.append('﻿');
		}
		int keys = 0;
		int translated = 0;
		String section = "";
		int start = 0;
		while (start < frame.length()) {
			int end = frame.indexOf('\n', start);
			end = end < 0 ? frame.length() : end + 1;
			final String withBreak = frame.substring(start, end);
			start = end;
			final int bodyEnd = withBreak.endsWith("\r\n") ? withBreak.length() - 2
					: withBreak.endsWith("\n") ? withBreak.length() - 1 : withBreak.length();
			final String line = withBreak.substring(0, bodyEnd);
			final String lineBreak = withBreak.substring(bodyEnd);
			String replaced = null;
			if (fdf) {
				final Matcher matcher = FDF_STRING.matcher(line);
				if (matcher.matches()) {
					keys++;
					final String value = oldValues.get(matcher.group(3).toLowerCase(Locale.ROOT));
					if (value != null) {
						replaced = matcher.group(1) + matcher.group(2) + matcher.group(3) + matcher.group(4)
								+ matcher.group(5) + '"' + value + '"' + matcher.group(7);
					}
				}
			} else {
				final String trimmed = line.trim();
				if (trimmed.startsWith("[") && (trimmed.indexOf(']') > 0)) {
					section = trimmed.substring(1, trimmed.indexOf(']')).toLowerCase(Locale.ROOT);
				} else if (!trimmed.startsWith("//") && !trimmed.startsWith(";") && (line.indexOf('=') > 0)) {
					keys++;
					final int equals = line.indexOf('=');
					final String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
					final String value = oldValues.get(section + "\n" + key);
					if (value != null) {
						replaced = line.substring(0, equals + 1) + value;
					}
				}
			}
			if (replaced != null) {
				translated++;
				out.append(replaced);
			} else {
				out.append(line);
			}
			out.append(lineBreak);
		}
		return new Result(out.toString().getBytes(StandardCharsets.UTF_8), keys, translated);
	}

	private static Map<String, String> txtValues(final String text) {
		final Map<String, String> values = new HashMap<>();
		String section = "";
		for (final String raw : text.split("\r?\n")) {
			final String line = raw.trim();
			if (line.startsWith("[") && (line.indexOf(']') > 0)) {
				section = line.substring(1, line.indexOf(']')).toLowerCase(Locale.ROOT);
			} else if (!line.startsWith("//") && !line.startsWith(";") && (line.indexOf('=') > 0)) {
				final int equals = line.indexOf('=');
				values.putIfAbsent(section + "\n" + line.substring(0, equals).trim().toLowerCase(Locale.ROOT),
						raw.substring(raw.indexOf('=') + 1).replaceAll("\\s+$", ""));
			}
		}
		return values;
	}

	private static Map<String, String> fdfValues(final String text) {
		final Map<String, String> values = new HashMap<>();
		for (final String raw : text.split("\r?\n")) {
			final Matcher matcher = FDF_STRING.matcher(raw);
			if (matcher.matches()) {
				values.putIfAbsent(matcher.group(3).toLowerCase(Locale.ROOT), matcher.group(6));
			}
		}
		return values;
	}

	private static boolean hasBom(final byte[] bytes) {
		return (bytes.length >= 3) && ((bytes[0] & 0xFF) == 0xEF) && ((bytes[1] & 0xFF) == 0xBB)
				&& ((bytes[2] & 0xFF) == 0xBF);
	}

	private static String text(final byte[] bytes) {
		final String text = new String(bytes, StandardCharsets.UTF_8);
		return text.startsWith("﻿") ? text.substring(1) : text;
	}
}
