package CSudoku.player.ai;

import CSudoku.board.CSudokuBoard;
import CSudoku.board.Move;
import CSudoku.player.MoveStrategy;
import CSudoku.player.Player;
import CSudoku.referee.Referee;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implements the Monte Carlo Tree Search (MCTS) algorithm as a move selection strategy.
 * MCTS builds a search tree by selectively exploring promising moves based on random simulations (rollouts).
 * It balances exploration (trying new moves) and exploitation (focusing on known good moves).
 * This class represents both the overall strategy configuration and the individual nodes within the MCTS tree.
 */

public class MCTSStrategy implements MoveStrategy {
    //    Node State
    EvaluatedSimulatedBoard state; // The board state associated with this node in the MCTS tree
    MCTSStrategy parent;    // Reference to the parent node in the tree (null for the root)
    List<MCTSStrategy> children;    // List of child nodes that have been expanded from this node
    Move move;  // The move that led from the parent node to this node (null for the root)
    int visitCount; // Number of times this node (and its descendants) have been visited during simulations
    double totalScore;  // The cumulative score obtained from simulations passing through this node
    List<Move> untriedMoves;   // List of valid moves from this node's state that have not yet been expanded into child nodes
    boolean isPlayer1TurnAtNode;    // Flag indicating if it was Player 1's turn at the time this node was created
    AIPlayer aiPlayerContext;   // Context for the AI player, potentially needed for validation

    /** Maximum number of simulations to run per move selection. Higher values lead to stronger play but take longer. */
    private static int simulationLimit = 100000;    // Default value
    private static final double EXPLORATION_CONSTANT = 1.414; // sqrt(2)
    private static Referee referee = Referee.getInstance();
    private static final Random random = ThreadLocalRandom.current();

    //     Constructors

    /**
     * Internal constructor used to create nodes within the MCTS tree during expansion.
     * Initializes node state based on the parent and the move taken.
     * @param state           The board state for this new node.
     * @param parent          The parent node from which this node is expanded.
     * @param move            The move that led from the parent to this state.
     * @param isPlayer1Turn   True if it's Player 1's turn at this new node's state.
     * @param playerContext   The AI player context.
     */
    protected MCTSStrategy(EvaluatedSimulatedBoard state, MCTSStrategy parent, Move move, boolean isPlayer1Turn, AIPlayer playerContext) {
        this.state = state;
        this.parent = parent;
        this.children = new ArrayList<>();
        this.move = move;
        this.visitCount = 0;
        this.totalScore = 0.0;
        this.isPlayer1TurnAtNode = isPlayer1Turn;
        this.aiPlayerContext = playerContext;
        this.untriedMoves = getValidMoves(state, this.aiPlayerContext);
    }

    /**
     * Public constructor used to configure the MCTS strategy instance before starting a game.
     * Sets the static simulation limit shared by all nodes created by this strategy instance.
     * @param simulationLimit The number of simulations to run per `selectMove` call. Must be positive.
     * @throws IllegalArgumentException if simulationLimit is not positive.
     */
    public MCTSStrategy(int simulationLimit) {
        if (simulationLimit <= 0) throw new IllegalArgumentException("Simulation limit must be positive.");
        MCTSStrategy.simulationLimit = simulationLimit;
        System.out.println("MCTS Strategy Configured (Sims: " + MCTSStrategy.simulationLimit + ")");
    }

    /** Default constructor using default simulation limit */
    public MCTSStrategy() {
        this(1000); // Default simulations
    }

    /**
     * Checks if this node represents a terminal state in the game (game over).
     * A state is terminal if the board is full or if the current player has no valid moves.
     *
     * @return True if the node's state is terminal, false otherwise.
     */
    public boolean isTerminal() {
        return state.isFull() || getValidMoves(state, this.aiPlayerContext).isEmpty();
    }

    /**
     * Checks if all possible moves from this node's state have been expanded into child nodes.
     *
     * @return True if the `untriedMoves` list is not null and is empty, false otherwise.
     */
    private boolean isFullyExpanded() {
        return untriedMoves != null && untriedMoves.isEmpty();
    }

