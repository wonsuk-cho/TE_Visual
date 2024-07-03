import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;
import org.graphstream.algorithm.Dijkstra;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import org.graphstream.ui.view.Viewer;

public class NetworkVisualizer {
    private Graph graph;
    private Map<String, Double> trafficMatrix = new HashMap<>();
    private Map<String, Double> linkLoads = new HashMap<>();
    private Scanner scanner = new Scanner(System.in);

    public NetworkVisualizer() {
        String stylesheet =
                "node { " +
                        "   size: 30px; " +
                        "   text-size: 50; " +
                        "   text-color: black; " +
                        "   text-style: bold; " +
                        "   text-background-mode: plain; " +
                        "   text-background-color: white; " +
                        "   text-padding: 3px; " +
                        "   text-alignment: under; " +
                        "} " +
                        "edge { " +
                        "   size: 2px; " +
                        "   text-size: 30; " +
                        "   text-color: blue; " +
                        "   text-style: bold; " +
                        "   text-background-mode: plain; " +
                        "   text-background-color: white; " +
                        "   text-padding: 3px; " +
                        "   text-alignment: along; " +
                        "   text-offset: 5px; " +
                        "   arrow-size: 10px; " +
                        "} " +
                        "edge.low { fill-color: green; } " +
                        "edge.medium { fill-color: orange; } " +
                        "edge.high { fill-color: red; }";

        System.setProperty("org.graphstream.ui", "swing");
        graph = new SingleGraph("Network");
        graph.setAttribute("ui.stylesheet", stylesheet);
        graph.setStrict(false);
        graph.setAutoCreate(true);
    }

