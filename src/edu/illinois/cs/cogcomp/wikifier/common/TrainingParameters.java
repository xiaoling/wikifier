package edu.illinois.cs.cogcomp.wikifier.common;

import edu.illinois.cs.cogcomp.wikifier.utils.learn.LearningProblemBuilder;

public class TrainingParameters {
    
    // Inference Training Section
    public static boolean trainRelationLinker = false;
    public static boolean trainRelationRescale = false;
    public static boolean trainCoref = false;
    public static LearningProblemBuilder problemBuilder = null;
    //---------------
    public static boolean generateFeaturesConjunctions = false;

}
