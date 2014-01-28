package edu.illinois.cs.cogcomp.wikifier.utils;

import static org.junit.Assert.*;
import edu.illinois.cs.cogcomp.wikifier.utils.Timer;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Iterator;

import org.apache.lucene.document.Document;
import org.junit.Test;


import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
/**12257 collisions out of 10215407 total urls mumur_32
 * 12096 collisions out of 10215407 total urls crc_32
 * 12274 collisions out of 10215407 total urls md5 in 125187 ms
 * 12148 collisions out of 10215407 total urls sha512 131674 ms
 * @author cheng88
 *
 */
public class HashingTest {
    

    
    public static void collisionTest(){
        
        Timer timer = new Timer("Collision Test"){

            @Override
            public void run() {
                
                HashFunction murmur = Hashing.murmur3_128(13);
                
                Iterator<Document> docs = TitleNameIndexer.getDocumentIterator();
                TIntObjectHashMap<String> hashCodes = new TIntObjectHashMap<String>(10215407);
                int total = 0;
                while(docs.hasNext()){
                    String url = docs.next().get(TitleNameIndexer.Fields.URL.name());
                    
                    int newHashCode = murmur.hashString(url).asInt();
                    
                    if(hashCodes.contains(newHashCode) && Math.random()<0.01){
                        System.out.println("Example collision: "+url+" and "+hashCodes.get(newHashCode));
                    }
                    hashCodes.put(newHashCode,url);
                    total++;
                    if(total%1000000==0){
                        System.out.printf("Hashed %d urls with %d collisions\n",total,total-hashCodes.size());
                    }
                }
                int collisions = total-hashCodes.size();
                System.out.printf("%d collisions out of %d total urls\n",collisions,total);
                double loadFactorUpper = 1-(double)collisions/total;
                System.out.printf("Load factor upperbound is %f",loadFactorUpper);
                assertTrue(loadFactorUpper>0.98);
                
            }
            
        };
        timer.timedRun();
    }
    
    /**
     * DJB hash
     * @param s
     * @return
     */
    static int hashString(String s){
        int seed = 5381;
        for(int i = 0;i<s.length();i++){
           char c =  s.charAt(i);
           seed = ((seed << 5) + seed) + c;
        }
        return seed;
    }
   
}
