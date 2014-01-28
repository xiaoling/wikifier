package edu.illinois.cs.cogcomp.wikifier.utils.datastructure;

import java.util.Arrays;
import java.util.HashSet;

public class ArraySet {
    public static <T> HashSet<T> create(T[] arr){
        return new HashSet<T>(Arrays.asList(arr));
    }
}
