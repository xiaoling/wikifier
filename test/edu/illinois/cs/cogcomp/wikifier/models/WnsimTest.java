package edu.illinois.cs.cogcomp.wikifier.models;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.inference.entailment.PathFinder;

public class WnsimTest {

    @Test
    public void test() {

    }
    
    static void testWinSim(PathFinder finder){
        assertTrue(finder.wnsim("hunter", "singer")==0);
        assertTrue(finder.wnsim("administrator", "head")==0.3);
        assertTrue(finder.wnsim("farmer", "singer")==0.0);
        System.out.println(finder.wnsim("dog", "cat"));
        System.out.println(finder.wnsim("writer", "president"));
        System.out.println(finder.wnsim("manager", "democrat"));
        System.out.println(finder.wnsim("manager", "doctor"));
        System.out.println(finder.wnsim("president", "farmer"));
        System.out.println(finder.wnsim("person", "state"));
        System.out.println(finder.wnsim("person", "head"));
        System.out.println(finder.wnsim("person", "widow"));
        assertEquals(0.027,finder.wnsim("dog", "cat"),0.0001);
        assertEquals(0.09,finder.wnsim("head", "person"),0.0001);
        assertEquals(0.027,finder.wnsim("widow", "person"),0.0001);
        assertEquals(0.0,finder.wnsim("coordinator", "person"),0.0001);
        assertEquals(0.00,finder.wnsim("director", "person"),0.0001);
        assertEquals(0.3,finder.wnsim("peoples", "person"),0.0001);
        assertEquals(0.3,finder.wnsim("personality", "person"),0.0001);
        assertEquals(0.09,finder.wnsim("hunter", "person"),0.0001);
    }

}
