// ClientGUI.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class ClientGUI extends JFrame {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JPanel productsPanel;
    private DefaultListModel<String> cartModel = new DefaultListModel<>();
    private Map<String,Integer> cartMap = new HashMap<>();
    private Map<String,ProductInfo> products = new LinkedHashMap<>(); // preserve order

    private JLabel statusLabel;
    private JList<String> cartList;
    private JLabel totalLabel;

    // product info container (local copy)
    static class ProductInfo {
        String name, desc, imgPath;
        double price;
        int stock;
        ProductInfo(String n,double p,int s,String d,String img){ name=n;price=p;stock=s;desc=d;imgPath=img;}
    }

    public ClientGUI() {
        setTitle("🛍️ Online Cloth Store");
        setSize(1000, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10,10));
        getContentPane().setBackground(new Color(245,245,245));

        // header
        JLabel header = new JLabel("Online Cloth Store", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 26));
        header.setOpaque(true);
        header.setBackground(new Color(255,102,0));
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(100,70));
        add(header, BorderLayout.NORTH);

        // left: products grid
        productsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 18, 18));
        productsPanel.setBackground(new Color(245,245,245));
        JScrollPane productScroll = new JScrollPane(productsPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        productScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(productScroll, BorderLayout.CENTER);

        // right: cart panel
        JPanel side = new JPanel(new BorderLayout(8,8));
        side.setPreferredSize(new Dimension(300, 0));
        side.setBackground(new Color(250,250,250));
        side.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JLabel cartLabel = new JLabel("Your Cart");
        cartLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        side.add(cartLabel, BorderLayout.NORTH);

        cartList = new JList<>(cartModel);
        JScrollPane cartScroll = new JScrollPane(cartList);
        side.add(cartScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(3,1,5,5));
        totalLabel = new JLabel("Total: Rs 0.0");
        JButton checkoutBtn = new JButton("Checkout");
        JButton clearBtn = new JButton("Clear Cart");

        checkoutBtn.setBackground(new Color(34,139,34));
        checkoutBtn.setForeground(Color.WHITE);
        clearBtn.setBackground(new Color(200,200,200));

        checkoutBtn.addActionListener(e -> doCheckout());
        clearBtn.addActionListener(e -> clearCart());

        bottom.add(totalLabel);
        bottom.add(checkoutBtn);
        bottom.add(clearBtn);
        side.add(bottom, BorderLayout.SOUTH);

        add(side, BorderLayout.EAST);

        // status bar
        statusLabel = new JLabel("Connecting to server...", SwingConstants.CENTER);
        statusLabel.setPreferredSize(new Dimension(100,28));
        add(statusLabel, BorderLayout.SOUTH);

        // connect and load
        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket("127.0.0.1", 5000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // read initial product list sent by server
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("PRODUCT|")) {
                    // format: PRODUCT|name|price|stock|desc
                    String[] parts = line.split("\\|",5);
                    if (parts.length>=5) {
                        String name = parts[1];
                        double price = Double.parseDouble(parts[2]);
                        int stock = Integer.parseInt(parts[3]);
                        String desc = parts[4];
                        // image path expected in client/images/<lowercase>.jpg
                        String img = "images/" + name.toLowerCase() + ".jpg";
                        ProductInfo pi = new ProductInfo(name,price,stock,desc,img);
                        products.put(name.toLowerCase(), pi);
                    }
                } else if (line.equals("END")) {
                    break;
                }
            }
            // build UI cards
            SwingUtilities.invokeLater(this::populateProductsUI);

            // start listener thread to receive async messages (payment notifications)
            new Thread(this::listenServer).start();
            statusLabel.setText("🟢 Connected to server");
        } catch (IOException e) {
            statusLabel.setText("🔴 Cannot connect to server");
            JOptionPane.showMessageDialog(this, "Server not running. Start server first.", "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void populateProductsUI() {
        productsPanel.removeAll();
        for (ProductInfo p : products.values()) {
            productsPanel.add(createCard(p));
        }
        productsPanel.revalidate();
        productsPanel.repaint();
    }

    private JPanel createCard(ProductInfo p) {
        JPanel card = new JPanel(new BorderLayout(6,6));
        card.setPreferredSize(new Dimension(260,330));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220,220,220),1,true),
                BorderFactory.createEmptyBorder(8,8,8,8)
        ));

        // image
        ImageIcon icon = new ImageIcon(p.imgPath);
        Image scaled = icon.getImage().getScaledInstance(240,170,Image.SCALE_SMOOTH);
        JLabel img = new JLabel(new ImageIcon(scaled));
        img.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(img, BorderLayout.NORTH);

        // info
        JPanel info = new JPanel(new GridLayout(0,1));
        info.setBackground(Color.WHITE);
        JLabel name = new JLabel(p.name);
        name.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel price = new JLabel("Rs " + p.price);
        price.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JLabel stock = new JLabel("Stock: " + p.stock);
        stock.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        stock.setForeground(new Color(100,100,100));
        JTextArea desc = new JTextArea(p.desc);
        desc.setWrapStyleWord(true);
        desc.setLineWrap(true);
        desc.setEditable(false);
        desc.setBackground(Color.WHITE);
        desc.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        desc.setBorder(null);

        info.add(name); info.add(price); info.add(stock);
        card.add(info, BorderLayout.CENTER);

        // buy panel
        JPanel buy = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buy.setBackground(Color.WHITE);
        JButton addBtn = new JButton("Add to Cart");
        JButton buyBtn = new JButton("Buy Now");

        addBtn.setBackground(new Color(255, 140, 0));
        addBtn.setForeground(Color.WHITE);
        buyBtn.setBackground(new Color(34,139,34));
        buyBtn.setForeground(Color.WHITE);

        addBtn.addActionListener(e -> addToCart(p.name.toLowerCase(), 1));
        buyBtn.addActionListener(e -> {
            addToCart(p.name.toLowerCase(), 1);
            doCheckout();
        });

        buy.add(addBtn);
        buy.add(buyBtn);
        card.add(buy, BorderLayout.SOUTH);

        return card;
    }

    private void addToCart(String name, int qty) {
        // optimistic local update
        cartMap.put(name, cartMap.getOrDefault(name,0) + qty);
        refreshCartUI();
        // notify server (ADD:name:qty)
        out.println("ADD:" + name + ":" + qty);
        // read server immediate response asynchronously in listenServer thread
    }

    private void refreshCartUI() {
        cartModel.clear();
        double total = 0;
        for (Map.Entry<String,Integer> e : cartMap.entrySet()) {
            ProductInfo p = products.get(e.getKey());
            if (p != null) {
                String line = p.name + " x" + e.getValue() + "  Rs " + (p.price * e.getValue());
                cartModel.addElement(line);
                total += p.price * e.getValue();
            }
        }
        totalLabel.setText("Total: Rs " + total);
    }

    private void clearCart() {
        cartMap.clear();
        refreshCartUI();
    }

    private void doCheckout() {
        if (cartMap.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Cart is empty");
            return;
        }
        // send CHECKOUT request
        out.println("CHECKOUT");
        // wait for server messages about PAYMENT|...
        JOptionPane.showMessageDialog(this, "Payment started. Wait for confirmation popup.");
    }

    private void listenServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                System.out.println("SERVER -> " + msg);
                SwingUtilities.invokeLater(() -> handleServerMessage(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> statusLabel.setText("🔴 Disconnected from server"));
        }
    }

    private void handleServerMessage(String msg) {
        if (msg.startsWith("OK|")) {
            JOptionPane.showMessageDialog(this, msg.substring(3));
        } else if (msg.startsWith("ERROR|")) {
            JOptionPane.showMessageDialog(this, msg.substring(6), "Error", JOptionPane.ERROR_MESSAGE);
            // optionally refresh products UI from server by requesting reconnect
        } else if (msg.startsWith("PAYMENT|PROCESSING|")) {
            String id = msg.split("\\|")[2];
            JOptionPane.showMessageDialog(this, "Payment processing (order " + id + "). Please wait...");
        } else if (msg.startsWith("PAYMENT|SUCCESS|")) {
            String id = msg.split("\\|")[2];
            JOptionPane.showMessageDialog(this, "Payment SUCCESS! Order ID: " + id);
            // commit cart -> clear local cart and refresh (server has already decremented)
            cartMap.clear();
            refreshCartUI();
            // request updated products? For simplicity, reduce local stock display
            refreshStockFromServer(); // optional attempt to keep UI in sync
        } else if (msg.startsWith("PAYMENT|FAILED|")) {
            String id = msg.split("\\|")[2];
            JOptionPane.showMessageDialog(this, "Payment FAILED for order " + id, "Payment Failed", JOptionPane.ERROR_MESSAGE);
            // refresh product UI as server restored stock
            refreshStockFromServer();
        } else if (msg.startsWith("PRODUCT|")) {
            // ignore (we already loaded at start)
        } else if (msg.startsWith("CART|")) {
            // could display server cart view if processed
            JOptionPane.showMessageDialog(this, "Server cart info: " + msg.substring(5));
        } else if (msg.startsWith("INFO|")) {
            // small info messages
            statusLabel.setText(msg.substring(5));
        }
    }

    // optional method: request latest product list by reconnecting quickly
    private void refreshStockFromServer() {
        // For simplicity, we will ask server for fresh list by reconnecting.
        // (Complex protocol not implemented here) -- Instead we reduce local stock values conservatively:
        for (ProductInfo p : products.values()) {
            // naive: decrement local stock by cart amounts (but at this stage cart cleared)
        }
        // redraw UI to reflect possible stock changes visually
        populateProductsUI();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI().setVisible(true));
    }
}
