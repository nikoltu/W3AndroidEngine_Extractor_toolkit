package com.nikoltu.w3prep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class OldLanguagePackTest {
	private static final String ENGLISH = "war3.w3mod\\_locales\\enus.w3mod\\";

	static final class FakeOld implements OldFiles {
		private final Map<String, byte[]> files = new HashMap<>();

		FakeOld add(final String path, final String content) {
			this.files.put(path.toLowerCase(Locale.ROOT), content.getBytes(StandardCharsets.UTF_8));
			return this;
		}

		@Override
		public boolean has(final String path) {
			return this.files.containsKey(path.toLowerCase(Locale.ROOT));
		}

		@Override
		public long size(final String path) {
			return has(path) ? this.files.get(path.toLowerCase(Locale.ROOT)).length : -1;
		}

		@Override
		public byte[] read(final String path) throws IOException {
			if (!has(path)) {
				throw new IOException("not in the old install");
			}
			return this.files.get(path.toLowerCase(Locale.ROOT));
		}
	}

	private static FakeStorage game() {
		return FakeStorage.classic()
				.add(ENGLISH + "ui\\worldeditgamestrings.txt", "[WorldEditStrings]\r\nWESTRING_A=A\r\nWESTRING_NEW=New\r\n")
				.add(ENGLISH + "units\\humanunitstrings.txt", "[hfoo]\nName=Footman\nUbertip=\"Strong.\"\n")
				.add(ENGLISH + "units\\itemstrings.txt", "[ritd]\nName=Ritual Dagger\n")
				.add(ENGLISH + "sound\\units\\footman\\what1.flac", "en")
				.add(ENGLISH + "sound\\units\\whitemane\\what1.flac", "en")
				.add(ENGLISH + "sound\\dialogue\\humancampaign\\human01\\l01.mp3", "en");
	}

	private static FakeOld spanish() {
		return new FakeOld().add("ui\\worldeditgamestrings.txt", "[WorldEditStrings]\nWESTRING_A=Á\n")
				.add("units\\humanunitstrings.txt", "[HFOO]\nName=Soldado raso\nUbertip=\"Fuerte.\"\n")
				.add("sound\\units\\footman\\what1.wav", "es")
				.add("sound\\dialogue\\humancampaign\\human01\\l01.mp3", "es");
	}

	private static Map<String, PlanEntry> byPath(final PackPlan plan) {
		return plan.getEntries().stream().collect(Collectors.toMap(PlanEntry::getZipPath, entry -> entry));
	}

	@Test
	void textKeysTakeTheOldValueAndNewKeysStay() {
		final String current = "﻿[hfoo]\r\nName=Footman\r\nTip=Train Footman\r\n\r\n[hkni]\r\nName=Knight\r\n";
		final String old = "[HFOO]\nname=Soldado raso\n// note\n[hpea]\nName=Peón\n";
		final StringMerge.Result result = StringMerge.merge("units\\humanunitstrings.txt",
				current.getBytes(StandardCharsets.UTF_8), old.getBytes(StandardCharsets.UTF_8));
		assertEquals("﻿[hfoo]\r\nName=Soldado raso\r\nTip=Train Footman\r\n\r\n[hkni]\r\nName=Knight\r\n",
				new String(result.getText(), StandardCharsets.UTF_8));
		assertEquals(3, result.getKeys());
		assertEquals(1, result.getTranslated());
	}

	@Test
	void valuesSavedAsUtf8TwiceAreRepairedAndOthersKept() {
		assertEquals("ñ", StringMerge.undoDoubleEncoding("Ã±"));
		assertEquals("¿Qué pasó?", StringMerge.undoDoubleEncoding("¿Qué pasó?"));
		assertEquals("Pequeño Árbol", StringMerge.undoDoubleEncoding("Pequeño Árbol"));
		assertEquals("Пехота", StringMerge.undoDoubleEncoding("Пехота"));
		final String current = "[nxyz]\nHotkey=N\n";
		final String old = "[nxyz]\nHotkey=Ã±\n";
		assertEquals("[nxyz]\nHotkey=ñ\n", new String(StringMerge.merge("units\\campaignunitstrings.txt",
				current.getBytes(StandardCharsets.UTF_8), old.getBytes(StandardCharsets.UTF_8)).getText(),
				StandardCharsets.UTF_8));
	}

	@Test
	void fdfStringsTakeTheOldValue() {
		final String current = "StringList {\n    OK      \"OK\",\n    NEW     \"New\",\n    QUIT \"Quit\", // menu\n}\n";
		final String old = "StringList {\n    OK \"Aceptar\",\n    QUIT \"Salir\",\n}\n";
		final StringMerge.Result result = StringMerge.merge("ui\\framedef\\globalstrings.fdf",
				current.getBytes(StandardCharsets.UTF_8), old.getBytes(StandardCharsets.UTF_8));
		assertEquals("StringList {\n    OK      \"Aceptar\",\n    NEW     \"New\",\n    QUIT \"Salir\", // menu\n}\n",
				new String(result.getText(), StandardCharsets.UTF_8));
		assertEquals(2, result.getTranslated());
	}

	@Test
	void packTakesOldSoundsAndMergedTextOfTheClassicLanguageLayer() throws Exception {
		final PackPlan plan = new PackPlanner(game().listEntries(), "3.0.1.24371").planOldLanguage(spanish(), "esES",
				PackOptions.defaults());
		assertEquals("w3-data-lang-eses-classic.zip", plan.getPackageName());
		final Map<String, PlanEntry> files = byPath(plan);
		assertEquals(OldLanguageStorage.OLD + "sound\\units\\footman\\what1.wav",
				files.get("_locales/eses.w3mod/sound/units/footman/what1.wav").getSourcePath());
		assertTrue(files.containsKey("_locales/eses.w3mod/sound/dialogue/humancampaign/human01/l01.mp3"));
		assertTrue(files.get("_locales/eses.w3mod/units/humanunitstrings.txt").getSourcePath()
				.startsWith(OldLanguageStorage.MERGED));
		assertFalse(files.keySet().stream().anyMatch(path -> path.contains("whitemane") || path.contains("itemstrings")));
		assertEquals(List.of("sound\\units\\whitemane\\what1.flac", "units\\itemstrings.txt"), plan.getSkipped());

		final List<PlanEntry> entries = plan.getEntries();
		final String marker = new String(entries.get(entries.size() - 1).getInlineContent(), StandardCharsets.UTF_8);
		assertTrue(marker.contains("\nlocale=esES\nspeech=yes\nsource=old game\n"));
		assertTrue(marker.endsWith("entries=" + entries.size() + "\n"));
	}

	@Test
	void storageWritesTheMergedTextAndTheOldSound() throws Exception {
		final FakeStorage game = game();
		final PackPlan plan = new PackPlanner(game.listEntries(), "3.0.1.24371").planOldLanguage(spanish(), "esES",
				PackOptions.defaults());
		final OldLanguageStorage storage = new OldLanguageStorage(game, spanish());
		final Map<String, PlanEntry> files = byPath(plan);
		assertEquals("[WorldEditStrings]\r\nWESTRING_A=Á\r\nWESTRING_NEW=New\r\n", new String(
				storage.read(files.get("_locales/eses.w3mod/ui/worldeditgamestrings.txt").getSourcePath()),
				StandardCharsets.UTF_8));
		assertEquals("es", new String(
				storage.read(files.get("_locales/eses.w3mod/sound/units/footman/what1.wav").getSourcePath()),
				StandardCharsets.UTF_8));
		assertEquals("unit data", new String(storage.read("war3.w3mod\\units\\unitdata.slk"), StandardCharsets.UTF_8));
	}

	@Test
	void meleeOnlyLeavesOutCampaignSpeech() throws Exception {
		final PackOptions melee = new PackOptions(PackOptions.Content.MELEE_ONLY, false,
				java.util.EnumSet.noneOf(GraphicsSet.class), false);
		final PackPlan plan = new PackPlanner(game().listEntries(), "3.0.1.24371").planOldLanguage(spanish(), "esES",
				melee);
		assertFalse(byPath(plan).keySet().stream().anyMatch(path -> path.contains("humancampaign")));
		assertTrue(byPath(plan).containsKey("_locales/eses.w3mod/sound/units/footman/what1.wav"));
	}

	@Test
	void refusesTheClassicPackagesOwnLanguageAndAGameWithoutText() {
		final PackPlanner planner = new PackPlanner(game().listEntries(), "3.0.1.24371");
		assertEquals(PackException.Kind.OLD_INSTALL, assertThrows(PackException.class,
				() -> planner.planOldLanguage(spanish(), "enUS", PackOptions.defaults())).getKind());
		assertEquals(PackException.Kind.OLD_INSTALL,
				assertThrows(PackException.class, () -> planner.planOldLanguage(
						new FakeOld().add("sound\\units\\footman\\what1.wav", "es"), "esES", PackOptions.defaults()))
						.getKind());
	}

	@Test
	void languageComesFromTheBattleNetSectionOfConfig() {
		final String config = "[Game]\nLANGID=0x0000040a\n\n// Spanish = 'esES'\n[Battle.net]\nLANGID='esES'\n";
		assertEquals("esES", OldInstall.languageOf(config));
		assertEquals("jaJP", OldInstall.languageOf("[Battle.net]\r\nLANGID='jaJA'\r\n"));
		assertNull(OldInstall.languageOf("[Game]\nLANGID=0x00000409\n"));
	}
}
