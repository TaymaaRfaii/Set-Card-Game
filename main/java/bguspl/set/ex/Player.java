package bguspl.set.ex;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final int one=1;
    private final int minone=-1;
   private final int zero=0;
    private final int thousand=1000;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    public Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    public Thread aiThread;
    //here it was private

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    private Dealer dealer;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    public volatile  boolean Include;

    public volatile long TimeSuspension;

    //We Added
    public volatile List<Integer> PlayersAction = new LinkedList<>();


    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        Include = true;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            if (this.PlayersAction.size() == env.config.featureSize && Include && (human || aiThread.getState() == Thread.State.WAITING )) {
                AddThePlayerToCheck(this);
                synchronized (this) {
                        try {
                            wait();
                        for (long i = TimeSuspension; i > zero; i = i - thousand) {
                            env.ui.setFreeze(this.id, i);
                            Thread.sleep(thousand);
                        }
                        env.ui.setFreeze(this.id, zero);
                            if(!human) {
                                ContinueRunningForAI();
                            }
                        } catch (InterruptedException ex) {
                            ContinueRunningForAI();
                        }
                }

            }
        }
        if (!human) {ContinueRunningForAI(); try { aiThread.join(); } catch (InterruptedException ignored) {}}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            aiThread.setName("AiThread of Player.id: " + this.id);
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                while (PlayersAction.size() != env.config.featureSize || !Include) {
                    int random = (int) (Math.random() * env.config.columns*env.config.rows);
                    keyPressed(random);//press a random key
                }
                synchronized (aiThread) {
                    try {
                        aiThread.wait();
                   } catch (InterruptedException ex) {
                        if(this.terminate)
                        {break;}
                        continue;
                    }
                }
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        this.terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if(playerThread.getState() == Thread.State.RUNNABLE && table.slotToCard[slot] != null && dealer.PlacementAllowed){
            if (PlayersAction.contains(slot)) {//remove the token from the slot if it is there
                table.removeToken(this.id, slot);
                PlayersAction.remove(Integer.valueOf(slot));
                Include = true;
            } else if (this.PlayersAction.size() < env.config.featureSize) {
                this.PlayersAction.add(slot);
                table.placeToken(this.id, slot);
            }
        }

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        synchronized (this) {
            int ignored = table.countCards(); // this part is just for demonstration in the unit tests
            env.ui.setScore(id, ++score);
            if(score > dealer.HighestScoreNumber) {
            dealer.HighestScoreNumber= score; dealer.HowManyWinningPlayers= one;//update the highest score
            }
            else if( score == dealer.HighestScoreNumber)
                dealer.HowManyWinningPlayers++;
            TimeSuspension = env.config.pointFreezeMillis;
            notify();
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        synchronized (this) {
            TimeSuspension = env.config.penaltyFreezeMillis;
            Include = false;
            RemoveThePenalizedPlayer(this);
            notify();
        }
    }

    public int score() {//return the score
        return score;
    }



    public void removeAllTokens(){//here we remove all the tokens from the players action
        for(int j =0;j<PlayersAction.size();j++){
            table.removeToken(this.id,PlayersAction.remove(j));
        }

    }

    public int [] MapPlayerActionsToCardRepresentations(){//creates an array where each element represents the card associated with a player action if not ex we put -1
        int [] cards = new int[PlayersAction.size()];
        for(int j = 0 ; j < PlayersAction.size(); j++){
            cards[j] = minone;
            if(table.slotToCard[PlayersAction.get(j)] != null)
            cards[j] = table.slotToCard[PlayersAction.get(j)];
        }
        return cards;
    }

    public void removeIt(int slot){//remove a token from the actions for the player and removes it from the playersActon
        if(PlayersAction.contains(slot)){
            Include = true;
            PlayersAction.remove(PlayersAction.indexOf(slot));
            table.removeToken(this.id,slot);
        }
    }

    public void Continue(){
        synchronized (this){
            TimeSuspension = zero;
            notify();
        }
    }
    public void ContinueRunningForAI(){
        synchronized (aiThread){
            aiThread.interrupt();
        }
    }
    public synchronized void AddThePlayerToCheck(Player player){
        this.table.AddPlayerToReadyToBeChecked(player);
    }

    public synchronized void RemoveThePenalizedPlayer(Player player){
        this.table.RemovePlayerToReadyToBeChecked(player);
    }

}
