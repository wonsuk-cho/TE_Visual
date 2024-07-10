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

    private void createInputWindow() {
        JFrame inputFrame = new JFrame("Input Data");
        inputFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        inputFrame.setLayout(new GridLayout(5, 2));

        JLabel startLabel = new JLabel("Start Node:");
        JTextField startField = new JTextField();
        JLabel endLabel = new JLabel("End Node:");
        JTextField endField = new JTextField();
        JLabel dataLabel = new JLabel("Amount of Data to Transfer (bytes):");
        JTextField dataField = new JTextField();
        JLabel methodLabel = new JLabel("Method:");
        JComboBox<String> methodComboBox = new JComboBox<>(new String[]{"Dijkstra", "Balanced"});
        JButton calculateButton = new JButton("Calculate");
        JButton exitButton = new JButton("Exit");

        inputFrame.add(startLabel);
        inputFrame.add(startField);
        inputFrame.add(endLabel);
        inputFrame.add(endField);
        inputFrame.add(dataLabel);
        inputFrame.add(dataField);
        inputFrame.add(methodLabel);
        inputFrame.add(methodComboBox);
        inputFrame.add(calculateButton);
        inputFrame.add(exitButton);

        inputFrame.setSize(500, 250);
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
                final String method = (String) methodComboBox.getSelectedItem();
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
                    System.out.println("Start Node: " + startNode + ", End Node: " + endNode + ", Data: " + data + " bytes, Method: " + method);
                    if ("Dijkstra".equals(method)) {
                        int finalData = data;
                        new Thread(() -> calculateAndPrintBpsDijkstra(startNode, endNode, finalData)).start();
                    } else if ("Balanced".equals(method)) {
                        int finalData1 = data;
                        new Thread(() -> calculateAndPrintBpsBalanced(startNode, endNode, finalData1)).start();
                    }
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
                edge.setAttribute("load", 0.0);
            }

            for (Edge edge : path.getEdgePath()) {
                String edgeId = edge.getId();
                double capacity = linkCapacities.getOrDefault(edgeId, 0.0);
                double dataTransferred = Math.min(bps, capacity);
                edge.setAttribute("load", dataTransferred);
                updateEdgeLabel(edge);
                System.out.println("Edge " + edgeId + ": " + dataTransferred + " Bps");
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

    private void calculateAndPrintBpsBalanced(String startNode, String endNode, int data) {
        System.out.println("Balanced method is not yet implemented.");
        // Placeholder for balanced method implementation
    }

    public static void main(String[] args) {
        NetworkVisualizer visualizer = new NetworkVisualizer();
        try {
            visualizer.readLinkCosts("linkcosts.txt");
            visualizer.readWeights("weights.txt");
            visualizer.readLinkCapacities("linkcapacities.txt");
            visualizer.display();
        } catch (IOException e) {
            System.out.println("Error processing files or running interactive mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
