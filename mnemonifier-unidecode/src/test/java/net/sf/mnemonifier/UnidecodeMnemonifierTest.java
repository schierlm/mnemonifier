package net.sf.mnemonifier;

import org.junit.*;

@SuppressWarnings("javadoc")
public class UnidecodeMnemonifierTest {

	private Mnemonifier instance;

	@Before
	public void setUp() {
		instance = new UnidecodeMnemonifier();
	}

	@Test
	public void testSimple() {
		testSingle("Hello", "Hello");
		testSingle("Für Elisè", "F[u:]r Elis[e!]");
		testSingle("[x]", "[[]x[]]");
		testSingle("\u20ac\u20B9", "[#20AC{EU}][#20B9]");
		testSingle("\u20ac\ud834\udd1e\u20b9", "[#20AC{EU}][#1D11E][#20B9]");
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
}