    /**
     * Selects the child node with the highest Upper Confidence Bound for Trees (UCT) value.
     * The UCT formula balances exploitation (choosing nodes with high average scores)
     * and exploration (choosing nodes that have been visited less often).
     *
     * @return The child node selected based on the UCT formula. Returns a random child if parent visit count is 0. Returns null if no children exist.
     */
    private MCTSStrategy selectBestChildUCT() {
        MCTSStrategy bestChild = null;
        double bestValue = Double.NEGATIVE_INFINITY;    // Start with the lowest possible value
        // Cannot calculate UCT reliably if the parent (this node) hasn't been visited
        if (this.visitCount == 0) {
            // Parent hasn't been visited, cannot calculate UCB1 reliably
            return children.isEmpty() ? null : children.get(random.nextInt(children.size()));
        }
        // Pre-calculate log of parent visits (N) for efficiency inside the loop
        double logN = Math.log(this.visitCount); // N = parent visits

        for (MCTSStrategy child : children) {
            double uctValue;
            // If a child has not been visited, prioritize exploring it (assign infinite UCT value)
            if (child.visitCount == 0) {
                uctValue = Double.POSITIVE_INFINITY; // Explore unvisited first
            } else {
                // w_i / n_i (Exploitation Term)
                // Assumes child.totalScore is relative to Player 1's perspective
                double averageScore = child.totalScore / child.visitCount;
                double exploitationTerm = this.isPlayer1TurnAtNode ? averageScore : -averageScore;

                // c * sqrt(ln N / n_i) (Exploration Term)
                double explorationTerm = EXPLORATION_CONSTANT * Math.sqrt(logN / child.visitCount);

                // Combine exploitation and exploration
                uctValue = exploitationTerm + explorationTerm;
            }
            // Update the best child if the current child has a higher UCT value
            if (uctValue > bestValue) {
                bestValue = uctValue;
                bestChild = child;
            }
        }
        return bestChild;
    }
    /**
     * Selects the best move from the current board state using the MCTS algorithm.
     * This involves running a configured number of simulations, building the MCTS tree,
     * and finally choosing the move that leads to the most promising child node (usually the most visited).
     * @param board  The current state of the Sudoku board.
     * @param player The AI player making the move.
     * @return The selected best {@link Move}, or null if no move can be determined.
     */
    @Override
    public Move selectMove(CSudokuBoard board, Player player) {
        // Cast player and determine if the root player is Player 1
        AIPlayer aiPlayer = (AIPlayer) player;
        boolean isRootPlayer1 = (player == referee.getPlayer1());

        // Create the root node of the MCTS tree based on the current actual board state
        EvaluatedSimulatedBoard rootStateBoard = new EvaluatedSimulatedBoard(board, player);
        MCTSStrategy rootNode = new MCTSStrategy(rootStateBoard, null, null, isRootPlayer1, aiPlayer);
        for (int i = 0; i < simulationLimit; i++) {
            // 1) Selection: Traverse the tree from the root using UCB1 until a terminal or non-fully expanded node is found
            MCTSStrategy selectedNode = selection(rootNode);
            if (selectedNode == null) continue; // Error in selection, skip iteration

            // 2) Expansion: If the selected node is not terminal and not fully expanded, create one new child node
            MCTSStrategy nodeToSimulateFrom = selectedNode;
            if (!selectedNode.isTerminal() && !selectedNode.isFullyExpanded()) {
                MCTSStrategy expandedChild = expansion(selectedNode);
                if (expandedChild != null) { // If expansion happened
                    nodeToSimulateFrom = expandedChild;
                }
            }

            // 3) Simulation (Rollout): From the chosen node (either selected or expanded), play randomly until a terminal state is reached
            double simulationResult = simulation(nodeToSimulateFrom);

            // 4) Backpropagation: Update the visit counts and total scores of all nodes from the simulated node back up to the root
            backPropagation(nodeToSimulateFrom, simulationResult);
        }

        // After all simulations, choose the best move from the root node's children
        // Typically, the move leading to the child node with the highest visit count is chosen
        Move bestMove = getBestMoveFromRoot(rootNode);

        //     Logging
        System.out.println("MCTS Search Complete:");
        System.out.println("  Simulations Run: " + simulationLimit);
        System.out.println("  Best Move Selected: " + bestMove);

        // Log stats of the chosen child node for debugging/analysis
        if (bestMove != null && rootNode.children != null && !rootNode.children.isEmpty()) {
            for (MCTSStrategy child : rootNode.children) {
                if (child.move != null && child.move.equals(bestMove)) {
                    System.out.println("  Chosen Child Stats: Visits=" + child.visitCount + ", AvgScore=" + (child.visitCount > 0 ? child.totalScore / child.visitCount : 0.0));
                    break;  // Found the chosen child, no need to check further
                }
            }
        } else if (bestMove == null) {
            // Fallback if MCTS somehow fails to select a move
            System.err.println("  Warning: MCTS could not determine a best move!");
            List<Move> fallbackMoves = rootNode.getValidMoves(rootNode.state, rootNode.aiPlayerContext);
            if (!fallbackMoves.isEmpty()) {
                System.err.println("  Fallback: Returning first valid move.");
                return fallbackMoves.get(0);    // Return first available valid move as a last resort
            } else {
                System.err.println("  Fallback failed: No valid moves found.");
            }
        }
        // Return the best move determined by MCTS
        return bestMove;
    }

