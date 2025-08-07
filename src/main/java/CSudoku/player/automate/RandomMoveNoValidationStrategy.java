package CSudoku.player.automate;

import CSudoku.board.CSudokuBoard;
import CSudoku.board.Move;
import CSudoku.player.Player;
import CSudoku.player.MoveStrategy;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a strategy for selecting a random move in the Sudoku game.
 * The strategy selects an empty cell at random and assigns a random value.
 * No validation is performed to check if the move complies with Sudoku rules.
 */
public class RandomMoveNoValidationStrategy implements MoveStrategy {
    private static final Random random = ThreadLocalRandom.current();

    /**
     * Selects a random move for the AI player by choosing an empty cell
     * and assigning a random value, without checking for move validity.
     *
     * @param board The current state of the Sudoku board.
     * @param player The AI player who is making the move.
     * @return A randomly selected {@link Move} object, or null if no empty cells are available.
     */
    @Override
    public Move selectMove(CSudokuBoard board, Player player) {
        if (board.isFull()) return null;
        int size_b = board.getSize();
        int r = -1;
        int c = -1;
        int v;
        int tries = 0;
        int maxTries = size_b * size_b * 3;
        boolean flag1 = false;
        do {
            r = random.nextInt(size_b);
            c = random.nextInt(size_b);
            if (board.isCellEmpty(r, c)) {
                flag1 = true;
                break;
            }
            tries++;
        } while (tries < maxTries);
        if (!flag1) {
            boolean flag2 = false;
            for (int row = 0; row < size_b; row++) {
                for (int col = 0; col < size_b; col++) {
                    if (board.isCellEmpty(row, col)) {
                        r = row;
                        c = col;
                        flag2 = true;
                        break;
                    }
                }
                if (flag2) {
                    break;
                }
            }
            if (!flag2) {
                return null;
            }
        }
        v = random.nextInt(size_b) + 1; // Generate random value (1 to size_b)
        return new Move(r, c, v);
    }
    /**
     * Returns the name of the strategy, which in this case is "Random No Validation".
     * <p>
     * This method is part of the {@link MoveStrategy} interface and is used to retrieve
     * the name of the strategy being implemented. In this case, it returns the string "Random No Validation",
     * which indicates that the Random No Validation Move strategy is used for selecting moves.
     * </p>
     *
     * @return The name of the strategy, which is {@code "Random"}.
     */
    @Override
    public String getName() {
        return "Random No Validation";
    }
}
