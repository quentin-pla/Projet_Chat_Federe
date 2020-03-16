import java.net.*;
import java.io.*;

public class SalonCentral {
    public static void main(String[] args) {
        if (args.length == 1) {
            try {
                ServerSocket socketServeur = new ServerSocket(Integer.parseInt(args[0]));
                System.out.println("# Démarrage du salon central sur le port " + args[0]);
                while (true) {
                    Socket socketClient = socketServeur.accept();
                    new ClientThread(socketClient).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class ClientThread extends Thread {
        private Socket socket;
        private String pseudo;

        public ClientThread(Socket clientSocket) {
            this.socket = clientSocket;
        }

        public void run() {
            String msg;
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                pseudo = in.readLine();
                System.out.println("# Connexion de " + pseudo + " au serveur.");
                while (true) {
                    msg = in.readLine();
                    if (msg == null || msg.length() == 0) break;
                    System.out.println(pseudo + ">" + msg);
                }
                System.out.println("# Déconnexion de " + pseudo + ".");
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
