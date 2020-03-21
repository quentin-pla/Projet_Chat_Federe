import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class ChatamuCentral {
    //Couleur attribuée pour les messages serveur envoyés aux clients
    private final static String clientsColor = "\u001B[32m";
    //Couleur attribuée aux messages du client
    private final static String userColor = "\u001B[34m";
    //Couleur par défaut du terminal
    private final static String defaultColor = "\u001B[0m";

    private static Selector selector;
    private static ServerSocketChannel ssc;
    private static Executor executor;

    //Liste des clients connectés au serveur
    private static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        int argc = args.length;
        ChatamuCentral serveur;
        /* Traitement des arguments */
        if (argc == 1) {
            try {
                serveur = new ChatamuCentral();
                serveur.demarrer(Integer.parseInt(args[0]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Usage: java EchoServer port");
        }
    }

    //Démarrage du serveur
    public void demarrer(int port) {
        System.out.println("# Démarrage du chatamu central sur le port " + port);
        try {
            selector = Selector.open();
            ssc = ServerSocketChannel.open();
            ssc.socket().bind(new InetSocketAddress(port));
            ssc.configureBlocking(false);
            int ops = ssc.validOps();
            ssc.register(selector, ops, null);

            executor = Executors.newWorkStealingPool();

            while (true) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> keys = selectionKeys.iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    if (key.isAcceptable()) {
                        SocketChannel csc = ssc.accept();
                        csc.configureBlocking(false);
                        csc.register(selector, SelectionKey.OP_READ);
                    }
                    if (key.isReadable()) {
                        ClientHandler clientHandler = new ClientHandler(key);
                        executor.execute(clientHandler);
                        clients.add(clientHandler);
                        key.cancel();
                    }
                }
                keys.remove();
            }
        } catch (IOException ex) {
            System.out.println("# Arrêt anormal du serveur.");
            ex.printStackTrace();
        }
    }

    public static void broadcast(String author, String message) {
        if (author.equals("#"))
            message = defaultColor + message;
        else
            message = clientsColor + message;

        for (ClientHandler client : clients)
            if (client.pseudo != null)
                if (!client.pseudo.equals(author)) client.outgoingMessages.add(message);
        System.out.println(defaultColor + message);
    }

    //Vérifier que le pseudo du nouveau client n'est pas déja pris
    public static boolean checkPseudo(ClientHandler newClient, String pseudo) {
        if (clients.size() > 0)
            for (ClientHandler client : clients)
                if (client.pseudo != null)
                    if (client != newClient && client.pseudo.equals(pseudo)) return false;
        return true;
    }

    //Thread client
    private static class ClientHandler implements Runnable {
        //Nombre de messages groupés maximal à envoyer
        private final int MAX_OUTGOING = 10;
        //Taille maximale pour un message
        private final int MESSAGE_LENGTH = 128;
        //Socket client
        private SocketChannel csc;
        //Clé liée au client
        private SelectionKey key;
        //Pseudo du client
        private String pseudo = null;
        //Liste des messages à envoyer au client
        private ArrayBlockingQueue<String> outgoingMessages = new ArrayBlockingQueue<>(MAX_OUTGOING);

        public ClientHandler(SelectionKey key) throws IOException {
            this.key = key;
            //Récupération du canal de la clé
            this.csc = (SocketChannel) key.channel();
        }

        //Envoyer un message au client depuis le serveur
        public void sendMessage(String message) {
            try {
                //Ajout de la couleur en fonction de l'état de connexion
                message = (pseudo != null) ? message + userColor : message + defaultColor;
                //Tableau contenant le message en bytes
                byte[] backbuffer = (message + "\n").getBytes();
                //Envoi du message au client
                csc.write(ByteBuffer.wrap(backbuffer));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendServerMessages() {
            //Récupération des messages à envoyer
            List<String> messages = getMessagesToSend();
            //S'il y a des messages à envoyer
            if (messages.size() > 0)
                //Pour chaque message à envoyer
                for (String message : messages)
                    //Écriture du message sur le flux sortant du client
                    sendMessage(message);
        }

        //Récupération des messsages à envoyer au client
        public List<String> getMessagesToSend() {
            //Liste des messages à envoyer
            List<String> messagesToSend = new LinkedList<>();
            //Récupération des messages à envoyer
            outgoingMessages.drainTo(messagesToSend);
            return messagesToSend;
        }

        //Extraire le message contenu dans le buffer
        private String extractMessage(ByteBuffer buffer) {
            //Inversion du buffer
            buffer.flip();
            //Instanciation d'un tableau de caractères de taille MESSAGE_LENGTH
            ArrayList<Byte> data = new ArrayList<>();
            //Tant qu'il reste du contenu à consommer dans le buffer
            while(buffer.hasRemaining()) {
                byte b = buffer.get();
                //Consommation d'un caractère
                if (b != 0) data.add(b);
            }
            byte[] conversion = new byte[data.size()];
            for(int i = 0; i < data.size(); i++)
                conversion[i] = data.get(i);
            //Retour du message au format chaine de caractères
            return new String(conversion, StandardCharsets.UTF_8).replace("\n","");
        }

        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes d'une taille MESSAGE_LENGTH
                ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LENGTH);
                //Tant qu'il est possible de lire depuis le client
                while (csc.read(buffer) != -1) {
                    //Envoi au client des messages serveur
                    sendServerMessages();
                    //Récupération du message
                    String message = extractMessage(buffer);
                    //Si le message est EXIT on sort de la boucle
                    if (message.equals("EXIT")) {
                        sendMessage(defaultColor + "# Déconnexion du serveur.");
                        break;
                    }
                    else if (message.getBytes().length > 0){
                        if (pseudo == null) {
                            if (message.startsWith("LOGIN ")) {
                                pseudo = message.substring(6);
                                if (pseudo.matches("[a-zA-Z]+") && pseudo.length() >= 3) {
                                    if (checkPseudo(this, pseudo)) {
                                        //Notification de connexion du client au serveur
                                        broadcast("#", "# Connexion de " + pseudo + " au serveur.");
                                        //Envoi message instruction au client
                                        sendMessage(defaultColor + "# Pour envoyer un message saisir la commande : MESSAGE message");
                                    } else {
                                        pseudo = null;
                                        sendMessage(defaultColor + "# Pseudo déjà utilisé sur le serveur, veuillez réessayer.");
                                    }
                                } else {
                                    pseudo = null;
                                    sendMessage(defaultColor + "# Pseudo invalide, seuls les caractères de l'alphabet sont acceptés (3 caractères minimum).");
                                }
                            } else {
                                sendMessage(defaultColor + "# ERROR LOGIN aborting chatamu protocol.");
                                break;
                            }
                        } else {
                            if (message.startsWith("MESSAGE "))
                                //Affichage du message
                                broadcast(pseudo, pseudo + " > " + message.substring(8));
                            else
                                sendMessage(defaultColor + "# ERROR chatamu.");
                        }
                    }
                    //Nettoyage du buffer
                    buffer.clear();
                }
                if (pseudo != null)
                    broadcast("#", "# Déconnexion de " + pseudo + ".");
                //Suppression du client dans la liste des clients
                clients.remove(this);
                //Fermeture du thread
                csc.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
