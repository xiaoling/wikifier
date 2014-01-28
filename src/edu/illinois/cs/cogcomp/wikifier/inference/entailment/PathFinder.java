package edu.illinois.cs.cogcomp.wikifier.inference.entailment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.mit.jwi.item.IPointer;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.item.Pointer;

//call constructor, and then wnsim(String,String) to get a double score.
public class PathFinder {

    public WNWrapper wrap;
    public static final int MAX_SEARCH_DEPTH = 10;
    public HashSet<Pointer> simPointers;
    public HashSet<Pointer> simPointersLex;
    public HashSet<Pointer> hypers;
    public HashSet<Pointer> ent;
    public boolean lexical = false;
    public boolean foundpath = false;

    static Logger logger = Logger.getLogger(PathFinder.class);

//    public static void main(String[] args) {
//        final PathFinder p = new PathFinder();
//        final AtomicDouble score = new AtomicDouble(0.0);
//        Timer timer = new Timer("1000 winSim") {
//            @Override
//            public void run() {
//                score.set(p.wnsim("wife", "spouse"));
//            }
//        };
//        timer.timedRun();
//        System.out.println(score);
//        // System.out.println(p.wnsim_wsd("car","automobile",null,null));
//    }

    public PathFinder(String wordnetPath) {

        // BasicConfigurator.configure();

        wrap = new WNWrapper(wordnetPath);
        simPointers = new HashSet<Pointer>();
        simPointersLex = new HashSet<Pointer>();
        hypers = new HashSet<Pointer>();
        ent = new HashSet<Pointer>();

        simPointers.add(Pointer.HYPERNYM);
        simPointers.add(Pointer.HOLONYM_MEMBER);
        simPointers.add(Pointer.HOLONYM_PART);
        simPointers.add(Pointer.ENTAILMENT);

        hypers.add(Pointer.HYPERNYM);
        hypers.add(Pointer.HOLONYM_MEMBER);
        hypers.add(Pointer.HOLONYM_PART);

        ent.add(Pointer.ENTAILMENT);

        // simPointers.add(Pointer.DERIVATIONALLY_RELATED);
        // simPointers.add(Pointer.DERIVED_FROM_ADJ);
        // simPointers.add(Pointer.ALSO_SEE);
        // simPointers.add(Pointer.SIMILAR_TO);
        // simPointers.add(Pointer.VERB_GROUP);

        // simPointers.add(Pointer.ANTONYM);
        // simPointersLex.add(Pointer.DERIVATIONALLY_RELATED);
        // simPointersLex.add(Pointer.ALSO_SEE);
        // simPointersLex.add(Pointer.PERTAINYM);

        // System.out.println(simPointers.contains(Pointer.VERB_GROUP));
    }

    public double score_binary(String w1, String w2) {
        if (findConnection(w1, w2))
            return 1.;

        return 0.;
    }

    public int getLCS(ISynset is) {
        return getDepth(is, 0);
    }

    private int getDepth(ISynset is, int depth) {
        Map<IPointer, List<ISynsetID>> map = is.getRelatedMap();
        int min = 1000;
        List<ISynsetID> lis = map.get(Pointer.HYPERNYM);

        if (depth > 50)
            return depth;

        if (lis == null || lis.size() == 0)
            return depth + 1;

        for (ISynsetID id : lis) {
            int m = getDepth(wrap.dict.getSynset(id), depth + 1);
            if (m < min)
                min = m;
        }

        return min;
    }

    // method to call to approximate cpp wnsim.
    // this method has slightly more coverage than cpp wnsim
    // it doesn't give scores for DT or PREP
    public double wnsim(String w1, String w2) {
        foundpath = false;
        double d1 = wnsim(w1, w2, hypers);
        double d2 = wnsim(w1, w2, ent);
        foundpath = false;
        double max = d1;

        if (d1 < 0)
            return d1;

        if (d1 > 0 || d2 > 0)
            max = d1 > d2 ? d1 : d2;

        double lcs = lcs_score(w1, w2);

        if (lcs > max)
            return lcs;

        return max;
        // if(path)
        // return 0.;

        // return lcs_score(w1,w2);
    }

    public double wnsim(String w1, String w2, HashSet<Pointer> ptrs) {

        int k = 3;
        double theta = 0.3;
        double alpha = 1.5;

        Triple<ISynset, Integer, ArrayList<IPointer>> t = findConnection(w1, w2, true, ptrs, null, null, false, null, null);
        if (t == null)
            return 0.;

        int pl = t.getMiddle();
        int depth = getLCS(t.getLeft());
        double score = 0.;

        // System.out.println("Path length: "+pl+" LCS depth: "+depth);
        // System.out.println("Synset: "+t.getLeft());

        if (pl <= k) {
            score = Math.pow(theta, pl);
        } else {
            if (depth >= alpha * pl) {
                score = Math.pow(theta, k);
            }
        }

        if (wrap.isAntonym(w1, w2))
            return -.5;

        return score;
    }

