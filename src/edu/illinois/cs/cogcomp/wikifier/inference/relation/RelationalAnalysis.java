package edu.illinois.cs.cogcomp.wikifier.inference.relation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;


import com.google.common.collect.Lists;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.CoherenceRelation;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Triple;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.CustomEditDistance;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.wikiRelations.WikiRelationSearcher;
import static edu.illinois.cs.cogcomp.wikifier.utils.WikiTitleUtils.*;

public class RelationalAnalysis {

    private static Logger logger = Logger.getLogger(RelationalAnalysis.class);

    // Applies strict location relation predicate filter
    public static final String LOCATED = "LOCATED";
    // Signifies that relations arguments must exist in current candidate set
    public static final String RESTRICTED = "RESTRICTED";
    


    private static final CustomEditDistance distance = new CustomEditDistance();
    
    /**
     * Searches the database
     * 
     * @param left
     * @param right
     * @param textualPredicate
     * @return
     */
    public static List<CoherenceRelation> retrieveRelation(
            Mention left, 
            Mention right, 
            String textualPredicate, 
            double scale,
            List<CoherenceRelation> relations) {
        
        boolean isLoc = LOCATED.equals(textualPredicate);
        boolean isRestricted = RESTRICTED.equals(textualPredicate);
        List<CoherenceRelation> ret = new ArrayList<>();
        
        for(Triple t : analyzeSearch(left, right,textualPredicate)){
            
            if(isLoc && !isLocationRelation(t))
                continue;
            
            if(isRestricted && !matchesBoth(left,right,t))
                continue;
            
            CoherenceRelation newRelation = new CoherenceRelation(left, right, t);
            // Conjunction is not really a relation, needs to be weaker
            newRelation.weight = isLoc ? 1.0 : newRelation.weight * scale;
            if ("NOM".equals(textualPredicate))
                newRelation.weight = CoherenceRelation.NOM_PREFERENCE;
            
            if (relations != null)
                relations.add(newRelation);
            ret.add(newRelation);
            logger.info("Accepting relation " + t + "For surfaces " + left.surfaceForm + " and " + right.surfaceForm);
        }
        return ret;
    }
    
    public static boolean matchesBoth(Mention left,Mention right,Triple t){
        return left.getCandidates().contains(t.getArg1()) && right.getCandidates().contains(t.getArg2());
    }
    

    public static boolean isLocationRelation(Triple t){
        return "isPartOf".equals(t.getPred()) 
                || getCanonicalTitle(t.getArg2()).equals(getSecondaryEntity(t.getArg1()));
                
    }
    
    // Return the most confident hypothesis derived from the searches
    private static List<Triple> analyzeSearch(Mention left, Mention right,String pred) {

        List<Triple> wikiRelations = WikiRelationSearcher.searchGroundedRelations(left, right);
        wikiRelations = filter(wikiRelations, left, right);
        wikiRelations = Triple.consolidate(wikiRelations, false);
        
        if (wikiRelations.size() == 0) {
            return wikiRelations;
        }
        
        if (wikiRelations.size() == 1) {
            Triple t = wikiRelations.get(0);
            t.setNormalizedScore(CoherenceRelation.HARD_PREFERENCE_SCORE);
            return wikiRelations;
        }
        
        boolean relaxMatch = 
        GlobalParameters.params.RESOLVE_NOMINAL_COREF && "NOM".equals(pred);
        
        
        // Goes into balancing mode TODO restrict to DBpedia relation only, or is scoring accounting for it anyways
        List<Triple> candidateFiltered = Lists.newArrayList();
        for (Triple t : wikiRelations) {
            // Perfect Match
            if(t.getArg1().equals(left.getTopTitle()) && t.getArg2().equals(right.getTopTitle())){
                candidateFiltered.clear();
                candidateFiltered.add(t.setNormalizedScore(CoherenceRelation.HARD_PREFERENCE_SCORE));
                return candidateFiltered;
            }
            if((relaxMatch && matchesOne(left,right,t) )||
                
                matchesBoth(left,right,t)) {
                candidateFiltered.add(t);
            }
        }
        
        
        if (candidateFiltered.size() == 1) {
            candidateFiltered.get(0).setNormalizedScore(CoherenceRelation.HARD_PREFERENCE_SCORE);
        }
        
        return candidateFiltered;
    }
    
    private static boolean matchesOne(Mention left, Mention right, Triple t) {
        return left.getCandidates().contains(t.getArg1()) || right.getCandidates().contains(t.getArg2());
    }

    /**
     * Filters out bad answers
     * @param relations
     * @param e1
     * @param e2
     * @return
     */
    public static List<Triple> filter(List<Triple> relations,Mention e1,Mention e2){
        if (relations.size() == 0)
            return relations;
        List<Triple> filtered = new ArrayList<Triple>();
        String leftExact = e1.getTopTitle();//e1.topDisambiguation==null? null:e1.topDisambiguation.titleName;
        String rightExact = e2.getTopTitle();
                
        // At least one of the argument has to be the existing top disambiguation, the other must satisfy 
        // certain entailment measures
        for(Triple t:relations){
            if( (t.getArg1().equals(leftExact) && e2.hasPotentialCandidate(t.getArg2()))
              ||(t.getArg2().equals(rightExact)&& e1.hasPotentialCandidate(t.getArg1()))
            ){
                if(!containsYear(t))
                    filtered.add(t);
            }

        }
        Collections.sort(filtered,Comparators.higherScoreTripleFirst);
        return filtered;
    }
    
    public static final Pattern year = Pattern.compile("\\d{4}");
    
    private static boolean containsYear(Triple t){
        return year.matcher(t.getArg1()).find() || year.matcher(t.getArg2()).find();
    }


    public static double titleSimScore(Mention query, String result) {
        String arg0 = getCanonicalTitle(result);
        String arg1 = query.surfaceForm;
        // Possible in the probability space
        if (query.getCandidates().contains(result))
            return query.getCandidateScore(result);
        if (query.isPER()) {
            return query.getPseudoTitle().equals(getCanonicalTitle(result))?1:0;
        }
        if (!query.headWord().equals(getHead(result))) {
            return -1;
        }
        if (StringUtils.countMatches(arg1, " ") < 2)
            return distance.tokenLevelSim(arg0, arg1);
        return distance.proximity(arg0, arg1);
    }

}
