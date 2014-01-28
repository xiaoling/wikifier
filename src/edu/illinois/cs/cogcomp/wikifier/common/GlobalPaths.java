package edu.illinois.cs.cogcomp.wikifier.common;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * Holds all the important data paths. Some of the paths in the previous version becomes hard-coded
 * relative paths to reduce the configuration complexity
 * 
 * @author cheng88
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class GlobalPaths {

    public String compressedRedirects;
    public String protobufferAccessDir;
    public String curatorCache;
    public String wikiRelationIndexDir;
    public String models;
    public String titleStringIndex;
    public String wordnetConfig;
    public String stopwords;
    public String wordNetDictionaryPath;
    public String nerConfig;
    public String wikiSummary;
    public String neSimPath;
    
    public static final GlobalPaths defaultInstance() {
        return new GlobalPaths() {
            {
                compressedRedirects = "data/WikiData/Redirects/2013-05-28.redirect";
                protobufferAccessDir = "data/Lucene4Index/";
                curatorCache = "data/TextAnnotationCache/";
                wikiRelationIndexDir = "data/WikiData/Index/WikiRelation/";
                models = "data/Models/TitleMatchPlusLexicalPlusCoherence/";
                titleStringIndex = "data/WikiData/Index/TitleAndRedirects/";
                wordnetConfig = "configs/jwnl_properties.xml";
                stopwords = "data/OtherData/stopwords_big";
                wordNetDictionaryPath = "data/WordNet/";
                nerConfig = "configs/NER.config";
                wikiSummary = null;
                neSimPath = "data/NESimdata/config.txt";
            }
        };
    }

}
