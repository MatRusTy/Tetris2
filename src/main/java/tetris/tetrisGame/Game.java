package tetris.tetrisGame;

import tetris.Framework.Position;
import tetris.Framework.Tetrimino;
import tetris.tetrisGame.MovementStrategy.MovementStrategy;
import tetris.tetrisGame.MovementStrategy.StandardMovementStrategy;
import tetris.tetrisGame.Pieces.*;
import tetris.tetrisGame.RotationStrategy.RotationStrategy;
import tetris.tetrisGame.RotationStrategy.StandardRotationStrategy;
import tetris.tetrisGame.TetriminoFactory.StandardTetriminoFactory;
import tetris.tetrisGame.TetriminoFactory.TetriminoFactory;
import tetris.tetrisGame.ValidationStrategy.StandardValidationStrategy;
import tetris.tetrisGame.ValidationStrategy.ValidationStrategy;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.*;

/**
 * The game class containing most of the game handling logic, as well as keybindings.
 * The logic handling the movement of the tetriminos is defined in the abstract tetris.Framework.Tetrimino class and its sub-classes.
 *
 * @author MatRusTy
 */
public class Game implements KeyListener {

    // --------------------- FIELD VARIABLES ---------------------
    private Game game;
    private int timePassed;
    private int score;
    private Timer time;
    private Random random;
    private boolean paused;
    private GUI gui;
    private boolean lost;

    /** Helper variable for moveDown method*/
    private int moveDownTries;

    /** The grid that the current tetrimino has to be placed in*/
    private PlayingField playfield;

    private Tetrimino nextTetrimino;

    /** Variable containing the current tetris.Framework.Tetrimino in the playingfield*/
    private Tetrimino currentTetrimino;

    /** Variable containing the tetrimino available for swapping */
    private Tetrimino savedTetrimino;

    /** Variable telling if the swap feature has been used this round ("round" being the fall of the current tetris.Framework.Tetrimino) */
    private boolean changedCurrentTetriminoThisRound;

    /** Defines the amount of time that passes between each ingame timetick*/
    private int period;

    private RotationStrategy rotationStrategy;
    private MovementStrategy movementStrategy;
    private TetriminoFactory tetriminoFactory;
    private ValidationStrategy validationStrategy;


    // --------------------- GAME CREATION AND TIME HANDLING ---------------------
    /**
     * Creates a new game object with all stats set to 0 and a new, empty playingfield.
     */
    public Game() {
        timePassed = 0;
        score = 0;
        period = 1000*1;
        playfield = new PlayingField(this);
        random = new Random();
        this.game = this;
        rotationStrategy = new StandardRotationStrategy();
        movementStrategy = new StandardMovementStrategy();
        tetriminoFactory = new StandardTetriminoFactory();
        validationStrategy = new StandardValidationStrategy();
        gui = new GUI(game, playfield);
    }

    /**
     * Initializes the game time and requests the first piece
     */
    public void startGame(){
        paused = false;
        lost = false;
        nextTetrimino = generateRandomPiece();
        nextPiece();
        startTimer(period);
    }

    /**
     * Resets stats and playfield, and start a new game.
     */
    public void newGame(){
        stopGame();
        timePassed = 0;
        score = 0;
        lost = false;
        changedCurrentTetriminoThisRound = false;
        savedTetrimino = null;
        playfield.clearGrid();
        startGame();
        paused = false;
        gui.updatePlayfield();
    }

    /**
     * Stops the time.
     */
    private void stopGame(){
        time.cancel();
        paused = true;
    }

    /**
     * Creates a new timer and starts it.
     */
    private void resumeGame(){
        startTimer(period);
        paused = false;
    }

    /**
     * If game is running, the method pauses the game. Otherwise, it resumes it.
     */
    public void pauseResume(){
        if(paused){
            resumeGame();
        } else {
            stopGame();
        }
    }

