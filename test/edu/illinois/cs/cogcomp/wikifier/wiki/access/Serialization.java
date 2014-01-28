package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.BasicTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess.WikiData;

public class Serialization {

    @Test
    public void test() throws IOException {
//        WikiData sample = new WikiData(
//                BasicTitleDataInfoProto.getDefaultInstance().toByteArray(), 
//                LexicalTitleDataInfoProto.getDefaultInstance().toByteArray(), 
//                SemanticTitleDataInfoProto.getDefaultInstance().toByteArray());
//        byte[] serialization = sample.serialize();
//        
//        WikiData readBack = new WikiData(serialization);
//        assertEquals(BasicTitleDataInfoProto.getDefaultInstance(),readBack.getBasicProto());
//        assertEquals(LexicalTitleDataInfoProto.getDefaultInstance(),readBack.getLexProto());
//        assertEquals(SemanticTitleDataInfoProto.getDefaultInstance(),readBack.getSemanticProto());
    }

}
