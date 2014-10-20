package net.sf.mnemonifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Mnemonifier provides methods for converting Unicode strings (containing any
 * Unicode characters including those not in the Basic Multilingual Plane) to
 * ASCII-only strings that can be converted back to the original strings while
 * still trying to be human-readable.
 *
 * <p>
 * This is achieved by using RFC1345 mnemonics ({@code à} becomes {@code [a!]})
 * and decomposition mappings ({@code Ǹ} becomes {@code [N|!]}). Anything not
 * included in either of these two lists is represented as hex code ({@code €}
 * becomes {@code [#20AC]}).
 *
 * <p>
 * To enable rounddtrip conversion, any square brackets inside the original
 * string are replaced by {@code [[]} and {@code []]}, respectively.
 *
 * <p>
 * The decoder is available in two versions: The strict version will throw an
 * exception if any square bracket is not encoded correctly, the lax version
 * (for cases where the user is able to edit/type encoded strings) will pass
 * these unchanged.
 *
 * <p>
 * Subclass implementations can override {{@link #getCodepointInfo(int)} to
 * provide additional information about an unencodable character, which is added
 * in curly braces (for example <code>[#20AC{EUR}]</code>).
 * 
 * @author Michael Schierl {@code <schierlm@gmx.de>}
 */
public class Mnemonifier {

	private static String[][] forwardMap = null;
	private static Map<String, Character> reverseMap = null;

	/**
	 * Class constructor.
	 */
	public Mnemonifier() {
		synchronized (Mnemonifier.class) {
			if (forwardMap == null) {
				loadMaps(Mnemonifier.class.getResourceAsStream("mnemonics.dat"));
			}
		}
	}

	/**
	 * Load mnemonics maps.
	 * @param inputStream Input stream to read from
	 */
	static void loadMaps(InputStream inputStream) {
		try {
			forwardMap = new String[256][];
			reverseMap = new HashMap<String, Character>();
			Reader r = new InputStreamReader(inputStream, "UTF-8");
			parseMnemonicsMap(r, forwardMap, reverseMap);
			r.close();
		} catch (IOException ex) {
			forwardMap = null;
			reverseMap = null;
			throw new IllegalStateException("Unable to load mnemonics map", ex);
		}
	}

	/**
	 * Convert any Unicode string into mnmenonics.
	 *
	 * @param input
	 *            original string
	 * @return mnemonified string
	 */
	public String mnemonify(String input) {
		int mode = 0;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (Character.isSurrogate(c)) {
				mode = 2;
				break;
			} else if (c == '[' || c == ']' || c > 127) {
				mode = 1;
			}
		}
		if (mode == 0)
			return input;
		if (mode == 1)
			return mnemonifyBMP(input);
		return mnemonifySurrogates(input);
	}

	private String mnemonifySurrogates(String input) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < input.length();) {
			final int codepoint = input.codePointAt(i);
			if (codepoint > Character.MAX_VALUE || !appendMnemonifiedChar(sb, (char) codepoint)) {
				sb.append("[#" + Integer.toHexString(codepoint).toUpperCase());
				String info = getCodepointInfo(codepoint);
				if (info != null)
					sb.append("{" + info + "}");
				sb.append("]");
			}
			i += Character.charCount(codepoint);
		}
		return sb.toString();
	}

	private String mnemonifyBMP(String input) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			final char c = input.charAt(i);
			if (!appendMnemonifiedChar(sb, c)) {
				sb.append("[#" + Integer.toHexString(c).toUpperCase());
				String info = getCodepointInfo(c);
				if (info != null)
					sb.append("{" + info + "}");
				sb.append("]");
			}
		}
		return sb.toString();
	}

	private boolean appendMnemonifiedChar(StringBuilder sb, char c) {
		if (c == '[') {
			sb.append("[[]");
		} else if (c == ']') {
			sb.append("[]]");
		} else if (c < 128) {
			sb.append(c);
		} else if (forwardMap[c / 256] != null && forwardMap[c / 256][c % 256] != null) {
			sb.append("[" + forwardMap[c / 256][c % 256] + "]");
		} else {
			return false;
		}
		return true;
	}

	/**
	 * Convert mnemonified string back to original. This override uses lax
	 * decoding rules.
	 *
	 * @param input
	 *            mnemonified string
	 * @return original string
	 */
	public String unmnemonify(String input) {
		return unmnemonify(input, false);
	}

	/**
	 * Convert mnemonified string back to original.
	 *
	 * @param input
	 *            mnemonified string
	 * @param strict
	 *            whether to use strict decoding rules
	 * @return original string
	 * @throws IllegalArgumentException
	 *             if strict decoding rules are used and the input is invalid
	 */
	public String unmnemonify(String input, boolean strict) {
		if (strict && input.contains("]") && !input.contains("[")) {
			throw new IllegalArgumentException(input);
		}
		if (!input.contains("["))
			return input;
		StringBuilder sb = new StringBuilder();
		int parsedOffset = 0;
		int offset = input.indexOf('[');
		while (offset != -1) {
			String literalPart = input.substring(parsedOffset, offset);
			if (strict && literalPart.contains("]"))
				throw new IllegalArgumentException(input);
			sb.append(literalPart);
			parsedOffset = offset;
			boolean parsed = false;
			if (offset + 2 < input.length() && input.charAt(offset + 1) == '#') {
				int hexEnd = offset + 2;
				char c = input.charAt(hexEnd);
				while ("0123456789ABCDEFabcdef".indexOf(c) != -1) {
					hexEnd++;
					if (hexEnd >= input.length())
						break;
					c = input.charAt(hexEnd);
				}
				int tagEnd = -1;
				if (c == ']') {
					tagEnd = hexEnd + 1;
				} else if (c == '{') {
					int pos = input.indexOf('}', hexEnd + 1);
					if (input.startsWith("}]", pos))
						tagEnd = pos + 2;
				}
				if (hexEnd > offset + 2 && tagEnd != -1) {
					parsed = true;
					String codepointHex = input.substring(offset + 2, hexEnd);
					int codepoint = Integer.parseInt(codepointHex, 16);
					if (strict && !codepointHex.equals(Integer.toHexString(codepoint).toUpperCase())) {
						throw new IllegalArgumentException(input);
					}
					sb.appendCodePoint(codepoint);
					parsedOffset = tagEnd;
				}
			} else if (offset + 1 < input.length() && "[]".indexOf(input.charAt(offset + 1)) != -1) {
				if (offset + 2 < input.length() && input.charAt(offset + 2) == ']') {
					parsed = true;
					sb.append(input.charAt(offset + 1));
					parsedOffset = offset + 3;
				}
			} else {
				int endOffset = input.indexOf(']', offset + 1);
				if (endOffset != -1) {
					Character decoded = reverseMap.get(input.substring(offset + 1, endOffset));
					if (decoded != null) {
						parsed = true;
						sb.append((char) decoded);
						parsedOffset = endOffset + 1;
					}
				}
			}
			if (!parsed) {
				if (strict)
					throw new IllegalArgumentException(input);
				sb.append("[");
				parsedOffset++;
			}
			offset = input.indexOf('[', parsedOffset);
		}
		String literalTail = input.substring(parsedOffset);
		if (strict && literalTail.contains("]"))
			throw new IllegalArgumentException(input);
		return sb.append(literalTail).toString();
	}

	/**
	 * Overridden by subclasses to provide information about the given
	 * codepoint. This implementation returns always {@code null}.
	 *
	 * @param codepoint
	 *            Codepoint to provide information about
	 * @return Codepoint information (not containing any curly braces), or
	 *         {@code null} if no information available
	 */
	protected String getCodepointInfo(int codepoint) {
		return null;
	}

	static void parseMnemonicsMap(Reader r, String[][] forwardMap, Map<String, Character> reverseMap) throws IOException {
		BufferedReader br = new BufferedReader(r);
		char current = 0;
		int ch = br.read();
		while (ch != -1) {
			if (ch == ' ')
				current++;
			else
				current = (char) ch;
			ch = br.read();
			StringBuilder sb = new StringBuilder();
			while (ch > 32 && ch < 128) {
				sb.append((char) ch);
				ch = br.read();
			}
			String mnemonic = sb.toString();
			if (forwardMap[current / 256] == null)
				forwardMap[current / 256] = new String[256];
			forwardMap[current / 256][current % 256] = mnemonic;
			reverseMap.put(mnemonic, current);
		}
	}
}
