package edu.illinois.cs.cogcomp.wikifier.utils.lucene;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.spell.JaroWinklerDistance;
import org.apache.lucene.search.spell.LevensteinDistance;


import com.aliasi.spell.TokenizedDistance;
import com.aliasi.tokenizer.RegExTokenizerFactory;
import com.google.common.collect.Sets;

import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.Index;


public class CustomEditDistance extends TokenizedDistance{
    
    JaroWinklerDistance metric = new JaroWinklerDistance();
    LevensteinDistance lemtric = new LevensteinDistance();

    public CustomEditDistance() {
        super(new RegExTokenizerFactory("[^_,.]+"));
    }

    @Override
    public double distance(CharSequence arg0, CharSequence arg1) {
        return 1-proximity(arg0, arg1);
    }
    
    @Override
    public double proximity(CharSequence arg0, CharSequence arg1) {
//        String s0 = arg0.toString();
//        String s1 = arg1.toString();
//        double sim1 = metric.getDistance(s0, s1);
//        double reverseSim = metric.getDistance(StringUtils.reverse(s0), StringUtils.reverse(s1));
//        return Math.min(sim1, reverseSim);

        if(arg0.equals(arg1))
            return 1;
        
        Set<String> tokens0 = Index.getTokenSet(arg0);
        Set<String> tokens1 = Index.getTokenSet(arg1);
        
        Set<String> intersection = Sets.newHashSet(tokens0);
        intersection.retainAll(tokens1);

        tokens0.removeAll(intersection);
        tokens1.removeAll(intersection);
        
        String prefix = StringUtils.join(intersection, ' ');
       
        List<String> arg0LeftOver = new ArrayList<String>(tokens0);
        List<String> arg1LeftOver = new ArrayList<String>(tokens1);
        String leftString0 = prefix + ' '+ StringUtils.join(arg0LeftOver,' ');
        String leftString1 = prefix + ' '+ StringUtils.join(arg1LeftOver,' ');
        
//        if(tokens0.size()!=1 || tokens1.size()!=1){
//        }
//        
//        String arg0leftOver = tokens0.iterator().next();
//        String arg1leftOver = tokens1.iterator().next();
//        
//        return (arg0len - 1 + tokenLevelSim(arg0leftOver, arg1leftOver)) / arg0len;

        return tokenLevelSim(leftString0,leftString1);
    }
    
    public double tokenLevelSim(CharSequence s1,CharSequence s2){
//        double ldis = lemtric.getDistance(s1, s2);
        double jaro = metric.getDistance(s1.toString(), s2.toString());
//        return Math.max(ldis,jaro);
        return jaro;
    }
    
    public static void main(String[] args){
        System.out.println(new CustomEditDistance().proximity("Goteborg University", "IT University"));
    }
//    
}
//
//public static class CustomEditDistance extends WeightedEditDistance{
//
//    @Override
//    public double deleteWeight(char arg0) {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double insertWeight(char arg0) {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double matchWeight(char arg0) {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double substituteWeight(char arg0, char arg1) {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public double transposeWeight(char arg0, char arg1) {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//   
//
//}
