package com.nikoltu.w3prep;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.hiveworkshop.blizzard.casc.nio.MalformedCASCStructureException;

public final class Analysis implements AutoCloseable {
	private final Path install;
	private final GameStorage storage;
	private final PackPlanner planner;
	private final Map<GraphicsSet, PackPlan> setPlans = new EnumMap<>(GraphicsSet.class);
	private OldInstall oldInstall;
	private OldLanguageStorage oldStorage;

	public Analysis(final Path install, final GameStorage storage) throws IOException {
		this.install = install;
		this.storage = storage;
		this.planner = new PackPlanner(storage.listEntries(), storage.getVersion(), storage);
		for (final GraphicsSet set : GraphicsSet.values()) {
			this.setPlans.put(set, this.planner.planSet(set));
		}
	}

	public static Analysis open(final Path install) throws PackException {
		final String installed = GameInstall.readVersion(install);
		if (isBeforeReforged(installed)) {
			throw new PackException(PackException.Kind.UNKNOWN_FORMAT, "Game version " + installed
					+ " is older than 1.32 (Reforged). Warsmash needs the files of 1.32 or newer. Nothing was written.");
		}
		GameStorage storage = null;
		try {
			storage = new CascGameStorage(install);
			final Analysis analysis = new Analysis(install, storage);
			storage = null;
			return analysis;
		} catch (final MalformedCASCStructureException e) {
			final String version = GameInstall.readVersion(install);
			throw new PackException(PackException.Kind.UNKNOWN_FORMAT,
					(version == null ? "This game version" : "Game version " + version)
							+ " stores its files in a way this tool can't read (" + e.getMessage()
							+ "). The tool works with the current game version from the game launcher. "
							+ "Nothing was written.",
					e);
		} catch (final IOException | RuntimeException e) {
			throw new PackException(PackException.Kind.UNKNOWN_FORMAT,
					"The game's storage could not be read (" + e.getClass().getSimpleName()
							+ (e.getMessage() == null ? "" : ": " + e.getMessage()) + where(e) + "). Nothing was written.",
					e);
		} finally {
			if (storage != null) {
				try {
					storage.close();
				} catch (final IOException e) {
				}
			}
		}
	}

	static boolean isBeforeReforged(final String version) {
		if (version == null) {
			return false;
		}
		final String[] parts = version.split("\\.");
		try {
			final int major = Integer.parseInt(parts[0]);
			return (major < 1) || ((major == 1) && (parts.length > 1) && (Integer.parseInt(parts[1]) < 32));
		} catch (final NumberFormatException e) {
			return false;
		}
	}

	static String where(final Throwable error) {
		for (final StackTraceElement frame : error.getStackTrace()) {
			if (frame.getClassName().startsWith("com.hiveworkshop") || frame.getClassName().startsWith("com.nikoltu")) {
				final String name = frame.getClassName();
				return " at " + name.substring(name.lastIndexOf('.') + 1) + "." + frame.getMethodName() + ":"
						+ frame.getLineNumber();
			}
		}
		return "";
	}

	public Path getInstall() {
		return this.install;
	}

	/** The storage the packages are written from; it reads the old install too while one is attached. */
	public synchronized GameStorage getStorage() {
		return this.oldStorage == null ? this.storage : this.oldStorage;
	}

	/** Reads the old game in this folder for a language pack; replaces one attached before. */
	public synchronized void attachOldInstall(final Path folder) throws PackException {
		final OldInstall opened = OldInstall.open(folder);
		detachOldInstall();
		this.oldInstall = opened;
		this.oldStorage = new OldLanguageStorage(this.storage, opened);
	}

	public synchronized void detachOldInstall() {
		if (this.oldInstall != null) {
			this.oldInstall.close();
		}
		this.oldInstall = null;
		this.oldStorage = null;
	}

	/** The old game's language as config.txt names it ("esES"), or null without an old install or a name. */
	public synchronized String getOldLanguage() {
		return this.oldInstall == null ? null : this.oldInstall.language();
	}

	/** The old game's folder is only read, so no package may go into it. */
	public void checkOutputOutsideOldGame(final Path output) throws PackException {
		if ((this.oldInstall != null) && output.toAbsolutePath().normalize()
				.startsWith(this.oldInstall.getFolder().toAbsolutePath().normalize())) {
			throw new PackException(PackException.Kind.BAD_OUTPUT,
					"The packages can't go into the old game's folder; it is only read. Pick another place.");
		}
	}

	public synchronized Path getOldInstallFolder() {
		return this.oldInstall == null ? null : this.oldInstall.getFolder();
	}

	public synchronized PackPlan planOldLanguage(final String language, final PackOptions options) throws PackException {
		if (this.oldInstall == null) {
			throw new PackException(PackException.Kind.OLD_INSTALL, "No old game was chosen.");
		}
		return this.planner.planOldLanguage(this.oldInstall, language, options);
	}

	public List<String> describeUnreadableNames() {
		final List<String> names = this.storage.getUnreadableNames();
		final List<String> lines = new ArrayList<>();
		if (names.isEmpty()) {
			return lines;
		}
		lines.add(names.size() + " listed files have names that are not valid text and were skipped, for example:");
		for (int i = 0; i < Math.min(5, names.size()); i++) {
			lines.add("  " + names.get(i));
		}
		return lines;
	}

	public String getLocale() {
		return PackPlanner.displayLocale(this.planner.getLocale());
	}

	/** The other languages this install has downloaded, as the packages name them ("esES"). */
	public List<String> getDownloadedLanguages() {
		final List<String> languages = new ArrayList<>();
		for (final String language : this.planner.downloadedLanguages()) {
			languages.add(PackPlanner.displayLocale(language));
		}
		return languages;
	}

	public String getVersion() {
		return this.storage.getVersion();
	}

	public PackPlan planClassic(final PackOptions options) throws PackException {
		return this.planner.planClassic(options);
	}

	public PackPlan planLanguage(final String language, final PackOptions options) throws PackException {
		return this.planner.planLanguage(language, options);
	}

	public PackPlan planSet(final GraphicsSet set) {
		return this.setPlans.get(set);
	}

	public List<PackPlan> plans(final PackOptions options) throws PackException {
		final List<PackPlan> plans = new ArrayList<>();
		plans.add(planClassic(options));
		for (final GraphicsSet set : options.getSets()) {
			final PackPlan plan = planSet(set);
			if (plan.getFileCount() > 0) {
				plans.add(plan);
			}
		}
		for (final String language : options.getLanguages()) {
			plans.add(this.planner.planLanguage(language, options));
		}
		if (options.getOldLanguage() != null) {
			plans.add(planOldLanguage(options.getOldLanguage(), options));
		}
		return plans;
	}

	@Override
	public void close() throws IOException {
		detachOldInstall();
		this.storage.close();
	}
}