    /**
     * MCTS Selection Phase: Traverses the tree from the given start node downwards.
     * At each step, if the node is not fully expanded, it's returned for expansion.
     * Otherwise, it selects the best child according to the UCT formula and continues descending.
     * Stops when a terminal node or a node that isn't fully expanded is reached.
     * @param startNode The node to start selection from (usually the root).
     * @return The selected node for expansion or simulation. Returns startNode on error.
     */
    private MCTSStrategy selection(MCTSStrategy startNode) {
        MCTSStrategy currentNode = startNode;
        // Continue descending as long as the current node is not terminal
        while (!currentNode.isTerminal()) {
            // If the node has untried moves, it's selected for expansion
            if (!currentNode.isFullyExpanded()) {
                return currentNode;
            } else {
                // If fully expanded, check if it has children
                if (currentNode.children.isEmpty()) {
                    System.err.println("Warning: Fully expanded node has no children in selection!");
                    return currentNode; // Return current node to avoid null pointer, but indicates problem
                }
                // Select the best child using UCT and continue descent
                currentNode = currentNode.selectBestChildUCT();
                // Error handling if UCT selection fails
                if (currentNode == null) {
                    System.err.println("Error during selection: selectBestChildUCT returned null.");
                    return startNode;
                }
            }
        }
        // If the loop terminates, it means a terminal node was reached
        return currentNode;
    }

    /**
     * MCTS Expansion Phase: Adds one new child node to the tree if the node is expandable.
     * Selects one untried move from the given node, creates the resulting board state,
     * constructs a new MCTS node for that state, and adds it to the children of the parent node.
     *
     * @param node The node to expand (must not be terminal and must have untried moves).
     * @return The newly created child node, or the original node if expansion is not possible.
     */
    private MCTSStrategy expansion(MCTSStrategy node) {
        // Preconditions: Cannot expand terminal or already fully expanded nodes
        if (node.isTerminal() || node.isFullyExpanded() || node.untriedMoves == null || node.untriedMoves.isEmpty()) {
            return node;
        }
        // Pick one untried move
        // Randomly select one move from the list of untried moves
        Move moveToExpand = node.untriedMoves.remove(random.nextInt(node.untriedMoves.size()));
        // Create the board state resulting from applying the chosen move
        EvaluatedSimulatedBoard nextState = new EvaluatedSimulatedBoard(node.state);
        // Apply the move - Using board's direct setValue, assuming MCTS doesn't need incremental eval during expansion itself
        nextState.setValue(moveToExpand.getRow(), moveToExpand.getCol(), moveToExpand.getValue());
        // Create the new child node
        MCTSStrategy childNode = new MCTSStrategy(nextState, node, moveToExpand, !node.isPlayer1TurnAtNode, node.aiPlayerContext);
        // Add the new child to the parent's list of children
        node.children.add(childNode);
        return childNode; // Return the new child
    }

    /**
     * MCTS Simulation Phase (Rollout): From a given node, simulate a random playout
     * until a terminal state is reached. The outcome determines the simulation score.
     * Uses a simple policy (random valid move selection) for the simulation.
     * @param startNode The node from which to start the random simulation.
     * @return The result of the simulation, typically expressed as a score difference (e.g., P1_score - P2_score).
     */
    protected double simulation(MCTSStrategy startNode) {
        // Create a copy of the starting node's state to avoid modifying the tree node's state
        EvaluatedSimulatedBoard simState = new EvaluatedSimulatedBoard(startNode.state);
        // Determine whose turn it is at the start of the simulation
        boolean currentTurnPlayer1 = startNode.isPlayer1TurnAtNode;
        // Get the player context (needed for getValidMoves if it uses player validation)
        AIPlayer playerContext = startNode.aiPlayerContext; // Validation context

        int simSteps = 0;   // Counter for simulation steps
        // Set a maximum number of steps to prevent infinite loops in rare cases
        int maxSimSteps = simState.getSize() * simState.getSize() * 2;

        // Continue simulation as long as the board is not full and step limit not reached
        while (!simState.isFull() && simSteps < maxSimSteps) {
            // Get valid moves for the current player in the simulation state
            List<Move> moves = getValidMoves(simState, playerContext);
            // If no valid moves, the simulation ends
            if (moves.isEmpty()) {
                break;
            }
            // Choose a random move from the valid ones
            Move randomMove = moves.get(random.nextInt(moves.size()));
            // Apply the random move to the simulation state, we pass currentTurnPlayer1 to setValue for score updates,
            // but the score accumulated during simulation is usually ignored. Only the final state's score matters for the simulation result.
            simState.setValue(randomMove.getRow(), randomMove.getCol(), randomMove.getValue()); // Apply move directly
            // Flip the turn for the next simulation step
            currentTurnPlayer1 = !currentTurnPlayer1;
            simSteps++; // Increment step counter
        }
        // Warn if the simulation hit the step limit
        if (simSteps >= maxSimSteps) System.err.println("Warning: Simulation reached step limit.");
        return calculateFinalScore(simState);    // Calculate the final score based on the terminal state reached by the simulation
    }

