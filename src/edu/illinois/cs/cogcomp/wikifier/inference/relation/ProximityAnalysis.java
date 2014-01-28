// $codepro.audit.disable useCharAtRatherThanStartsWith
package edu.illinois.cs.cogcomp.wikifier.inference.relation;

import static edu.illinois.cs.cogcomp.edison.sentences.Queries.*;
import static edu.illinois.cs.cogcomp.edison.sentences.TextAnnotationUtilities.*;
import static edu.illinois.cs.cogcomp.wikifier.utils.Comparators.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.Logger;

import edu.illinois.cs.cogcomp.core.datastructures.IQueryable;
import edu.illinois.cs.cogcomp.core.transformers.Predicate;
import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.utils.WordFeatures;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;
/**
 * 
 * @author cheng88
 *
 */
public class ProximityAnalysis {
    
    static final Logger logger = Logger.getLogger(ProximityAnalysis.class);
    private static final double NESimTheshold = 0.99999;
    
    /**
     * Analyzes chunks that are known to be close to each other
     * that is very likely to have some kind of relations
     * @param problem
     * @param relations
     */
    static void analyze(LinkingProblem problem){
        int lastStartSpan = -1;
        for (Constituent c : problem.entityView.orderBy(constituentStartComparator)) {
            if (c.getStartSpan() <= lastStartSpan)
                continue;

            lastStartSpan = c.getStartSpan();
            Constituent longest = getCoStartLongChunk(problem, c);
            if (longest != null) {
                
                analyzeChunks(longest, problem);
                // Do no check the same start span again
//                lastToken = longest.getEndSpan();
            }
        }
    }
    
    
    /**
     * Gets a long span chunk starting at the constituent c
     * @param problem
     * @param c
     * @return
     */
    private static Constituent getCoStartLongChunk(LinkingProblem problem,Constituent c){
        Constituent longest = null;
        if(WordFeatures.isCapitalized(c.getSurfaceString())){
            for(Constituent coStart:problem.entityView
                    .where(sameStartSpanAs(c))
                    .orderBy(longerConstituentFirst)){
                if(!problem.getComponent(coStart).isPER() && coStart.size()>=3)
                    longest = coStart;
                // Only pick the longest one
                break;
            }
            
        }
        return longest;
    }
    
    /** Aims at fixing the following expressions
     * Longest = leftSub + [connective] + rightSub ; 
     * [Prime Minister] [Phan Van Khai]
     * [Iranian][Ministry of Defense]
     * [Supreme Court] in [Florida]
     * [Oxford University]'s [Melton College]
     * @param longSpan
     * @param problem
     * @param relations
     * @return Whether there are some relations extracted from this chunk
     */
    private static void analyzeChunks(Constituent longSpan, LinkingProblem problem){
        
        IQueryable<Constituent> subChunks = longSpan.getView().where(containedInConstituent(longSpan));
        //[[Supreme Court] of [Florida]]
        //[[left][right]]
        //[    top      ]
        Mention top = problem.getComponent(longSpan);
        Mention left  = getTopMatchingConstituent(
                            subChunks, 
                            sameStartSpanAs(longSpan), 
                            longerConstituentFirst,
                            longSpan,
                            problem
                       );
        if (left == null)
            return;
        Mention right = getTopMatchingConstituent(
                            problem.entityView, 
                            after(left.constituent).and(hasOverlap(longSpan)), 
                            constituentStartComparator,
                            null,
                            problem
                        );

        // Two entities have to be close
        if(right== null || left.overlaps(right) || containsCapitalLettersBetween(left,right)
                || right.surfaceForm.length()<2 || left.surfaceForm.length()<2 || left.surfaceForm.equals(right.surfaceForm))
            return;

        // Conjunction requires extra care
        boolean isConjunction = longSpan.getSurfaceString().contains(" and ");
        boolean containsComma = top.surfaceForm.contains(",");
        
        if(!containsComma && !isConjunction && top.isUnmapped()){
            analyzeLongEntity(top);
        }
        
        if(!top.isUnmapped()){
            if(containsComma){
                problem.relations.addAll(left.getCorefRelationsTo(top));
            }else{
                // These two relations are specially handled
                if(!containsComma && !isConjunction){
                    for(Mention part:Arrays.asList(left,right)){
                        if(top.sameHeadWord(part)){
                            problem.relations.addAll(part.getCorefRelationsTo(top));
                        }
                    }
                }
            }
        }else{
            /**
             * Where top is null. We need to be careful here not to over-extend the context of the
             * sub entities. i.e. person name requires disambiguation page matches, concepts requires
             * head word matches etc. Here we can check the relation between the two adjacent
             * entities
             */
            int maxTokenLength = Math.max(left.maxNormLength(), right.maxNormLength());
            int minTokenLength = Math.min(left.tokenLength(), right.tokenLength());
            if((left.isLOC() || right.isLOC()) && !containsComma)
                return;
            boolean isLocExp = isLocationExpression(top, left, right);
            boolean restricted = true;
            
            if( (maxTokenLength+minTokenLength>3 && !containsComma) 
                    || (minTokenLength>1 && maxTokenLength>2)
                    || isLocExp){
                restricted = false;
            }

            double relationScale = isConjunction ? 0.5 : 1.0;
            String predicate = restricted ? RelationalAnalysis.RESTRICTED : null;
            // LOCATED is a stronger restriction that restricted
            predicate = isLocExp ? RelationalAnalysis.LOCATED : predicate;
            RelationalAnalysis.retrieveRelation(left, right, predicate, relationScale, problem.relations);

        }

    }

    
    /**
     * We assume that the top entity is null in here
     * @param top
     */
    private static void analyzeLongEntity(Mention top){
        List<String> results = TitleNameIndexer.searchTitles(top.surfaceForm);
        String bestAnswer = null;
        for(String title:results){
            double sim = RelationalAnalysis.titleSimScore(top,title);
            if (sim > NESimTheshold) {
                if (bestAnswer == null){
                    bestAnswer = title;
                }else {
                    // When ambiguity exists, this method do nothing
                    bestAnswer = null;
                    break;
                }
            }
        }
        if(bestAnswer!=null && !(!bestAnswer.contains("_in_") && top.surfaceForm.contains(" in ")))
            top.topCandidate = top.getCandidate(bestAnswer);
    }
    
