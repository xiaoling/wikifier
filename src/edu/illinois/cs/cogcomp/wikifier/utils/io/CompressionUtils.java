package edu.illinois.cs.cogcomp.wikifier.utils.io;

import it.unimi.dsi.io.ByteBufferInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;

import edu.illinois.cs.cogcomp.wikifier.utils.Timer;

public class CompressionUtils {
    
//    private static final String in = "../Data/WikiDump/2013-05-28/extracted/enwiki-clean-redirect.txt";
//    private static final String out = "../Data/WikiData/Redirects/2013-05-28.redirect";
//    
//    @SuppressWarnings("serial")
//    public static final Map<String,String> filesToCompress = new HashMap<String,String>(){{
//        put(in,out);
//    }};
    
    
    public static InputStream mmapedStream(String file) throws IOException{
        return mmapedStream(new File(file));
    }
        
    @SuppressWarnings("resource")
    public static InputStream mmapedStream(File f) throws IOException{
        FileInputStream fis = new FileInputStream(f);
        return ByteBufferInputStream.map(fis.getChannel(), MapMode.READ_ONLY);
    }
    
    public static List<String> readSnappyCompressedLines(String file) throws IOException{
        FileInputStream fis = new FileInputStream(file);
        ByteBufferInputStream is = ByteBufferInputStream.map(fis.getChannel(), MapMode.READ_ONLY);
        SnappyInputStream ss = new SnappyInputStream(is);
        List<String> lines = IOUtils.readLines(ss);
        fis.close();
        return lines;
    }
    
    @SuppressWarnings("resource")
    public static InputStream readSnappyCompressed(String file) throws IOException{
        FileInputStream fis = new FileInputStream(file);
        ByteBufferInputStream is = ByteBufferInputStream.map(fis.getChannel(), MapMode.READ_ONLY);
        InputStream ss = new SnappyInputStream(is);
        return ss;
    }

    public static void writeSnappyCompressed(String src,String dest) throws IOException{
        FileOutputStream fo = new FileOutputStream(dest);
        SnappyOutputStream sos = new SnappyOutputStream(fo);
        FileInputStream fis = new FileInputStream(src);
        ByteBufferInputStream is = ByteBufferInputStream.map(fis.getChannel(), MapMode.READ_ONLY);
        IOUtils.copy(is, sos);
        fis.close();
        sos.close();
    }
    
//    protected static void timeTest(){
//        Timer snappy = new Timer("Snappy"){
//            @Override
//            public void run() throws IOException {
//                readSnappyCompressedLines(out).get(0);
//            }
//        };
//        snappy.timedRun(10);
//        
//        Timer snappystream = new Timer("Snappy Stream"){
//            @Override
//            public void run() throws IOException {
//                LineIterator it = IOUtils.lineIterator(readSnappyCompressed(out), StandardCharsets.UTF_8);
//                while(it.hasNext()){
//                    it.nextLine();
//                }
//            }
//        };
//        snappystream.timedRun(10);
//
//        Timer rawRead = new Timer("Raw"){
//            @Override
//            public void run() throws Exception {
//                IOUtils.readLines(new FileInputStream(in)).get(0);
//            }
//            
//        };
//        rawRead.timedRun(10);
//    }
//
//    protected static void compress() throws IOException{
//
//        for(String src:filesToCompress.keySet()){
//            writeSnappyCompressed(src,filesToCompress.get(src));
//        }
//    }
    
    public static void main(String[] args) throws IOException{
//        timeTest();
//        compress();
    }

}
