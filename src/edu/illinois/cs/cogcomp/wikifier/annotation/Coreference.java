package edu.illinois.cs.cogcomp.wikifier.annotation;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.illinois.cs.cogcomp.edison.annotators.GazetteerViewGenerator;
import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.CoreferenceView;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.View;
import edu.illinois.cs.cogcomp.edison.sentences.ViewNames;
import edu.illinois.cs.cogcomp.lbj.coref.decoders.BIODecoder;
import edu.illinois.cs.cogcomp.lbj.coref.decoders.BestLinkDecoder;
import edu.illinois.cs.cogcomp.lbj.coref.decoders.ExtendHeadsDecoder;
import edu.illinois.cs.cogcomp.lbj.coref.decoders.ILPDecoderBestLink;
import edu.illinois.cs.cogcomp.lbj.coref.decoders.MentionDecoder;
import edu.illinois.cs.cogcomp.lbj.coref.decoders.ScoredCorefDecoder;
import edu.illinois.cs.cogcomp.lbj.coref.io.loaders.DocFromTextLoader;
import edu.illinois.cs.cogcomp.lbj.coref.io.loaders.DocLoader;
import edu.illinois.cs.cogcomp.lbj.coref.ir.Mention;
import edu.illinois.cs.cogcomp.lbj.coref.ir.docs.DocPlainText;
import edu.illinois.cs.cogcomp.lbj.coref.ir.solutions.ChainSolution;
import edu.illinois.cs.cogcomp.lbj.coref.learned.MDExtendHeads;
import edu.illinois.cs.cogcomp.lbj.coref.learned.MTypePredictor;
import edu.illinois.cs.cogcomp.lbj.coref.learned.MentionDetectorMyBIOHead;
import edu.illinois.cs.cogcomp.lbj.coref.learned.aceCorefSPLearner;
import edu.illinois.cs.cogcomp.lbj.coref.util.aux.Constants;

public class Coreference {

    private static final double COREF_THRESHOLD = 0.0;
    static Logger logger = LoggerFactory.getLogger(Coreference.class);
    public static boolean useGurobiDecoder = false;
    private aceCorefSPLearner corefClassifier;
    private DocLoader loader;
    private ScoredCorefDecoder decoder;
    private boolean isInitialized = false;
    private static Coreference instance = new Coreference();
    static{
        org.apache.log4j.Logger.getLogger(GazetteerViewGenerator.class.getName()).setLevel(org.apache.log4j.Level.OFF);
    }

    private Coreference() {
        System.out.println("Creating coreferencer by thread "+Thread.currentThread().getId());
        corefClassifier = new aceCorefSPLearner();
        corefClassifier.setThreshold(COREF_THRESHOLD);

        MentionDecoder mdDec = new ExtendHeadsDecoder(new MDExtendHeads(), new BIODecoder(new MentionDetectorMyBIOHead()));
        MTypePredictor mTyper = new MTypePredictor();

        loader = new DocFromTextLoader(mdDec,mTyper);
        decoder = useGurobiDecoder ? 
                new ILPDecoderBestLink(corefClassifier) : 
                new BestLinkDecoder(corefClassifier);
    }
    
    public static View getMentionView(TextAnnotation ta){
        if(ta.hasView(ViewNames.COREF) && isMentionView(ta.getView(ViewNames.COREF)))
            return ta.getView(ViewNames.COREF);
        
        View annotation = getMentionView(ta.getText());
        synchronized(ta){
            ta.addView(Constants.PRED_MENTION_VIEW,annotation);
        }
        return annotation;
    }
    
    public static View getMentionView(String text){

        try{
            System.out.println("Annotating mention view..");
            instance.initialize();
            DocPlainText doc = (DocPlainText) instance.loader.loadDoc(text);
            View ret = doc.getTextAnnotation().getView(Constants.PRED_MENTION_VIEW);
            for(Mention m:doc.getPredMentions()){
                Constituent c = doc.getConstituent(m);
                c.addAttribute(Constants.MentionType, m.getType());
            }
            return ret;
        }catch(NullPointerException ne){
            ne.printStackTrace();
        }
        return new View(Constants.PRED_MENTION_VIEW, "", FakeCurator.createAnnotation(text), 1.0);
    }

    private CoreferenceView annotate(String text) {
        DocPlainText doc = (DocPlainText) loader.loadDoc(text);
        
        ChainSolution<Mention> solution = decoder.decode(doc);
        doc.setPredEntities(solution);
        TextAnnotation ta = doc.getTextAnnotation();
        CoreferenceView ret = (CoreferenceView) ta.getView(Constants.PRED_COREF_VIEW);
        for(Mention m:doc.getPredMentions()){
            Constituent c = doc.getConstituent(m);
            c.addAttribute(Constants.MentionType, m.getType());
        }
        return ret;
    }
    
    /**
     * Returns a thread object that is annotating coref
     * @param ta
     * @param text
     * @return
     */
    public static Thread getStartedCorefThread(final TextAnnotation ta){
        Thread newAnnotation = getCorefThread(ta);
        newAnnotation.start();
        return newAnnotation;
    }
    
    public static View annotateTA(TextAnnotation ta){
        CoreferenceView annotation = getCorefView(ta.getText());
        synchronized(ta){
            ta.addView(ViewNames.COREF,annotation);
        }
        return annotation;
    }
    
    public static Thread getCorefThread(final TextAnnotation ta){
        Thread newAnnotation = new Thread(){
            public void run(){
                try{
                    logger.info("Started coreference...");
                    long start = System.currentTimeMillis();
                    annotateTA(ta);
                    logger.info("Coreference took "+ (System.currentTimeMillis()-start) + "ms");
                }catch(Exception e){
                    System.err.println("Failed coref on text "+ta.getCorpusId());
                    e.printStackTrace();
                }
                // Prevent concurrent adding views
            }
        };
        return newAnnotation;
    }
    
    public static boolean isMentionView(View corefView){
        if(corefView==null)
            return false;
        for (Constituent c : corefView) {
            if (c.hasAttribute(Constants.MentionType)) {
                return true;
            }
        }
        return false;
    }
    
    
    private void initialize(){
        if(!isInitialized){
            synchronized(this){
                if(!isInitialized){
                    instance.annotate("I love the ice-cream in New York, where the WTC was located.");
                    isInitialized = true;
                }
            }
        }
    }
    /**
     * Annotates a given text, blocking
     * @param text
     * @return
     */
    public static CoreferenceView getCorefView(String text){
        instance.initialize();
        return instance.annotate(text);
    }

}
