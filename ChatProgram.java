import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Scanner;

public class ChatProgram {

    private static final String multiAdress = "224.0.1.1";
    private static final int port = 4446;
    private static MulticastSocket socket;
    private static InetAddress group;
    private static String userName;

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your name: ");
            userName = scanner.nextLine();

            socket = new MulticastSocket(port);
            group = InetAddress.getByName(multiAdress);
            socket.joinGroup(group);

            Thread receiverThread = new Thread(new Receiver());
            receiverThread.start();

            while (true) {
                String message = scanner.nextLine();
                sendMessage(userName + ": " + message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (socket != null && group != null) {
                try {
                    socket.leaveGroup(group);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                socket.close();
            }
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
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength());
                    System.out.println(received);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
