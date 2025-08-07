package CSudoku.player.ai;

import CSudoku.board.CSudokuBoard;
import CSudoku.board.Move;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong; // For potentially large cut counts
import CSudoku.player.MoveStrategy;
import CSudoku.player.Player;
import CSudoku.referee.Referee; // Import Referee

import java.util.*;
import java.util.concurrent.*;

/**
 * Implements an Alpha-Beta search algorithm using root parallelization, IDDFS,
 * Transposition Tables, and Move Ordering, while retaining the class name MinimaxMoveStrategy.
 * * NOTE: This class is named MinimaxMoveStrategy but implements Alpha-Beta logic internally, if you need to check the minimax algorithm
 * you can find it on the GitHub: commit: Update before the deadline without optimizations 12.04.2025
 */
// Class name remains MinimaxMoveStrategy
public class MinimaxMoveStrategy implements MoveStrategy {

    //     Configuration
    private static final int MAX_SEARCH_DEPTH = 9; // Max depth for IDDFS
    private static final long DEFAULT_TIME_LIMIT_MS = 2000; // Default time limit
    private final int numThreads;

    //     Thread-Safe State
    private final AtomicInteger nodeCounter;
    // Add counters for Alpha-Beta cuts
    /** Counter for the number of alpha cutoffs (pruning by MIN player). Thread-safe. */
    private final AtomicLong alphaCuts;
    /** Counter for the number of beta cutoffs (pruning by MAX player). Thread-safe. */
    private final AtomicLong betaCuts;
    /** Transposition table to store previously computed results. Thread-safe. */
    private final ConcurrentHashMap<Long, TranspositionTableEntrys> transpositionTable;
    /** Service to manage the pool of worker threads for parallel root search. */
    private final ExecutorService executorService;

    // --- Per-Search State ---
    private long timeLimitMillis = DEFAULT_TIME_LIMIT_MS; // time limit for the search in milliseconds
    private volatile boolean timeUp; // Volatile flag indicating if the time limit has been reached during the current search
    private long searchStartTimeNanos; // Start time (in nanoseconds) of the current `selectMove` call, used for time limit checks


    //    Transposition Table Entry
    /**
     * Represents an entry in the transposition table, storing the result of a previous search.
     * Includes the score, the depth at which it was calculated, and a flag indicating the
     * type of bound (exact score, lower bound, or upper bound).
     */
    private static class TranspositionTableEntrys {
        enum Flag { EXACT, LOWER_BOUND, UPPER_BOUND } // Flags for bounds
        final int score;    // The evaluated score for the board state
        final int depth;    // The remaining depth at which this score was determined
        final Flag flag;    // The type of score bound (EXACT, LOWER_BOUND, or UPPER_BOUND)

        /**
         * Constructs a new TranspositionTableEntrys.
         * @param score The score value.
         * @param depth The remaining search depth when this entry was created.
         * @param flag The type of bound represented by the score.
         */
        TranspositionTableEntrys(int score, int depth, Flag flag) { // Added flag
            this.score = score;
            this.depth = depth;
            this.flag = flag;
        }
    }

    /**
     * Constructor: Initializes thread pool and counters
     * Determines the number of threads based on available processors
     */
    public MinimaxMoveStrategy() {
        this.nodeCounter = new AtomicInteger(0);
        this.alphaCuts = new AtomicLong(0); // Initialize cut counters
        this.betaCuts = new AtomicLong(0);
        // Initialize TT with a reasonable capacity and concurrency level based on threads
        this.transpositionTable = new ConcurrentHashMap<>(32 * 1024, 0.75f, Runtime.getRuntime().availableProcessors() * 4);
        this.numThreads = Runtime.getRuntime().availableProcessors();
        // Using a fixed thread pool
        this.executorService = Executors.newFixedThreadPool(numThreads);
    }

    /**
     * Allows setting a custom time limit for the search.
     * @param ms The time limit in milliseconds. Must be positive.
     */
    public void setTimeLimit(long ms) {
        this.timeLimitMillis = ms > 0 ? ms : DEFAULT_TIME_LIMIT_MS;
    }

