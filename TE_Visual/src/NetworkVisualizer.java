import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;
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
                    e.setAttribute("ui.label", String.format("%.2f", Double.parseDouble(components[2])));
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
            int lineNumber = 0;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                // Splits the line into two parts: "A-B" and "100"
                String[] parts = line.split(",");
                if (parts.length != 2) {
                    System.out.println("Skipping invalid line " + lineNumber + ": " + line);
                    continue;
                }
                // Further splits the first part into two nodes: "A" and "B"
                String[] nodes = parts[0].split("-");
                if (nodes.length != 2) {
                    System.out.println("Invalid edge format on line " + lineNumber + ": " + line);
                    continue;
                }
                try {
                    String edgeKey = nodes[0].trim() + "-" + nodes[1].trim();
                    double trafficValue = Double.parseDouble(parts[1].trim());
                    trafficMatrix.put(edgeKey, trafficValue);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid traffic value on line " + lineNumber + ": " + line);
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
        for (Edge edge : graph.edges().toArray(Edge[]::new)) {
            String edgeId = edge.getId();
            double weight = edge.getAttribute("weight", Double.class);
            Double traffic = trafficMatrix.get(edgeId);
            if (traffic == null) {
                System.out.println("No traffic data for edge: " + edgeId);
                continue; // skip if no traffic is defined for this edge
            }
            double load = weight * traffic;
            System.out.println("Edge: " + edgeId + ", Weight: " + weight + ", Traffic: " + traffic + ", Load: " + load);
            edge.setAttribute("load", load);
            edge.setAttribute("ui.label", String.format("%.2f", load));
            linkLoads.put(edgeId, load);
        }
    }

    public void updateGraphColors() {
        double maxLoad = linkLoads.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0); // Ensure there's a default to avoid division by zero
        for (Edge edge : graph.edges().toArray(Edge[]::new)) {
            Double load = edge.getAttribute("load", Double.class);
            load = (load != null) ? load : 0.0; // Default to zero if load is not set

            // Determine the color based on load thresholds
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
        viewer.setCloseFramePolicy(Viewer.CloseFramePolicy.HIDE_ONLY);  // Ensures the frame isn't disposed
        viewer.enableAutoLayout();  // Optionally enable automatic layout
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
                        System.out.println("Recomputing loads after weight update...");
                        for (Edge edge : graph.edges().toArray(Edge[]::new)) {
                            System.out.println("Edge: " + edge.getId() + ", Load: " + edge.getAttribute("load"));
                        }
                        computeLinkLoads();
                        updateGraphColors();
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

        for (int iter = 0; iter < iterations; iter++) {
            // Randomly select an edge to modify
            Edge edge = graph.getEdge(rand.nextInt(graph.getEdgeCount()));
            if (edge == null) continue;

            double currentWeight = edge.getAttribute("weight", Double.class);
            double newWeight = currentWeight + rand.nextDouble() * 2 - 1; // Random change between -1 and +1
            if (newWeight <= 0) newWeight = 1; // Ensure weights are positive

            edge.setAttribute("weight", newWeight);
            computeLinkLoads(); // Recompute loads after weight change
            updateGraphColors(); // Update edge colors based on new loads
            System.out.println("Iteration " + iter + ": Updated weight of edge " + edge.getId() + " to " + newWeight);

            if (iter % 100 == 0) {
                System.out.println("Intermediate stats after " + iter + " iterations:");
                displayLoadStatistics();
            }
        }
    }

    public void interactiveMode() {
        display();
        String command = "";
        while (!command.equals("exit")) {
            System.out.println("Enter command ('update' to change weight, 'optimize' to run optimization, 'stats' to display statistics, 'exit' to quit):");
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
