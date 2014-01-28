package edu.illinois.cs.cogcomp.wikifier.models;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;


@XmlAccessorType(XmlAccessType.FIELD)
public abstract class TextSpan implements Serializable{
    
    /**
     * 
     */
    private static final long serialVersionUID = 5101286571585149457L;
    public String surfaceForm;
    public int charStart;
    public int charLength;
    
    // the text is passed for debugging purposes only
    public static String getPositionHashKey(Constituent c) throws Exception{
        return getPositionHashKey(c.getStartCharOffset(), c.getEndCharOffset()  - c.getStartCharOffset());
    }
    
    public String getPositionHashKey(){
        return getPositionHashKey(charStart, charLength);
    }
    
    public static String getPositionHashKey(int start,int len){
        return start+"-"+(start+len);
    }

}
