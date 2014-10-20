package net.sf.mnemonifier;

import me.xuender.unidecode.Unidecode;

/**
 * Mnemonifier implementation that uses <a
 * href="https://github.com/xuender/unidecode">Unidecode</a> to provide
 * codepoint info for codepoints that are not covered by RFC1345 or by
 * decomposition mapping.
 *
 * @author Michael Schierl {@code <schierlm@gmx.de>}
 */
public class UnidecodeMnemonifier extends Mnemonifier {
	protected String getCodepointInfo(int codepoint) {
		if (codepoint > 0xFFFF)
			return null;
		String info = Unidecode.decode("" + (char) codepoint);
		if (info.contains("[?]") || info.contains("{") || info.contains("}"))
			info = null;
		return info;
	}
}