    public double wnsimPOS(String word1, POS pos1, String word2, POS pos2) {
        foundpath = false;
        double d1 = wnsimPOS(word1, pos1, word2, pos2, hypers);
        double d2 = wnsimPOS(word1, pos1, word2, pos2, ent);
        foundpath = false;
        double max = d1;

        if (d1 < 0)
            return d1;

        if (d1 > 0 || d2 > 0)
            max = d1 > d2 ? d1 : d2;

        double lcs = lcs_score(word1, pos1, word2, pos2); // should leave in?

        if (lcs > max)
            return lcs;

        return max;
    }

    private double wnsimPOS(String word1, POS pos1, String word2, POS pos2, HashSet<Pointer> ptrs) {

        int k = 3;
        double theta = 0.3;
        double alpha = 1.5;

        Triple<ISynset, Integer, ArrayList<IPointer>> t = findConnection(word1, word2, true, ptrs, null, null, true, pos1, pos2);
        if (t == null)
            return 0.;

        int pl = t.getMiddle();
        int depth = getLCS(t.getLeft());
        double score = 0.;

        // System.out.println("Path length: "+pl+" LCS depth: "+depth);
        // System.out.println("Synset: "+t.getLeft());

        if (pl <= k) {
            score = Math.pow(theta, pl);
        } else {
            if (depth >= alpha * pl) {
                score = Math.pow(theta, k);
            }
        }

        if (wrap.isAntonym(word1, pos1, word2, pos2))
            return -.5;

        return score;
    }

    public double wnsim_wsd(String w1, String w2, ArrayList<Pair<ISynset, Double>> d1, ArrayList<Pair<ISynset, Double>> d2) {

        if (d1 == null || d1.size() == 0) {
            d1 = getUniform(w1);
        }

        if (d2 == null || d2.size() == 0) {
            d2 = getUniform(w2);
        }

        // System.out.println("Size: "+d1.size()+" "+d2.size()+" "+w1+" "+w2);
        if (d1.size() == 0 || d2.size() == 0)
            return 0.;

        double score = 0.;

        for (int i = 0; i < d1.size(); i++) {
            for (int j = 0; j < d2.size(); j++) {
                double s = wnsim_scoreSynsets(d1.get(i).getFirst(), d2.get(j).getFirst());
                // System.out.println(s);
                score += d1.get(i).getSecond() * d2.get(j).getSecond() * s;
            }
        }

        return score;

    }

    public double wnsim_scoreSynsets(ISynset s1, ISynset s2) {
        foundpath = false;
        double d1 = wnsim_scoreSynsets(s1, s2, hypers);
        double d2 = wnsim_scoreSynsets(s1, s2, ent);
        foundpath = false;

        // System.out.println(d1+" "+d2);

        if (d1 < 0)
            return d1;

        // if(d1 > 0 || d2 > 0)
        return d1 > d2 ? d1 : d2;

        // if(path)
        // return 0.;

        // return lcs_score(w1,w2);
    }

    public double wnsim_scoreSynsets(ISynset s1, ISynset s2, HashSet<Pointer> ptrs) {

        int k = 3;
        double theta = 0.3;
        double alpha = 1.5;

        // System.out.println(s1+" "+s2);
        ArrayList<ISynset> lis1 = new ArrayList<ISynset>();
        lis1.add(s1);

        ArrayList<ISynset> lis2 = new ArrayList<ISynset>();
        lis2.add(s2);

        Triple<ISynset, Integer, ArrayList<IPointer>> t = findConnection("", "", true, ptrs, lis1, lis2);
        if (t == null)
            return 0.;

        int pl = t.getMiddle();
        int depth = getLCS(t.getLeft());
        double score = 0.;

        // System.out.println("Path length: "+pl+" LCS depth: "+depth);
        // System.out.println("Synset: "+t.getLeft());

        if (pl <= k) {
            score = Math.pow(theta, pl);
        } else {
            if (depth >= alpha * pl) {
                score = Math.pow(theta, k);
            }
        }

        // if(wrap.isAntonym(w1, w2))
        // return -.5;

        return score;

    }

    public boolean findConnection(String w1, String w2) {
        Triple<ISynset, Integer, ArrayList<IPointer>> t = findConnection(w1, w2, false, simPointers, null, null);
        if (t == null)
            return false;
        return true;
    }

