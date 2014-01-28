package edu.illinois.cs.cogcomp.wikifier.utils.datastructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.wikiRelations.WikiRelationIndexUtils;

import LBJ2.classify.Score;
import LBJ2.classify.ScoreSet;
import LBJ2.learn.Normalizer;
import LBJ2.learn.Softmax;

public class Triple {

    public static final String MULTIPLE_RELATION = "MULTIPLE_RELATION";
    protected static final double LINK_RELATION_DAMPING_FACTOR = 0.2;
    private static final Normalizer DEFAULT_NORMALIZER = new Softmax(0.2);

    protected String arg1;
    protected String pred;
    protected String arg2;
    protected double score;
    protected double normalizedScore = 0.0;

    private List<String> multiplePred = new ArrayList<String>();

    public Triple(String arg1, String pred, String arg2, double weight) {
        this.arg1 = arg1;
        this.pred = pred;
        this.arg2 = arg2;
        setScore(weight);
    }

    public Triple(String arg1, String pred, String arg2) {
        this(arg1, pred, arg2, 0.0);
    }

    public String getArg1() {
        return arg1;
    }

    public void setArg1(String arg1) {
        this.arg1 = arg1;
    }

    @Override
    public String toString() {
        return "Triple [arg1=" + arg1 + ", pred=" + pred + ", arg2=" + arg2 + ", score=" + score + ", normalizedScore=" + normalizedScore
                + "]";
    }

    public String getPred() {
        return pred;
    }

    public void swapArgs() {
        String temp = arg2;
        arg2 = arg1;
        arg1 = temp;
    }
    
    public List<String> getArgs() {
        return Arrays.asList(arg1, arg2);
    }
    
    public Triple createReversed(){
        return new Triple(arg2,pred,arg2,score);
    }
    
    // Should only call this when the relation is MULTI_RELATION
    // Otherwise this will not give any results
    public List<String> getMultiplePredicates(){
        return multiplePred;
    }
    
    public void addPredicate(String pred) {
        multiplePred.add(pred);
    }

    public void setPred(String pred) {
        this.pred = pred;
    }

    public String getArg2() {
        return arg2;
    }
    
    public double getNormalizedScore() {
        return normalizedScore;
    }

    public Triple setNormalizedScore(double normalizedScore) {
        this.normalizedScore = normalizedScore;
        return this;
    }

    public void setArg2(String arg2) {
        this.arg2 = arg2;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((arg1 == null) ? 0 : arg1.hashCode());
        result = prime * result + ((arg2 == null) ? 0 : arg2.hashCode());
        result = prime * result + ((pred == null) ? 0 : pred.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Triple other = (Triple) obj;
        if (arg1 == null) {
            if (other.arg1 != null)
                return false;
        } else if (!arg1.equals(other.arg1))
            return false;
        if (arg2 == null) {
            if (other.arg2 != null)
                return false;
        } else if (!arg2.equals(other.arg2))
            return false;
        if (pred == null) {
            if (other.pred != null)
                return false;
        } else if (!pred.equals(other.pred))
            return false;
        return true;
    }

    /**
     * Consolidates a list of weighted triples when they have the same args,
     * note that the return value might be MultiRelationTriple
     * 
     * @param triples
     * @return
     */
    public static List<Triple> consolidate(List<Triple> triples,boolean normalizeScore) {

        Map<String, Triple> combined = new HashMap<String, Triple>();
//        for (Triple t : triples) {
//            t.arg1 = TitleNameNormalizer.normalize(t.arg1);
//            t.arg2 = TitleNameNormalizer.normalize(t.arg2);
//        }
        for (Triple t : triples) {
            String key = t.arg1 + "_" + t.arg2;
            if (!combined.containsKey(key)){
                combined.put(key, t);
                t.addPredicate(t.pred);
            }
            else {
                Triple existingTriple = combined.get(key);
                existingTriple.addPredicate(t.pred);
                
                if (WikiRelationIndexUtils.WIKI_LINK_RELATION.equals(t.pred))
                    existingTriple.score += t.score * LINK_RELATION_DAMPING_FACTOR;
                else{
                    // Prefers the more explicity predicates
                    existingTriple.pred = t.pred;
                    existingTriple.score += t.score;
                }
                
                combined.put(key, existingTriple);
            }
        }

        List<Triple> consolidated = new ArrayList<Triple>(combined.values());

        if (consolidated.size() > 1)
            Collections.sort(consolidated, Comparators.higherScoreTripleFirst);

        return normalizeScore? normalizeScores(consolidated,DEFAULT_NORMALIZER) : consolidated;
    }
    
    // Normalizes the score to a softmax distribution
    private static List<Triple> normalizeScores(List<Triple> triples,Normalizer normalizer){
        // Intialize score set
        ScoreSet scoreSet = new ScoreSet();
        for(Triple t:triples){
            scoreSet.put(t.toString(), t.getScore());
        }

        scoreSet = normalizer.normalize(scoreSet);
        
        for(Triple t:triples){
            double newScore = scoreSet.get(t.toString());
            t.setNormalizedScore(newScore);
        }
    
        return triples;
    }
    
    protected static class AvgNorm extends Normalizer{

        @Override
        public ScoreSet normalize(ScoreSet ss) {
            double sum = 0;
            for(Score sc :ss.toArray()){
                sum+=sc.score;
            }
            for(Score sc :ss.toArray()){
                sc.score = sc.score/sum;
            }
            return ss;
        }
        
    }

}