    /**
     * MCTS Backpropagation Phase: Update the visit counts and total scores of nodes
     * along the path from the simulation start node back up to the root.
     * @param node The node from which the simulation started.
     * @param simulationResultP1 The result of the simulation (score relative to Player 1: P1 - P2).
     */
    private void backPropagation(MCTSStrategy node, double simulationResultP1) {
        MCTSStrategy tempNode = node;   // Start from the node where simulation began
        // Traverse up the tree using parent references until the root (parent is null) is reached
        while (tempNode != null) {
            tempNode.visitCount++;  // Increment the visit count for this node
            // Update the total score based on the simulation result and whose turn it was at this node
            // Scores are stored relative to Player 1. If it was P1's turn at this node, P1 wants to maximize (P1-P2), so add the result
            if (tempNode.isPlayer1TurnAtNode) { // If P1's turn node
                tempNode.totalScore += simulationResultP1; // P1 wants to maximize P1-P2
            } else { // If P2's turn node
                // If it was P2's turn at this node, P2 wants to minimize (P1-P2), which is equivalent to maximizing -(P1-P2)
                // We store the score relative to P1, so we subtract the P1-relative result
                tempNode.totalScore -= simulationResultP1; // P2 wants to minimize P1-P2 -> maximize -(P1-P2)
            }
            // Move up to the parent node
            tempNode = tempNode.parent;
        }
    }
    /**
     * Retrieves all valid moves for the AI player.
     * @param board  The current state of the Sudoku board.
     * @param player The AI player.
     * @return A list of all valid {@link Move} objects.
     */
    protected List<Move> getValidMoves(EvaluatedSimulatedBoard board, AIPlayer player) {
        List<Move> validMoves = new ArrayList<>();
        int boardSize = board.getSize();
        for (int row = 0; row < boardSize; row++) {
            for (int col = 0; col < boardSize; col++) {
                if (board.isCellEmpty(row, col)) {
                    for (int value = 1; value <= boardSize; value++) {
                        Move m = new Move(row, col, value);
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
     * Calculates the final score difference (Player 1 score - Player 2 score) for a terminal board state.
     * NOTE: This implementation currently uses the *global* scores from the Referee.
     * This is likely INCORRECT for simulation results, as the simulation modifies a *copy*
     * of the board and doesn't update the Referee's main game scores.
     * This method needs to calculate the score based *solely* on the provided `finalBoard` state.
     * @param finalBoard The terminal board state reached at the end of a simulation.
     * @return The score difference (Score P1 - Score P2) for the `finalBoard`.
     */
    private double calculateFinalScore(EvaluatedSimulatedBoard finalBoard) {
        int scoreP1 = referee.getP1Score();
        int scoreP2 = referee.getP2Score();
        return (double) scoreP1 - scoreP2;
    }


    /**
     * Selects the best move from the children of the root node after MCTS completes.
     * The standard policy is to choose the child node that was visited the most times,
     * as this indicates it was explored most thoroughly and found promising most often.
     * @param rootNode The root node of the MCTS tree after simulations.
     * @return The {@link Move} associated with the most visited child node, or null if no children exist or were visited.
     */
    protected Move getBestMoveFromRoot(MCTSStrategy rootNode) {
        MCTSStrategy bestChild = null;
        int maxVisits = -1; // Start with -1 to ensure any visited node is chosen
        // Check if the root node has any children
        if (rootNode.children == null || rootNode.children.isEmpty()) return null;

        for (MCTSStrategy child : rootNode.children) {
            // If this child was visited more times than the current best
            if (child.visitCount > maxVisits) {
                // update the max visit count and set this child as the current best
                maxVisits = child.visitCount;
                bestChild = child;
            }
        }
        // Return the move associated with the most visited child, if no child was visited (maxVisits remains -1), bestChild will be null
        return bestChild != null ? bestChild.move : null;
    }
    /**
     * Returns the name of this strategy, including the configured simulation limit.
     *
     * @return The strategy name string.
     */
    @Override
    public String getName() {
        return "MCTS (" + MCTSStrategy.simulationLimit + ")";
    }
}
