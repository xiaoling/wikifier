package edu.illinois.cs.cogcomp.wikifier.utils;

import java.util.List;
import java.util.Random;

import com.google.common.collect.Lists;

public class RandomSampleFromStream {
    private Random rand = new Random(13);

    public <T> List<T> sample(List<T> input, int maxSampleSize) {
        List<T> res = Lists.newArrayList();
        for (int i = 0; i < input.size(); i++) {
            if (res.size() < maxSampleSize)
                res.add(input.get(i));
            else {
                double probabilityToSelect = 1.0 / (i + 1);
                if (rand.nextDouble() < probabilityToSelect) {
                    int pos = rand.nextInt(res.size());
                    res.remove(pos);
                    res.add(input.get(i));
                }
            }
        }
        return res;
    }
}
