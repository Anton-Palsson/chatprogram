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

    // Multicast add och port
    private static final String multiAddress = "224.0.1.1";
    private static final int port = 4446;
    
    // MulticastSocket och Inet till multicast-gruppen
    private static MulticastSocket socket;
    private static InetAddress group;
    
    // Username
    private static String userName;
    
    // GUI
    private static JTextArea chatArea;
    private static DefaultListModel<String> userListModel;
    private static Set<String> userSet = new HashSet<>();
    
    // Flagga för att kontrollera mottagartråden
    private static volatile boolean running = true;

    public static void main(String[] args) {
        try {
            // GUI-inställningar
            JFrame frame = new JFrame("Chat Program");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(500, 500);

            // Visa chatmeddelanden
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            JScrollPane chatScrollPane = new JScrollPane(chatArea);

            // Skriva in meddelanden
            JTextField messageField = new JTextField();
            messageField.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Skicka meddelandet och empty
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
                    // Avsluta mottagartråden och lämna multicast-gruppen
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

            // Panel för att hålla textfältet och disconnect-knappen
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(messageField, BorderLayout.CENTER);
            panel.add(disconnectButton, BorderLayout.EAST);

            // Lägg till alla komponenter till huvudfönstret
            frame.setLayout(new BorderLayout());
            frame.add(chatScrollPane, BorderLayout.CENTER);
            frame.add(panel, BorderLayout.SOUTH);
            frame.add(userScrollPane, BorderLayout.EAST);

            // Be användaren om ett användarnamn
            userName = JOptionPane.showInputDialog(frame, "Enter your name:");
            if (userName == null || userName.trim().isEmpty()) {
                System.exit(0);
            }

            // Sätt fönstrets titel till att inkludera användarnamnet
            frame.setTitle("Chat Program - " + userName);
            frame.setVisible(true);

            // Skapa en MulticastSocket och anslut till gruppen
            socket = new MulticastSocket(port);
            group = InetAddress.getByName(multiAddress);
            socket.joinGroup(group);

            // Starta mottagartråden
            Thread receiverThread = new Thread(new Receiver());
            receiverThread.start();

            // Skicka ett meddelande om att användaren har anslutit till chatten
            sendMessage(userName + " has joined the chat");

            // Lägg till en nedstängningskrok för att städa upp vid avslut
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

    // Metod för att skicka meddelanden
    private static void sendMessage(String message) {
        try {
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), group, port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Inre klass för mottagartråden
    static class Receiver implements Runnable {
        @Override
        public void run() {
            try {
                byte[] buf = new byte[1024];
                while (running) {
                    // Ta emot paket från multicast-gruppen
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength());
                    SwingUtilities.invokeLater(() -> {
                        // Uppdatera chatArea med det mottagna meddelandet
                        chatArea.append(received + "\n");
                        String[] parts = received.split(": ", 2);
                        if (parts.length > 1) {
                            String sender = parts[0];
                            // Lägg till användaren i userSet och userListModel om den inte redan finns
                            if (!userSet.contains(sender)) {
                                userSet.add(sender);
                                userListModel.addElement(sender);
                            }
                        } else if (received.endsWith("has left the chat")) {
                            String sender = received.replace(" has left the chat", "");
                            // Ta bort användaren från userSet och userListModel
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

