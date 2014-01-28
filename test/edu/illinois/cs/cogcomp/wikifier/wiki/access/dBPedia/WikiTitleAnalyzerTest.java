package edu.illinois.cs.cogcomp.wikifier.wiki.access.dBPedia;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.utils.WikiTitleUtils;


/**
 * 
 * @author cheng88
 *
 */
public class WikiTitleAnalyzerTest {

	@Test
	public void test() {
		assertEquals("South_Africa",WikiTitleUtils.getSecondaryEntity("National_Party_(South_Africa)"));
		assertEquals("UK",WikiTitleUtils.getSecondaryEntity("National_Party_(UK,_1976)"));
		assertEquals("Illinois",WikiTitleUtils.getSecondaryEntity("Chicago,_Illinois"));
		assertEquals("Puerto_Rico",WikiTitleUtils.getSecondaryEntity("New_Progressive_Party_of_Puerto_Rico"));
		assertEquals("Atlas_Shrugged",WikiTitleUtils.getSecondaryEntity("List_of_Atlas_Shrugged_characters"));
	}

}
