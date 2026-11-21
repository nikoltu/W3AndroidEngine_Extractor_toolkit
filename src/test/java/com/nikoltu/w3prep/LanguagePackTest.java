package com.nikoltu.w3prep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class LanguagePackTest {
	private static final String SPANISH = "war3.w3mod\\_locales\\eses.w3mod\\";

	private static Map<String, PlanEntry> byPath(final PackPlan plan) {
		return plan.getEntries().stream().collect(Collectors.toMap(PlanEntry::getZipPath, entry -> entry));
	}

	private static FakeStorage withSpanish() {
		return FakeStorage.classic().add("war3.w3mod\\_locales\\enus.w3mod\\ui\\worldeditgamestrings.txt", "en")
				.add(SPANISH + "ui\\worldeditgamestrings.txt", "es").add(SPANISH + "ui\\framedef\\globalstrings.fdf", "es")
				.add(SPANISH + "sound\\dialogue\\humancampaign\\human01\\l01.flac", "es")
				.add(SPANISH + "sound\\units\\footman\\what1.flac", "es")
				.add(SPANISH + "cinematics\\intro.pkb", "es").addRemote(SPANISH + "sound\\units\\knight\\what1.flac")
				.add("war3.w3mod\\_locales\\frfr.w3mod\\ui\\other.txt", "fr")
				.addRemote("war3.w3mod\\_locales\\dede.w3mod\\ui\\worldeditgamestrings.txt");
	}

	@Test
	void downloadedLanguagesLeaveOutThePackageLanguageAndThoseWithoutText() {
		final PackPlanner planner = new PackPlanner(withSpanish().listEntries());
		assertEquals("enus", planner.getLocale());
		assertEquals(List.of("eses"), planner.downloadedLanguages());
	}

	@Test
	void packHoldsTheLanguageFolderAndItsMarkerLast() throws Exception {
		final PackPlan plan = new PackPlanner(withSpanish().listEntries(), "3.0.1.24371").planLanguage("esES",
				PackOptions.defaults());
		assertEquals("w3-data-lang-eses.zip", plan.getPackageName());
		final Map<String, PlanEntry> files = byPath(plan);
		assertTrue(files.containsKey("_locales/eses.w3mod/ui/worldeditgamestrings.txt"));
		assertTrue(files.containsKey("_locales/eses.w3mod/sound/units/footman/what1.flac"));
		assertTrue(files.containsKey("_locales/eses.w3mod/sound/dialogue/humancampaign/human01/l01.flac"));
		assertFalse(files.containsKey("_locales/eses.w3mod/cinematics/intro.pkb"));
		assertFalse(files.keySet().stream().anyMatch(path -> path.contains("frfr") || path.contains("enus")));
		assertEquals(List.of("sound\\units\\knight\\what1.flac"), plan.getSkipped());

		final List<PlanEntry> entries = plan.getEntries();
		final PlanEntry marker = entries.get(entries.size() - 1);
		assertEquals(PackPlanner.LANGUAGE_MARKER, marker.getZipPath());
		final String text = new String(marker.getInlineContent(), StandardCharsets.UTF_8);
		assertTrue(text.startsWith("tool=Reforged Extractor toolkit "));
		assertTrue(text.contains("\ngameVersion=3.0.1.24371\n"));
		assertTrue(text.contains("\nlocale=esES\n"));
		assertTrue(text.contains("\nspeech=yes\n"));
		assertTrue(text.contains("\nentries=" + entries.size() + "\n"));
	}

	@Test
	void meleeOnlyLeavesOutCampaignSpeech() throws Exception {
		final PackOptions melee = new PackOptions(PackOptions.Content.MELEE_ONLY, false,
				EnumSet.noneOf(GraphicsSet.class), false);
		final Map<String, PlanEntry> files = byPath(
				new PackPlanner(withSpanish().listEntries()).planLanguage("eses", melee));
		assertFalse(files.containsKey("_locales/eses.w3mod/sound/dialogue/humancampaign/human01/l01.flac"));
		assertTrue(files.containsKey("_locales/eses.w3mod/sound/units/footman/what1.flac"));
	}

	@Test
	void textWithoutSpeechIsAPackToo() throws Exception {
		final FakeStorage storage = FakeStorage.classic().add(SPANISH + "ui\\worldeditgamestrings.txt", "es")
				.addRemote(SPANISH + "sound\\units\\footman\\what1.flac");
		final PackPlan plan = new PackPlanner(storage.listEntries()).planLanguage("eses", PackOptions.defaults());
		final PlanEntry marker = plan.getEntries().get(plan.getEntries().size() - 1);
		assertTrue(new String(marker.getInlineContent(), StandardCharsets.UTF_8).contains("\nspeech=no\n"));
	}

	@Test
	void languageWithoutDownloadedTextIsRefused() {
		final PackPlanner planner = new PackPlanner(withSpanish().listEntries());
		final PackException error = assertThrows(PackException.class,
				() -> planner.planLanguage("dede", PackOptions.defaults()));
		assertEquals(PackException.Kind.LANGUAGE_NOT_DOWNLOADED, error.getKind());
		assertThrows(PackException.class, () -> planner.planLanguage("frfr", PackOptions.defaults()));
	}

	@Test
	void eastAsianPackCarriesTheFontsTheClassicPackageLeavesOut() throws Exception {
		final FakeStorage storage = FakeStorage.classic().add("war3.w3mod\\fonts\\ulei00m.ttf", "font")
				.add("war3.w3mod\\fonts\\frizqt__.ttf", "latin")
				.add("war3.w3mod\\_locales\\enus.w3mod\\ui\\worldeditgamestrings.txt", "en")
				.add("war3.w3mod\\_locales\\zhcn.w3mod\\ui\\worldeditgamestrings.txt", "zh");
		final PackPlanner planner = new PackPlanner(storage.listEntries());
		assertFalse(byPath(planner.planClassic(PackOptions.defaults())).containsKey("fonts/ulei00m.ttf"));
		final Map<String, PlanEntry> files = byPath(planner.planLanguage("zhCN", PackOptions.defaults()));
		assertEquals("war3.w3mod\\fonts\\ulei00m.ttf", files.get("_locales/zhcn.w3mod/fonts/ulei00m.ttf").getSourcePath());
		assertFalse(files.containsKey("_locales/zhcn.w3mod/fonts/frizqt__.ttf"));
	}

	@Test
	void optionsKeepLanguagesInLowerCase() {
		final PackOptions options = PackOptions.defaults().withLanguages(Set.of("esES", "ZHCN"));
		assertEquals(Set.of("eses", "zhcn"), options.getLanguages());
		assertEquals(Collections.emptySet(), PackOptions.defaults().getLanguages());
	}
}
