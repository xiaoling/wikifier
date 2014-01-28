package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import static org.junit.Assert.*;

import java.net.UnknownHostException;

public class MongoDBTest {


    public void test() throws UnknownHostException{
        MongoDBWikiAccess access = new MongoDBWikiAccess();
        for(int i = 0;i<100;i++)
            assertTrue(access.iterator().hasNext());
        assertTrue(access.getSurfaceFormInfo("Washington").getTotalAppearanceCount()>0);
    }

}
