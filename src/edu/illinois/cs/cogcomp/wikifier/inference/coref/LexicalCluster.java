package edu.illinois.cs.cogcomp.wikifier.inference.coref;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


import com.google.common.collect.Lists;

import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.Index;

/**
 * Normalizes the surfaces of strings
 * @author cheng88
 *
 */
public class LexicalCluster extends HashMap<String, Set<Mention>> implements Iterable<Mention>{
    /**
         * 
         */
    private static final long serialVersionUID = -5590683413114781547L;

    public void put(Mention candidate) {
        if (candidate.topCandidate == null)
            put(candidate.surfaceForm, candidate);
        else
            put(candidate.topCandidate.titleName, candidate);
    }

    private void put(String s, Mention e) {
        String normalized = Index.stripNonAlphnumeric(s);
        Set<Mention> existingSet = get(normalized);
        existingSet.add(e);
    }

    public Set<Mention> get(String s) {
        if (!containsKey(s)) {
            put(s, new HashSet<Mention>());
        }
        return super.get(s);
    }
    
    public List<Mention> entities(){
        List<Mention> iters = Lists.newArrayListWithExpectedSize(size());
        for(Set<Mention> cluster:values()){
            iters.addAll(cluster);
        }
        return iters;
    }
    
    @Override
    public Iterator<Mention> iterator(){
        return entities().iterator();
    }

}
