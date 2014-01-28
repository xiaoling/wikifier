package edu.illinois.cs.cogcomp.wikifier.utils.datastructure;

import gnu.trove.list.array.TIntArrayList;
import it.unimi.dsi.io.ByteBufferInputStream;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Simple fast implementation of a disk backed array with byte[] data type.
 * Index file is an array of n integers that represents data length.
 * When reading index, we construct the offset array of n+1 elements.
 * Assumption: byte array object size smaller than 2GB
 * @author cheng88
 *
 */
public class DiskArray implements Iterable<byte[]>{
    
    private static final String INDEX_FILE = "idx";
    private static final String ARRAY_FILE = "arr";
    
    private TIntArrayList starts = new TIntArrayList();
    private AsynchronousFileChannel fc = null;
    private MappedByteBuffer mbuf = null;
    private File directory;
    
    public static enum Mode{
        MMAP,DEFAULT
    }
    
    public DiskArray(final File dir,Mode mode) throws IOException{
        this(dir);
        switch(mode){
        case DEFAULT:
            fc = AsynchronousFileChannel.open(Paths.get(dir.getPath(),ARRAY_FILE),StandardOpenOption.READ);
            break;
        case MMAP:
            RandomAccessFile raf = new RandomAccessFile(new File(dir,ARRAY_FILE), "r");
            FileChannel fChannel = raf.getChannel();
            long fileSize = fChannel.size();
            if(fileSize > Integer.MAX_VALUE)
                throw new UnsupportedAddressTypeException();
            mbuf = fChannel.map(MapMode.READ_ONLY,0,fileSize);
            break;
        }
    }
    
    private DiskArray(final File dir) throws IOException{
        // Indexing
        File indexF = new File(dir, INDEX_FILE);
        FileInputStream in = new FileInputStream(indexF);
        FileChannel fc = in.getChannel();
        IntBuffer ib = fc.map(MapMode.READ_ONLY, 0, fc.size()).asIntBuffer();
        int lastStart = 0;
        starts.add(0);
        while(ib.hasRemaining()){
            int length = ib.get();
            lastStart += length;
            starts.add(lastStart);
        }
        starts.trimToSize();
        in.close();
        directory = dir;
    }
    
    public void loadToMemory(){
        if (mbuf != null && !mbuf.isLoaded()) {
            mbuf.load();
        }
    }
    
    /**
     * Get the byte representation of the ith element
     * @param idx
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public byte[] get(int idx) throws IOException, InterruptedException, ExecutionException{
        int nextStart = starts.getQuick(idx + 1);
        int start = starts.getQuick(idx);
        int len = (nextStart - start);
        byte[] ret = new byte[len];
        int readLength = 0;
        if (mbuf == null) {
            ByteBuffer buffer = ByteBuffer.wrap(ret);
            while (readLength < len) {
                Future<Integer> bytesRead = fc.read(buffer, start);
                readLength += bytesRead.get();
            }
        } else {
//            mbuf.position(start);
//            while (readLength < len) {
//                readLength += mbuf.read(ret);
//            }
            mbuf.position(start);
            mbuf.get(ret, (int)start, len);
        }
        return ret;
    }

    /**
     * Protobuffer interface for writing
     * @param file
     * @param messages
     * @throws IOException
     */
    public static void index(String dir,Iterator<byte[]> messages) throws IOException{
        BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(new File(dir,ARRAY_FILE)));
        DataOutputStream ids = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir,INDEX_FILE))));
        while(messages.hasNext()){
            byte[] data = messages.next();
            os.write(data);
            ids.writeInt(data.length);
        }
        os.close();
        ids.close();
    }

    @Override
    public Iterator<byte[]> iterator() {
        try {
            return new ByteArrayIterator(directory,starts);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Indexes the array in the compatible format
     * @author cheng88
     *
     */
    public static class ByteArrayIndexer implements Closeable{
        
        File dir;
        BufferedOutputStream os;
        DataOutputStream ids;
        public ByteArrayIndexer(String dir) throws FileNotFoundException{
            this.dir = new File(dir);
            this.dir.mkdirs();
            os = new BufferedOutputStream(new FileOutputStream(new File(dir,ARRAY_FILE)));
            ids = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir,INDEX_FILE))));
        }
        
        public void add(byte[] data) throws IOException{
            os.write(data);
            ids.writeInt(data.length);
        }

        @Override
        public void close() throws IOException {
            os.close();
            ids.close();
        }
        
    }
    
    private static class ByteArrayIterator implements Iterator<byte[]>{

        int pointer = 0;
        TIntArrayList offsets;
        DataInputStream in;
        RandomAccessFile raf;
        
        ByteArrayIterator(File dir,TIntArrayList offsets) throws IOException{
            raf = new RandomAccessFile(new File(dir,ARRAY_FILE), "r");
            in = new DataInputStream(ByteBufferInputStream.map(raf.getChannel(),MapMode.READ_ONLY));
            this.offsets = offsets;
        }
        
        @Override
        public boolean hasNext() {
            return pointer < offsets.size() - 2;
        }

        @Override
        public byte[] next() {
            int len = (offsets.get(pointer + 1) - offsets.get(pointer));
            byte[] ret = new byte[len];
            try {
                in.readFully(ret);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
            pointer++;
            return ret;
        }

        @Override
        public void remove() {}
        
        
        protected void finalize(){
            if(raf!=null)
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        
    }

}