    public Triple<ISynset, Integer, ArrayList<IPointer>> findConnection(String w1, String w2, boolean twoWay, HashSet<Pointer> ptrs,
            IWord iw1, IWord iw2, boolean pos, POS pos1, POS pos2) {

        ArrayList<ISynset> synsets2;
        ArrayList<ISynset> synsets1;

        if (iw1 == null) {
            if (pos)
                synsets1 = wrap.getAllSynset(w1, pos1);
            else
                synsets1 = wrap.getAllSynset(w1);
        } else {
            synsets1 = new ArrayList<ISynset>();
            synsets1.add(iw1.getSynset());
        }

        if (iw2 == null) {
            if (pos)
                synsets2 = wrap.getAllSynset(w2, pos2);
            else
                synsets2 = wrap.getAllSynset(w2);
        } else {
            synsets2 = new ArrayList<ISynset>();
            synsets2.add(iw2.getSynset());
        }

        return findConnection(w1, w2, twoWay, ptrs, synsets1, synsets2);
    }

    public Triple<ISynset, Integer, ArrayList<IPointer>> findConnection(String w1, String w2, boolean twoWay, HashSet<Pointer> ptrs,
            ArrayList<ISynset> synsets1, ArrayList<ISynset> synsets2) {
        // for(ISynset is: synsets1)
        // System.out.println("0 "+is);
        if (lexical) {
            getLexicalSynsets(synsets1);
            getLexicalSynsets(synsets2);
        }

        ArrayList<Triple<ISynset, Integer, ArrayList<IPointer>>> parents = new ArrayList<Triple<ISynset, Integer, ArrayList<IPointer>>>();
        for (ISynset is : synsets1) {
            parents.addAll(getParents(is, 0, new ArrayList<Triple<ISynset, Integer, ArrayList<IPointer>>>(), new ArrayList<IPointer>(),
                    ptrs));
        }

        Triple<ISynset, Integer, ArrayList<IPointer>> t = null;
        if (twoWay) {
            // for(ISynset is: synsets2)
            // System.out.println("0 "+is);
            ArrayList<Triple<ISynset, Integer, ArrayList<IPointer>>> parents2 = new ArrayList<Triple<ISynset, Integer, ArrayList<IPointer>>>();
            for (ISynset is : synsets2) {
                parents2.addAll(getParents(is, 0, new ArrayList<Triple<ISynset, Integer, ArrayList<IPointer>>>(),
                        new ArrayList<IPointer>(), ptrs));
            }
            // System.out.println(parents.size()+" "+parents2.size());
            for (Triple<ISynset, Integer, ArrayList<IPointer>> tt : parents) {
                for (Triple<ISynset, Integer, ArrayList<IPointer>> tt2 : parents2)
                    if (match((ISynset) tt.getLeft(), (ISynset) tt2.getLeft())) {
                        if (t == null || (Integer) (tt.getMiddle()) + (Integer) (tt2.getMiddle()) < (Integer) t.getMiddle()) {

                            ArrayList<IPointer> pters = tt.getRight();
                            ArrayList<IPointer> pters2 = tt2.getRight();
                            pters.addAll(pters2);

                            t = new Triple<ISynset, Integer, ArrayList<IPointer>>((ISynset) tt.getLeft(), (Integer) (tt.getMiddle())
                                    + (Integer) (tt2.getMiddle()), pters);
                        }
                    }
            }
        } else {
            for (Triple<ISynset, Integer, ArrayList<IPointer>> tt : parents) {
                if (match((ISynset) tt.getLeft(), synsets2)) {
                    if (t == null || (Integer) tt.getMiddle() < (Integer) t.getMiddle()) {

                        t = tt;
                    }
                }
            }
        }
        if (t != null) {
//            ArrayList<IPointer> pts = (ArrayList<IPointer>) t.getRight();
//            String s = "";
//            for (IPointer ip : pts) {
//                s += ip.toString() + " ";
//            }
            // logger.debug("Found path from: "+w1+" "+w2+": "+s);
            // System.out.println(s);
            foundpath = true;
            return t;
        }
        return null;
    }

    private boolean match(ISynset left, ISynset left2) {
        return left.equals(left2) && left.getPOS().equals(left2.getPOS());
    }

    private boolean match(ISynset left, ArrayList<ISynset> synsets2) {
        for (ISynset is : synsets2) {
            if (is.equals(left) && is.getPOS().equals(left.getPOS()))
                return true;
        }
        return false;
    }

