package edu.illinois.cs.cogcomp.wikifier.models;

import static org.junit.Assert.*;
import javatools.parsers.NounGroup;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.utils.WikiTitleUtils;

public class NPParsingTest {

    @Test
    public void test() {
        assertEquals(new NounGroup("Florida Supreme Court").head(),"Court");
        assertEquals("Florida_Supreme",new NounGroup("Florida Supreme Court").preModifier());
        assertEquals(new NounGroup("Supreme Court in Florida").head(),"Court");
        assertEquals(new NounGroup("Prime Minister of the United Kingdom").head(),"Minister");
        assertEquals(new NounGroup("Prime Minister Gordon Brown").head(),"Brown");
        assertEquals(new NounGroup("Iranian Ministry of Defense").head(),"Ministry");
        assertEquals(new NounGroup("Milosevic's Socialist Party").head(),"Party");
        NounGroup ng = new NounGroup("Michigan Department of Natural Resources");
        assertEquals(ng.head(),"Department");
        assertEquals(ng.preModifier(),"Michigan");
        assertEquals(ng.postModifier().original(),"Natural Resources");
        assertEquals(ng.preposition(),"of");
        assertEquals(new NounGroup("J. Mack Robinson College of Business").head(),"College");
        assertEquals(WikiTitleUtils.getHead("J._Mack_Robinson_College_of_Business"),"College");
        assertEquals(new NounGroup("Robinson College").head(),"College");
        assertEquals(new NounGroup("Central Asia").head(),"Asia");
        assertEquals(new NounGroup("Florida Green Party").head(),"Party");
        assertEquals(new NounGroup("Chicago, Illinois").head(),"Illinois");
        assertEquals(WikiTitleUtils.getHead("Chicago, Illinois"),"Chicago");
        assertEquals(new NounGroup("Amgen Inc.").head(),"Inc.");
        assertEquals("James", StringUtils.substringBefore("James", "("));
        assertEquals("", StringUtils.substringAfter("James", "("));
        assertEquals("", StringUtils.substringAfterLast("James", "("));
        assertEquals("Chicago", WikiTitleUtils.getCanonicalTitle("Chicago, Illinois"));
        assertEquals("Chicago", WikiTitleUtils.getCanonicalTitle("Chicago "));
        assertEquals("Chicago", WikiTitleUtils.getCanonicalTitle("Chicago_(Font)"));
        assertEquals("Secretary", new NounGroup("New Hampshire Secretary of State").head());
        assertEquals("peoples", new NounGroup("Indigenous peoples of Siberia").head());
        
//        GlobalParameters.NESimMetric = new EntityComparison();
//        assertTrue(GlobalParameters.NESimMetric.NESimilarity("ORG#Supreme Court of Florida", "ORG#The Supreme Court in Florida")>0.7);
////        System.out.println(GlobalParameters.NESimMetric.("Supreme Court of Florida", "The Supreme Court in Florida"));
//        System.out.println(GlobalParameters.NESimMetric.NESimilarity("Supreme Court of Florida", "The Supreme Court in Florida"));
        
//        EntityComparison ec = new EntityComparison();
//        System.out.println(ec.NESimilarity("Bill Gates", "Williams H. Gates"));
//        System.out.println(ec.NESimilarity("Robinson College", "Robinson College of London"));
    }

}