    private static boolean containsCapitalLettersBetween(Mention left,Mention right){
        int start = left.endTokenId;
        int end = right.startTokenId;
        if (start >= end)
            return false;
        for (String s : left.parentProblem.ta.getTokensInSpan(start, end)) {
            if (WordFeatures.isCapitalized(s))
                return true;
        }
        return false;
    }
    
    private static boolean containsCommaBetween(Mention left,Mention right){
        if (left.surfaceForm.endsWith(",") || right.surfaceForm.startsWith(","))
            return true;
        int start = left.endTokenId;
        int end = right.startTokenId;
        if (start >= end)
            return false;
        for(String s:left.parentProblem.ta.getTokensInSpan(start, end)){
            if(s.equals(","))
                return true;
        }
        return false;
    }
    
    
    /**
     * 
     * @param top
     * @param left
     * @param right
     * @return Whether top is in the form of "Chicago, Illinois"
     */
    private static boolean isLocationExpression(
            Mention top,
            Mention left,
            Mention right
            ){
        if(top.isLOC())
            return true;
        // We only want to correct location expression, therefore tight
        // on what can qualify
        if(!containsCommaBetween(left,right) || (!left.isConditionallyLOC(right)) )
            return false;
        int curEnd = top.endTokenId;
        TextAnnotation ta = top.parentProblem.ta;
        int max = Math.min(ta.getTokens().length,curEnd+3);
        int next = Math.min(ta.getTokens().length,curEnd);
        int prev = Math.max(0,left.startTokenId-1);
        if(ta.getToken(next).equals("and")||ta.getToken(prev).equals(","))
            return false;
        if(ta.getToken(next-1).endsWith(".")||ta.getToken(next).equals("."))
            return true;
        // Checking for list structure
        // If there exists a violation in the next 3 tokens, then it must be a 
        // location expression
        for(int i = top.endTokenId;i<max;i++){
            String token = ta.getToken(i);
            if(!token.equals("and")&&!token.contains(",")&&!WordFeatures.isCapitalized(token))
                return true;
        }
        return false;
    }
    
    /**
     * 
     * @param candidates
     * @param criteria
     * @param avoid
     * @param problem
     * @return first constituent matching the criteria in the IQueryable list
     */
    private static Mention getTopMatchingConstituent(
            IQueryable<Constituent> candidates,
            Predicate<Constituent> criteria,
            Comparator<Constituent> ordering,
            Constituent avoid,
            LinkingProblem problem){
        IQueryable<Constituent> altered = candidates.where(criteria).orderBy(ordering);
        for(Constituent c:altered){
            if (c == avoid)
                continue;
            Mention e = problem.getComponent(c);
            
            if(e!=null && WordFeatures.isCapitalized(e.surfaceForm) 
                    && (e.topCandidate!=null || e.isNamedEntity())){
                
                //Matching the right side of the arguments, prevent recursion of dept 2+
                if (avoid == null && candidates == problem.entityView) {
                   return getTopMatchingConstituent(altered,sameStartSpanAs(c),longerConstituentFirst,null,problem);
                }
                return e;
            }
        }
        return null;
    }


}
