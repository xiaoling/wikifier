package edu.illinois.cs.cogcomp.wikifier.inference.coref;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;


import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;

public class CorefElection {
    
    static final Logger logger = Logger.getLogger(CorefElection.class);
    
    Map<String, Double> primaryVotes = Maps.newHashMap();
    Map<String, Double> secondaryVotes = Maps.newHashMap();
    List<Mention> candidates = Lists.newArrayList();;
    
    public CorefElection(Iterable<Mention> es){
        for(Mention e:es){
            candidates.add(e);
        }
        // Longer entities vote first, overrides short nulls
        Collections.sort(candidates,Comparators.longerEntityFirst);
        voteAll(candidates);
    }
    
    public List<Mention> getSortedEntities(){
        return candidates;
    }
    
    private void voteAll(List<Mention> candidates){
        for(Mention e:candidates){
            vote(e);
        }
    }

    private void vote(Mention e){
        Map<String, Double> box = e.surfaceForm.contains(" ") ? primaryVotes : secondaryVotes;
        // Voting null
        if (e.finalCandidate == null && (box == primaryVotes || primaryVotes.isEmpty())) {
            if (e.topCandidate == null)
                vote(null, 1, box);
            else
                vote(null, e.topCandidate.rankerScore, box);
        } else {
            for (WikiCandidate dc : e.getRerankCandidates()) {
                if (box == primaryVotes) {
                    vote(dc.titleName, dc.rankerScore, box);
                }
                if (box == secondaryVotes && primaryVotes.containsKey(dc.titleName)) {
                    vote(dc.titleName, 1, box);
                    logger.info("Promoting " + dc.titleName + " due to a longer mention than " 
                    + e.surfaceForm + " that referred to the same thing");
                }
            }
        }
    }
    
    private static void vote(String candidate,double score,Map<String,Double> votes){
        double previous = votes.containsKey(candidate)? votes.get(candidate) : 0.0;
        votes.put(candidate, previous + score);
    }
    
    public String elect(){
        // Consolidate votes
        for (String s : secondaryVotes.keySet()) {
            if (s == null && !primaryVotes.containsKey(null)) {
                primaryVotes.put(null, 0.0);
            }
            primaryVotes.put(s, primaryVotes.get(s) + secondaryVotes.get(s));
        }
        if (primaryVotes.isEmpty())
            return null;
        // Count votes
        String bestCandidate = Collections.max(primaryVotes.keySet(), new Comparators.MapValueComparator(primaryVotes));
        return bestCandidate;
    }

}