    /**
     * Creates a new timer, and schedules it to run with a given delay and period
     *
     * @param period The time that goes between the execution of each run
     */
    public void startTimer(int period){
        paused = false;
        time = new Timer();
        time.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                step();
                System.out.println(timePassed);
                for(GridElement g : currentTetrimino.getBlocks()){
                    System.out.print("(" + g.y() +", "+  g.x() + ") ");
                }
                System.out.println();
            }
        }, period, period);
    }

    /**
     * One ingame timeunit passes.
     */
    public void step(){
        timePassed++;
        computerMoveDown();
    }

    // --------------------- GAME LOGIC ---------------------

    /**
     * Calculates the next tetrimino to move down.
     */
    public void nextPiece(){
        currentTetrimino = nextTetrimino;
        nextTetrimino = generateRandomPiece();
        moveDownTries = 0; // The amount of times the player has tried to move it down unsuccessfully
        playfield.setCurrentTetrimino(currentTetrimino);
    }

    private Tetrimino generateRandomPiece(){
        return tetriminoFactory.createNewTetrimino();
    }

    /**
     * This method is used to support the swap feature.
     * You can change your current tetrimino with one you have set a side earlier
     * If you haven't set one aside earlier (first time the method is ever called), you set your piece aside, and the next piece is generated.
     */
    private void changeCurrentTetrimino(){
        if(!changedCurrentTetriminoThisRound) {
            if (savedTetrimino == null) {
                savedTetrimino = currentTetrimino;
                savedTetrimino = tetriminoFactory.createNewInstanceOf(savedTetrimino);
                nextPiece();
            } else {
                Tetrimino temp = savedTetrimino;
                savedTetrimino = currentTetrimino;
                savedTetrimino = tetriminoFactory.createNewInstanceOf(savedTetrimino);
                currentTetrimino = temp;
                playfield.setCurrentTetrimino(currentTetrimino);
            }
            changedCurrentTetriminoThisRound = true;
        }

        gui.updatePlayfield();
    }

    /**
     * Makes the current tetrimino moveDown down 1 row if there is space for it.
     * If there isn't space for it. Then it needs to be inserted into the playingfield, and a new piece should be created.
     */
    public void computerMoveDown(){
        Map<GridElement, Position> suggestedMove = movementStrategy.moveDown(currentTetrimino);
        boolean successful = validationStrategy.validateMove(new ArrayList<>(suggestedMove.values()), playfield);
        if(successful){
            moveCurrentTetrimino(suggestedMove);
            moveDownTries = 0;
        } else {
            insertIntoGrid();
        }
        gui.updatePlayfield();
    }

    /**
     * Helper method for the computerMoveDownMethod.
     * Inserts the current piece into the grid and checks to see if the game is lost.
     */
    private void insertIntoGrid(){
        playfield.insertCurrentPieceIntoGrid();

        // After the moveDown, check if any rows have been filled out.
        playfield.removeFullRows();
        boolean lost = playfield.calculateLost();
        if(!lost){
            nextPiece();
            changedCurrentTetriminoThisRound = false;
            System.out.println("Piece fallen");
            System.out.println("Next piece is: " + currentTetrimino.toString());
            System.out.println("Currently occupied slots:");
            for(GridElement[] G : playfield.getGrid()){
                for(GridElement g : G){
                    if(g.isOccupied()){
                        System.out.print("(" + g.y() +", "+  g.x() + ") ");
                    }
                }
            }
            System.out.println();

        } else {
            this.lost = true;
            game.stopGame();
            System.out.println("tetris.Game Over!");
            gui.gameLostScreen();
            gui.updatePlayfield();
        }
    }

    /**
     * Method to drop the current piece to the bottom of the playingfield.
     */
    private void drop(){
        Map<GridElement, Position> suggestedMove = movementStrategy.drop(playfield, currentTetrimino);
        boolean successful = validationStrategy.validateMove(new ArrayList<>(suggestedMove.values()), playfield);
        if(successful) {
            moveCurrentTetrimino(suggestedMove);
            insertIntoGrid();
        }
        gui.updatePlayfield();
    }

    /**
     * Move down the current tetrimino 1 gridelement
     */
    private void moveDown(){
        time.cancel();
        Map<GridElement, Position> suggestedMove = movementStrategy.moveDown(currentTetrimino);
        boolean successful = validationStrategy.validateMove(new ArrayList<>(suggestedMove.values()), playfield);
        if(successful) {
            moveCurrentTetrimino(suggestedMove);
        }
        if(!successful){
            moveDownTries++;
        }
        if(moveDownTries > 10){
            computerMoveDown();
        }
        startTimer(period);
        gui.updatePlayfield();
    }

    /**
     * Move the tetrimino left
     */
    private void moveLeft(){
        Map<GridElement, Position> suggestedMove = movementStrategy.moveLeft(currentTetrimino);
        boolean successful = validationStrategy.validateMove(new ArrayList<>(suggestedMove.values()), playfield);
        if(successful) {
            moveCurrentTetrimino(suggestedMove);
        }
    }

    /**
     * Move the tetrimino right
     */
    private void moveRight(){
        Map<GridElement, Position> suggestedMove = movementStrategy.moveRight(currentTetrimino);
        boolean successful = validationStrategy.validateMove(new ArrayList<>(suggestedMove.values()), playfield);
        if(successful) {
            moveCurrentTetrimino(suggestedMove);
        }
    }

    /**
     * Rotate the current tetrimino in the clockwise direction
     */
    private void rotateClockWise(){
        Map<GridElement, Position> suggestedMove = rotationStrategy.rotateClockWise(currentTetrimino);
        boolean successful = validationStrategy.validateMove(new ArrayList<>(suggestedMove.values()), playfield);
        if(successful) {
            moveCurrentTetrimino(suggestedMove);
        }
    }

    /**
     * Rotate the current tetrimino in the counter-clockwise direction
     */
    private void rotateCounterClockWise(){
        Map<GridElement, Position> suggestedMove = rotationStrategy.rotateCounterClockWise(currentTetrimino);
        boolean successful = validationStrategy.validateMove(new ArrayList<>(suggestedMove.values()), playfield);
        if(successful) {
            moveCurrentTetrimino(suggestedMove);
        }
    }

    private void moveCurrentTetrimino(Map<GridElement, Position> suggestMove) {
        Set<GridElement> blocks = suggestMove.keySet();
        for (GridElement block : blocks) {
            Position newPosition = suggestMove.get(block);
            block.setX(newPosition.getCol());
            block.setY(newPosition.getRow());
        }
        gui.updatePlayfield();
    }

    // --------------------- GAME INPUT ---------------------

    @Override
    public void keyTyped(KeyEvent e) {
        //Won't be implemented
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch(e.getKeyCode()) {
            case KeyEvent.VK_LEFT : {
                if (!paused) { moveLeft(); }
                break;
            }
            case KeyEvent.VK_RIGHT : {
                if (!paused) { moveRight(); }
                break;
            }
            case KeyEvent.VK_CONTROL : {
                if (!paused) { rotateCounterClockWise(); }
                break;
            }
            case KeyEvent.VK_SPACE : case KeyEvent.VK_NUMPAD0 : {
                if (!paused) { drop(); }
                break;
            }
            case KeyEvent.VK_UP : {
                if (!paused) { rotateClockWise(); }
                break;
            }
            case KeyEvent.VK_DOWN : {
                if (!paused) { moveDown(); }
                break;
            }
            case KeyEvent.VK_C : {
                if(!paused){ changeCurrentTetrimino(); }
                break;
            }
            case KeyEvent.VK_ESCAPE : case KeyEvent.VK_NUMPAD1 :{
                gui.pauseResume();
                break;
            }
            case KeyEvent.VK_W : {
                if(e.isControlDown()) { System.exit(0); }
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        //Won't be implemented
    }

    // --------------------- SETTERS HANDLER ---------------------

    /**
     * Increases the current score with the specified value
     * @param amount Amount to be added to the score
     */
    public void increaseScore(int amount){
        score += amount;
    }

    // --------------------- GETTERS ---------------------

    public Random getRandom(){
        return random;
    }
    public int getTimePassed(){
        return timePassed;
    }
    public int getScore(){
        return score;
    }
    public boolean isPaused(){
        return paused;
    }
    public Tetrimino getSavedTetrimino(){
        return savedTetrimino;
    }
    public Tetrimino getNextTetrimino(){
        return nextTetrimino;
    }
    public boolean hasLost(){
        return lost;
    }
}
