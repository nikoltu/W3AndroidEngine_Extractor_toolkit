package com.nikoltu.w3prep.mpq;

import java.util.logging.Level;
import java.util.logging.Logger;

/** The archive reader's notes, kept out of the console; a file that cannot be read fails on its own. */
public final class MpqLog {
	private static final Logger LOG = Logger.getLogger("com.nikoltu.w3prep.mpq");

	private MpqLog() {
	}

	public static void warn(final String message) {
		LOG.log(Level.FINE, message);
	}

	public static void error(final String message) {
		LOG.log(Level.FINE, message);
	}

	public static void errorf(final String format, final Object... args) {
		LOG.log(Level.FINE, String.format(format, args));
	}
}