    public ArrayList<Triple<ISynset, Integer, ArrayList<IPointer>>> getParents(ISynset is, int curr,
            ArrayList<Triple<ISynset, Integer, ArrayList<IPointer>>> lis, ArrayList<IPointer> pts, HashSet<Pointer> ptrs) {

        if (curr == MAX_SEARCH_DEPTH) {
            return lis;
        }

        if (lis.size() > 0) {
            if (badPath(lis.get(lis.size() - 1).getRight()))
                return lis;
        }

        Triple<ISynset, Integer, ArrayList<IPointer>> t = new Triple<>(is, curr, pts);
        lis.add(t);
        // now for each matching relation recurse
        Map<IPointer, List<ISynsetID>> map = is.getRelatedMap();
        curr++;
        for (IPointer ip : map.keySet()) {
            // System.out.println(ip+" "+simPointers.contains(ip));
            if (ptrs.contains(ip)) {
                ArrayList<IPointer> newlis = new ArrayList<>(pts);
                newlis.add(ip);
                for (ISynsetID sid : map.get(ip)) {
                    if (lexical) {
                        ArrayList<ISynset> synsets = new ArrayList<ISynset>();
                        synsets.add(wrap.dict.getSynset(sid));
                        // getLexicalSynsets(synsets);
                        for (int i = 0; i < synsets.size(); i++) {
                            getParents(synsets.get(i), curr, lis, newlis, ptrs);
                        }
                    } else {
//                        String space = "";
//                        for (int i = 0; i < curr; i++) {
//                            space += "\t";
//                        }
                        // System.out.println(space+" "+curr+" "+wrap.dict.getSynset(sid));
                        getParents(wrap.dict.getSynset(sid), curr, lis, newlis, ptrs);
                    }
                }
            }
        }
        // System.exit(0);
        return lis;
    }

    private double lcs_score(String w1, String w2) {
        return lcs_score(w1, null, w2, null);
    }

    private double lcs_score(String w1, POS p1, String w2, POS p2) {

        ArrayList<ISynset> synsets1 = wrap.getAllSynset(w1, p1);
        ArrayList<ISynset> synsets2 = wrap.getAllSynset(w2, p2);

        if (p1 != null && p2 != null && !p1.equals(p2))
            return 0.;

        double min1 = 100;
        double min2 = 100;
        double sum = 100;
        for (POS pos : POS.values()) {
            min1 = 100;
            min2 = 100;
            // System.out.println(pos);
            if (pos.equals(POS.NOUN))
                continue;
            for (ISynset is : synsets1) {
                // System.out.println(is);
                if (!pos.equals(is.getPOS()))
                    continue;
                double d = getLCS(is);
                if (d < min1)
                    min1 = d;
                // System.out.println("1: "+d);
            }
            // System.out.println();
            for (ISynset is : synsets2) {
                if (!pos.equals(is.getPOS()))
                    continue;
                // System.out.println(is);
                double d = getLCS(is);
                if (d < min2)
                    min2 = d;
                // System.out.println("2 :"+d+" "+min2);
            }
            double s = min1 + min2;
            // System.out.println(s);
            if (s < sum)
                sum = s;
        }
        // System.out.println(sum);
        if (sum <= 3)
            return Math.pow(0.3, sum);

        return 0;
    }

    private void getLexicalSynsets(ArrayList<ISynset> synsets1) {
        ArrayList<ISynset> lis = new ArrayList<>(synsets1);
        for (ISynset is : synsets1) {
            List<IWord> words = is.getWords();
            for (IWord iw : words) {
                Map<IPointer, List<IWordID>> map = iw.getRelatedMap();
                for (IPointer ip : map.keySet()) {
                    // System.out.println(ip+" "+simPointers.contains(ip));
                    if (simPointersLex.contains(ip)) {
                        for (IWordID id : map.get(ip)) {
                            lis.add(wrap.dict.getSynset(id.getSynsetID()));
                        }
                    }
                }
            }
        }
        synsets1 = lis;
    }

    private boolean badPath(ArrayList<IPointer> right) {
        int nSim = 0;
        for (int i = 0; i < right.size(); i++) {
            if (right.get(i).equals(Pointer.SIMILAR_TO))
                nSim++;
        }

        if (nSim > 5)
            return true;

        return false;
    }

    public ArrayList<Pair<ISynset, Double>> getUniform(String w) {
        ArrayList<ISynset> synsets = wrap.getAllSynset(w);
        ArrayList<Pair<ISynset, Double>> d = new ArrayList<Pair<ISynset, Double>>();

        for (int i = 0; i < synsets.size(); i++) {
            // d.add(new Pair<ISynset,Double>(synsets.get(i), prob));
            d.add(new Pair<ISynset, Double>(synsets.get(i), 1.));
        }

        return d;
    }
}
