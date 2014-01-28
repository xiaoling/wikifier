package edu.illinois.cs.cogcomp.wikifier.models;

import java.io.Serializable;
import java.util.List;

public class ReferenceProblem implements Serializable{
    
    /**
     * 
     */
    private static final long serialVersionUID = 8663010068161144851L;
    public ReferenceProblem(String text, List<ReferenceInstance> instances) {
        this.text = text;
        this.instances = instances;
    }
    
    public final String text;
    public final List<ReferenceInstance> instances;
    

}
