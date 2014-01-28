package edu.illinois.cs.cogcomp.wikifier.models.extractors;

import static edu.illinois.cs.cogcomp.wikifier.models.extractors.MentionExtractor.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.illinois.cs.cogcomp.wikifier.utils.Timer;

public class RegexTest {

    @Test
    public void connectiveTest() {
        assertTrue("Al-Quaeda".matches(capitalizedWord));
        assertTrue("and".matches(connective));
        assertTrue("the".matches(connective));
        assertTrue("and the".matches(atMost2Connectives));
//        Matcher m = Pattern.compile(atMost2Connectives).matcher("and the");
//        while(m.find()){
//            System.out.println(m.group());
//        }
        String replaced = "Washington, D.C.".replaceAll(topLevelEntity, "X");
        assertTrue(replaced.equals("X, X"));
    }
    
    static final boolean debug = false;
    static final String[] tests = new String[] {
            "\n" + "Built by gskinner.com with Flex 3 in the "
                    + "with Youth and Sports Bureau other famous President of \tthe United Kingdom [adobe.com/go/flex] "
                    + "and Spelling Plus Library for text highlighting at the Christies' [gskinner.com/products/spl].Washington, D.C."
                    + " and our next test entity is Secretary of Commerce features" + "the character Barnes & Noble aaaa ",
            "played by John Corbett on ``Sex and the City,\"\"; Aidan Shaw,",
            "aments only each calendar year. The players who won the most money (without having already made it "
                    + "automatically) were awarded qualifying places at the   World Matchplay  ,   World Grand Prix   and   World Championship  ."
                    + " Previously, a sudden-death knockout qualifying tournament decided the players which meant players "
                    + "could miss out on tournaments because of unlucky matches or unlucky draws.",
            "Mr. Milosevic's Socialist Party and its allies won about 1/5th of the vote.",
            " Suns and Shaq As close as the western conference is just think if the suns start"
                    + " loosing and miss the playoffs. The fans would start booing Shaq. I wonder if that "
                    + "would make him retire. I have a feeling the fan's could start throwing hand grenades..."
                    + "Shaq is not gonna walk away from 40mil....It could get ugly. The way Kupchak has been "
                    + "dealing lately, maybe if we wait a while we can trade Mihm and a third round pick for "
                    + "Shaq and Nash. BD news:yYZrj.15$KO4.11@newsfe05.lga ... news:yYZrj.15$KO4.11@newsfe05.lga"
                    + " ... Nah... there are still 27 more teams he hasn't played for... -Sac D- news:9oKdnX256b"
                    + "ta7i3anZ2dnUVZ_vumnZ2d@comcast.com ... 26 brink Naw, Shaq would just pull the Injury Card "
                    + "as sit out the season. \"aluckyguess\" <n ... @me.com> wrote in news:yYZrj.15$KO4.11@newsfe"
                    + "05.lga: and leave $40 mil on the table...not snaq...",
            "Cohen, who owns Atlantic Capes Fisheries in Cape May, New Jersey. Fishing company owners have put up the money for the project's development stage.",
            "Washington, D.C.",
            "the Michigan Department of Natural Resources &UR;",
            " Finsceal Beo wins 1,000 Guineas Newmarket, England 2007-05-06 15:04:59 UTC Finsceal Beo won the 1,000 Guineas at Newmarket on Sunday, beating Arch Swing by 2 1/2 lengths. The 5-4 favorite, trained by Jim Bolger and ridden by Kevin Manning, hit the lead two furlongs out and was never headed. Simply Perfect was third in the 1-mile (1.61 kilometer) race for 3-year-old fillies. Sander Camillo, which was rated at 5-1, withdrew from the race because she is in season. \"It's a pretty good feeling and I'm delighted for everybody concerned,\" Bolger said. \"I've put so much work into this filly and into all the horses in the yard. This one is down to my staff and the owner, Michael Ryan.\" Another of Bolger's horses, Teofilo, withdrew from the 2,000 Guineas on Saturday with injury. That race -- for 3-year-old colts and fillies -- was won by Cockney Rebel. \n" + 
            "",
            "from a backcourt of Vujacic and Jordan Farmar, with Bryant on the wing"};
    @Test
    public void test() {


        
        final List<String> extractions = Lists.newArrayList();
        
        Timer timer = new Timer("Regex Test") {
            @Override
            public void run() {
                for (int d = 0; d < 1; d++) {
                    for (String test : tests) {
                        Matcher matcher = superEntityPattern.matcher(test);

                        while (matcher.find()) {
                            if (debug) {
                                for (int i = 0; i < matcher.groupCount(); i++)
                                    System.out.println(i + ":" + matcher.group(i));
                                System.out.println("-----------");
                            } 
                            int startChar = matcher.start(1);
                            int endChar = matcher.end(1);
                            String match = test.substring(startChar, endChar);
                            String matchclean = match.replaceAll("\\s+", " ");
                            extractions.add(matchclean);
                            matcher.region(startChar+1, test.length());
                        }
                    }
                }
            }
        };

        timer.timedRun();
        assertTrue(extractions.contains("World Grand Prix and World Championship"));
        assertTrue(extractions.contains("President of the United Kingdom"));

        assertTrue(extractions.contains("Sex and the City"));
        assertTrue(extractions.contains("John Corbett on ``Sex and the City"));
        assertTrue(extractions.contains("Corbett on ``Sex and the City"));
        assertTrue(extractions.contains("Newmarket, England"));
        assertTrue(extractions.contains("Youth and Sports Bureau"));
        assertTrue(extractions.contains("Secretary of Commerce"));
        assertTrue(extractions.contains("Barnes & Noble"));
        assertTrue(extractions.contains("Milosevic's Socialist Party"));
        assertTrue(extractions.contains("Cape May, New Jersey"));
        assertTrue(extractions.contains("Washington, D.C."));
        assertTrue(extractions.contains("Michigan Department of Natural Resources"));
        assertTrue(extractions.contains("Jordan Farmar, with Bryant"));
//        assertTrue(extractions.contains("Shaq and Nash."));
    }
    
