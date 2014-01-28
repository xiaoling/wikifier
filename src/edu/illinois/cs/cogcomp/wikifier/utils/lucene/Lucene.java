package edu.illinois.cs.cogcomp.wikifier.utils.lucene;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

/**
 * Lucene interface
 * @author cheng88
 *
 */
public class Lucene {

    private static final IndexWriterConfig storeConfig = new IndexWriterConfig(Version.LUCENE_43, new KeywordAnalyzer());
        
    public static IndexWriter writer(String pathToIndexDir,IndexWriterConfig config) throws IOException{
        return new IndexWriter(new MMapDirectory(new File(pathToIndexDir)),config);
    }
    
    public static IndexWriter storeOnlyWriter(String pathToIndexDir) throws IOException{
        return new IndexWriter(new MMapDirectory(new File(pathToIndexDir)),storeConfig);
    }
    
    public static IndexReader ramReader(String pathToIndex) throws IOException{
        return DirectoryReader.open(new RAMDirectory(new MMapDirectory(new File(pathToIndex)),IOContext.READ));
    }
    
    public static IndexReader reader(String dir,String... children) throws IOException{
        return reader(Paths.get(dir, children).toString());
    }
    
    public static IndexReader reader(String pathToIndex) throws IOException{
        return DirectoryReader.open(new MMapDirectory(new File(pathToIndex)));
    }
    
    public static IndexSearcher searcher(String dir,String... children) throws IOException{
        return searcher(Paths.get(dir, children).toString());
    }
    
    public static IndexSearcher searcher(String path) throws IOException{
        return new IndexSearcher(reader(path));
    }
}
