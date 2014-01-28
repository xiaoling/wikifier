package edu.illinois.cs.cogcomp.wikifier.models;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.List;

import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.POS;

import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.LevWordNetManager;
import edu.illinois.cs.cogcomp.wikifier.utils.WordFeatures;



public class WordNetTest {

    @Test
    public void test() throws JWNLException, FileNotFoundException {
        assertEquals("Armenia",WordFeatures.nounForm("Armenian"));
        assertEquals("Greece",WordFeatures.nounForm("Greek"));
        GlobalParameters.wordnet = LevWordNetManager.getInstance("../Config/jwnl_properties.xml");
        String[] sq = new String[]{
                "head","director"
        };
        for(String query:sq){
            List<String> ss = GlobalParameters.wordnet.getDefaultHypenymsToTheRoot(query, POS.NOUN);
            System.out.println(ss);
        }
    }

}
