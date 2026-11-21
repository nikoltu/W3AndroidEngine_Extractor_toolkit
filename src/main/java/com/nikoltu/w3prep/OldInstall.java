package com.nikoltu.w3prep;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nikoltu.w3prep.mpq.ArchivedFile;
import com.nikoltu.w3prep.mpq.ArchivedFileExtractor;
import com.nikoltu.w3prep.mpq.ArchivedFileStream;
import com.nikoltu.w3prep.mpq.HashLookup;
import com.nikoltu.w3prep.mpq.MPQArchive;
import com.nikoltu.w3prep.mpq.MPQException;

/**
 * A Warcraft III install from before 1.30, which keeps its files in MPQ archives beside the game. It is only read:
 * the archives are opened read-only and nothing is written next to them.
 */
public final class OldInstall implements OldFiles, Closeable {
	/** Lowest priority first; a later archive's file wins, as in the game. */
	private static final List<String> ARCHIVES = Arrays.asList("war3.mpq", "war3local.mpq", "war3x.mpq",
			"war3xlocal.mpq", "war3patch.mpq");
	static final List<String> LANGUAGES = Arrays.asList("deDE", "esES", "esMX", "frFR", "itIT", "plPL", "ptBR",
			"ruRU", "koKR", "zhTW", "zhCN", "jaJP");
	private static final Pattern BATTLE_NET_LANGUAGE = Pattern
			.compile("(?is)\\[battle\\.net\\][^\\[]*?langid\\s*=\\s*'([a-z]{4})'");

	private final Path folder;
	private final List<SeekableByteChannel> channels = new ArrayList<>();
	private final List<MPQArchive> archives = new ArrayList<>();
	private final ArchivedFileExtractor extractor = new ArchivedFileExtractor();

	private OldInstall(final Path folder) {
		this.folder = folder;
	}

	public static OldInstall open(final Path folder) throws PackException {
		final Map<String, Path> found = new HashMap<>();
		try (DirectoryStream<Path> children = Files.newDirectoryStream(folder)) {
			for (final Path child : children) {
				found.put(child.getFileName().toString().toLowerCase(Locale.ROOT), child);
			}
		} catch (final IOException e) {
			throw new PackException(PackException.Kind.OLD_INSTALL, "The folder " + folder + " could not be read.", e);
		}
		if (!found.containsKey("war3.mpq")) {
			throw new PackException(PackException.Kind.OLD_INSTALL, found.containsKey(".build.info")
					? "This is a game of version 1.30 or newer. Pick a Warcraft III folder from before 1.30, the one "
							+ "with war3.mpq."
					: "This folder has no war3.mpq. Pick the folder of an old Warcraft III (before 1.30) that "
							+ "contains war3.mpq.");
		}
		final OldInstall install = new OldInstall(folder);
		try {
			for (final String name : ARCHIVES) {
				final Path archive = found.get(name);
				if (archive != null) {
					final SeekableByteChannel channel = Files.newByteChannel(archive, StandardOpenOption.READ);
					install.channels.add(channel);
					install.archives.add(new MPQArchive(channel));
				}
			}
			return install;
		} catch (final IOException | MPQException | RuntimeException e) {
			install.close();
			throw new PackException(PackException.Kind.OLD_INSTALL, "The archives in " + folder
					+ " could not be read (" + e.getClass().getSimpleName() + "). Nothing was written.", e);
		}
	}

	public Path getFolder() {
		return this.folder;
	}

	/** The index of the archive that gives this file, or -1. */
	private int archiveOf(final String path) {
		final HashLookup lookup = new HashLookup(path.replace('/', '\\'));
		for (int i = this.archives.size() - 1; i >= 0; i--) {
			try {
				this.archives.get(i).lookupHash(lookup);
				return i;
			} catch (final MPQException e) {
				// not in this archive
			}
		}
		return -1;
	}

	@Override
	public boolean has(final String path) {
		return archiveOf(path) >= 0;
	}

	@Override
	public long size(final String path) {
		final int index = archiveOf(path);
		if (index < 0) {
			return -1;
		}
		try {
			return this.archives.get(index).lookupHash2(new HashLookup(path.replace('/', '\\'))).fileSize;
		} catch (final MPQException e) {
			return -1;
		}
	}

	@Override
	public byte[] read(final String path) throws IOException {
		final int index = archiveOf(path);
		if (index < 0) {
			throw new IOException("not in the old install");
		}
		try {
			final ArchivedFile file = this.archives.get(index).lookupHash2(new HashLookup(path.replace('/', '\\')));
			try (ArchivedFileStream stream = new ArchivedFileStream(this.channels.get(index), this.extractor, file)) {
				final ByteBuffer buffer = ByteBuffer.allocate((int) stream.size());
				while (buffer.hasRemaining() && (stream.read(buffer) > 0)) {
					// a read gives one sector at most
				}
				if (buffer.hasRemaining()) {
					throw new IOException("the archive ended early");
				}
				return buffer.array();
			}
		} catch (final MPQException e) {
			throw new IOException(e);
		}
	}

	/** The language of the install ("esES") from its config.txt, or null when it does not name one. */
	public String language() {
		try {
			return languageOf(new String(read("config.txt"), StandardCharsets.UTF_8));
		} catch (final IOException e) {
			return null;
		}
	}

	static String languageOf(final String config) {
		final Matcher matcher = BATTLE_NET_LANGUAGE.matcher(config);
		if (!matcher.find()) {
			return null;
		}
		final String code = matcher.group(1);
		final String canonical = code.substring(0, 2).toLowerCase(Locale.ROOT) + code.substring(2).toUpperCase(Locale.ROOT);
		// The old config spells Japanese as the game never did elsewhere.
		return "jaJA".equals(canonical) ? "jaJP" : canonical;
	}

	@Override
	public void close() {
		for (final SeekableByteChannel channel : this.channels) {
			try {
				channel.close();
			} catch (final IOException e) {
				// read-only, nothing to lose
			}
		}
		this.channels.clear();
		this.archives.clear();
	}
}
