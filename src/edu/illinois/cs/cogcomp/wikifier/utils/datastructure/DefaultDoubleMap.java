package edu.illinois.cs.cogcomp.wikifier.utils.datastructure;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class DefaultDoubleMap<K> extends HashMap<K,Double> {
    private static final long serialVersionUID = 1L;
    
    public Double get(Object key){
        Double retval = super.get(key);
        if(retval == null)
            return 0.;
        return retval;
    }
    
    public void add(K key,double d){
        put(key,get(key)+d);
    }
    
    public Map.Entry<K,Double> max(){
        if(size()==0)
            return null;
        return Collections.max(entrySet(),vComparator);
    }
    
    private static final Comparator<Map.Entry<? extends Object,Double>> vComparator = 
            
            new Comparator<Map.Entry<? extends Object,Double>>(){

                @Override
                public int compare(Map.Entry<? extends Object, Double> o1, Map.Entry<? extends Object, Double> o2) {
                    return Double.compare(o1.getValue(), o2.getValue());
                }
            };

}
