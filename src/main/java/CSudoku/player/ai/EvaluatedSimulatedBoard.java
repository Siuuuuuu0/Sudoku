package CSudoku.player.ai;

import CSudoku.board.CSudokuBoard;
import CSudoku.board.Move;
import CSudoku.player.Player;
import java.util.Arrays;


/**
 * A class representing an evaluated and simulated Sudoku board.
 * Extends the functionality of {@link CSudokuBoard} by adding evaluation capabilities
 * for AI strategies and maintaining the state of rows, columns, and subgrids.
 */
public class EvaluatedSimulatedBoard extends CSudokuBoard {

    private int eval; // Evaluation score of the board.
    private Move lastMove; // The last move played on the board.
    private int[] zerosInRows; // Number of empty cells (zeros) in each row.
    private int[] zerosInColumns; // Number of empty cells (zeros) in each column.

    /**
     * Creates an empty evaluated simulated board with the given size.
     *
     * @param size The size of the board (e.g., 4, 9, 16, etc.).
     */
    public EvaluatedSimulatedBoard(int size) {
        // Initialize the underlying grid and constraints by using the parent constructor
        super(size);
        this.eval = 0;
        this.lastMove = null;
        this.zerosInRows = new int[size];
        this.zerosInColumns = new int[size];
        // Initialize all rows and all columns as having 'size' zeros == empty cells
        for (int i = 0; i < size; i++) {
            this.zerosInRows[i] = size;
            this.zerosInColumns[i] = size;
        }
    }

    /**
     * Creates a deep copy of the given evaluated simulated board.
     *
     * @param board The board to copy.
     */
    public EvaluatedSimulatedBoard(EvaluatedSimulatedBoard board) {
        this(board.getSize());  // Initialize basic board by using the sizeof the constructor
        int boardSize = board.getSize();
        // Deep copy the grid state
        for(int i = 0; i < boardSize; i++ ){
            for(int j = 0; j < boardSize; j++){
                // Use parent's setValue to ensure consistency
                super.setValue(i, j, board.getValue(i, j));
            }
        }
        // Copy constraints
        for(Constraint c: board.getConstraints()){
            // Create new Constraint objects to avoid shared references if for ex: Constraint is mutable
            this.addConstraint(new Constraint(c.row1, c.col1, c.row2, c.col2));
        }
        // Copy evaluation state and last move reference
        this.eval = board.eval;
        this.lastMove = board.lastMove;
        // Deep copy the zero counter arrays
        this.zerosInRows = Arrays.copyOf(board.zerosInRows, boardSize);
        this.zerosInColumns = Arrays.copyOf(board.zerosInColumns, boardSize);
    }

    /**
     * Creates a simulated board from an existing board, initializing it for evaluation.
     *
     * @param board  The original board.
     * @param player The AI/Automate player making the evaluation.
     */
    public EvaluatedSimulatedBoard(CSudokuBoard board, Player player) {
        // Initialize basic structure and zero counts
        this(board.getSize());
        // Populate grid and update zero counts by using the initial board
        for(int i = 0; i < board.getSize(); i++){
            for(int j = 0; j < board.getSize(); j++){
                // Set value in the grid
                super.setValue(i, j, board.getValue(i, j));
                if(board.getValue(i, j) != 0){
                    // If a cell != empty, decrement the zero counts for its row and column
                    this.decreaseZerosInRows(i);
                    this.decreaseZerosInColumns(j);
                }
            }
        }
        // Copy constraints from the original board
        for(Constraint c: board.getConstraints()){
            this.addConstraint(new Constraint(c.row1, c.col1, c.row2, c.col2));
        }
        // Initial evaluation is typically = 0, assuming the starting board is neutral
        this.eval = 0;
        this.lastMove = null;
    }

    /**
     * Returns the number of empty cells in a specified row.
     *
     * @param row The row index.
     * @return The number of empty cells in the row.
     * @throws IllegalArgumentException If the row index is invalid.
     */
    public int getZerosInRow(int row) {
        if(row < 0 || row >= this.getSize()){
            throw  new IllegalArgumentException("Invalid row index: " + row);
        }
        int cpt = 0; // res
        for(int i = 0; i < this.getSize(); i++) {
            if (this.getValue(row, i) == 0) cpt++;
        }
        return cpt;
    }

