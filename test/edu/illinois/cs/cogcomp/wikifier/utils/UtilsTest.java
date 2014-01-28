package edu.illinois.cs.cogcomp.wikifier.utils;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Window;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.WindowList;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Window.Neighbor;



public class UtilsTest {

	@Test
	public void windowIteratorTest() {
		List<Integer> testQueue = Arrays.asList(1,2,3,4,5);
		
		// basic window
		Window<Integer> window = new Window<Integer>(testQueue,2,1);

		
		List<Neighbor<Integer>> windowList = Arrays.asList(new Neighbor<Integer>(2,1),new Neighbor<Integer>(4,1));
		assertEquals(3,window.getCenter().intValue());
		assertEquals(windowList,window.getNeigbors());
		
		
		//corner cases min
		Window<Integer> window2 = new Window<Integer>(testQueue,0,2);
	
		List<Neighbor<Integer>> windowList2 = Arrays.asList(new Neighbor<Integer>(2,1),new Neighbor<Integer>(3,2));
		
		assertEquals(1,window2.getCenter().intValue());
		assertEquals(windowList2,window2.getNeigbors());
		
		//corner cases max
		Window<Integer> window3 = new Window<Integer>(testQueue,4,3);
		List<Neighbor<Integer>> windowList3 = Arrays.asList(
				new Neighbor<Integer>(2,3),
				new Neighbor<Integer>(3,2),
				new Neighbor<Integer>(4,1)
				);
		
		
		assertEquals(5,window3.getCenter().intValue());
		assertEquals(windowList3,window3.getNeigbors());
		
		//right neighbor
		Window<Integer> sampleWindow = new Window<Integer>(testQueue, 3, 1);
		assertEquals(5, sampleWindow.getRightNeigbors().get(0).object.intValue());
	}

	
	@Test
	public void windowListTest(){
		List<Integer> list = Arrays.asList(1,2,3,4,5);
		WindowList<Integer> wlist = new WindowList<Integer>(2, list);
		int count = 1;
		for(Window<Integer> window:wlist){
			assertEquals(count,window.getCenter().intValue());
			count++;
		}
		assertEquals(count,6);
	}
	
	@Test
	public void comparatorTest(){
		Mention w1 = new Mention(); w1.startTokenId = 1; w1.endTokenId = 2;
		Mention w2 = new Mention(); w2.startTokenId = 3; w2.endTokenId = 6;
		List<Mention> testList = Arrays.asList(w1,w2);
		Collections.sort(testList,Comparators.longerEntityFirst);
		assertEquals(Arrays.asList(w2,w1),testList);
	}

}
