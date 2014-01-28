package edu.illinois.cs.cogcomp.wikifier.inference.coref;

import java.util.Collections;
import java.util.HashSet;

import edu.illinois.cs.cogcomp.wikifier.models.Mention;


//Reducing bracket-madness in code
public class EntityCluster extends HashSet<Mention>{
    
    private Mention head = null;
    
    public static final EntityCluster emptyCluster = new EntityCluster();
    static{
        Collections.unmodifiableSet(emptyCluster);
    }
    
    public EntityCluster() {
        
    }
    
    public EntityCluster(Mention head){
        this.head = head;
        if(head!=null)
            add(head);
    }

    /**
     * 
     */
    private static final long serialVersionUID = 3086077878738783360L;

    public Mention getHead() {
        return head;
    }

    public void setHead(Mention head) {
        this.head = head;
    }

}