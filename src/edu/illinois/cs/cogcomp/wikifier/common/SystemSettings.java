package edu.illinois.cs.cogcomp.wikifier.common;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import edu.illinois.cs.cogcomp.edison.sentences.ViewNames;
import edu.illinois.cs.cogcomp.wikifier.annotation.CachingCurator;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.FileBasedWikiAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.MapDBAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.MongoDBWikiAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.ProtobufferBasedWikipediaAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;

@XmlAccessorType(XmlAccessType.FIELD)
public class SystemSettings {

    public static enum WikiAccessType {
        FileBased {

            @Override
            public WikiAccess getAccess(String wikiSummary, String path) throws Exception {
                return new FileBasedWikiAccess(wikiSummary, path);
            }

        },
        Lucene {

            @Override
            public WikiAccess getAccess(String wikiSummary, String path) throws Exception {
                return new ProtobufferBasedWikipediaAccess(path);
            }

        },
        Chronicle {

            @Override
            public WikiAccess getAccess(String wikiSummary, String path) throws Exception {
                // return new ChronicleBasedWikiAccess(path);
                System.err.println("Chronicle based wiki access not supported yet");
                System.exit(0);
                return null;
            }

        },
        MongoDB {
            @Override
            public WikiAccess getAccess(String wikiSummary, String path) throws Exception {
                return new MongoDBWikiAccess(wikiSummary);
            }

        },
        MapDB {

            @Override
            public WikiAccess getAccess(String wikiSummary, String path) throws Exception {
                return new MapDBAccess(path);
            }
            
        };
        public abstract WikiAccess getAccess(String wikiSummary, String path) throws Exception;
    }



    // Curator related settings
    public String curatorURL;
    public Integer curatorPort;
    public boolean annotateNER;
    public boolean annotateCOREF;
    public boolean annotateSHALLOW_PARSE;
    public String featureExtractionThreadCount;

    // Wiki access related
    public WikiAccessType wikiAccessProvider;
    public boolean bypassCurator;

    public CachingCurator createCurator(String cachePath, String nerConfigFile) throws Exception {
        Set<String> views = getAnnotationViews();
        if (bypassCurator) {
            System.out.println("----------------->Bypassing the curator!");
            return new CachingCurator(getAnnotationViews(), cachePath, nerConfigFile);
        } else {
            return new CachingCurator(curatorURL, curatorPort, views, cachePath);
        }
    }

    public Set<String> getAnnotationViews() {
        Set<String> views = new HashSet<String>();
        if (annotateNER)
            views.add(ViewNames.NER);
        if (annotateSHALLOW_PARSE)
            views.add(ViewNames.SHALLOW_PARSE);
        if (annotateCOREF)
            views.add(ViewNames.COREF);
        return views;
    }

    public static final SystemSettings defaultInstance() {
        return new SystemSettings() {
            {
                curatorURL = "trollope.cs.illinois.edu";
                curatorPort = 9010;
                wikiAccessProvider = WikiAccessType.Lucene;
                bypassCurator = true;

                annotateNER = true;
                annotateSHALLOW_PARSE = true;
                annotateCOREF = false;
                featureExtractionThreadCount = "AUTO";
            }
        };
    }

}
