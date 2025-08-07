package CSudoku.player;

import CSudoku.board.CSudokuBoard;
import CSudoku.board.Move;
import CSudoku.player.ai.EvaluatedSimulatedBoard;

/**
 * Represents a player in the Sudoku game.
 * This interface defines the behavior for any type of player,
 * whether human or AI, by specifying how they select their moves.
 * <p>
 * Implementing classes should provide the logic for how players make decisions during the game,
 * including how they generate and validate moves based on the current state of the board.
 * </p>
 */
public interface Player {

    /**
     * Retrieves the next move chosen by the player.
     * <p>
     * This method is used to ask the player for their next move given the current state of the Sudoku board.
     * The move is returned as a {@link Move} object, which contains the row, column, and value chosen by the player.
     * </p>
     *
     * @param board The current state of the Sudoku board, represented by the {@link CSudokuBoard} object.
     *              The player will use this board to determine their next move.
     * @return The move selected by the player as a {@link Move} object.
     *         The {@link Move} includes the row, column, and value that the player has chosen.
     * @see Move
     */
    Move getMove(CSudokuBoard board);

    /**
     * Validates if a given move is valid on a simulated board state.
     * <p>
     * This method checks if a move is valid based on the current state of a simulated version of the Sudoku board.
     * This is typically used by AI players to evaluate possible moves during the decision-making process.
     * </p>
     *
     * @param board The simulated board state represented by an {@link EvaluatedSimulatedBoard} object.
     *              The simulated board reflects the potential outcomes of different moves.
     * @param move  The {@link Move} object representing the move that is being validated.
     * @return {@code true} if the move is valid according to the simulated board, {@code false} otherwise.
     * @see EvaluatedSimulatedBoard
     * @see Move
     */
    /**
     * Checks whether a move is valid on the given game board.
     *
     * @param board The current game board.
     * @param move  The move to validate.
     * @return {@code true} if the move is valid, {@code false} otherwise.
     */
    default boolean isValidMove(CSudokuBoard board, Move move) {
        int row = move.getRow();
        int col = move.getCol();
        int value = move.getValue();
        int size = board.getSize();

        // 1. Bounds and Value Range Check
        if (row < 0 || row >= size || col < 0 || col >= size || value < 1 || value > size) {
            return false;
        }
        // 2. Cell Empty Check - *** Use getValue() instead of isCellEmpty() ***
        if (board.getValue(row, col) != 0) { // Check if cell value is not 0
            return false;
        }
        // 3. Row/Column Conflict Check
        for (int i = 0; i < size; i++) {
            if (board.getValue(row, i) == value /* && i != col */) {
                return false;
            }
            if (board.getValue(i, col) == value /* && i != row */) {
                return false;
            }
        }
        // 4. Subgrid Conflict Check
        int blockSize = (int) Math.sqrt(size);
        if (blockSize <= 0) {
            System.err.println("Warning: Invalid block size calculation in isValidMove.");
            return false; // Or handle appropriately
        }
        int startRow = (row / blockSize) * blockSize;
        int startCol = (col / blockSize) * blockSize;

        for (int i = startRow; i < startRow + blockSize; i++) {
            for (int j = startCol; j < startCol + blockSize; j++) {
                if (board.getValue(i, j) == value) {
                    return false;
                }
            }
        }
        // 5. Consecutive Constraint Check
        int[] dr = {-1, 1, 0, 0}; // Directions: up, down, left, right
        int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int nr = row + dr[i];
            int nc = col + dc[i];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                if (board.hasConsecutiveConstraint(row, col, nr, nc)) {
                    int neighborValue = board.getValue(nr, nc);
                    if (neighborValue != 0 && Math.abs(value - neighborValue) != 1) {
                        return false;
                    }
                }
            }
        }
        // 6. everything is good
        return true;
    }
}