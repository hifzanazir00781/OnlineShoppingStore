import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public Client(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // GUI setup
            JFrame frame = new JFrame("🛍 Online Cloth Store Client");
            JTextArea textArea = new JTextArea(15, 40);
            JTextField inputField = new JTextField(30);
            JButton sendButton = new JButton("Send");

            textArea.setEditable(false);
            frame.add(new JScrollPane(textArea), BorderLayout.CENTER);
            JPanel panel = new JPanel();
            panel.add(inputField);
            panel.add(sendButton);
            frame.add(panel, BorderLayout.SOUTH);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);

            // Background thread for server messages
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        textArea.append(line + "\n");
                    }
                } catch (IOException e) {
                    textArea.append("Disconnected.\n");
                }
            }).start();

            // Send message
            sendButton.addActionListener(e -> {
                out.println(inputField.getText());
                inputField.setText("");
            });

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Server not running!");
        }
    }

    public static void main(String[] args) {
        new Client("127.0.0.1", 5000); // connect to localhost
    }
}
