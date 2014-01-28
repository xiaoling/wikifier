package edu.illinois.cs.cogcomp.wikifier.inference.coref;

import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import gnu.trove.set.hash.TCustomHashSet;
import gnu.trove.strategy.IdentityHashingStrategy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;


/**
 * A disjoint set structure of entities
 * @author cheng88
 *
 */
public class CorefClusters extends HashMap<Mention, EntityCluster> implements Iterable<EntityCluster>{


    // all coref relations of the same surface form, as they should refer to the same thing

    /**
     * 
     */
    private static final long serialVersionUID = 3743428902053778230L;

    void addCluster(EntityCluster cluster) {
        EntityCluster mergedCluster = new EntityCluster();
        for (Mention e : cluster) {
            EntityCluster existingCluster = get(e);
            if (existingCluster != null)
                mergedCluster.addAll(existingCluster);
            else
                mergedCluster.add(e);
        }
        for(Mention e: mergedCluster){
            put(e,mergedCluster);
        }
    }

    @Override
    public Iterator<EntityCluster> iterator() {
        return new IdentitySet(values()).iterator();
    }
    
    /**
     * Note that null clusters are considered disjoint
     * @param e1
     * @param e2
     * @return
     */
    public boolean sameCluster(Mention e1,Mention e2){
        EntityCluster c1 = get(e1);
        return c1!=null && c1==get(e2);
    }
    
    private static class IdentitySet extends TCustomHashSet<EntityCluster>{
        public IdentitySet(Collection<? extends EntityCluster> ini){
            super(new IdentityHashingStrategy<EntityCluster>(),ini);
        }
    }

}
