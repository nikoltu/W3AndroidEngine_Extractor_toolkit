package com.nikoltu.w3prep;

import java.io.IOException;
import java.util.List;

/**
 * The game's storage with an old install beside it, for a language pack from the old game: sounds come from the old
 * archives, and string files are the new game's with the old translation merged in when they are written.
 */
public final class OldLanguageStorage implements GameStorage {
	static final String OLD = "old-install:";
	static final String MERGED = "merged:";
	private static final char SEPARATOR = '|';

	private final GameStorage base;
	private final OldFiles old;

	public OldLanguageStorage(final GameStorage base, final OldFiles old) {
		this.base = base;
		this.old = old;
	}

	static String mergedSource(final String basePath, final String oldPath) {
		return MERGED + basePath + SEPARATOR + oldPath;
	}

	@Override
	public String getVersion() {
		return this.base.getVersion();
	}

	@Override
	public List<StorageEntry> listEntries() throws IOException {
		return this.base.listEntries();
	}

	@Override
	public byte[] read(final String path) throws IOException {
		if (path.startsWith(OLD)) {
			return this.old.read(path.substring(OLD.length()));
		}
		if (path.startsWith(MERGED)) {
			final int separator = path.lastIndexOf(SEPARATOR);
			final String oldPath = path.substring(separator + 1);
			return StringMerge.merge(oldPath, this.base.read(path.substring(MERGED.length(), separator)),
					this.old.read(oldPath)).getText();
		}
		return this.base.read(path);
	}

	@Override
	public List<String> getUnreadableNames() {
		return this.base.getUnreadableNames();
	}

	@Override
	public BlockCheck calibrate(final List<String> samplePaths) {
		final List<String> gamePaths = new java.util.ArrayList<>();
		for (final String path : samplePaths) {
			if (!path.startsWith(OLD) && !path.startsWith(MERGED)) {
				gamePaths.add(path);
			}
		}
		return this.base.calibrate(gamePaths);
	}

	/** Closes nothing: the game's storage and the old install belong to whoever opened them. */
	@Override
	public void close() {
	}
}
