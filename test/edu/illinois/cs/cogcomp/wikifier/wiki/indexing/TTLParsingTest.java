// $codepro.audit.disable useCharAtRatherThanStartsWith
package edu.illinois.cs.cogcomp.wikifier.wiki.indexing;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.utils.TTLIterator;


public class TTLParsingTest {

	static File f;
	static {
		try {
			f = File.createTempFile("123", "123");
			f.deleteOnExit();
			FileUtils
					.writeStringToFile(
							f,
							"#"+
							"<http://dbpedia.org/resource/Agricultural_science> <http://dbpedia.org/property/employmentField> <http://dbpedia.org/resource/Food_industry> .\n"
							+ "<http://dbpedia.org/resource/Agricultural_science> <http://dbpedia.org/property/employmentField> <http://dbpedia.org/resource/Science> .\n"
							+ "<http://dbpedia.org/resource/Agricultural_science> <http://dbpedia.org/property/employmentField> <http://dbpedia.org/resource/Research_and_development> .\n"
							+ "<http://dbpedia.org/resource/Agricultural_science> <http://dbpedia.org/property/wikiPageUsesTemplate> <http://dbpedia.org/resource/Template:Infobox_occupation> .\n"
							+ "<http://dbpedia.org/resource/A> <http://dbpedia.org/property/wikiPageUsesTemplate> <http://dbpedia.org/resource/Template:Latin_alphabet_navbox> .");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	@Test
	public void test() throws Exception {
		TTLIterator iterator = new TTLIterator(f.getPath());
		for (int i = 0; i < 5; i++) {
			if (iterator.hasNext())
				assertTrue(iterator.next().getArg1().startsWith("A"));
		}
		assertTrue(!iterator.hasNext());
		iterator.close();
	}

}