    @Test
    public void topLevelEntityTest(){
        final Set<String> extractions = Sets.newHashSet();
        Timer timer = new Timer("top level entity test") {
            @Override
            public void run() {
                for (int d = 0; d < 1; d++) {
                    for (String test : tests) {
                        Matcher matcher = Pattern.compile(MentionExtractor.topLevelEntity).matcher(test);

                        while (matcher.find()) {
                            if (debug) {
                                for (int i = 0; i < matcher.groupCount(); i++)
                                    System.out.println(i + ":" + matcher.group(i));
                                System.out.println("-----------");
                            }
                            String match = matcher.group();
                            String matchclean = match.replaceAll("\\s+", " ");
                            extractions.add(matchclean);
                           
                        }
                    }
                }
            }
        };
        timer.timedRun();
        assertTrue(!extractions.contains("John"));
        assertTrue(extractions.contains("City"));
        assertTrue(extractions.contains("Sex"));
    }
    

    @Test
    public void taggedLineTest() {
        String text = "<POSTER> Sue H &lt;daho...@cox.net&gt; </POSTER>\n" + "<POSTDATE> 2008-04-02T20:35:58 </POSTDATE>\n"
                + "I am really looking forward to the film... so hope they settle that\n"
                + "soon.  I'd love them to use some of the same people if they can\n" + "(anyone who might overlap).\n" + "\n"
                + "On Wed, 2 Apr 2008 15:18:55 -0500, &quot;Bill Plenge&quot; &lt;B_Ple ... @NoSpam.com&gt;\n" + "wrote:\n" + "\n"
                + "</POST>\n";
        Matcher matcher = Pattern.compile("^<.*$",Pattern.MULTILINE).matcher(text);
        while (matcher.find()) {
            assertTrue(matcher.group().contains("POST"));
        }
    }
    
    @Test
    public void commaTest(){
        

        assertTrue("Cape May".matches(topLevelEntity));
        assertTrue("New Jersey".matches(topLevelEntity));
        assertTrue("Bryant".matches(topLevelEntity));
        assertTrue(", with ".matches(atMost2Connectives));
        
        assertTrue(!"Resources &UR".matches(topLevelEntity));
        assertTrue("Cape May, New Jersey".matches(joinedPattern));
        assertTrue("President of the United Kingdom".matches(joinedPattern));
        assertTrue("Jordan Farmar, with Bryant".matches(joinedPattern));
        
        assertTrue(!"Michigan Department of Natural Resources &UR".matches(joinedPattern));
        

        assertTrue("D.C.".matches(capitalizedWord));
        Matcher cm = Pattern.compile(capitalizedWord).matcher("D.C.");
        cm.find();
        assertEquals("D.C.",cm.group());
        
        Matcher wm = Pattern.compile(joinedPattern).matcher("Washington, D.C.");
        wm.find();
        assertEquals("Washington, D.C.",wm.group());


        assertTrue("Washington".matches(topLevelEntity));
        assertTrue("D.C.".matches(topLevelEntity));
        assertTrue("Inc".matches("\\w+"));
        
        assertTrue("Washington, D.C.".matches(joinedPattern));
        assertTrue("Washington, D".matches(joinedPattern));
        Matcher m = superEntityPattern.matcher("Washington, D.C.");
        
        String[] components = new String[]{
                "entity1",
                "entity2",
                "connective1",
                "connective2"
        };
        if(debug){
            while(m.find()){
    
                for (String s:components){
                    System.out.println(s + ":" + m.group(s));
                }
                System.out.println("-----------");
            }
    
            System.out.println("(?=(\\bAB*B))".matches("AAABB"));
            System.out.println(Pattern.compile(topLevelEntity).matcher("D.C.").matches());
            System.out.println(Pattern.compile("(?=("+topLevelEntity+"))").matcher("D.C.").matches());
            Matcher m2 = Pattern.compile("(?=("+topLevelEntity+"))").matcher("D.C.");
            while(m2.find()){
                System.out.println(m2.group(1));
                System.out.println("m2 matched");
            }
            m2.reset();
            System.out.println(m2.matches());
            
            m.reset();
        }
        assertTrue(!"Cape May, New Jersey. Fin".matches(joinedPattern));
        assertTrue(!"Jersey.".matches(capitalizedWord));
        assertTrue("D.C.".matches(capitalizedWord));
    }
    
    @Test
    public void atomTest() {
        String text = "A  B";
        assertTrue(text.matches("A\\s{0,5}B"));
        assertTrue(!text.matches("A\\s{0,1}B"));
        assertTrue("D.C.".matches("([\\w\\-]{0,4}\\.)*"));

    }


    @Test
    public void whiteSpaceTest() {
        String text = "A  B";
        assertTrue(text.matches("A\\s{0,5}B"));
        assertTrue(!text.matches("A\\s{0,1}B"));
        assertTrue("D.".matches("[A-Z\\w\\-]{0,4}\\."));
    }

}
