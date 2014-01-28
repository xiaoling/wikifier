package edu.illinois.cs.cogcomp.wikifier.utils.datastructure;

import gnu.trove.map.hash.TCustomHashMap;
import gnu.trove.set.hash.TCustomHashSet;
import gnu.trove.strategy.HashingStrategy;

import org.apache.commons.lang3.StringUtils;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * A much more compact map than the default hash map
 * @author cheng88
 *
 * @param <T>
 */
public class StringMap<T> extends TCustomHashMap<String,T>{
    
    public static final float DEFAULT_LOAD_FACTOR = 0.97f;
    private static final HashFunction defaultHashFunction = Hashing.murmur3_32();
    private static final HashingStrategy<String> stringHashStrat = new HashingStrategy<String>() {

        /**
         * 
         */
        private static final long serialVersionUID = -7000292520586925123L;

        @Override
        public int computeHashCode(String arg0) {
            return defaultHashFunction.hashString(arg0).asInt();
        }

        @Override
        public boolean equals(String arg0, String arg1) {
            return StringUtils.equals(arg0, arg1);
        }
        
    };

    public StringMap(){
        super(stringHashStrat,10,DEFAULT_LOAD_FACTOR);
    }
    
    public StringMap(int capacity){
        super(stringHashStrat,(int) Math.floor(capacity/DEFAULT_LOAD_FACTOR),DEFAULT_LOAD_FACTOR);
    }
    
    public static class StringSet extends TCustomHashSet<String>{
        public StringSet(int capacity){
            super(stringHashStrat,(int) Math.floor(capacity/DEFAULT_LOAD_FACTOR),DEFAULT_LOAD_FACTOR);
        }
    }

}
