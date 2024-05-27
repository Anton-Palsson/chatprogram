import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ChatProgram {

    private static final String multiAddress = "224.0.1.1";
    private static final int port = 4446;
    private static MulticastSocket socket;
    private static InetAddress group;
    private static String userName;
    private static JTextArea chatArea;
    private static DefaultListModel<String> userListModel;
    private static Set<String> userSet = new HashSet<>();
    private static volatile boolean running = true;  // Flag to control the receiver thread

    public static void main(String[] args) {
        try {
            // GUI setup
            JFrame frame = new JFrame("Chat Program");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(500, 500);

            chatArea = new JTextArea();
            chatArea.setEditable(false);
            JScrollPane chatScrollPane = new JScrollPane(chatArea);

            JTextField messageField = new JTextField();
            messageField.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    sendMessage(userName + ": " + messageField.getText());
                    messageField.setText("");
                }
            });

            userListModel = new DefaultListModel<>();
            JList<String> userList = new JList<>(userListModel);
            JScrollPane userScrollPane = new JScrollPane(userList);

            JButton disconnectButton = new JButton("Koppla ner");
            disconnectButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    running = false;
                    sendMessage(userName + " has left the chat");
                    try {
                        socket.leaveGroup(group);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    socket.close();
                    System.exit(0);
                }
            });

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(messageField, BorderLayout.CENTER);
            panel.add(disconnectButton, BorderLayout.EAST);

            frame.setLayout(new BorderLayout());
            frame.add(chatScrollPane, BorderLayout.CENTER);
            frame.add(panel, BorderLayout.SOUTH);
            frame.add(userScrollPane, BorderLayout.EAST);

            // Ask for username
            userName = JOptionPane.showInputDialog(frame, "Enter your name:");
            if (userName == null || userName.trim().isEmpty()) {
                System.exit(0);
            }

            frame.setTitle("Chat Program - " + userName);
            frame.setVisible(true);

            socket = new MulticastSocket(port);
            group = InetAddress.getByName(multiAddress);
            socket.joinGroup(group);

            Thread receiverThread = new Thread(new Receiver());
            receiverThread.start();

            sendMessage(userName + " has joined the chat");

            // Add a shutdown hook to clean up on exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running = false;
                sendMessage(userName + " has left the chat");
                try {
                    socket.leaveGroup(group);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                socket.close();
            }));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendMessage(String message) {
        try {
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), group, port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class Receiver implements Runnable {
        @Override
        public void run() {
            try {
                byte[] buf = new byte[1024];
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength());
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append(received + "\n");
                        String[] parts = received.split(": ", 2);
                        if (parts.length > 1) {
                            String sender = parts[0];
                            if (!userSet.contains(sender)) {
                                userSet.add(sender);
                                userListModel.addElement(sender);
                            }
                        } else if (received.endsWith("has left the chat")) {
                            String sender = received.replace(" has left the chat", "");
                            userSet.remove(sender);
                            userListModel.removeElement(sender);
                        }
                    });
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }
}