    @Override
    public String getName() {
        return "Minimax Optimized";
    }

    /**
     * Selects the best move using parallelized Iterative Deepening Alpha-Beta search.
     * Manages the IDDFS loop, parallel task submission for root moves, result collection,
     * time limiting, and selection of the best move from the deepest completed iteration.
     *
     * @param board  The current state of the Sudoku board.
     * @param player The AI player making the move.
     * @return The best {@link Move} found within the constraints, or null if no moves are possible.
     */
    @Override
    public Move selectMove(CSudokuBoard board, Player player) {
        //     Initialization for this search
        this.nodeCounter.set(0);
        this.alphaCuts.set(0); // Reset counters
        this.betaCuts.set(0);
        this.timeUp = false;
        this.searchStartTimeNanos = System.nanoTime();

        // Create the initial evaluated board state
        EvaluatedSimulatedBoard rootBoard = new EvaluatedSimulatedBoard(board, player);

        Move bestMoveOverall = null;    // Best move found across all completed depths
        // Root acts as MAX node, this is root's alpha (best score found so far)
        int bestValueOverall = Integer.MIN_VALUE;
        // Stores scores from depth d-1 to order moves for depth d
        Map<Move, Integer> previousScores = new HashMap<>();

        //    Get and Validate Root Moves
        List<Move> rootMoves = getValidMoves(rootBoard, player);
        if (rootMoves.isEmpty()) {
            return null;
        }

        System.out.println("Starting " + getName() + " IDDFS search for Player " + (player == Referee.getInstance().getPlayer1() ? "1" : "2") + "...");

        // --- IDDFS Loop ---
        for (int currentDepth = 1; currentDepth <= MAX_SEARCH_DEPTH; currentDepth++) {

            this.transpositionTable.clear();
            long depthStartTime = System.nanoTime();
            int nodesThisDepthStart = nodeCounter.get();    // Track nodes for this specific depth
            System.out.println("IDDFS: Starting Depth " + currentDepth);

            //     Move Ordering
            // Uses results from previous depth (if any) and heuristics for better ordering
            final Map<Move, Integer> scoresThisDepth = new ConcurrentHashMap<>(); // Store scores from this depth
            sortMovesAdvanced(rootMoves, rootBoard, bestMoveOverall, previousScores);   // Sort based on previous results
            previousScores.clear(); // Will be populated by results from this depth

            Move bestMoveThisDepth = null;  // Best move found specifically for this depth
            // Use alpha for tracking best score at this depth's root
            int alphaThisDepth = Integer.MIN_VALUE;

            List<Future<Integer>> futureResults = new ArrayList<>();    // To hold results from parallel tasks
            List<Move> submittedMoves = new ArrayList<>();  // To map results back to moves
            List<Callable<Integer>> tasks = new ArrayList<>();  // Tasks to be submitted

            // Create Parallel Tasks for Root Moves
            for (Move move : rootMoves) {
                if (timeUp) break;  // Check global time limit flag before creating task
                // Deep copy of the board is important for thread safety
                EvaluatedSimulatedBoard boardCopy = new EvaluatedSimulatedBoard(rootBoard);
                boardCopy.setValue(move, true); // Apply the root move (root is max p1 player)
                final int depthForTask = currentDepth;  // Effectively final depth for lambda

                // Create a task that will run the recursive alpha-beta search for this move
                Callable<Integer> task = () -> minimax( // Call the internal alpha-beta function (named minimax)
                        boardCopy,
                        depthForTask - 1,   // Remaining depth
                        Integer.MIN_VALUE, // Initial alpha for the recursive call
                        Integer.MAX_VALUE, // Initial beta for the recursive call
                        false, // Next turn is for the MIN player
                        player // Pass player if needed by evaluation
                );
                tasks.add(task);
                submittedMoves.add(move);
            }

            // Submit tasks if time allows
            if (!timeUp && !tasks.isEmpty()) {
                try {
                    // Calculate remaining time for invokeAll timeout
                    long remainingTime = timeLimitMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - searchStartTimeNanos);
                    if (remainingTime <= 0) { timeUp = true; } // Timeout occurred before even starting tasks for this depth
                    // Submit all tasks and wait for completion or timeout
                    else { futureResults.addAll(executorService.invokeAll(tasks, remainingTime, TimeUnit.MILLISECONDS)); }
                } catch (InterruptedException e) {  timeUp = true;  Thread.currentThread().interrupt(); }
                // Cancel remaining tasks that might not have started
            }

            // Collect results if not already timed out
            if (!timeUp) {
                boolean completedFully = true;  // Flag to track if this depth finished without issues
                try {
                    for (int i = 0; i < futureResults.size(); i++) {
                        Future<Integer> future = futureResults.get(i);
                        Move correspondingMove = submittedMoves.get(i);
                        // Check if the task was cancelled
                        if (future.isCancelled()) {  timeUp = true; completedFully = false; continue; }
                        // Should ideally be done after invokeAll, but check defensively
                        if (!future.isDone()) {  timeUp = true; completedFully = false; continue; }

                        try {
                            // Block and get the result from the completed task
                            int value = future.get();
                            // Store score from this depth for potential use in sorting the next depth
                            scoresThisDepth.put(correspondingMove, value);

                            // Update the best move for this specific depth
                            if (value > alphaThisDepth) {
                                // alphaThisDepth holds the best max score for this depth's root
                                alphaThisDepth = value;
                                bestMoveThisDepth = correspondingMove;
                            }
                        } catch (ExecutionException | CancellationException e) {
                            completedFully = false;
                            if (e instanceof ExecutionException) e.printStackTrace();
                        }
                    }
                } catch (InterruptedException e) {
                    timeUp = true;
                    Thread.currentThread().interrupt();
                }

                // Update Overall Best Move only if the entire depth search completed without being cut short by time or errors
                if (completedFully && !timeUp) {
                    bestMoveOverall = bestMoveThisDepth;    // Update the globally best move
                    bestValueOverall = alphaThisDepth;      // Update the globally best score (root alpha)
                    previousScores.putAll(scoresThisDepth); // Store scores for sorting next depth
                    long depthEndTime = System.nanoTime();
                    int nodesThisDepth = nodeCounter.get() - nodesThisDepthStart;
                    System.out.println("IDDFS (AlphaBeta): Depth " + currentDepth + " complete. Best: " + bestMoveOverall + " | Score: " + bestValueOverall + " | Time: " + TimeUnit.NANOSECONDS.toMillis(depthEndTime - depthStartTime) + "ms | Nodes: " + nodesThisDepth);
                } else {
                    System.out.println("IDDFS: Depth " + currentDepth + " incomplete. Using results from depth " + (currentDepth - 1));
                    timeUp = true;
                }
            }

            // Exit IDDFS loop if time is up or max depth reached
            if (timeUp) {
                System.out.println("IDDFS: Exiting search loop.");
                break;
            }
        } // End IDDFS Loop

