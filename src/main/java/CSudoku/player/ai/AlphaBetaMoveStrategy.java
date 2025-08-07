package CSudoku.player.ai;

import CSudoku.observers.AlphaBetaPruningObserver;
import CSudoku.board.CSudokuBoard;
import CSudoku.board.Move;
import CSudoku.player.MoveStrategy;
import CSudoku.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implements the Alpha-Beta pruning algorithm as a move selection strategy for an AI player.
 * This strategy explores the game tree like Minimax but prunes branches that cannot possibly
 * influence the final decision, significantly reducing the number of nodes visited compared to plain Minimax.
 * It uses an {@link EvaluatedSimulatedBoard} to efficiently manage board state and heuristic evaluation.
 */
public class AlphaBetaMoveStrategy implements MoveStrategy {
    //    Configuration

    private static final int MAX_DEPTH = 3; // Depth of the search tree
    private AlphaBetaPruningObserver observer;

    /**
     * Constructor for AlphaBetaMoveStrategy.
     * Initializes the observer for tracking the Alpha-Beta cuts and node visits.
     */

    //     Constructor
    public AlphaBetaMoveStrategy() {
        this.observer = new AlphaBetaPruningObserver(); // Initialize the observer
    }

    /**
     * Selects the best move using the Alpha-Beta pruning algorithm.
     *
     * @param board  The current state of the Sudoku board.
     * @param player The AI player making the move.
     * @return The best {@link Move} according to the Alpha-Beta evaluation.
     */
    @Override
    public Move selectMove(CSudokuBoard board, Player player) {
        // we record the node count before starting the search
        int bef = this.observer.getNodeCount();

        // cast the player to AIPlayer if specific AI methods are needed
        AIPlayer aiPlayer = (AIPlayer) player;

        // EvaluatedSimulatedBoard based on the current board state for the efficient evaluation
        EvaluatedSimulatedBoard srcBoard = new EvaluatedSimulatedBoard(board, player);

        // Generate all valid moves from the root position.
        List<Move> validMoves = getValidMoves(srcBoard, aiPlayer);
        // if no valid moves are possible return null and mini message.
        if(validMoves.isEmpty()){
            System.out.println("Alpha-Beta: No valid moves found.");
            return null;
        }
        // Initialize variables to track the best move and its score found so far.
        Move bestMove = null;
        // we start with the lowest possible score
        int bestValue = Integer.MIN_VALUE; // Maximizing player

        for(Move move: validMoves){
            // we apply the move tentatively to the board and get the evaluation delta
            int delta = srcBoard.setValue(move, true); // true == p1 or false == p2

            // We start the recursive Alpha-Beta search from the resulting board state:
            //    - Depth starts at MAX_DEPTH - 1 because one move has been made
            //    - Initial alpha is the best score found so far for the root player
            //    - Initial beta is the highest possible score (opponent = p2 starts with no constraints)
            //    - false means the next turn belongs to the minimizing player (opponent = p2)
            int value = alphaBeta(srcBoard, MAX_DEPTH - 1, bestValue, Integer.MAX_VALUE, false, aiPlayer);

            // undo the move to restore the board state for evaluating the next sibling move
            srcBoard.unSet(move, delta, true);
            // we update the best move if the evaluated score value for this move is bette than the best score found previously bestValue
            if(value > bestValue){
                bestValue = value;
                bestMove = move;
            }
        }
        // print statistics after the search is complete
        System.out.println("Recherche AlphaBeta terminée:");
        System.out.println("\tNoeuds visités: " + (this.observer.getNodeCount() - bef));
        System.out.println("\tNoeuds visités (en totale): " + this.observer.getNodeCount());
        System.out.println("\tCoupes alpha (en totale): " + observer.getAlphaCutCount());   // Pruning by MIN player
        System.out.println("\tCoupes beta (en totale): " + observer.getBetaCutCount());     // Pruning by MAX player
        System.out.println("Meilleur coup trouvé: " + bestMove + " | Score évalué : " + bestValue);
        /*
        // Petit verif:
        if(bestMove == null && !validMoves.isEmpty()){
            System.out.println("Aucun meilleur coup n'a pas été sélectionné, retour du premier coup valide.");
            bestMove = validMoves.get(0);
        }
        */
        return bestMove;    // the best move
    }

    /**
     * Retrieves all valid moves for the AI player.
     *
     * @param board  The current state of the Sudoku board.
     * @param player The AI player.
     * @return A list of all valid {@link Move} objects.
     */
    protected List<Move> getValidMoves(EvaluatedSimulatedBoard board, AIPlayer player) {
        // Increment node count for visiting this state to generate moves
        this.observer.incrementNodeCount();
        List<Move> validMoves = new ArrayList<>();
        int size = board.getSize();;
        for(int i = 0; i < size; i++){
            for(int j = 0; j < size; j++){
                // If the cell is empty
                if(board.isCellEmpty(i, j)){
                    for(int v = 1; v <= size; v++){
                        Move m = new Move(i, j, v);
                        // is valid?
                        if(player.isValidMove(board, m)){
                            // Add to the list if valid
                            validMoves.add(m);
                        }
                    }
                }
            }
        }
        return validMoves;  // the list of generated valid moves
    }

