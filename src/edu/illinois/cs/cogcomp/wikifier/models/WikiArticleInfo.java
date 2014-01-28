package edu.illinois.cs.cogcomp.wikifier.models;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.illinois.cs.cogcomp.wikifier.utils.XmlModel;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.ArraySet;
import edu.illinois.cs.cogcomp.wikifier.utils.io.CSVAdapter.CSVSetAdapter;
import edu.illinois.cs.cogcomp.wikifier.utils.io.CompressionUtils;
import edu.illinois.cs.cogcomp.wikifier.utils.io.Serializer;

@XmlRootElement(name = "wikiArticleInfo")
@XmlAccessorType(XmlAccessType.FIELD)
public class WikiArticleInfo implements Iterable<WikiArticleInfo.Entry>{

    @XmlElement(name="article")
    public List<Entry> entries = new ArrayList<>();
    
    public static enum Gender{
        m,f,unknown,nonhuman
    }
    
    public static enum CoarseEntityType {
        PER, ORG, FILM, FACILITY, LOC, SONG, IDEA, MONEY,
        EVENT, MISC, ARTWORK, MAN_MADE_OBJECT, MEDICAL,
        FLORAFAUNA, DISEASE, PRODUCT, GPE, DRINKFOOD
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Entry{
        @XmlAttribute
        public int tid;
        @XmlAttribute
        public String title;
        @XmlAttribute
        public int prevalence = 0;
        @XmlAttribute
        public Gender gender = Gender.nonhuman;
        @XmlJavaTypeAdapter(CSVSetAdapter.class)
        public Set<String> nationalities = new HashSet<>();
        @XmlJavaTypeAdapter(CSVSetAdapter.class)
        public Set<String> coarseCategories = new HashSet<>();
        @XmlJavaTypeAdapter(CSVSetAdapter.class)
        public Set<String> categories = new HashSet<>();
        @Override
        public String toString() {
            return "Entry [tid=" + tid + ", \ntitle=" + title + ", \nprevalence=" + prevalence + ", \ngender=" + gender
                    + ", \nnationalities=" + nationalities + ", \ncoarseCategories=" + coarseCategories + ", \ncategories=" + categories
                    + "]";
        }

    }
 
    @SuppressWarnings("unchecked")
    public static WikiArticleInfo legacyToXml(String fileName) throws IOException {
        WikiArticleInfo result = new WikiArticleInfo();
        InputStream is = CompressionUtils.mmapedStream(fileName);
        LineIterator it = IOUtils.lineIterator(is, StandardCharsets.UTF_8);
        while (it.hasNext()) {
            Entry e = new Entry();
            String line = it.next();
            e.title = StringUtils.substringBetween(line, "title=", "\t").replace(' ', '_');
            e.tid = Integer.parseInt(StringUtils.substringBetween(line, "tid=", "\t"));
            e.prevalence = Integer.parseInt(StringUtils.substringBetween(line, "prevalence=", "\t"));
            e.gender = Gender.valueOf(StringUtils.substringAfter(line, "gender="));
            line = it.next();
            e.nationalities = line.endsWith(":\t")? Collections.EMPTY_SET:ArraySet.create(line.split(":\\s+")[1].split("\t"));
            line = it.next();
            e.coarseCategories = line.endsWith(":\t")? Collections.EMPTY_SET:ArraySet.create(line.split(":\\s+")[1].split("\t"));
            line = it.next();
            e.categories = line.endsWith(":\t")? Collections.EMPTY_SET:ArraySet.create(line.split(":\\s+")[1].split("\t"));
            result.entries.add(e);
            it.next();// skip an empty line
        }
        it.close();
        return result;
    }
    
    
    
    public static WikiArticleInfo defaultInstance(){
        return XmlModel.load(WikiArticleInfo.class, "data/Lucene4Index/TitleSummaryDataReplenished.xml");
    }
    


    @Override
    public Iterator<Entry> iterator() {
        return entries.iterator();
    }
}
