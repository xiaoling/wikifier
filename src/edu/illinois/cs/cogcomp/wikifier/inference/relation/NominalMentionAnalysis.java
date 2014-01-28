package edu.illinois.cs.cogcomp.wikifier.inference.relation;

import static edu.illinois.cs.cogcomp.edison.sentences.Queries.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.POS;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.primitives.Ints;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.Queries;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.View;
import edu.illinois.cs.cogcomp.edison.sentences.ViewNames;
import edu.illinois.cs.cogcomp.lbj.coref.util.aux.Constants;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.CoherenceRelation;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.Mention.SurfaceType;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.TFIDF;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.TFIDF.TF_IDF_Doc;
import edu.illinois.cs.cogcomp.wikifier.utils.WikiTitleUtils;

/**
 * Extracts the nominal relations between coref entity
 * and the nominal mention NP phrase
 * @author cheng88
 *
 */
public class NominalMentionAnalysis {
    
    static final Logger logger = Logger.getLogger(NominalMentionAnalysis.class);
        
    /**
     *  This component should not be instantiated
     */
    private NominalMentionAnalysis(){}
    
    /**
     * Analyzes the nominal mentions and their corresponding coreferent entities
     * @param problem
     * @param clusters
     */
    public static void analyze(LinkingProblem problem){
        
        View mentionView = problem.getMentionView();

        for(Constituent c:problem.ta.getView(ViewNames.NER)){

            Mention e = problem.getComponent(c);
            if (e != null && e.isNamedEntity()) {
                double simSum = 0.0;
                boolean changed = false;
                for (Constituent nom : adjacentNom(mentionView, c)) {
                    changed = true;
                    double simScore = analyzeTransitiveNomRelation(e, nom, problem);
                    simSum += simScore;
                }
                // When the sim score is effectively zero
                if (changed && simSum < 0.00001) {
                    logger.debug("Force linking " + e + " to null");
                    e.forceLinkToNull();
                }
            }
        }
        
        for (Constituent nom : getNoms(mentionView)) {
            analyzeNomMention(nom, problem);
        }
        
    }
    
    private static List<Constituent> getNoms(Iterable<Constituent> mentionView){
        List<Constituent> corefChain = new ArrayList<Constituent>();
        for(Constituent c:mentionView){
            if(c.size()>2 && "NOM".equals(c.getAttribute(Constants.MentionType)))
                corefChain.add(c);
        }
        return corefChain;
    }
    
    private static Pattern lowercaseWord = Pattern.compile("\\b[a-z]");

    /**
     * Lightweight coref heuristics
     * @param mentionView
     * @param c
     * @return
     */
    private static List<Constituent> adjacentNom(View mentionView, Constituent c) {

        TextAnnotation ta = c.getTextAnnotation();
        
        List<Constituent> corefChain = new ArrayList<Constituent>();
        
        Set<String> duplicates = new HashSet<String>();
        for(Constituent nom:mentionView.where(hasOverlap(getExtendedSpan(c)).and(hasNoOverlap(c)))){
            // Mismatch sentence ids between annotations
            if(ta.getSentenceId(nom.getStartSpan())==c.getSentenceId() 
                    && ta.getSentenceId(nom.getEndSpan()-1)==c.getSentenceId()
                    && isNom(nom)
                    && validApposition(c,nom,ta)){

                // At least one lower case word
                if( !duplicates.contains(nom.getSurfaceString())){
                    duplicates.add(nom.getSurfaceString());
                    corefChain.add(nom);
                }
            }
        }
        return corefChain;
    }
    
    private static Constituent getExtendedSpan(Constituent c){
        int safeMin = Math.max(0, c.getStartSpan()-2);
        int safeMax = Math.min(c.getTextAnnotation().getTokens().length, c.getEndSpan()+2);
        return new Constituent("", "", c.getTextAnnotation(), safeMin,safeMax);
    }
    
