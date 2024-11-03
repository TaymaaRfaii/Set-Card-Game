package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;
    Thread timer;

    public volatile int HighestScoreNumber;
    public volatile int HowManyWinningPlayers;
    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    private final int minone=-1;
    private final int one=1;
    private final int zero=0;
    private final int ten=10;
    private final int thousand=1000;

    public volatile boolean PlacementAllowed;
    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;
    private final List<Integer> gridCards;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    private volatile boolean NextReshuffle;

    private long TimePassedAction;



    private Object locker = new Object();
    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long countDown ;


    private List<Thread> threads;


    public Dealer(Env env, Table table, Player[] players) {
        this.HighestScoreNumber= 0;
        this.env = env;
        this.table = table;
        this.players = players;
        this.threads = new ArrayList<>();
        TimePassedAction=0;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        gridCards = new ArrayList<>(env.config.columns*env.config.rows);
        this.countDown = System.currentTimeMillis();
        PlacementAllowed = false;
        NextReshuffle = (env.config.turnTimeoutMillis <= 0);
        MakeTimer(NextReshuffle);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        placeCardsOnTable();
        playerStart();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        this.countDown = System.currentTimeMillis();
        timer.start();
        while (!shouldFinish()) {
            PostTimeReorganize();
            checkLegalSets();
        }

        terminate = true;
        terminateAll();
        try{
            Thread.sleep(200);
            removeAllCardsFromTable();
            Thread.sleep(500);
            announceWinners();
        }catch (InterruptedException ex){}

        Collections.reverse(threads);
            for (Thread thd : threads) {//we terminate it in reverse order
                    if (terminate) {
                        thd.interrupt();
                        try {thd.join();}
                        catch (InterruptedException ex){}
                    }
            }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        this.terminate = true;
        System.exit(zero);
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        synchronized (locker) {
            return terminate || (env.util.findSets(deck, one).isEmpty() && env.util.findSets(gridCards, one).isEmpty());
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        PlacementAllowed = false;
        countDown= System.currentTimeMillis();
        shuffleDeck();
        for(int i = 0; i < env.config.columns*env.config.rows && !deck.isEmpty(); i++) {
            //removing cards from deck
            int random = (int) (Math.random() * deck.size());
            Integer currCard = this.deck.remove(random);
            this.gridCards.add(currCard);
            this.table.placeCard(currCard, i);
        }
        PlacementAllowed = true;
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        PlacementAllowed = false;
        for(int i = 0; i<table.slotToCard.length && !gridCards.isEmpty(); i++){
            this.deck.add(gridCards.remove(0));
            for(int j =0 ; j<players.length;j++){
                table.removeToken(j,i);
                players[j].removeAllTokens();
            }
        }
        for(int i = 0; i<table.slotToCard.length;i++){
            table.removeCard(i);
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {//announce the winners
        ArrayList<Integer> WinningScore = new ArrayList<>();
        for (Player ThisPlayer: players) {
           if(ThisPlayer.score() == HighestScoreNumber) WinningScore.add(ThisPlayer.id);
        }
        int [] Theplayers = new int [WinningScore.size()];
         Theplayers = WinningScore.stream().mapToInt(i-> i).toArray();
        env.ui.announceWinner(Theplayers);
    }


    private void SlotCompletion(){//fill in the slots that is empty
        for(int j = 0 ; j <env.config.columns*env.config.rows;j++){
            if(table.slotToCard[j] == null && !deck.isEmpty()){
                int a =(int) (Math.random() * (deck.size()));
                Integer currCard = deck.remove(a);
                gridCards.add(currCard);
                table.placeCard(currCard,j);
                try {
                    Thread.sleep(env.config.tableDelayMillis);
                }catch (InterruptedException ex){}
            }
        }
    }


    private void shuffleDeck(){
        Collections.shuffle(this.deck);
    }


    private void playerStart(){
        for(Player player : this.players ){
            Thread playerThread = new Thread(player);
            playerThread.setName("Player Thread.id: " + player.id);
            playerThread.start();
            threads.add(playerThread);
        }
    }

    private void checkLegalSets(){
        if (!table.ReadyToBeChecked.isEmpty()) {
            Player ThisPlayer = table.ReadyToBeChecked.poll();
            int[] Cards = ThisPlayer.MapPlayerActionsToCardRepresentations();
            if (Cards.length == env.config.featureSize) {
                NullDetector(ThisPlayer,Cards);
                if (env.util.testSet(Cards)) {
                    ReinitializeForTimer();
                    for (int j = 0; j < Cards.length; j++) {
                        for (Player CurrentPlayer : players) {
                            CurrentPlayer.removeIt(table.cardToSlot[Cards[j]]);
                        }
                        table.removeCard(table.cardToSlot[Cards[j]]);
                        this.gridCards.remove(Integer.valueOf(Cards[j]));
                    }
                    SlotCompletion();
                    ThisPlayer.point();
                } else {
                    ThisPlayer.penalty();
                }
            } else {
                ThisPlayer.Continue();
            }
        }
    }

    public void ReinitializeForTimer(){
        if(!NextReshuffle) {
            this.countDown = System.currentTimeMillis();
        }
        else{
            env.ui.setElapsed(TimePassedAction);
        }
    }

    public  void PostTimeoutReshuffle(){
        synchronized (locker) {
            shuffleDeck();
            removeAllCardsFromTable();
            placeCardsOnTable();
            ReinitializeForTimer();
            GivePermissionToAll();
        }
    }
    public void GivePermissionToAll(){
        for (Player player: players) {
            player.Include = true;
        }
    }


    //this function was meant to be used to make a smart pick in the AI mode but We didn't use it 
    
    // public List<int []> findGridSetSlots(){
    //     List<int []> set = env.util.findSets(gridCards,20);
    //     int[] slots = new int[3];
    //     if(set.size() == 0) {
    //         for (int j = 0; j < set.size(); j++) {
    //             slots[j] = table.cardToSlot[set];
    //         }
    //     }
    //     return set;
    // }


    public void PostTimeReorganize(){
        if(NextReshuffle){
            if(env.util.findSets(gridCards, one).isEmpty()){
                PostTimeoutReshuffle();
            }
        }
    }

    private void MakeTimer(boolean reset){
        if(!reset){
            timer = new Thread(){
                public void run(){
                    env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
                    while(!terminate) {
                        long timePassed =  (env.config.turnTimeoutMillis+thousand)+(countDown - System.currentTimeMillis());
                        try {
                            Thread.sleep(ten);
                        } catch (InterruptedException ex) {}

                        if(timePassed<=zero){env.ui.setCountdown(zero,true);
                            PostTimeoutReshuffle();}
                        env.ui.setCountdown(timePassed, timePassed<=env.config.turnTimeoutWarningMillis);

                    }
                    try {
                        Thread.sleep(thousand);
                    }catch (InterruptedException ex){}
                    env.ui.setCountdown(zero,false);
                    env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
                }

            };
        }
        else {

            timer = new Thread(){
                public void run(){
                    env.ui.setElapsed(zero);
                    env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
                    while(!terminate && NextReshuffle){
                        long elapsedTime = (System.currentTimeMillis() - countDown);
                        if(env.config.turnTimeoutMillis == zero) {TimePassedAction = elapsedTime;} //if what I understood first then ==>  //{env.ui.setElapsed(elapsedTime + 500);}
                        else {threads.remove(timer);break;}}
                    env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
                }
            };
        }
        threads.add(timer);
        timer.setName("Timer Thread");
    }

    public void NullDetector(Player ThisPlayer,int [] Cards){//check if one of the tokens in players action is -1
        for (int card : Cards) {
            if (card == minone) {
                ThisPlayer.removeAllTokens();
                ThisPlayer.Include = true;
                ThisPlayer.Continue();
                break;
            }
        }

    }

    private void terminateAll(){
        for (Player TheCurrentPlayer: players) {
            TheCurrentPlayer.terminate();
        }
    }

    //for test
    public long time(){
        return countDown;
    }


}
