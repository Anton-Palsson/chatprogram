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

    // Multicast address och port
    private static final String multiAddress = "224.0.1.1";
    private static final int port = 4446;

    // MulticastSocket och InetAddress till multicast
    private static MulticastSocket socket;
    private static InetAddress group;

    // UserName
    private static String userName;

    // GUI
    private static JTextArea chatArea;
    private static DefaultListModel<String> userListModel;
    private static Set<String> userSet = new HashSet<>();

    // Flagga för att kontrollera mottagartread
    private static volatile boolean running = true;

    public static void main(String[] args) {
        try {
            // GUI
            JFrame frame = new JFrame("Chat Program");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(500, 500);

            // Textarea
            chatArea = new JTextArea();
            chatArea.setEditable(false);  // Gör textarea icke-redigerbar
            JScrollPane chatScrollPane = new JScrollPane(chatArea);

            // Textfield
            JTextField messageField = new JTextField();
            messageField.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Skicka meddelandet och töm textfältet
                    sendMessage(userName + ": " + messageField.getText());
                    messageField.setText("");
                }
            });

            // Lista för att visa anslutna användare
            userListModel = new DefaultListModel<>();
            JList<String> userList = new JList<>(userListModel);
            JScrollPane userScrollPane = new JScrollPane(userList);

            // Knapp för att koppla ner från chatten
            JButton disconnectButton = new JButton("Koppla ner");
            disconnectButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Avsluta mottagarthread och lämna multicast
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

            // Panel textfältet och disconnect-knappen
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(messageField, BorderLayout.CENTER);
            panel.add(disconnectButton, BorderLayout.EAST);

            // huvudfönstret
            frame.setLayout(new BorderLayout());
            frame.add(chatScrollPane, BorderLayout.CENTER);
            frame.add(panel, BorderLayout.SOUTH);
            frame.add(userScrollPane, BorderLayout.EAST);

            // användarnamn
            userName = JOptionPane.showInputDialog(frame, "Enter your name:");
            if (userName == null || userName.trim().isEmpty()) {
                System.exit(0); // Avsluta programmet om användarnamnet är ogiltigt
            }

            frame.setTitle("Chat Program - " + userName);
            frame.setVisible(true);

            // Skapa en MulticastSocket och anslut till gruppen
            socket = new MulticastSocket(port);
            group = InetAddress.getByName(multiAddress);
            socket.joinGroup(group);

            // Starta thread
            Thread receiverThread = new Thread(new Receiver());
            receiverThread.start();

            // Skicka ett medd om att användaren har anslutit till chatten
            sendMessage(userName + " has joined the chat");

            sendMessage("requestUserList");

            // Cleanup
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

    // skicka meddelanden
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
                    // Ta emot paket från multicast
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength());

                    // Filtrera bort "interna" medd
                    if (!received.equals("requestUserList") && !received.startsWith("userList: ")) {
                        SwingUtilities.invokeLater(() -> chatArea.append(received + "\n"));
                    }

                    if (received.equals("requestUserList")) {
                        // Skicka lista över anslutna användare
                        sendMessage("userList: " + String.join(",", userSet));
                    } else if (received.startsWith("userList: ")) {
                        // Uppdatera användarlistan med mottagna namn
                        String[] users = received.substring(10).split(",");
                        for (String user : users) {
                            if (!userSet.contains(user)) {
                                userSet.add(user);
                                userListModel.addElement(user);
                            }
                        }
                    } else if (received.endsWith("has joined the chat")) {
                        String newUser = received.replace(" has joined the chat", "");
                        // Add användaren i userSet och userListModel om den inte redan finns
                        if (!userSet.contains(newUser)) {
                            userSet.add(newUser);
                            userListModel.addElement(newUser);
                        }
                    } else if (received.endsWith("has left the chat")) {
                        String departingUser = received.replace(" has left the chat", "");
                        // Remove användaren från userSet och userListModel
                        userSet.remove(departingUser);
                        userListModel.removeElement(departingUser);
                    } else {
                        // Hantera vanliga meddelanden
                        String[] parts = received.split(": ", 2);
                        if (parts.length > 1) {
                            String sender = parts[0];
                            // Lägg till användaren i userSet och userListModel om den inte redan finns
                            if (!userSet.contains(sender)) {
                                userSet.add(sender);
                                userListModel.addElement(sender);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }
}
