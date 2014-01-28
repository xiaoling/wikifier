package edu.illinois.cs.cogcomp.wikifier.models;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.CoreferenceView;
import edu.illinois.cs.cogcomp.edison.sentences.View;
import edu.illinois.cs.cogcomp.lbj.coref.util.aux.Constants;
import edu.illinois.cs.cogcomp.wikifier.annotation.Coreference;

/**
 * Test whether the coref integration works
 * 
 * @author cheng88
 * 
 */
public class CorefTest {

    public static final String text = "\n" + "Stocks, which had surged from the start on word "
            + "that Home Depot's chairman and chief executive had resigned after years of "
            + "lackluster performance in the company's stock, added to their gains after "
            + "Wall Street received a stronger-than-expected report on December manufacturing "
            + "from the Institute for Supply Management and saw a softer-than-expected decline " + "in construction spending.\n" + "\n"
            + "\"The ISM number was better than expected and construction fell less than expected,"
            + "\" said Al Goldman, chief market strategist at A.G. Edwards. \"I think those of us "
            + "looking for a soft landing had more support on that stance today.\"\n";

    public static final String text2 = "Home Depot CEO Nardelli quits \n"
            + "Home-improvement retailer's chief executive had been criticized over pay \n"
            + " \n"
            + "ATLANTA - Bob Nardelli abruptly resigned Wednesday as chairman and chief executive of The Home Depot Inc. after a six-year tenure that saw the world's largest home improvement store chain post big profits but left investors disheartened by poor stock performance. \n"
            + " \n"
            + "Nardelli has also been under fire by investors for his hefty pay and is leaving with a severance package valued at about $210 million. He became CEO in December 2000 after being passed over for the top job at General Electric Co., where Nardelli had been a senior executive. \n"
            + " \n"
            + "Home Depot said Nardelli was being replaced by Frank Blake, its vice chairman, effective immediately. \n"
            + " \n"
            + "Blake's appointment is permanent, Home Depot spokesman Jerry Shields said. What he will be paid was not immediately disclosed, Shields said. The company declined to make Blake available for comment, and a message left for Nardelli with his secretary was not immediately returned. \n"
            + " \n"
            + "Before Wednesday's news, Home Depot's stock had been down more than 3 percent on a split-adjusted basis since Nardelli took over. \n"
            + " \n"
            + "Nardelli's sudden departure was stunning in that he told The Associated Press as recently as Sept. 1 that he had no intention of leaving, and a key director also said that the board was pleased with Nardelli despite the uproar by some investors. \n"
            + " \n"
            + "Asked in that interview if he had thought of hanging up his orange apron and leaving Home Depot, Nardelli said unequivocally that he hadn't. Asked what he thought he would be doing 10 years from now, Nardelli said, 'Selling hammers.' \n"
            + " \n"
            + "For The Home Depot? \n"
            + " \n"
            + "'Absolutely,' he said at the time. \n"
            + " \n"
            + "Home Depot said Nardelli's decision to resign was by mutual agreement with the Atlanta-based company. \n"
            + " \n"
            + "'We are very grateful to Bob for his strong leadership of The Home Depot over the past six years. Under Bob's tenure, the company made significant and necessary investments that greatly improved the company's infrastructure and operations, expanded our markets to include wholesale distribution and new geographies, and undertook key strategic initiatives to strengthen the company's foundation for the future,' Home Depot's board said in a statement. \n"
            + " \n"
            + "Nardelli was a nuts-and-bolts leader, a former college football player and friend of President Bush. He helped increase revenue and profits at Home Depot and increase the number of stores the company operates to more than 2,000. Home Depot's earnings per share have increased by approximately 150 percent over the last five years. But the public discussion about his pay and the company's stock price had become a distraction. \n"
            + " \n"
            + "Industry experts and analysts said his departure and Blake's ascent to the top job are a good thing for Home Depot. \n"
            + " \n"
            + "'This is not about strategy or vision,' said James Senn, director of Robinson College's Center for Global Business Leadership at Georgia State University. 'This is coming down to two things. Really the foundation of leadership is credibility. Bob has run into some problems there. The second is execution.' \n"
            + " \n"
            + "The Home Depot board also announced that Carol Tome, its chief financial officer, and Joe DeAngelo, its executive vice president for Home Depot Supply, will be assuming additional responsibilities. \n"
            + " \n"
            + "Tome will be assuming responsibility for mergers and acquisitions, credit services and additional strategic responsibilities. DeAngelo was appointed to the newly created position of chief operating officer. \n"
            + " \n"
            + "Nardelli and Home Depot have agreed to terms of a separation agreement that would provide for payment of the amounts he is entitled to receive under his pre-existing employment contract entered into in 2000. Under this agreement, Nardelli will receive consideration currently valued at about $210 million. \n"
            + " \n"
            + "The package includes a cash severance payment of $20 million, the acceleration of unvested deferred stock awards currently valued at approximately $77 million and unvested options with an intrinsic value of approximately $7 million. It also includes payments of earned bonuses and long-term incentive awards of approximately $9 million, account balances under the Company's 401(k) plan and other benefit programs currently valued at approximately $2 million, previously earned and vested deferred shares with an approximate value of $44 million, the present value of retirement benefits currently valued at approximately $32 million and $18 million for other entitlements under his contract. Those entitlements will be paid over a four-year period and will be forfeited if he does not honor his contractual obligations. \n"
            + " \n"
            + "Nardelli has also agreed not to compete with the company for one year, and not to solicit employees or customers of the company for four years. \n"
            + " \n"
            + "Home Depot did not say what Nardelli would be doing next. \n"
            + " \n"
            + "In conjunction with the management changes, the board also announced that it had waived the retirement age of 72 and has asked John L. Clendenin, Claudio X. Gonzales and Milledge A. Hart III to stand for re-election at the 2007 annual shareholders meeting. This action was taken to retain these directors' experience. Home Depot said the action was temporary. \n"
            + "";

