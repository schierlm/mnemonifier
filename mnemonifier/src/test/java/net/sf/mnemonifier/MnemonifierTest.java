package net.sf.mnemonifier;

import java.io.IOException;
import java.io.InputStream;

import org.junit.*;

@SuppressWarnings("javadoc")
public class MnemonifierTest {

	private Mnemonifier instance;

	@Before
	public void setUp() {
		instance = new Mnemonifier();
	}

	@Test
	public void testSimple() {
		testSingle("Hello", "Hello");
		testSingle("Für Elisè", "F[u:]r Elis[e!]");
		testSingle("[x]", "[[]x[]]");
		testSingle("\u20ac\u20b9", "[#20AC][#20B9]");
		testSingle("\u20ac\ud834\udd1e\u20b9", "[#20AC][#1D11E][#20B9]");
		testSingle("\u0301\u0400", "[|'][E=|!]");
	}

	@Test
	public void testLaxDecoding() {
		Assert.assertEquals("]][[Hello][#q][", instance.unmnemonify("]][[Hello][#q]["));
		Assert.assertEquals("\u20ac[[", instance.unmnemonify("[#20ac][["));
		for (int i = 0; i < 8; i++) {
			Assert.assertEquals("[#123{4}".substring(0, i), instance.unmnemonify("[#123{4}".substring(0, i)));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStrictDecoding1() {
		instance.unmnemonify("]][[Hello][#q][", true);
		Assert.fail();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStrictDecoding2() {
		instance.unmnemonify("[#20aC]", true);
		Assert.fail();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStrictDecoding3() {
		instance.unmnemonify("Lo]vely", true);
		Assert.fail();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStrictDecoding4() {
		instance.unmnemonify("[O:]rks]l", true);
		Assert.fail();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStrictDecoding5() {
		instance.unmnemonify("Hi[", true);
		Assert.fail();
	}

	@Test
	public void testRoundtrip() {
		char[] chars = new char[65536];
		for (int i = 0; i < chars.length; i++) {
			chars[i] = (char) i;
		}
		testRoundtrip(new String(chars));
	}

	@Test
	public void testRoundtripSurrogates() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i <= Character.MAX_CODE_POINT; i++) {
			if (Character.isDefined(i))
				sb.appendCodePoint(i);
		}
		testRoundtrip(sb.toString());
	}

	@Test
	public void testSubclass() {
		MyMnemonifier mm = new MyMnemonifier();
		Assert.assertEquals("[#20AC{:8364:}]", mm.mnemonify("\u20ac"));
		Assert.assertEquals("[#1D11E{:119070:}]", mm.mnemonify("\ud834\udd1e"));
		Assert.assertEquals("\ud834\udd1e\u20ac", mm.unmnemonify("[#1D11E{xx}][#20AC]"));
		Assert.assertEquals("\ud834\udd1e\u20ac", mm.unmnemonify("[#1D11E{::}][#20AC{:8364:}]", true));
	}

	@Test(expected = IllegalStateException.class)
	public void testLoadFailure() {
		Mnemonifier.loadMaps(new InputStream() {
			public int read() throws IOException {
				throw new IOException("Read fails always!");
			}
		});
	}

	private void testSingle(String input, String encoded) {
		Mnemonifier m = instance;
		Assert.assertEquals(encoded, m.mnemonify(input));
		Assert.assertEquals(input, m.unmnemonify(encoded));
		Assert.assertEquals(input, m.unmnemonify(encoded, true));
	}

	private void testRoundtrip(String input) {
		Mnemonifier m = instance;
		String encoded = m.mnemonify(input);
		Assert.assertTrue(encoded.matches("[\0-\u007f]+"));
		Assert.assertEquals(input, m.unmnemonify(encoded));
		Assert.assertEquals(input, m.unmnemonify(encoded, true));
	}

	private static class MyMnemonifier extends Mnemonifier {
		@Override
		protected String getCodepointInfo(int codepoint) {
			return ":" + codepoint + ":";
		}
	}

}