    /**
     * Returns the number of empty cells in a specified column.
     *
     * @param col The column index.
     * @return The number of empty cells in the column.
     * @throws IllegalArgumentException If the column index is invalid.
     */
    public int getZerosInColumn(int col) {
        if(col < 0 || col >= this.getSize()){
            throw new IllegalArgumentException("Invalid column index: " + col);
        }
        int cpt = 0;    // res
        for(int i = 0; i < this.getSize(); i++) {
            if (this.getValue(i, col) == 0) cpt++;
        }
        return cpt;
    }

    /**
     * Checks if the board is completely filled.
     *
     * @return {@code true} if there are no empty cells, {@code false} otherwise.
     */
    public boolean isFull() {
        for (int i = 0; i < this.getSize() ; i++){
            // Check the counter, if any row has non-zero count, board is not full
            if (this.zerosInRows[i] != 0){
                return false;
            }
        }
        // all row zero counts are 0
        return true;
    }

    /**
     * Gets the evaluation score of the board.
     *
     * @return The current evaluation score.
     */
    public int getEval() {
        return eval;
    }

    /**
     * Sets the evaluation score of the board.
     *
     * @param eval The new evaluation score.
     */
    public void setEval(int eval) {
        this.eval = eval;
    }


    /**
     * Gets the last move played on the board.
     *
     * @return The last move.
     */
    public Move getLastMove() {
        return lastMove;
    }

    /**
     * Sets the last move played on the board.
     *
     * @param move The move to set.
     */
    public void setLastMove(Move move) {
        this.lastMove = move;
    }


    /**
     * Sets a value on the board and updates the evaluation score.
     *
     * @param move                The move to apply.
     * @param isMaximisingPlayer  {@code true} if the move is by the maximizing player.
     */
    public int setValue(Move move, boolean isMaximisingPlayer) {
        int row = move.getRow();
        int col = move.getCol();
        int value = move.getValue();
        int size = this.getSize();

        // Update the grid
        super.setValue(row, col, value);
        // Update Zero Counts: ensure these are called only when moving from 0 to non-zero
        decreaseZerosInRows(row);
        decreaseZerosInColumns(col);
        // Calculation of the evaluation Delta
        int delta = value; // base score
        int bns = size * size;  // Standard bonus

        // Add bonus for satisfying adjacent consecutive constraints
        if (this.hasConsecutiveConstraint(row, col, row + 1, col) ||
                this.hasConsecutiveConstraint(row, col, row - 1, col)
                || this.hasConsecutiveConstraint(row, col, row, col + 1) ||
                this.hasConsecutiveConstraint(row, col, row, col - 1)) {
            // Heuristic: Add a bonus (e.g., size) for placing a number involved in a constraint
            delta += size;
        }

        // bonus:we add bonus if this move completes a row, column, or subgrid
        if(this.isRowFilled(row)) delta += bns;
        if(this.isColumnFilled(col)) delta += bns;
        if (this.isSubgridFilled(row, col)) delta += bns;

        // penalisation: we add penalty if this move sets up the opponent (p2) to complete a unit on their next turn
        // This check assumes the move just made leaves exactly one zero remaining:
        // Check if row has only one zero remaining
        if (this.zerosInRows[row] == 1) delta -= bns;   // Penalty for leaving row almost full
        // Check if column has only one zero remaining
        if (this.zerosInColumns[col] == 1) delta -= bns;    // Penalty for leaving column almost full
        // Check if subgrid has only one zero remaining
        if (this.isSubgridAlmostFilled(row, col)) delta -= bns; // Penalty for leaving subgrid almost full

        // update total Evaluation Score based on player perspective
        if(isMaximisingPlayer){
            // Add delta if maximizing player (p1) made the move
            this.eval += delta;
        }else{
            // Subtract delta if minimizing player (p2) made the move
            this.eval -= delta;
        }
        // I record the move
        this.setLastMove(move);
        // Return the raw delta
        return delta;
    }

