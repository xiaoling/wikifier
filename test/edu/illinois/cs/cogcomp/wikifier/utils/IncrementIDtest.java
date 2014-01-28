package edu.illinois.cs.cogcomp.wikifier.utils;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.IncrementID;


public class IncrementIDtest {

    @Test
    public void test() {
        IncrementID<String> ids = new IncrementID<String>();
        ids.id("A");
        ids.id("A");
        ids.id("B");
        ids.id("C");
        assertEquals(0,ids.id("A"));
        assertEquals(2,ids.id("C"));
        assertEquals("B",ids.get(1));
        ids.reset();
        ids.id("C");
        assertEquals("C",ids.get(0));
    }

}
