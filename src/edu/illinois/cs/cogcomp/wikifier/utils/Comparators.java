package edu.illinois.cs.cogcomp.wikifier.utils;

import java.util.Comparator;
import java.util.Map;



import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotationUtilities;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Triple;



public class Comparators {

    public static class MapValueComparator implements Comparator<Object> {

        private final Map<? extends Object, ? extends Number> map;
        private boolean reverse = false;

        public MapValueComparator(Map<? extends Object, ? extends Number> map, boolean reverse) {
            this.map = map;
            this.reverse = reverse;
        }

        public MapValueComparator(Map<? extends Object, ? extends Number> map) {
            this.map = map;
        }

        @Override
        public int compare(Object o1, Object o2) {
            if (!reverse)
                return Double.compare(map.get(o1).doubleValue(),map.get(o2).doubleValue());
            return Double.compare(map.get(o2).doubleValue(), map.get(o1).doubleValue());
        }
    }

    public static final Comparator<Constituent> longerConstituentFirst = new Comparator<Constituent>() {
        @Override
        public int compare(Constituent o1, Constituent o2) {
            return -TextAnnotationUtilities.constituentLengthComparator.compare(o1, o2);
        }
    };
    
    /**
     * Sort longer candidates first
     */
    public static final Comparator<Mention> longerEntityFirst = new Comparator<Mention>() {
        @Override
        public int compare(Mention o1, Mention o2) {
            if(o1.tokenLength()==o2.tokenLength())
                return o2.surfaceForm.length()-o1.surfaceForm.length();
            return o2.tokenLength() - o1.tokenLength();
        }
    };

    public static final Comparator<Mention> shorterEntityFirst = new Comparator<Mention>() {
        @Override
        public int compare(Mention o1, Mention o2) {
            return -longerEntityFirst.compare(o1, o2);
        }
    };

    public static final Comparator<Mention> earlierEntityFirst = new Comparator<Mention>() {
        @Override
        public int compare(Mention o1, Mention o2) {
            return o1.startTokenId - o2.startTokenId;
        }
    };
    
    public static final Comparator<Mention> higherRankerScoreFirst = new Comparator<Mention>() {
        @Override
        public int compare(Mention e1, Mention e2) {
            double s1 = e1.finalCandidate==null ? 0.0:e1.finalCandidate.rankerScore;
            double s2 = e2.finalCandidate==null ? 0.0:e2.finalCandidate.rankerScore;
            return Double.compare(s2, s1);
        }
    };
    
    public static Comparator<Mention> proximityComparator(final Mention e){
        return new Comparator<Mention>() {
            @Override
            public int compare(Mention o1, Mention o2) {
                return o1.distanceTo(e) - o2.distanceTo(e);
            }
        };
    }

    /**
     * Higher score first
     */
    public static final Comparator<WikiCandidate> confidenceFirst = new Comparator<WikiCandidate>() {
        @Override
        public int compare(WikiCandidate o1, WikiCandidate o2) {
            return Double.compare(o2.rankerScore, o1.rankerScore);
        }
    };

    public static final Comparator<Triple> higherScoreTripleFirst = new Comparator<Triple>() {

        @Override
        public int compare(Triple o1, Triple o2) {
            return Double.compare(o2.getScore(), o1.getScore());
        }

    };
    
    public static final Comparator<Triple> higherNormalizedScoreTripleFirst = new Comparator<Triple>() {

        @Override
        public int compare(Triple o1, Triple o2) {
            return Double.compare(o2.getNormalizedScore(), o1.getNormalizedScore());
        }

    };
    
    public static final Comparator<Mention> corefHeadComparator = new Comparator<Mention>(){

        @Override
        public int compare(Mention o1, Mention o2) {

            int dist = shorterEntityFirst.compare(o1, o2);
            
            // 1 token entity yields to longer entities
            if(o1.tokenLength()==1||o2.tokenLength()==1 && dist!=0)
                return dist;
            
            return Double.compare(o1.getTopRankScore(),o2.getTopRankScore());
            
        }
    };
    
    public static final Comparator<Mention> topCandidateComparator = new Comparator<Mention>(){

        @Override
        public int compare(Mention o1, Mention o2) {
            int result = -Double.compare(o1.getTopRankScore(), o2.getTopRankScore());
            if (result == 0)
                return -Integer.compare(o1.tokenLength(), o2.tokenLength());
            return result;
        }
        
    };

    public static final Comparator<String> shorterStringFirst = new Comparator<String>() {

        @Override
        public int compare(String o1, String o2) {
            int l1 = o1 == null? 0 : o1.length();
            int l2 = o2 == null? 0 : o2.length();
            return l1 - l2;
        }

    };
    
    public static Comparator<String> subStringAlignment(final String s){ 
        return new Comparator<String>(){

            @Override
            public int compare(String o1, String o2) {
                return s.indexOf(o1) - s.indexOf(o2);
            }
            
        };
    };

}
