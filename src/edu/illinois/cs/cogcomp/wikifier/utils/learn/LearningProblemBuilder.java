package edu.illinois.cs.cogcomp.wikifier.utils.learn;

import java.util.ArrayList;
import java.util.List;

import liblinear.FeatureNode;
import liblinear.Problem;

import com.google.common.primitives.Ints;

import edu.illinois.cs.cogcomp.wikifier.inference.LiblinearInterface;

public class LearningProblemBuilder {

    List<FeatureNode[]> vectors = new ArrayList<FeatureNode[]>();
    List<Integer> labels = new ArrayList<Integer>();
        
    public void addInstance(double[] features,boolean label){
        addInstance(features, label?1:-1);
    }
    
    public void addInstance(double[] features,int label){
        vectors.add(LiblinearInterface.featureVectorToSvmFormat(features));
        labels.add(label);
    }
    
    public Problem getProblem(){
        Problem prob = new Problem();
        prob.l = labels.size();
        prob.y = Ints.toArray(labels);
        prob.x = vectors.toArray(new FeatureNode[prob.l][]);
        prob.n = prob.x[0].length+1;
        prob.bias = 0;
        return prob;
    }
 
    
}
