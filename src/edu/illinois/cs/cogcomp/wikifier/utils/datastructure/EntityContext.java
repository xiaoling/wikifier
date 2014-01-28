package edu.illinois.cs.cogcomp.wikifier.utils.datastructure;

import java.util.List;

import edu.illinois.cs.cogcomp.wikifier.models.Mention;


public class EntityContext {
    
    private List<Mention> components;
    
    public EntityContext(List<Mention> components){
        this.components = components;
    }
    
    public List<Mention> getContext(int pos,int charWindow){
        int start = pos;
        int end = pos;
        int center = components.get(pos).charStart;
        for(int i=pos-1;i>=0;i--){
            if(center - components.get(i).charStart <charWindow)
                start = i;
            else
                break;
        }
        for(int i=pos+1;i<components.size();i++){
            if(components.get(i).charStart-center<charWindow)
                end = i;
            else
                break;
        }
        return components.subList(start, end+1);
    }

}