    /**
     * Reverses the effect of applying a move, restoring the board state and evaluation score.
     * This includes resetting the cell value to 0, updating zero counts, and adjusting the evaluation score.
     * @param move   The {@link Move} to undo. Must be the same move previously passed to `setValue`.
     * @param res  The exact evaluation delta that was returned by the corresponding `setValue` call.
     * @param player Indicates if the player who originally made the move (that is now being undone)
     *       was the maximizing player. This determines if the delta is subtracted or added back.
     */
    public void unSet(Move move, int res, boolean player) {
        int row = move.getRow();
        int col = move.getCol();
        int value = this.getValue(row, col);
        // basic check: ensure the value on the board matches the move being undone, if they don't match, problem
        if(move.getValue() != value){
            return;
        }
        // we restore evaluation score: reverse the addition and substraction
        if(player){
            // Subtract delta if maximizing player's (p1) move is undone
            this.eval -= res;
        }else{
            // Add delta back if minimizing player's (p2) move is undone
            this.eval += res;
        }
        // we restore the grid value
        super.setValue(row, col, 0);
        // we restore Zero Counts
        increaseZerosInRows(row);
        increaseZerosInColumns(col);
    }

    /**
     * Checks if a row is completely filled.
     *
     * @param row The row index.
     * @return {@code true} if the row is filled, {@code false} otherwise.
     */
    public boolean isRowFilled(int row) {
        return this.getZerosInRow(row) == 0;
    }

    /**
     * Checks if a column is completely filled.
     *
     * @param col The column index.
     * @return {@code true} if the column is filled, {@code false} otherwise.
     */
    public boolean isColumnFilled(int col) {
        return this.getZerosInColumn(col) == 0;
    }

    /**
     * Checks if the subgrid containing a cell is completely filled.
     *
     * @param row The row index.
     * @param col The column index.
     * @return {@code true} if the subgrid is filled, {@code false} otherwise.
     */
    public boolean isSubgridFilled(int row, int col) {
        int size = this.getSize();
        // Input validation
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IllegalArgumentException("Invalid coordinates: row=" + row + ", col=" + col);
        }
        int subGrid = (int) Math.sqrt(size);    // size of the subgrid (e.g., 3 for 9x9)
        // calculate top-left corner of the subgrid
        int newRow = (row / subGrid) * subGrid;
        int newCol = (col / subGrid) * subGrid;
        for(int i = newRow; i < newRow + subGrid; i++){
            for(int j = newCol; j < newCol + subGrid; j++){
                // if any cell in the subgrid is empty, the subgrid is not filled, so it false
                if(this.isCellEmpty(i, j)){
                    return false;
                }
            }
        }
        // if the loops complete without finding an empty cell
        return true;
    }

    /**
     * Decreases the count of empty cells in a row.
     *
     * @param row The row index.
     */
    public void decreaseZerosInRows(int row) { this.zerosInRows[row]--;}
    /**
     * Decreases the count of empty cells in a column.
     *
     * @param col The column index.
     */
    public void decreaseZerosInColumns(int col) {this.zerosInColumns[col]--;}

    /**
     * Increase the count of empty cells in a row.
     *
     * @param row The row index.
     */
    public void increaseZerosInRows(int row) {this.zerosInRows[row]++;}

    /**
     * Increase the count of empty cells in a column.
     *
     * @param col The column index.
     */
    public void increaseZerosInColumns(int col) {this.zerosInColumns[col]++;}

    /**
     * Checks if a cell is empty.
     *
     * @param row The row index.
     * @param col The column index.
     * @return {@code true} if the cell is empty, {@code false} otherwise.
     */
    public boolean isCellEmpty(int row, int col) {
        return this.getValue(row, col) == 0;
    }

    /**
     * Checks if the subgrid containing a cell is almost filled.
     *
     * @param row The row index.
     * @param col The column index.
     * @return {@code true} if the subgrid is almost filled, {@code false} otherwise.
     */
    private boolean isSubgridAlmostFilled(int row, int col) {
        int size = this.getSize();
        // Input validation
        if (row < 0 || row >= size || col < 0 || col >= size) {
            return false;
        }
        int subGrid = (int) Math.sqrt(size); // res 3
        int newRow = (row / subGrid) * subGrid;
        int newCol = (col / subGrid) * subGrid;
        int cpt = 0;
        for(int i = newRow; i < newRow + subGrid; i++){
            for(int j = newCol; j < newCol + subGrid; j++){
                if(this.isCellEmpty(i, j)){
                    cpt++;
                    // Optimization: if we find more than one empty cell it means that we can stop early
                    if(cpt > 1){
                        return false;
                    }
                }
            }
        }
        // The subgrid is almost filled if exactly one empty cell was found
        return cpt == 1;
    }
}