    private static boolean validApposition(Constituent c,Constituent nom,TextAnnotation ta){
        int start = Math.min(nom.getEndSpan(), c.getEndSpan());
        int end = Math.max(nom.getStartSpan(), c.getStartSpan());
        if(start==end)
            return true;
        String[] tokensBetween = ta.getTokensInSpan(start, end);
        boolean commaBetween = tokensBetween.length==1 && ",".equals(tokensBetween[0]);
        return commaBetween;
    }

    /**
     * Extracts relations within a nominal mention chunk
     * @param nom
     * @param problem
     */
    private static void analyzeNomMention(Constituent nom,LinkingProblem problem){
        //  TODO: checks relations inside nominal mention chunks
//        if(nom.getSurfaceString().contains("mayor"))
//            System.out.println(nom);
    }
    
    static boolean printToken = false;
    
    private static double analyzeTransitiveNomRelation(Mention ne,Constituent nom,LinkingProblem problem){
        if(!ne.isPER())
            return 1.0;
        String headWord = getCorefHeadWord(nom);
        logger.debug("NOMCOREF DETECTED:" + ne + " === " + nom);
        
        if(GlobalParameters.params.RESOLVE_NOMINAL_COREF){
            for(Constituent nomEntity:problem.entityView.where(Queries.containedInConstituent(nom))){
                Mention m = problem.getComponent(nomEntity);
                if (m != null && m.isNamedEntity() && !m.types.contains(SurfaceType.MISC)) {
                    for(CoherenceRelation r:RelationalAnalysis.retrieveRelation(ne, m, "NOM", 1.0, problem.relations)){
                        problem.addCorefException(r.arg1.mentionToDisambiguate, r.arg2.mentionToDisambiguate);
                    }
                }
            }
        }
        
        if(!isPersonNom(headWord))
            return 1.0;
        WikiCandidate top = ne.topCandidate;
        if(top==null || top.wikiData==null || top.wikiData.lexInfo == null)
            return 1.0;
        
        TF_IDF_Doc nomContext = GlobalParameters.wikiAccess.getWikiSummaryData().getTextRepresentation(nom.getSurfaceString(), true);
        TF_IDF_Doc candidateDoc = new TF_IDF_Doc(top.wikiData.lexInfo);
        
        if(printToken){
            printPairs(nomContext,top);
        }
        
        double cosSim = TFIDF.getCosineSim(nomContext, candidateDoc);
        
        return cosSim;
    }
    
    private static boolean isNom(Constituent nom){
        return nom.size()>2 && hasNomAttr(nom) && lowercaseWord.matcher(nom.getSurfaceString()).find();
    }
    
    private static boolean hasNomAttr(Constituent nom){
        return "NOM".equals(nom.getAttribute(Constants.MentionType));
    }

    public static boolean isPersonNom(String s){
        try {
            return s.endsWith("or")
                    || s.endsWith("er")
                    || GlobalParameters.wordnet.getDefaultHypenymsToTheRoot(s, POS.NOUN).contains("person")
                    || GlobalParameters.wnsim.wnsim("person", s)>0 ;
        } catch (JWNLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public static String getCorefHeadWord(Constituent c){
        return WikiTitleUtils.getHead(StringUtils.strip(c.getSurfaceString(),"(),."));
    }
    
    private static void printPairs(TF_IDF_Doc nom,WikiCandidate top){
        System.out.print("Constructed representation "+top);
        printTokens(Ints.asList(nom.featureMap.keys()));
        System.out.println("Text tokens for "+top);
        printTokens(top.wikiData.lexInfo.getTextTokensFidsList());
        System.out.println("Context tokens for "+top);
        printTokens(top.wikiData.lexInfo.getContextTokensFidsList());
    }
    
    private static void printTokens(Iterable<Integer> wordIds){
        for(int fid:wordIds){
            System.out.print(GlobalParameters.wikiAccess.getWikiSummaryData().tokensFeatureMap.fidToWord.get(fid)+",");
        }
    }


}
