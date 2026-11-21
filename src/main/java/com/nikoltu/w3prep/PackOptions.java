package com.nikoltu.w3prep;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

public final class PackOptions {
	public enum Content {
		FULL_CLASSIC, MELEE_ONLY
	}

	/** The engine plays the smallest copy at least as wide as the screen, else the widest there is. */
	public static final int[] MOVIE_WIDTHS = { 1920, 1280, 640 };
	public static final int DEFAULT_MOVIE_WIDTH = 1920;

	private final Content content;
	private final boolean movies;
	private final int movieWidth;
	private final Set<GraphicsSet> sets;
	private final boolean desktopFolder;
	/** Lower case codes ("eses") of the languages that get a language pack of their own. */
	private final Set<String> languages;
	/** Lower case code of the old game's language that gets a pack from an old install, or null. */
	private final String oldLanguage;

	public PackOptions(final Content content, final boolean movies, final Set<GraphicsSet> sets,
			final boolean desktopFolder) {
		this(content, movies, DEFAULT_MOVIE_WIDTH, sets, desktopFolder);
	}

	public PackOptions(final Content content, final boolean movies, final int movieWidth,
			final Set<GraphicsSet> sets, final boolean desktopFolder) {
		this(content, movies, movieWidth, sets, desktopFolder, Collections.emptySet(), null);
	}

	private PackOptions(final Content content, final boolean movies, final int movieWidth,
			final Set<GraphicsSet> sets, final boolean desktopFolder, final Set<String> languages,
			final String oldLanguage) {
		if (!isMovieWidth(movieWidth)) {
			throw new IllegalArgumentException("movie width " + movieWidth);
		}
		this.content = content;
		this.movies = movies;
		this.movieWidth = movieWidth;
		this.sets = sets.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(sets));
		this.desktopFolder = desktopFolder;
		this.languages = languages.isEmpty() ? Collections.emptySet()
				: Collections.unmodifiableSet(new TreeSet<>(languages));
		this.oldLanguage = oldLanguage == null ? null : oldLanguage.toLowerCase(java.util.Locale.ROOT);
	}

	public PackOptions withLanguages(final Set<String> codes) {
		final Set<String> lower = new TreeSet<>();
		for (final String code : codes) {
			lower.add(code.toLowerCase(java.util.Locale.ROOT));
		}
		return new PackOptions(this.content, this.movies, this.movieWidth, this.sets, this.desktopFolder, lower,
				this.oldLanguage);
	}

	/** A language pack from the old install in this language ("esES"), or none for null. */
	public PackOptions withOldLanguage(final String code) {
		return new PackOptions(this.content, this.movies, this.movieWidth, this.sets, this.desktopFolder,
				this.languages, code);
	}

	public static PackOptions defaults() {
		return new PackOptions(Content.FULL_CLASSIC, false, EnumSet.noneOf(GraphicsSet.class), false);
	}

	public static boolean isMovieWidth(final int width) {
		for (final int known : MOVIE_WIDTHS) {
			if (known == width) {
				return true;
			}
		}
		return false;
	}

	public Content getContent() {
		return this.content;
	}

	public boolean isMovies() {
		return this.movies;
	}

	public int getMovieWidth() {
		return this.movieWidth;
	}

	public Set<GraphicsSet> getSets() {
		return this.sets;
	}

	public Set<String> getLanguages() {
		return this.languages;
	}

	public String getOldLanguage() {
		return this.oldLanguage;
	}

	public boolean isDesktopFolder() {
		return this.desktopFolder;
	}

	public String describe() {
		final StringBuilder builder = new StringBuilder(
				this.content == Content.FULL_CLASSIC ? "full-classic" : "melee-only");
		if (this.movies) {
			builder.append("+movies").append(this.movieWidth);
		}
		return builder.toString();
	}
}
