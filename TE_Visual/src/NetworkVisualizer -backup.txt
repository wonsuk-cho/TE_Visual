import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;
import org.graphstream.algorithm.Dijkstra;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.camera.Camera;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;

public class NetworkVisualizer {
    private Graph graph;
    private Map<String, Double> linkCapacities = new HashMap<>();
    private Scanner scanner = new Scanner(System.in);

    public NetworkVisualizer() {
        String stylesheet =
                "node { " +
                        "   size: 30px; " +
                        "   text-size: 20; " +
                        "   text-color: black; " +
                        "   text-style: bold; " +
                        "   text-background-mode: plain; " +
                        "   text-background-color: white; " +
                        "   text-padding: 3px; " +
                        "   text-alignment: under; " +
                        "} " +
                        "edge { " +
                        "   size: 3px; " +
                        "   text-size: 12; " +
                        "   text-color: blue; " +
                        "   text-style: bold; " +
                        "   text-background-mode: plain; " +
                        "   text-background-color: white; " +
                        "   text-padding: 3px; " +
                        "   text-alignment: along; " +
                        "   text-offset: 5px; " +
                        "   arrow-size: 10px; " +
                        "} ";

        System.setProperty("org.graphstream.ui", "swing");
        graph = new SingleGraph("Network");
        graph.setAttribute("ui.stylesheet", stylesheet);
        graph.setStrict(false);
        graph.setAutoCreate(true);
    }

