package CSudoku.player.automate;

import CSudoku.board.CSudokuBoard;
import CSudoku.board.Move;
import CSudoku.player.Player;
import CSudoku.player.MoveStrategy;
import CSudoku.referee.Referee;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a strategy for selecting a random valid move in the Sudoku game.
 * The strategy generates a list of all valid moves and randomly selects one.
 */
public class RandomMoveStrategy implements MoveStrategy {
    private static final Random random = ThreadLocalRandom.current();
    /**
     * Selects a valid move for the AI player by randomly choosing from all possible valid moves.
     * A valid move is one that satisfies the Sudoku rules.
     *
     * @param board The current state of the Sudoku board.
     * @param player The AI player who is making the move.
     * @return A randomly selected {@link Move} object, or null if no valid moves are available.
     */
    @Override
    public Move selectMove(CSudokuBoard board, Player player) {
        if (board.isFull()) return null;
        int boardSize = board.getSize();
        List<int[]> emptyCells = new ArrayList<>();
        for(int r = 0; r < boardSize; r++){
            for(int c = 0; c < boardSize; c++){
                if(board.isCellEmpty(r,c)){
                    emptyCells.add(new int[]{r, c});

                }
            }
        }
        if(emptyCells.isEmpty()){
            return null;
        }
        Collections.shuffle(emptyCells);
        for(int[] cell: emptyCells){
            int r = cell[0];
            int c = cell[1];
            List<Integer> values = new ArrayList<>(boardSize);
            for(int v = 1; v <= boardSize; v++){
                values.add(v);
            }
            Collections.shuffle(values);

            for(int v: values){
                Move move = new Move(r,c,v);
                if(player.isValidMove(board,move)){
                    return move;

                }
            }
        }
        return null;
    }
    /**
     * Returns the name of the strategy, which in this case is "Random".
     * <p>
     * This method is part of the {@link MoveStrategy} interface and is used to retrieve
     * the name of the strategy being implemented. In this case, it returns the string "Random",
     * which indicates that the Random Move strategy is used for selecting moves.
     * </p>
     *
     * @return The name of the strategy, which is {@code "Random"}.
     */
    @Override
    public String getName() {
        return "Random";
    }
}