        //    Final Output
        long searchEndTimeNanos = System.nanoTime();
        System.out.println(getName() + " Search Finished.");
        System.out.println("\tTotal Nodes Visited: " + nodeCounter.get());
        System.out.println("\tAlpha Cuts: " + alphaCuts.get()); // Log cuts
        System.out.println("\tBeta Cuts: " + betaCuts.get());   // Log cuts
        System.out.println("\tTotal Time: " + TimeUnit.NANOSECONDS.toMillis(searchEndTimeNanos - searchStartTimeNanos) + " ms");

        // Fallback if no move was ever selected
        if (bestMoveOverall == null && !rootMoves.isEmpty()) { bestMoveOverall = rootMoves.get(0);}
        System.out.println("\tSelected Move: " + bestMoveOverall + " | Final Evaluated Score: " + bestValueOverall);

        return bestMoveOverall; // Return the best move from the deepest fully completed search
    }

    /**
     * The recursive Alpha-Beta function (internally named minimax).
     * Performs the core depth-first search with pruning.
     *
     * @param board              The current board state (modified and restored during search).
     * @param depth              Remaining search depth.
     * @param alpha              Best score guaranteed for MAX player so far on this path.
     * @param beta               Best score guaranteed for MIN player so far on this path.
     * @param isMaximizingPlayer True if this node represents MAX player's turn.
     * @param player             The original Player object (passed for context if needed by helpers/eval).
     * @return The best score achievable from this node within the alpha-beta bounds.
     */
    private int minimax(EvaluatedSimulatedBoard board, int depth, int alpha, int beta, boolean isMaximizingPlayer , Player player) {
        // Time Limit Check: check periodically to avoid excessive overhead on every node
        if (timeUp) return isMaximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        if (nodeCounter.get() % 2048 == 0 && (System.nanoTime() - searchStartTimeNanos) > timeLimitMillis * 1_000_000L) {
            timeUp = true; return isMaximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        nodeCounter.incrementAndGet();  // Count node visit
        int originalAlpha = alpha; // Store original alpha for TT bound type calculation
        long boardHash = calculateBoardHash(board, isMaximizingPlayer);

        // TT Lookup with Flags
        TranspositionTableEntrys ttEntry = transpositionTable.get(boardHash);
        if (ttEntry != null && ttEntry.depth >= depth) {
            // Use the stored information if it's deep enough
            switch (ttEntry.flag) {
                case EXACT:       return ttEntry.score; // Found exact score
                case LOWER_BOUND: alpha = Math.max(alpha, ttEntry.score); break;     // Found a lower bound, raise alpha
                case UPPER_BOUND: beta = Math.min(beta, ttEntry.score); break;  // Found an upper bound, lower beta
            }
            // Check if the TT entry itself causes a cutoff
            if (beta <= alpha) {
                if (isMaximizingPlayer) betaCuts.incrementAndGet(); else alphaCuts.incrementAndGet();
                return ttEntry.score;   // Return the score from TT that caused the cutoff
            }
        }

        // Base Cases: reached maximum depth or the board is full
        if (depth == 0 || board.isFull()) { return board.getEval(); }

        //     Generate and Order Moves
        List<Move> validMoves = getValidMoves(board, player); // Use Referee-based method
        if (validMoves.isEmpty()) { return isMaximizingPlayer ? Integer.MIN_VALUE + (MAX_SEARCH_DEPTH - depth) : Integer.MAX_VALUE - (MAX_SEARCH_DEPTH - depth); }

        // Sort moves to improve pruning effectiveness (essential for Alpha-Beta)
        sortMovesByHeuristic(validMoves, board, isMaximizingPlayer); // Simple sort okay here

        // Exploration with Pruning
        int bestValue;
        if (isMaximizingPlayer) {
            // Initialize best score for MAX
            bestValue = Integer.MIN_VALUE;
            for (Move move : validMoves) {
                if (timeUp) break; // Check time before recursing
                // Apply move, recurse, undo move (make/unmake pattern)
                int delta = board.setValue(move, true);
                // Pass alpha/beta down
                int value = minimax(board, depth - 1, alpha, beta, false, player); // Recursive call for MIN
                board.unSet(move, delta, true);
                // Check time immediately after returning from recursion
                if (timeUp) { bestValue = Integer.MIN_VALUE; break; }

                bestValue = Math.max(bestValue, value); // Update best score found for MAX
                alpha = Math.max(alpha, bestValue); // Update MAX's lower bound (alpha)

                // Pruning Check
                if (beta <= alpha) {
                    betaCuts.incrementAndGet();    // Increment count for pruning events by MAX
                    break;  // Prune remaining sibling moves
                }
            }
        } else { // Minimizing Player
            // Initialize best score for MIN
            bestValue = Integer.MAX_VALUE;
            for (Move move : validMoves) {
                if (timeUp) break; // Check time before recursing
                // Apply move, recurse, undo move
                int delta = board.setValue(move, false);
                // Pass alpha/beta down
                int value = minimax(board, depth - 1, alpha, beta, true, player);   // Recursive call for MAX
                board.unSet(move, delta, false);
                // Check time immediately after returning from recursion
                if (timeUp) { bestValue = Integer.MAX_VALUE; break; } // Check time after returning

                bestValue = Math.min(bestValue, value); // Update best score found for MIN
                beta = Math.min(beta, bestValue); // Update MIN's upper bound (beta)

                // Pruning Check
                if (beta <= alpha) {
                    alphaCuts.incrementAndGet();    // Increment count for pruning events by MIN
                    break;  // Prune remaining sibling moves
                }
            }
        }

        // TT Store with Flags
        if (!timeUp) {
            // Determine the correct flag based on the result relative to the original bounds
            TranspositionTableEntrys.Flag flag;
            if (bestValue <= originalAlpha)       flag = TranspositionTableEntrys.Flag.UPPER_BOUND; // Failed low (didn't raise alpha)
            else if (bestValue >= beta)           flag = TranspositionTableEntrys.Flag.LOWER_BOUND; // Failed high (exceeded beta)
            else                                  flag = TranspositionTableEntrys.Flag.EXACT;   // Score is within the [alpha, beta] window

            // Create new entry and store it using compute for atomic update
            TranspositionTableEntrys newEntry = new TranspositionTableEntrys(bestValue, depth, flag);
            transpositionTable.compute(boardHash, (key, oldEntry) ->
                    (oldEntry == null || newEntry.depth >= oldEntry.depth) ? newEntry : oldEntry
            );
        }
        // Return the best value found, or a default timeout value if aborted
        return timeUp ? (isMaximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE) : bestValue;
    }

    /**
     * Retrieves all valid moves for the AI player.
     *
     * @param board  The current state of the Sudoku board.
     * @param player The AI player.
     * @return A list of all valid {@link Move} objects.
     */
    private List<Move> getValidMoves(CSudokuBoard board, Player player) {
        List<Move> validMoves = new ArrayList<>();
        int size = board.getSize();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (board.isCellEmpty(i, j)) {
                    for (int v = 1; v <= size; v++) {
                        Move m = new Move(i, j, v);
                        if (player.isValidMove(board, m)) {
                            validMoves.add(m);
                        }
                    }
                }
            }
        }
        return validMoves;
    }

    /**
     * Sorts moves for the Root of the IDDFS search. Prioritizes:
     * 1) Best move from previous iteration.
     * 2) Moves with higher scores from previous iteration.
     * 3) Heuristic evaluation.
     * @param moves List of moves to sort.
     * @param board Current board state for heuristic calculation.
     * @param previousBest The best move found in the last completed IDDFS iteration.
     * @param previousScores Map of moves to scores from the last completed IDDFS iteration.
     */
    private void sortMovesAdvanced(List<Move> moves, EvaluatedSimulatedBoard board, Move previousBest, Map<Move, Integer> previousScores) {
        moves.sort((m1, m2) -> {
            int score1 = Integer.MIN_VALUE;
            int score2 = Integer.MIN_VALUE;

            // Prioritize previous best move
            if (m1.equals(previousBest)) score1 = Integer.MAX_VALUE;
            if (m2.equals(previousBest)) score2 = Integer.MAX_VALUE;
            if (score1 != Integer.MIN_VALUE || score2 != Integer.MIN_VALUE) {
                return Integer.compare(score2, score1); // MAX_VALUE comes first
            }

            // Use scores from previous iteration if available
            boolean score1Prev = previousScores.containsKey(m1);
            boolean score2Prev = previousScores.containsKey(m2);
            if (score1Prev || score2Prev) {
                score1 = previousScores.getOrDefault(m1, Integer.MIN_VALUE);
                score2 = previousScores.getOrDefault(m2, Integer.MIN_VALUE);
                return Integer.compare(score2, score1); // Higher previous score comes first
            }

            // Fallback to heuristic score (expensive, do last)
            // Using a temporary board copy for safety during heuristic calculation if needed,
            // but calculateHeuristicScore modifies and restores internally.
            EvaluatedSimulatedBoard tempBoard = board; // Or new EvaluatedSimulatedBoard(board) if worried
            score1 = calculateHeuristicScore(tempBoard, m1, true); // Assuming root is maximizing
            score2 = calculateHeuristicScore(tempBoard, m2, true);
            return Integer.compare(score2, score1); // Higher heuristic score comes first
        });
    }

    /**
     * Sorts moves using a simple heuristic score. Used within the recursive search.
     * Sort order depends on whether it's MAX or MIN player's turn.
     *
     * @param moves List of moves to sort.
     * @param board Current board state for heuristic calculation.
     * @param isMaximizingPlayer True if the current node is maximizing.
     */
    private void sortMovesByHeuristic(List<Move> moves, EvaluatedSimulatedBoard board, boolean isMaximizingPlayer) {
        List<ScoredMoves> scoredMoves = new ArrayList<>(moves.size());
        // Use a temporary copy for heuristic calculation if state modification is complex/unsafe concurrently
        EvaluatedSimulatedBoard tempBoard = new EvaluatedSimulatedBoard(board); // Safer for heuristic calc
        for (Move move : moves) {
            int heuristicScore = calculateHeuristicScore(tempBoard, move, isMaximizingPlayer);
            scoredMoves.add(new ScoredMoves(move, heuristicScore));
        }
        // Sort based on the player whose turn it is at this node
        Collections.sort(scoredMoves, (sm1, sm2) ->
                isMaximizingPlayer ? Integer.compare(sm2.score, sm1.score) : Integer.compare(sm1.score, sm2.score)); // Min player prefers lower scores
        moves.clear();
        for (ScoredMoves sm : scoredMoves) moves.add(sm.move);
    }

    /**
     * Calculates a heuristic score for move ordering. Temporarily modifies the board.
     * @param board Board state.
     * @param move Move to evaluate.
     * @param isMaximizingPlayer Perspective for score sign.
     * @return Heuristic score delta.
     */
    private int calculateHeuristicScore(EvaluatedSimulatedBoard board, Move move, boolean isMaximizingPlayer) {
        int delta = board.setValue(move, isMaximizingPlayer); // Temporarily apply
        board.unSet(move, delta, isMaximizingPlayer);       // Undo
        // Return score relative to the player *evaluating* this node
        return isMaximizingPlayer ? delta : -delta;
    }

    /**
     * Calculates board hash using Objects.hash for potentially better distribution.
     * @param board Current board state.
     * @param isMaximizingPlayer Whose turn it is.
     * @return The calculated hash value.
     */
    private long calculateBoardHash(CSudokuBoard board, boolean isMaximizingPlayer) {
        long hash = 17; int size = board.getSize();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                hash = 31 * hash + Objects.hash(i, j, board.getValue(i, j));
            }
        }
        hash = 31 * hash + (isMaximizingPlayer ? 1 : 2); return hash;
    }

    // Helper class for sorting
    private static class ScoredMoves {
        final Move move;
        final int score;
        ScoredMoves(Move m, int s) { move = m; score = s; }
    }

    /**
     * Call this method when the game ends or the strategy instance is no longer needed
     * to properly shut down the thread pool.
     */
    public void shutdownExecutor() {
        System.out.println("Shutting down Minimax ExecutorService...");
        executorService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Executor service did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
        System.out.println("Minimax ExecutorService shut down.");
    }
    /** Finalizer fallback for executor shutdown. */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!executorService.isShutdown()) {
                System.err.println("MinimaxMoveStrategy finalize: ExecutorService was not shut down. Forcing shutdown.");
                shutdownExecutor();
            }
        } finally {
            super.finalize();
        }
    }
}