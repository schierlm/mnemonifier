package net.sf.mnemonifier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to parse RFC1345 and generate mnemonics.dat file.
 *
 * @author Michael Schierl {@code <schierlm@gmx.de>}
 */
public class MappingGenerator {

	private static final String COMBINING_MNEMONICS = "\u0300! '\u0303? -\u0306( . :\u030b\" <\u030f!!\u0311)\u0313=, ==,\u0326-, ,\u0338/\u0342=?\u0345--,";

	/**
	 * Main method for mapping generator.
	 *
	 * @param args
	 *            Command line arguments (ignored)
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static void main(String[] args) throws IOException {
		// load and parse RFC1345
		BufferedReader br = new BufferedReader(new InputStreamReader(new URL("http://www.ietf.org/rfc/rfc1345.txt").openStream()));
		String line;
		while ((line = br.readLine()) != null) {
			if (line.equals(" SP     0020    SPACE"))
				break;
		}
		String[] mnemonics = new String[0x10000];
		Pattern linePattern = Pattern.compile(" ([!-~][ -~]{5}) ([0-9a-f]{4})    [A-Za-z(/:)0-9 -]*");
		while ((line = br.readLine()) != null) {
			if (line.equals("4.  CHARSETS"))
				break;
			if (line.length() == 0 || line.startsWith("                "))
				continue;
			if (line.startsWith("Simonsen            ")) {
				line = br.readLine();
				line = br.readLine();
				continue;
			}
			if (line.equals("        e000    indicates unfinished (Mnemonic)"))
				continue;
			if (line.equals(" 1000RCD        2180    ROMAN NUMERAL ONE THOUSAND C D")) {
				mnemonics[0x2180] = "1000RCD";
				continue;
			}
			Matcher m = linePattern.matcher(line);
			if (!m.matches())
				throw new IOException(line);
			int codepoint = Integer.parseInt(m.group(2), 16);
			if (codepoint < 128)
				continue;
			mnemonics[codepoint] = m.group(1).trim();
		}
		br.close();

		// correct http://www.rfc-editor.org/errata_search.php?eid=2683
		mnemonics[0x1e4b] = "n->";
		mnemonics[0x1e69] = "s.-.";

		// add trivial mnemonics to make adding decomposition mappings easier
		for (int i = 0; i < 128; i++) {
			mnemonics[i] = "" + (char) i;
		}

		// add decomposition mappings
		String[][] combiningMnemonics = new String[256][];
		Mnemonifier.parseMnemonicsMap(new StringReader(COMBINING_MNEMONICS), combiningMnemonics, new HashMap<String, Character>());
		for (int i = 0; i < mnemonics.length; i++) {
			if (mnemonics[i] != null)
				continue;
			if (combiningMnemonics[i / 256] != null && combiningMnemonics[i / 256][i % 256] != null) {
				mnemonics[i] = "|" + combiningMnemonics[i / 256][i % 256];
				continue;
			}
			String normalForm = Normalizer.normalize(new StringBuilder(2).appendCodePoint(i).toString(), Form.NFD);
			String origForm = Normalizer.normalize(normalForm, Form.NFC);
			if (origForm.length() != 1 || origForm.charAt(0) != i)
				continue;
			if (mnemonics[normalForm.codePointAt(0)] != null) {
				if (normalForm.length() != normalForm.codePointCount(0, normalForm.length()))
					throw new UnsupportedOperationException("mnemonics outside of BMP not supported");
				String combined = mnemonics[normalForm.codePointAt(0)];
				for (int j = 1; j < normalForm.length(); j++) {
					char c = normalForm.charAt(j);
					combined += "|" + combiningMnemonics[c / 256][c % 256].toString();
				}
				mnemonics[i] = combined;
			}
		}

		// remove trivial mnemonics again
		for (int i = 0; i < 128; i++) {
			mnemonics[i] = null;
		}

		// verify codepoints, unique reverse mapping and absense of [, ], #
		// characters
		Set<String> mappedMnemonics = new HashSet<String>();
		for (int i = 0; i < mnemonics.length; i++) {
			if (mnemonics[i] == null)
				continue;
			if (!Character.isDefined(i))
				throw new IllegalStateException(Integer.toHexString(i));
			String mnemonic = mnemonics[i];
			if (mnemonic.contains("[") || mnemonic.contains("]") || mnemonic.contains("#") || !mnemonic.matches("[!-~]+"))
				throw new IllegalStateException(mnemonic);
			if (mappedMnemonics.contains(mnemonic))
				throw new IllegalStateException(mnemonic);
			mappedMnemonics.add(mnemonic);
		}

		// store mnemonics map
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("mnemonics.dat"), "UTF-8"));
		int lastMnemonic = 0;
		for (int i = 0; i < mnemonics.length; i++) {
			if (mnemonics[i] == null)
				continue;
			bw.write(i == lastMnemonic + 1 ? " " : "" + (char) i);
			bw.write(mnemonics[i]);
			lastMnemonic = i;
		}
		bw.close();

		// load and verify mnemonics map
		Reader r = new InputStreamReader(new FileInputStream("mnemonics.dat"), "UTF-8");
		String[][] roundtripMap = new String[256][];
		Mnemonifier.parseMnemonicsMap(r, roundtripMap, new HashMap<String, Character>());
		r.close();
		for (int i = 0; i < mnemonics.length; i++) {
			String roundtripValue = roundtripMap[i / 256] == null ? null : roundtripMap[i / 256][i % 256];
			if ((roundtripValue == null && mnemonics[i] != null) || (roundtripValue != null && !roundtripValue.equals(mnemonics[i])))
				throw new IllegalStateException(i + "\t" + mnemonics[i] + "\t" + roundtripValue);
		}
	}
}
