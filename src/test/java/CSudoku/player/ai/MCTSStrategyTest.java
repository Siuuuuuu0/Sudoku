package CSudoku.player.ai;

import CSudoku.board.CSudokuBoard;
import CSudoku.board.Move;
import CSudoku.player.Player;
import CSudoku.player.MoveStrategy;
import CSudoku.observers.NodeCounterObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MCTSStrategyTest {

    private MCTSStrategy MCTSStrategy;
    private CSudokuBoard board;
    private AIPlayer aiPlayer;

    @BeforeEach
    void setUp() {
        MCTSStrategy = new MCTSStrategy();

        // Initialize a real instance of CSudokuBoard with a size of 9 (standard 9x9 Sudoku)
        board = new CSudokuBoard(9);

        // Initialize the AI player (you may need to pass a MoveStrategy if necessary)
        aiPlayer = new AIPlayer(MCTSStrategy);

        // You may want to set up a board state or mock it for specific tests
        // Example: Manually set some values or create a custom configuration for testing.
        // For this example, we will not fill the board, so it will be all empty.
    }

    @Test
    void testTerminalStateDetection() {
        EvaluatedSimulatedBoard simulatedBoard = new EvaluatedSimulatedBoard(board, aiPlayer);
        MCTSStrategy node = new MCTSStrategy(simulatedBoard, null, null, true, aiPlayer);
        
        assertFalse(node.isTerminal());
        
        // Fill the board completely
        for (int i = 0; i < board.getSize(); i++) {
            for (int j = 0; j < board.getSize(); j++) {
                board.setValue(i, j, (i + j) % 9 + 1);
            }
        }
        simulatedBoard = new EvaluatedSimulatedBoard(board, aiPlayer);
        node = new MCTSStrategy(simulatedBoard, null, null, true, aiPlayer);
        assertTrue(node.isTerminal());
    }

    @Test
    void testSimulationPhase() {
        EvaluatedSimulatedBoard simulatedBoard = new EvaluatedSimulatedBoard(board, aiPlayer);
        MCTSStrategy node = new MCTSStrategy(simulatedBoard, null, null, true, aiPlayer);
        
        double score = MCTSStrategy.simulation(node);
        
        assertTrue(score >= -81 && score <= 81);
    }

    @Test
    void testGetValidMoves() {
        // Simulate valid moves for the AI player
        List<Move> validMoves = MCTSStrategy.getValidMoves(new EvaluatedSimulatedBoard(board, aiPlayer), aiPlayer);

        // Assert that the valid moves are correctly identified
        assertNotNull(validMoves);
        assertTrue(validMoves.size() > 0); // At least some valid moves should be identified
    }

    @Test
    void testBestMoveSelection() {
        // Create a root node with some dummy children
        EvaluatedSimulatedBoard simulatedBoard = new EvaluatedSimulatedBoard(board, aiPlayer);
        MCTSStrategy root = new MCTSStrategy(simulatedBoard, null, null, true, aiPlayer);
        
        // Add some dummy children with different visit counts
        MCTSStrategy child1 = new MCTSStrategy(simulatedBoard, root, new Move(0, 0, 1), false, aiPlayer);
        child1.visitCount = 10;
        child1.totalScore = 50;
        
        MCTSStrategy child2 = new MCTSStrategy(simulatedBoard, root, new Move(0, 1, 2), false, aiPlayer);
        child2.visitCount = 20;
        child2.totalScore = 60;
        
        root.children.add(child1);
        root.children.add(child2);
        
        Move bestMove = MCTSStrategy.getBestMoveFromRoot(root);
        assertEquals(child2.move, bestMove);
    }
}