    public void readTopology(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] components = line.split(",");
                if (components.length == 3) {
                    String node1 = components[0].trim();
                    String node2 = components[1].trim();
                    double weight = Double.parseDouble(components[2].trim());

                    Node n1 = graph.addNode(node1);
                    n1.setAttribute("ui.label", node1);

                    Node n2 = graph.addNode(node2);
                    n2.setAttribute("ui.label", node2);

                    String edgeId = node1 + "-" + node2;
                    Edge e = graph.addEdge(edgeId, node1, node2, true); // true for directed, false or omit for undirected
                    e.setAttribute("weight", weight);
                    e.setAttribute("ui.label", String.format("%.2f", weight));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void readTrafficMatrix(String filePath) throws IOException {
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
                    double trafficValue = Double.parseDouble(parts[1].trim());
                    trafficMatrix.put(edgeKey, trafficValue);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid traffic value: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void computeLinkLoads() {
        System.out.println("Computing link loads...");
        linkLoads.clear();

        // Reset all link loads
        for (Edge edge : graph.edges().toArray(Edge[]::new)) {
            edge.setAttribute("load", 0.0);
        }

        // For each traffic demand, compute the shortest path and distribute the traffic
        for (String key : trafficMatrix.keySet()) {
            String[] nodes = key.split("-");
            String source = nodes[0];
            String destination = nodes[1];
            double traffic = trafficMatrix.get(key);

            // Compute shortest path using Dijkstra's algorithm
            Path path = computeShortestPath(source, destination);
            if (path != null) {
                for (Edge edge : path.getEdgePath()) {
                    double currentLoad = edge.getAttribute("load", Double.class);
                    edge.setAttribute("load", currentLoad + traffic);
                }
            }
        }

        // Update the graph labels and colors
        for (Edge edge : graph.edges().toArray(Edge[]::new)) {
            double load = edge.getAttribute("load", Double.class);
            edge.setAttribute("ui.label", String.format("%.2f", load));
            linkLoads.put(edge.getId(), load);
        }

        updateGraphColors();
        // Display the computed link loads
        displayLinkLoads();
    }

    private Path computeShortestPath(String source, String destination) {
        Dijkstra dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, "weight");
        dijkstra.init(graph);
        dijkstra.setSource(graph.getNode(source));
        dijkstra.compute();
        Path path = dijkstra.getPath(graph.getNode(destination));
        dijkstra.clear();
        return path;
    }

    public void displayLinkLoads() {
        System.out.println("Link loads after computation:");
        for (Edge edge : graph.edges().toArray(Edge[]::new)) {
            double load = edge.getAttribute("load", Double.class);
            System.out.println("Edge " + edge.getId() + " Load: " + load);
        }
    }

    public void updateGraphColors() {
        double maxLoad = linkLoads.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        for (Edge edge : graph.edges().toArray(Edge[]::new)) {
            Double load = edge.getAttribute("load", Double.class);
            load = (load != null) ? load : 0.0;

            String color;
            if (load < 0.5 * maxLoad) {
                color = "green";
            } else if (load < 0.8 * maxLoad) {
                color = "orange";
            } else {
                color = "red";
            }

            edge.setAttribute("ui.style", "fill-color: " + color + ";");
        }
    }

    public void display() {
        Viewer viewer = graph.display();
        viewer.setCloseFramePolicy(Viewer.CloseFramePolicy.HIDE_ONLY);
        viewer.enableAutoLayout();
    }

    public void changeLinkWeight() {
        System.out.println("Enter the edge (format 'A-B') and new weight (format 'A-B,weight'):");
        String input = scanner.nextLine();
        String[] parts = input.split(",");
        if (parts.length == 2) {
            String[] nodes = parts[0].split("-");
            if (nodes.length == 2) {
                String edgeId = nodes[0].trim() + "-" + nodes[1].trim();
                Edge e = graph.getEdge(edgeId);
                if (e != null) {
                    try {
                        double newWeight = Double.parseDouble(parts[1].trim());
                        e.setAttribute("weight", newWeight);
                        e.setAttribute("ui.label", String.format("%.2f", newWeight));
                        System.out.println("Weight updated for edge " + edgeId + " to " + newWeight);
                        computeLinkLoads();  // Recompute loads
                        refreshGraph();
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid weight format!");
                    }
                } else {
                    System.out.println("Edge not found!");
                }
            } else {
                System.out.println("Invalid edge format!");
            }
        } else {
            System.out.println("Invalid input format!");
        }
    }

    public void refreshGraph() {
        for (Node node : graph) {
            node.setAttribute("ui.class", "refresh");
            node.removeAttribute("ui.class");
        }
        for (Edge edge : graph.edges().toArray(Edge[]::new)) {
            edge.setAttribute("ui.class", "refresh");
            edge.removeAttribute("ui.class");
        }
    }

    // Implementing the Local Search Heuristic
    public void optimizeOSPFWeights() {
        System.out.println("Optimizing OSPF weights using local search...");
        int iterations = 5000;
        Random rand = new Random();
        double bestCost = computeTotalCost();

        for (int iter = 0; iter < iterations; iter++) {
            // Randomly select an edge to modify
            Edge edge = graph.getEdge(rand.nextInt(graph.getEdgeCount()));
            if (edge == null) continue;

            // Retrieve current weight
            double currentWeight = edge.getAttribute("weight", Double.class);
            // Apply a small change to the weight
            double newWeight = currentWeight + (rand.nextDouble() * 0.2 - 0.1); // Random change between -0.1 and +0.1
            if (newWeight <= 0) newWeight = 1; // Ensure weights are positive

            // Set the new weight
            edge.setAttribute("weight", newWeight);

            // Recompute loads and cost with the new weight
            computeLinkLoads();
            double currentCost = computeTotalCost();

            if (currentCost < bestCost) {
                bestCost = currentCost;
                System.out.println("Iteration " + iter + ": Improved cost to " + currentCost + " with weight " + newWeight + " on edge " + edge.getId());
            } else {
                // Revert to the old weight if no improvement
                edge.setAttribute("weight", currentWeight);
            }

            if (iter % 100 == 0) {
                System.out.println("Intermediate stats after " + iter + " iterations:");
                displayLoadStatistics();
            }
        }
    }

    // Compute the total cost of the network
    public double computeTotalCost() {
        double totalCost = 0.0;
        for (Edge edge : graph.edges().toArray(Edge[]::new)) {
            double load = edge.getAttribute("load", Double.class);
            double weight = edge.getAttribute("weight", Double.class);
            totalCost += load * weight; // Simple cost function, can be more complex
        }
        return totalCost;
    }

    public void interactiveMode() {
        display();
        String command = "";
        while (!command.equals("exit")) {
            System.out.println("Enter command ('update' to change weight, 'optimize' to run optimization, 'stats' to display statistics, 'compute' to display computation, 'exit' to quit):");
            command = scanner.nextLine().trim();
            switch (command) {
                case "update":
                    changeLinkWeight();
                    break;
                case "optimize":
                    optimizeOSPFWeights(); // New optimization functionality
                    break;
                case "stats":
                    displayLoadStatistics();
                    break;
                case "compute":
                    computeLinkLoads();
                    break;
                case "exit":
                    System.out.println("Exiting...");
                    break;
                default:
                    System.out.println("Unknown command. Please try again.");
                    break;
            }
        }
    }

    public void displayLoadStatistics() {
        double totalLoad = 0.0;
        int count = 0;
        for (Edge e : graph.edges().toArray(Edge[]::new)) {
            double load = e.getAttribute("load", Double.class);
            totalLoad += load;
            count++;
        }
        double averageLoad = (count > 0) ? totalLoad / count : 0;

        System.out.println("Average Load: " + averageLoad);

        // Additional statistics like min, max, or distribution can be calculated similarly
        double maxLoad = Double.MIN_VALUE;
        double minLoad = Double.MAX_VALUE;

        for (Edge e : graph.edges().toArray(Edge[]::new)) {
            double load = e.getAttribute("load", Double.class);
            if (load > maxLoad) maxLoad = load;
            if (load < minLoad) minLoad = load;
        }
        System.out.println("Max Load: " + maxLoad);
        System.out.println("Min Load: " + minLoad);
    }

    public static void main(String[] args) {
        NetworkVisualizer visualizer = new NetworkVisualizer();
        try {
            visualizer.readTopology("topology.txt"); // Read the network topology from a file.
            visualizer.readTrafficMatrix("traffic.txt"); // Read traffic data from a file.
            visualizer.computeLinkLoads(); // Compute initial loads based on traffic matrix.
            visualizer.updateGraphColors(); // Color the edges based on computed loads.
            visualizer.displayLoadStatistics(); // Display initial statistics about the loads.
            visualizer.interactiveMode(); // Enter interactive mode to allow user to update link weights and see changes.
        } catch (IOException e) {
            System.out.println("Error processing files or running interactive mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
