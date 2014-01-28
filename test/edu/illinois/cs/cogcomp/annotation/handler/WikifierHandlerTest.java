package edu.illinois.cs.cogcomp.annotation.handler;

import edu.illinois.cs.cogcomp.edison.data.curator.CuratorClient;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.TokenizerUtilities.SentenceViewGenerators;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;

public class WikifierHandlerTest {
    
    public static void main(String[] args) throws Exception{
        CuratorClient client = new CuratorClient("trollope.cs.illinois.edu", 9010);
        String text = "On Wednesday, while taping an episode of â€œCenterStageâ€ for the YES Network, Commissioner Bud Selig said this to Michael Kay.";
        TextAnnotation ta = new TextAnnotation("", "", text,SentenceViewGenerators.LBJSentenceViewGenerator);
        client.addPOSView(ta, false);
        client.addChunkView(ta, false);
        client.addNamedEntityView(ta, false);
        
        IllinoisWikifierHandler h = new IllinoisWikifierHandler("configs/STAND_ALONE_NO_INFERENCE.xml");
        h.tagText(ta);
    }

}