    public void readLinkCosts(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    String[] nodes = parts[0].trim().split("-");
                    if (nodes.length == 2) {
                        String node1 = nodes[0].trim();
                        String node2 = nodes[1].trim();
                        double cost = Double.parseDouble(parts[1].trim());

                        if (graph.getNode(node1) == null) {
                            Node n1 = graph.addNode(node1);
                            n1.setAttribute("ui.label", node1);
                        }
                        if (graph.getNode(node2) == null) {
                            Node n2 = graph.addNode(node2);
                            n2.setAttribute("ui.label", node2);
                        }

                        String edgeId = node1 + "-" + node2;
                        Edge e = graph.addEdge(edgeId, node1, node2, true);
                        e.setAttribute("linkcost", cost);
                        e.setAttribute("load", 0.0); // Initialize load to 0
                        updateEdgeLabel(e);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void readWeights(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    String[] nodes = parts[0].trim().split("-");
                    if (nodes.length == 2) {
                        String node1 = nodes[0].trim();
                        String node2 = nodes[1].trim();
                        double weight = Double.parseDouble(parts[1].trim());

                        String edgeId = node1 + "-" + node2;
                        Edge e = graph.getEdge(edgeId);
                        if (e != null) {
                            e.setAttribute("weight", weight);
                            updateEdgeLabel(e);
                        } else {
                            System.out.println("Edge " + edgeId + " not found for weight.");
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void updateEdgeLabel(Edge e) {
        double cost = e.getAttribute("linkcost") != null ? (double) e.getAttribute("linkcost") : 0.0;
        double weight = e.getAttribute("weight") != null ? (double) e.getAttribute("weight") : 0.0;
        double load = e.getAttribute("load") != null ? (double) e.getAttribute("load") : 0.0;

        // Calculate load as a percentage of capacity
        double capacity = linkCapacities.getOrDefault(e.getId(), 0.0);
        double loadPercentage = (capacity > 0) ? (load / capacity) * 100 : 0;

        // Set edge color based on load percentage
        String color;
        if (loadPercentage > 75) {
            color = "red";
        } else if (loadPercentage > 50) {
            color = "orange";
        } else {
            color = "green";
        }

        e.setAttribute("ui.style", "fill-color: " + color + ";");

        String label = String.format("Link Cost: %.2f\nWeight: %.2f\nLoad: %.2f%%", cost, weight, loadPercentage);
        e.setAttribute("ui.label", label);
    }

    public void readLinkCapacities(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != 2) {
                    System.out.println("Skipping invalid line: " + line);
                    continue;
                }
                String[] nodes = parts[0].split("-");
                if (nodes.length != 2) {
                    System.out.println("Invalid edge format: " + line);
                    continue;
                }
                try {
                    String edgeKey = nodes[0].trim() + "-" + nodes[1].trim();
                    double capacityValue = Double.parseDouble(parts[1].trim());
                    linkCapacities.put(edgeKey, capacityValue);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid capacity value: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void display() {
        Viewer viewer = graph.display();
        viewer.setCloseFramePolicy(Viewer.CloseFramePolicy.EXIT);
        viewer.enableAutoLayout();

        // Adjust the view to fit the graph
        fitView(viewer);

        // Create and display the input window
        createInputWindow();
    }

    private void fitView(Viewer viewer) {
        View view = viewer.getDefaultView();
        Camera camera = view.getCamera();

        camera.setViewCenter(0, 0, 0);
        camera.setViewPercent(1.0);
        camera.resetView();
    }

    private JFrame statisticsFrame;
    private JTextArea statisticsArea;

    private void createInputWindow() {
        JFrame inputFrame = new JFrame("Input Data");
        inputFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        inputFrame.setLayout(new GridLayout(6, 2)); // Adjusted layout

        JLabel startLabel = new JLabel("Start Node:");
        JTextField startField = new JTextField();
        JLabel endLabel = new JLabel("End Node:");
        JTextField endField = new JTextField();
        JLabel dataLabel = new JLabel("Amount of Data to Transfer (bytes):");
        JTextField dataField = new JTextField();
        JLabel optimizationLabel = new JLabel("Method:");
        JComboBox<String> optimizationComboBox = new JComboBox<>(new String[]{"Dijkstra", "Optimize Network (Based on Paper)", "Custom Weights"});
        JButton calculateButton = new JButton("Calculate");
        JButton exitButton = new JButton("Exit");

        inputFrame.add(startLabel);
        inputFrame.add(startField);
        inputFrame.add(endLabel);
        inputFrame.add(endField);
        inputFrame.add(dataLabel);
        inputFrame.add(dataField);
        inputFrame.add(optimizationLabel);
        inputFrame.add(optimizationComboBox);
        inputFrame.add(new JLabel());  // Placeholder
        inputFrame.add(new JLabel());  // Placeholder
        inputFrame.add(calculateButton);
        inputFrame.add(exitButton);

        inputFrame.setSize(500, 300); // Adjusted size
        inputFrame.setVisible(true);

        // Action listener for the calculate button
        calculateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Reset all edge loads to zero
                List<Edge> edges = graph.edges().collect(Collectors.toList());
                for (Edge edge : edges) {
                    edge.setAttribute("load", 0.0);
                    updateEdgeLabel(edge);
                }

                final String startNode = startField.getText().trim();
                final String endNode = endField.getText().trim();
                final String dataAmount = dataField.getText().trim();
                StringBuilder errorMessage = new StringBuilder();

                if (graph.getNode(startNode) == null) {
                    errorMessage.append("Start Node does not exist.\n");
                }

                if (graph.getNode(endNode) == null) {
                    errorMessage.append("End Node does not exist.\n");
                }

                int data = 0;
                try {
                    data = Integer.parseInt(dataAmount);
                } catch (NumberFormatException ex) {
                    errorMessage.append("Amount of Data must be a valid integer.\n");
                }

                if (errorMessage.length() > 0) {
                    JOptionPane.showMessageDialog(inputFrame, errorMessage.toString(), "Input Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    System.out.println("Calculating path from " + startNode + " to " + endNode + " with data amount: " + data + " bytes.");
                    int finalData = data;
                    new Thread(() -> {
                        String selectedMethod = (String) optimizationComboBox.getSelectedItem();
                        if ("Optimize Network (Based on Paper)".equals(selectedMethod)) {
                            optimizeNetworkWeights(startNode, endNode);
                        } else if ("Dijkstra".equals(selectedMethod)) {
                            calculateAndPrintBpsDijkstra(startNode, endNode, finalData);
                        } else if ("Custom Weights".equals(selectedMethod)) {
                            calculateWithCustomWeights(startNode, endNode, finalData);
                        }
                        calculateAndDisplayStatistics(statisticsFrame, statisticsArea); // Update statistics after calculation
                    }).start();
                }
            }
        });

        // Action listener for the exit button
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        // Display statistics window when the application starts
        calculateAndDisplayStatistics(null, null);
    }

    private void calculateWithCustomWeights(String startNode, String endNode, int data) {
        Dijkstra dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, "weight");
        dijkstra.init(graph);
        dijkstra.setSource(graph.getNode(startNode));
        dijkstra.compute();

        Path path = dijkstra.getPath(graph.getNode(endNode));
        if (path == null) {
            System.out.println("No path found from " + startNode + " to " + endNode);
            return;
        }

        System.out.println("Path found: " + path.toString());

        double totalData = data;
        int cycles = 0;
        while (totalData > 0) {
            double minCapacity = Double.MAX_VALUE;
            for (Edge edge : path.getEdgePath()) {
                String edgeId = edge.getId();
                double capacity = linkCapacities.getOrDefault(edgeId, 0.0);
                if (capacity < minCapacity) {
                    minCapacity = capacity;
                }
            }

            double bps = Math.min(totalData, minCapacity);
            totalData -= bps;

            // Reset the load for each edge before calculating the new load
            for (Edge edge : path.getEdgePath()) {
                edge.setAttribute("load", bps);
            }

            for (Edge edge : path.getEdgePath()) {
                updateEdgeLabel(edge);
                System.out.println("Edge " + edge.getId() + ": Sent " + bps + " Bps");
            }

            System.out.println("Remaining data: " + totalData + " bytes");
            cycles++;
            System.out.println("Elapsed time: " + cycles + " seconds");

            // Repaint the graph view to reflect the changes
            graph.nodes().forEach(node -> node.setAttribute("ui.label", node.getAttribute("ui.label")));
            graph.edges().forEach(edge -> edge.setAttribute("ui.label", edge.getAttribute("ui.label")));

            try {
                Thread.sleep(1000); // Simulate 1 second per cycle
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        dijkstra.clear();
    }


    private void calculateAndPrintBpsDijkstra(String startNode, String endNode, int data) {
        Dijkstra dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, "linkcost");
        dijkstra.init(graph);
        dijkstra.setSource(graph.getNode(startNode));
        dijkstra.compute();

        Path path = dijkstra.getPath(graph.getNode(endNode));
        if (path == null) {
            System.out.println("No path found from " + startNode + " to " + endNode);
            return;
        }

        System.out.println("Path found: " + path.toString());

        double totalData = data;
        int cycles = 0;
        while (totalData > 0) {
            double minCapacity = Double.MAX_VALUE;
            for (Edge edge : path.getEdgePath()) {
                String edgeId = edge.getId();
                double capacity = linkCapacities.getOrDefault(edgeId, 0.0);
                if (capacity < minCapacity) {
                    minCapacity = capacity;
                }
            }

            double bps = Math.min(totalData, minCapacity);
            totalData -= bps;

            // Reset the load for each edge before calculating the new load
            for (Edge edge : path.getEdgePath()) {
                edge.setAttribute("load", bps);
            }

            for (Edge edge : path.getEdgePath()) {
                updateEdgeLabel(edge);
                System.out.println("Edge " + edge.getId() + ": Sent " + bps + " Bps");
            }

            System.out.println("Remaining data: " + totalData + " bytes");
            cycles++;
            System.out.println("Elapsed time: " + cycles + " seconds");

            // Repaint the graph view to reflect the changes
            graph.nodes().forEach(node -> node.setAttribute("ui.label", node.getAttribute("ui.label")));
            graph.edges().forEach(edge -> edge.setAttribute("ui.label", edge.getAttribute("ui.label")));

            try {
                Thread.sleep(1000); // Simulate 1 second per cycle
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        dijkstra.clear();
    }

    private void optimizeNetworkWeights(String startNode, String endNode) {
        Random random = new Random();
        int wmax = 20; // Maximum weight value to ensure weights are within a reasonable range

        // Initial solution: set all weights randomly between 1 and wmax
        graph.edges().forEach(edge -> {
            int initialWeight = random.nextInt(wmax) + 1; // Random weight between 1 and wmax
            edge.setAttribute("weight", (double) initialWeight);
            updateEdgeLabel(edge);
        });

        // Calculate the shortest path from startNode to endNode using initial weights
        Dijkstra dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, "weight");
        dijkstra.init(graph);
        dijkstra.setSource(graph.getNode(startNode));
        dijkstra.compute();
        Path path = dijkstra.getPath(graph.getNode(endNode));

        if (path == null) {
            System.out.println("No path found from " + startNode + " to " + endNode);
            return;
        }

        // Perform local search on the edges in the path
        for (int iteration = 0; iteration < 5000; iteration++) {
            Edge bestEdge = null;
            double bestCost = Double.MAX_VALUE;
            int bestWeight = 0;

            for (Edge edge : path.getEdgePath()) {
                int currentWeight = ((Double) edge.getAttribute("weight")).intValue();

                for (int newWeight = 1; newWeight <= wmax; newWeight++) {
                    if (newWeight == currentWeight) continue;

                    edge.setAttribute("weight", (double) newWeight);
                    double cost = evaluateNetworkCost();

                    if (cost < bestCost) {
                        bestCost = cost;
                        bestEdge = edge;
                        bestWeight = newWeight;
                    }
                }

                edge.setAttribute("weight", (double) currentWeight); // Restore original weight
            }

            if (bestEdge != null) {
                bestEdge.setAttribute("weight", (double) bestWeight);
                updateEdgeLabel(bestEdge);
            }
        }
    }

    private double evaluateNetworkCost() {
        double totalCost = 0.0;

        for (Edge edge : graph.edges().collect(Collectors.toList())) {
            double load = (double) edge.getAttribute("load");
            double capacity = linkCapacities.getOrDefault(edge.getId(), 1.0);
            double utilization = load / capacity;

            // Piecewise linear cost function as described in the paper
            double cost;
            if (utilization < 1.0 / 3.0) {
                cost = utilization;
            } else if (utilization < 2.0 / 3.0) {
                cost = 3 * utilization - 2.0 / 3.0;
            } else if (utilization < 9.0 / 10.0) {
                cost = 10 * utilization - 16.0 / 3.0;
            } else if (utilization < 1.0) {
                cost = 70 * utilization - 178.0 / 3.0;
            } else if (utilization < 11.0 / 10.0) {
                cost = 500 * utilization - 1468.0 / 3.0;
            } else {
                cost = 5000 * utilization - 19468.0 / 3.0;
            }

            totalCost += cost;
        }

        return totalCost;
    }

    private void calculateAndDisplayStatistics(JFrame statisticsFrame, JTextArea statisticsArea) {
        List<Double> loads = graph.edges().map(e -> {
            double load = e.getAttribute("load") != null ? (double) e.getAttribute("load") : 0.0;
            double capacity = linkCapacities.getOrDefault(e.getId(), 1.0);
            return (capacity > 0) ? (load / capacity) * 100 : 0.0; // Convert load to percentage of capacity
        }).collect(Collectors.toList());

        double totalLoad = loads.stream().mapToDouble(Double::doubleValue).sum();
        double averageLoad = totalLoad / loads.size();
        double minLoad = loads.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxLoad = loads.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        double totalBps = graph.edges().map(e -> (double) e.getAttribute("load")).mapToDouble(Double::doubleValue).sum();
        double averageBps = totalBps / loads.size();
        double minBps = graph.edges().map(e -> (double) e.getAttribute("load")).mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxBps = graph.edges().map(e -> (double) e.getAttribute("load")).mapToDouble(Double::doubleValue).max().orElse(0.0);

        StringBuilder statistics = new StringBuilder();
        statistics.append(String.format("Average Load: %.2f%% (%.2f Bps)\n", averageLoad, averageBps));
        statistics.append(String.format("Minimum Load: %.2f%% (%.2f Bps)\n", minLoad, minBps));
        statistics.append(String.format("Maximum Load: %.2f%% (%.2f Bps)\n", maxLoad, maxBps));

        // Update the statistics window
        if (statisticsFrame != null && statisticsArea != null) {
            statisticsArea.setText(statistics.toString());
        } else {
            displayStatisticsWindow(statistics.toString());
        }
    }

    private void displayStatisticsWindow(String statistics) {
        JFrame statisticsFrame = new JFrame("Network Statistics");
        statisticsFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        statisticsFrame.setLayout(new BorderLayout());

        JTextArea statisticsArea = new JTextArea(statistics);
        statisticsArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(statisticsArea);

        statisticsFrame.add(scrollPane, BorderLayout.CENTER);

        statisticsFrame.setSize(500, 400);
        statisticsFrame.setVisible(true);

        // Store references to the frame and text area for updating
        this.statisticsFrame = statisticsFrame;
        this.statisticsArea = statisticsArea;
    }

    private void displayChangeWeightsWindow() {
        JFrame changeWeightsFrame = new JFrame("Change Weights and Capacity");
        changeWeightsFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        changeWeightsFrame.setLayout(new GridLayout(0, 4)); // Use 0 for rows to dynamically add rows

        List<Edge> edges = graph.edges().collect(Collectors.toList());
        Map<String, JTextField> weightFields = new HashMap<>();
        Map<String, JTextField> capacityFields = new HashMap<>();

        changeWeightsFrame.add(new JLabel("Edge"));
        changeWeightsFrame.add(new JLabel("Weight"));
        changeWeightsFrame.add(new JLabel("Capacity"));
        changeWeightsFrame.add(new JLabel("")); // Placeholder for layout

        for (Edge edge : edges) {
            String edgeId = edge.getId();
            double currentWeight = edge.getAttribute("weight") != null ? (double) edge.getAttribute("weight") : 0.0;
            double currentCapacity = linkCapacities.getOrDefault(edgeId, 0.0);

            JLabel edgeLabel = new JLabel(edgeId);
            JTextField weightField = new JTextField(String.valueOf(currentWeight));
            JTextField capacityField = new JTextField(String.valueOf(currentCapacity));
            weightFields.put(edgeId, weightField);
            capacityFields.put(edgeId, capacityField);

            changeWeightsFrame.add(edgeLabel);
            changeWeightsFrame.add(weightField);
            changeWeightsFrame.add(capacityField);
            changeWeightsFrame.add(new JLabel("")); // Placeholder for layout
        }

        JButton applyButton = new JButton("Apply Changes");
        changeWeightsFrame.add(applyButton);
        changeWeightsFrame.add(new JLabel("")); // Placeholder
        changeWeightsFrame.add(new JLabel("")); // Placeholder
        changeWeightsFrame.add(new JLabel("")); // Placeholder

        changeWeightsFrame.setSize(600, 400); // Adjust size as needed
        changeWeightsFrame.setVisible(true);

        // Action listener for the apply button
        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (Map.Entry<String, JTextField> entry : weightFields.entrySet()) {
                    String edgeId = entry.getKey();
                    JTextField weightField = entry.getValue();
                    JTextField capacityField = capacityFields.get(edgeId);

                    try {
                        double newWeight = Double.parseDouble(weightField.getText().trim());
                        double newCapacity = Double.parseDouble(capacityField.getText().trim());
                        Edge edge = graph.getEdge(edgeId);
                        if (edge != null) {
                            edge.setAttribute("weight", newWeight);
                            linkCapacities.put(edgeId, newCapacity);
                            updateEdgeLabel(edge);
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(changeWeightsFrame, "Invalid input for edge: " + edgeId, "Input Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
                calculateAndDisplayStatistics(statisticsFrame, statisticsArea); // Update statistics after changing weights and capacities
            }
        });
    }


    public static void main(String[] args) {
        NetworkVisualizer visualizer = new NetworkVisualizer();
        try {
            visualizer.readLinkCosts("linkcosts.txt");
            visualizer.readWeights("weights.txt");
            visualizer.readLinkCapacities("linkcapacities.txt");
            visualizer.displayChangeWeightsWindow(); // Display the "Change Weights and Capacity" window at startup
            visualizer.display();
        } catch (IOException e) {
            System.out.println("Error processing files or running interactive mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
