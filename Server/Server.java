// Server.java
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.AtomicInteger;


public class Server {
    private static final int PORT = 5000;
    private static final String PRODUCTS_PATH = "../Data/products.txt";

    // products map keyed by lowercase name
    private static final Map<String, Product> products = new ConcurrentHashMap<>();
    // per-product locks
    private static final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private static final ExecutorService clientPool = Executors.newCachedThreadPool();
    private static final ExecutorService paymentPool = Executors.newFixedThreadPool(4);
    private static final AtomicInteger orderCounter = new AtomicInteger(1000);

    public static void main(String[] args) {
        loadProducts();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Products loaded successfully!");
            System.out.println("✅ Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            clientPool.shutdown();
            paymentPool.shutdown();
        }
    }

    private static void loadProducts() {
        File f = new File(PRODUCTS_PATH);
        if (!f.exists()) {
            System.out.println("❌ products file not found: " + PRODUCTS_PATH);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", 4);
                if (p.length >= 4) {
                    String name = p[0].trim();
                    double price = Double.parseDouble(p[1].trim());
                    int stock = Integer.parseInt(p[2].trim());
                    String desc = p[3].trim();
                    Product prod = new Product(name, price, stock, desc);
                    products.put(name.toLowerCase(), prod);
                    locks.put(name.toLowerCase(), new ReentrantLock());
                }
            }
        } catch (IOException ex) {
            System.out.println("❌ Failed to load products: " + ex.getMessage());
        }
    }

    // save products back to file (synchronized)
    private static synchronized void saveProducts() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(PRODUCTS_PATH, false))) {
            for (Product p : products.values()) {
                pw.printf("%s,%.2f,%d,%s%n", p.name, p.price, p.stock, p.description);
            }
        } catch (IOException e) {
            System.err.println("Failed to save products: " + e.getMessage());
        }
    }

    // Product class
    static class Product {
        final String name;
        final double price;
        int stock;
        final String description;
        Product(String n, double pr, int s, String d) {
            name = n; price = pr; stock = s; description = d;
        }
    }

    // Handler for each connected client
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        // simple per-client cart: name->qty
        private final Map<String,Integer> cart = new HashMap<>();

        ClientHandler(Socket s) throws IOException {
            this.socket = s;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        public void run() {
            String clientAddr = socket.getInetAddress().toString() + ":" + socket.getPort();
            System.out.println("Client connected: " + clientAddr);
            try {
                // send initial product list (protocol: PRODUCTS lines then END)
                for (Product p : products.values()) {
                    out.println("PRODUCT|" + p.name + "|" + p.price + "|" + p.stock + "|" + p.description);
                }
                out.println("END"); // end of product list

                out.println("INFO|Send commands: ADD:name:qty  VIEW_CART  CHECKOUT  EXIT");

                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.equalsIgnoreCase("VIEW_CART")) {
                        sendCart();
                    } else if (line.startsWith("ADD:")) {
                        handleAdd(line);
                    } else if (line.equalsIgnoreCase("CHECKOUT")) {
                        handleCheckout();
                    } else if (line.equalsIgnoreCase("EXIT")) {
                        out.println("INFO|Goodbye");
                        break;
                    } else {
                        out.println("ERROR|Unknown command");
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + clientAddr);
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void sendCart() {
            if (cart.isEmpty()) {
                out.println("CART|EMPTY");
                return;
            }
            StringBuilder sb = new StringBuilder();
            double total = 0;
            for (Map.Entry<String,Integer> e : cart.entrySet()) {
                Product p = products.get(e.getKey());
                if (p != null) {
                    int qty = e.getValue();
                    sb.append(p.name).append(" x").append(qty).append(" | ");
                    total += p.price * qty;
                }
            }
            out.println("CART|" + sb.toString() + "TOTAL:" + total);
        }

        private void handleAdd(String cmd) {
            // format: ADD:name:qty
            try {
                String[] parts = cmd.split(":", 3);
                if (parts.length < 3) {
                    out.println("ERROR|Invalid ADD format. Use ADD:name:qty");
                    return;
                }
                String name = parts[1].trim().toLowerCase();
                int qty = Integer.parseInt(parts[2].trim());
                Product p = products.get(name);
                if (p == null) {
                    out.println("ERROR|Product not found: " + parts[1]);
                    return;
                }
                if (qty <= 0) { out.println("ERROR|Quantity must be >=1"); return; }

                // quick check of availability (not reserving yet)
                ReentrantLock lock = locks.get(name);
                lock.lock();
                try {
                    if (p.stock >= qty) {
                        cart.put(name, cart.getOrDefault(name,0) + qty);
                        out.println("OK|Added " + qty + " x " + p.name + " to cart");
                    } else {
                        out.println("ERROR|Only " + p.stock + " left for " + p.name);
                    }
                } finally {
                    lock.unlock();
                }
            } catch (NumberFormatException e) {
                out.println("ERROR|Invalid quantity");
            }
        }

        private void handleCheckout() {
            if (cart.isEmpty()) {
                out.println("ERROR|Cart is empty");
                return;
            }

            // Attempt to reserve stock for all items atomically using per-product locks sorted order
            List<String> names = new ArrayList<>(cart.keySet());
            // sort to prevent deadlocks (canonical lock order)
            Collections.sort(names);

            List<ReentrantLock> acquired = new ArrayList<>();
            try {
                // acquire locks in order
                for (String name : names) {
                    ReentrantLock lk = locks.get(name);
                    lk.lock();
                    acquired.add(lk);
                    Product p = products.get(name);
                    int want = cart.get(name);
                    if (p.stock < want) {
                        out.println("ERROR|Insufficient stock for " + p.name + ". Available: " + p.stock);
                        // release locks and return
                        return;
                    }
                }

                // reserve (decrement now) — will restore if payment fails
                for (String name : names) {
                    Product p = products.get(name);
                    int want = cart.get(name);
                    p.stock -= want;
                }
                // persist current stock to file (optional)
                saveProducts();

                // send processing message and start asynchronous payment
                int orderId = orderCounter.incrementAndGet();
                out.println("PAYMENT|PROCESSING|" + orderId);
                System.out.println("Order " + orderId + " processing for client " + socket.getPort());

                // run payment simulation async
                paymentPool.submit(() -> {
                    boolean success = simulatePayment();
                    if (success) {
                        // on success, commit already reserved stock; create order record (not persisted beyond console here)
                        out.println("PAYMENT|SUCCESS|" + orderId);
                        System.out.println("Order " + orderId + " SUCCESS");
                        // empty the cart
                        cart.clear();
                    } else {
                        // payment failed -> restore stock
                        // restore using locks (acquire same locks again)
                        // (we already hold the locks in current thread? No - locks are still held in this handler thread; but payment runs async in different thread: must use locks map)
                        for (String nm : names) {
                            ReentrantLock lk = locks.get(nm);
                            lk.lock();
                            try {
                                Product p = products.get(nm);
                                p.stock += cart.get(nm); // add back reserved qty
                            } finally {
                                lk.unlock();
                            }
                        }
                        saveProducts();
                        out.println("PAYMENT|FAILED|" + orderId);
                        System.out.println("Order " + orderId + " FAILED - stock restored");
                    }
                });

            } finally {
                // release locks we acquired in this thread (we had acquired earlier)
                for (ReentrantLock lk : acquired) {
                    if (lk.isHeldByCurrentThread()) lk.unlock();
                }
            }
        }

        private boolean simulatePayment() {
            // simulate network/payment delay and random success
            try {
                Thread.sleep(2000 + new Random().nextInt(2000)); // 2-4 seconds
            } catch (InterruptedException ignored) {}
            // 90% chance success
            return new Random().nextInt(100) < 90;
        }
    }
}