    /**
     * Implements the Alpha-Beta pruning algorithm recursively to evaluate moves.
     *
     * @param board              The current state of the board.
     * @param depth              The remaining search depth.
     * @param alpha              The best value for the maximizing player so far.
     * @param beta               The best value for the minimizing player so far.
     * @param isMaximizingPlayer True if the current player is the maximizing player.
     * @param player             The AI player.
     * @return The evaluation score of the board state.
     */
    protected int alphaBeta(EvaluatedSimulatedBoard board, int depth, int alpha, int beta, boolean isMaximizingPlayer, AIPlayer player) {
        //    Base Cases: Leaf Node or Terminal State
        // if maximum depth is reached or the board is full, return the static evaluation
        if(depth == 0 || board.isFull()){
            return board.getEval();
        }
        // Valides moves
        List<Move> validMoves = getValidMoves(board, player);
        if(validMoves.isEmpty()){
            // if it's max's turn p1, and they have no moves, it's bad for MAX (return low score)
            // if it's min's turn p2, and they have no moves, it's good for MAX (return high score)
            return isMaximizingPlayer ? Integer.MIN_VALUE + depth : Integer.MAX_VALUE - depth;
        }

                                        //     Move Ordering
        // Optimization Part:
        // before it was 11382 noeuds visités, after the optimization is 7750 for the first step
        // sort moves heuristically to explore potentially better moves first
        // this significantly increases the effectiveness of Alpha-Beta pruning
        sortValidMoves(validMoves, board, isMaximizingPlayer);



        int bestValue;  // the best score found for this node
        if (isMaximizingPlayer) {
            // Initialize with worst score for MAX
            bestValue = Integer.MIN_VALUE;
            for (Move move : validMoves) {
                // apply move, recurse, undo move
                int delta = board.setValue(move, isMaximizingPlayer);
                // recursive call for the opponent (min player), passing current alpha/beta
                int eval = alphaBeta(board, depth - 1, alpha, beta, false, player); // Recurse with the MODIFIED board
                board.unSet(move, delta, isMaximizingPlayer); // restore board state

                // Update bestValue
                bestValue = Math.max(bestValue, eval);
                alpha = Math.max(alpha, bestValue); // Update alpha (best score guaranteed for MAX along this path)

                                                //     Beta Pruning
                // If the current best score for max p1 (alpha) is >= the best score min is guaranteed (beta),
                // then min player will never choose this path, so we can stop exploring
                if (beta <= alpha) { // Pruning condition
                    observer.incrementBetaCut(); // Record the pruning event
                    break; // Beta cut-off
                }
            }
        } else { // Minimizing Player
            // Initialize with worst score for MIN
            bestValue = Integer.MAX_VALUE;
            for (Move move : validMoves) {
                // apply move, recurse, undo move
                int delta = board.setValue(move, isMaximizingPlayer);
                // recursive call for the opponent (max player), passing current alpha/beta
                int eval = alphaBeta(board, depth - 1, alpha, beta, true, player); // Recurse
                board.unSet(move, delta, isMaximizingPlayer);   // restore board state

                // Update bestValue
                bestValue = Math.min(bestValue, eval);
                beta = Math.min(beta, bestValue); // Update beta (best score guaranteed for min along this path)

                                            //    Alpha Pruning
                // If the best score min p2 can achieve (beta) is <= the score max p1 is guaranteed (alpha),
                // then max  player will never choose this path, so we can stop exploring siblings.
                if (beta <= alpha) { // Pruning condition
                    observer.incrementAlphaCut();   // Record the pruning event
                    break; // Alpha cut-off
                }
            }
        }
        return bestValue;   // the best value found for this node
    }

    /**
     * Returns the name of the strategy, which in this case is "AlphaBeta".
     * <p>
     * This method is part of the {@link MoveStrategy} interface and is used to retrieve
     * the name of the strategy being implemented. In this case, it returns the string "AlphaBeta",
     * which indicates that the AlphaBeta Move strategy is used for selecting moves.
     * </p>
     *
     * @return The name of the strategy, which is {@code "AlphaBeta"}.
     */
    @Override
    public String getName() {
        return "AlphaBeta";
    }