    private static final String separationTest = "Timberlake, Diaz reportedly break up\n" + 
    		"Former N&apos; Sync singer seeing former flame, magazine reports\n" + 
    		"\n" + 
    		"Justin Timberlake and Cameron Diaz have called it quits, according to a report in Star magazine.\n" + 
    		"\n" + 
    		"According to Star, Diaz, 34, spent Christmas with her family in Vail, Colo., while Timberlake, 25, was with his family near Memphis. The magazine quotes a source who says the former N&apos; Sync singer told friends that he and the actress had broken up. The couple were last seen together on Dec. 16 when she introduced his musical performance on &quot;Saturday Night Live.&quot;\n" + 
    		"\n" + 
    		"Diaz and Timberlake started dating shortly after they met each other at the 2003 Kids&apos; Choice Awards.\n" + 
    		"\n" + 
    		"The magazine also reported that Timberlake has started seeing a former flame, Veronica Finn. The pair dated in the late &apos;90s before he began dating Britney Spears. Finn and Spears were briefly in a girl group together called Innosense, according to music impresario Lou Pearlman.";
    
    @Test
    public void mentionTest() {

        View view = Coreference.getMentionView(separationTest);

        assertTrue(view.getConstituents().size() > 0);
        for(Constituent c:view){
            assertTrue(c.hasAttribute(Constants.MentionType));
        }
    }
    
    @Test
    public void separationTest() {
        CoreferenceView view = Coreference.getCorefView(separationTest);

        assertTrue(view.getConstituents().size() > 0);
        for(Constituent c:view){
            if(c.getSurfaceString().equals("Justin Timberlake")){
                for(Constituent c2:view.getCoreferentMentions(c)){
                    assertTrue(!c2.getSurfaceString().equals("Diaz"));
                }
            }
        }
    }
    

    
    
    
    @Test
    public void test() {
        System.out.printf("EL_%05d\n",23);
        CoreferenceView view = Coreference.getCorefView(text);
        assertTrue(view.getConstituents().size() > 0);
    }
    
    @Test
    public void test2() {
        CoreferenceView view = Coreference.getCorefView(text2);
        assertTrue(view.getConstituents().size() > 0);
        for(Constituent c:view){
            if(c.getSurfaceString().equals("Nardelli")){
                assertTrue(view.getCoreferentMentions(c).size()>5);
                break;
            }
        }
    }

}
