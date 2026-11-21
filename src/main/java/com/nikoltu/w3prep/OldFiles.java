package com.nikoltu.w3prep;

import java.io.IOException;

/** The files of an old game that a language pack takes. */
public interface OldFiles {
	boolean has(String path);

	/** The size of the file, or -1 when the old game has none. */
	long size(String path);

	byte[] read(String path) throws IOException;

	/**
	 * The name of the old game's version of a sound, which may be stored in another format (a ".wav" for the ".flac"
	 * of a newer game); null when it has none.
	 */
	default String findSound(final String path) {
		if (has(path)) {
			return path;
		}
		final int dot = path.lastIndexOf('.');
		if (dot < 0) {
			return null;
		}
		for (final String extension : new String[] { ".wav", ".mp3", ".flac", ".ogg" }) {
			final String candidate = path.substring(0, dot) + extension;
			if (has(candidate)) {
				return candidate;
			}
		}
		return null;
	}
}