    /**
     * Sorts the given list of moves in place based on a heuristic evaluation.
     * Aims to put potentially better moves earlier in the list to improve pruning efficiency.
     * The sorting order depends on whether the current player is maximizing or minimizing.
     *
     * @param moves             The list of {@link Move} objects to sort.
     * @param board             The current {@link EvaluatedSimulatedBoard} state for heuristic calculation.
     * @param isMaximizingPlayer True if the current player aims to maximize the score.
     */
    private void sortValidMoves(List<Move> moves, EvaluatedSimulatedBoard board, boolean isMaximizingPlayer) {
        // temporary list to hold moves along with their heuristic scores
        List<ScoredMoves> scoredMoves = new ArrayList<>();
        for (Move move : moves) {
            // calculate a quick heuristic score for the move without the deep recursion
            int heuristicScore = calculateHeuristicScore(board, move, isMaximizingPlayer);
            scoredMoves.add(new ScoredMoves(move, heuristicScore));
        }

        // Sort: Higher scores first for maximizing, lower scores first for minimizing:
        //  - if maximizing p1, sort in descending order (higher scores first)
        //  - if minimizing p2, sort in ascending order (lower scores first)
        Collections.sort(scoredMoves, (sm1, sm2) ->
                isMaximizingPlayer ? Integer.compare(sm2.score, sm1.score) : Integer.compare(sm1.score, sm2.score)
        );

        // Update the original list with the sorted moves
        moves.clear();
        for (ScoredMoves sm : scoredMoves) {
            moves.add(sm.move);
        }
    }

    /**
     * Calculates a simple heuristic score for a given move without performing a full search.
     * This score is used for move ordering. It temporarily applies the move, calculates
     * immediate bonuses/penalties, and then undoes the move.
     *
     * @param board             The current {@link EvaluatedSimulatedBoard} state.
     * @param move              The {@link Move} to evaluate heuristically.
     * @param isMaximizingPlayer True if the evaluation perspective is for the maximizing player.
     * @return A heuristic score estimating the immediate value of the move.
     */
    private int calculateHeuristicScore(EvaluatedSimulatedBoard board, Move move, boolean isMaximizingPlayer) {
        int row = move.getRow();
        int col = move.getCol();
        int value = move.getValue();
        int size = board.getSize();
        int delta = value; // Base score starts with the base value of the number placed

        // Store original value to restore later
        int originalValue = board.getValue(row, col);
        board.setValue(row, col, value); // Temporarily set for checks

        int bns = size * size;  // Bonus/penalty value

        // Check for constraint bonus simple heuristic
        if (hasAdjacentConstraint(board, row, col)) {
            delta += size; // Or some other heuristic weight
        }

        // if this is the last empty cell
        if (board.getZerosInRow(row) == 1) delta += bns; // Becomes 0 after move
        if (board.getZerosInColumn(col) == 1) delta += bns; // Becomes 0 after move
        if (isSubgridAlmostFilledAndThisIsLast(board, row, col)) delta += bns;

        // Restore the original value on the board
        board.setValue(row, col, originalValue); // Restore original value
        return isMaximizingPlayer ? delta : -delta; // Return score relative to current player
    }

    /**
     * A simple class to pair a Move with its calculated heuristic score for sorting purposes.
     */
    protected static class ScoredMoves {
        Move move;
        int score;

        /**
         * Constructs a ScoredMoves.
         * @param m The move.
         * @param s The associated heuristic score.
         */
        ScoredMoves(Move m, int s) { move = m; score = s; }
    }

    /**
     * Checks if the cell at (row, col) has any defined consecutive constraints with its immediate neighbors.
     *
     * @param board The current board state.
     * @param row The row index of the cell.
     * @param col The column index of the cell.
     * @return True if a consecutive constraint exists with any adjacent cell, false otherwise.
     */

    private boolean hasAdjacentConstraint(CSudokuBoard board, int row, int col) {
        // N, S, E, W neighbors for constraints defined in the board
        return board.hasConsecutiveConstraint(row, col, row + 1, col) ||
                board.hasConsecutiveConstraint(row, col, row - 1, col) ||
                board.hasConsecutiveConstraint(row, col, row, col + 1) ||
                board.hasConsecutiveConstraint(row, col, row, col - 1);
    }

    /**
     * Checks if the subgrid containing the cell at (row, col) has exactly one empty cell remaining,
     * and that empty cell is the one at (row, col) itself (meaning placing a value here would fill it).
     * @param board The current evaluated board state.
     * @param row The row index of the cell.
     * @param col The column index of the cell.
     * @return True if placing a value at (row, col) would complete the subgrid, false otherwise.
     */

    private boolean isSubgridAlmostFilledAndThisIsLast(EvaluatedSimulatedBoard board, int row, int col) {
        // Check if the subgrid containing (row, col) has exactly one zero, which is at (row, col)
        int size = board.getSize();
        int subGridSize = (int) Math.sqrt(size);
        int startRow = (row / subGridSize) * subGridSize;
        int startCol = (col / subGridSize) * subGridSize;
        int zeroCount = 0;  // counter
        for (int i = startRow; i < startRow + subGridSize; i++) {
            for (int j = startCol; j < startCol + subGridSize; j++) {
                if (board.isCellEmpty(i, j)) {
                    zeroCount++;
                    // Optimization: if more than one empty cell is found, it can't be "almost filled" in the way we need
                    if (zeroCount > 1) return false; // More than one zero
                }
            }
        }
        return zeroCount == 1 && board.isCellEmpty(row, col); // Exactly one zero, and it's the target cell
    }
}
