package controllers;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import models.ChatFunctions;
import models.Client;
import views.ChatPanel;
import views.ConnectPanel;
import views.LoginPanel;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

import static com.sun.javafx.scene.control.skin.Utils.getResource;

public class ClientController {
    /**
     * Client utilisé par le controleur
     */
    private Client client;

    /**
     * Classe contenant des méthodes pour le chat
     */
    private ChatFunctions chatFunctions = ChatFunctions.getInstance();

    /**
     *
     */
    private ByteArrayOutputStream bytes;

    /**
     * Messages reçus depuis la console
     */
    private PrintStream output;

    /**
     *
     */
    private PrintStream defaultOutput = System.out;

    /**
     * Fenêtre de connexion
     */
    private ConnectPanel connectPanel = new ConnectPanel();

    /**
     * Fenêtre d'authentification
     */
    private LoginPanel loginPanel = new LoginPanel();

    /**
     * Fenêtre de chat
     */
    private ChatPanel chatPanel = new ChatPanel();

    /**
     * Fenêtre affichée à l'écran
     */
    private Pane windowContent;

    /**
     * Thread récupérant les messages en sortie depuis le client
     */
    private ClientOutputReceiver clientOutputReceiver;

    /**
     * Salons disponibles avec les messages qu'ils contiennent
     */
    private HashMap<String, HashMap<String, Color>> fairsMessages = new HashMap<>();

    /**
     * Messages retour après une opération
     */
    private PriorityQueue<String> outResults = new PriorityQueue<>();

    private Color serverMessagesColor = Color.BLUE;

    /**
     * Constructeur
     */
    public ClientController(Stage stage) {
        //Définition de la fenêtre sur le panneau de connexion
        windowContent = new Pane(connectPanel);
        //Initialisation de la sortie
        initOutput();
        //Création d'un thread gérant les messages de retour du client
        new Thread(clientOutputReceiver = new ClientOutputReceiver()).start();
        //Initialisation de l'évènement de sortie de l'application
        initStageEventClosed(stage);
        //Initialisation de la connexion
        initConnection();
    }

    /**
     * Initialisation de l'évènement de sortie de l'application
     * @param stage stage
     */
    private void initStageEventClosed(Stage stage) {
        //Définition de l'évènement de sortie de l'application
        stage.setOnCloseRequest(event -> {
            try {
                //Si le client est définit, on le déconnecte du serveur
                if (client != null) client.disconnect();
                //Terminer l'exécution du thread
                clientOutputReceiver.terminate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Initialisation de la sortie sur laquelle les messages sont redirigés
     */
    private void initOutput() {
        //Initialisation du tableau de bytes
        bytes = new ByteArrayOutputStream();
        //Récupération de la sortie terminal
        output = new PrintStream(bytes);
        //Définition de la sortie sur output
        System.setOut(output);
    }

    /**
     * Initialiser la connexion au serveur
     */
    private void initConnection() {
        //Lorsque l'utilisateur clique sur le bouton de connexion
        connectPanel.getConnect().setOnAction(e -> {
            //Initialisation du client
            client = new Client(connectPanel.getIpText(), connectPanel.getPortText(), output);
            //Récupération du résultat
            String result = getLastResult();
            //Message de retour succès
            if (isSuccess(result)) {
                //Affichage de la fenêtre d'authentification
                setWindow(loginPanel);
                //Initialisation de l'authentification
                initAuthentification();
            }
            //Affichage d'un message d'erreur
            else connectPanel.showError(chatFunctions.traductResult(result));
        });
    }

    /**
     * Authentifier l'utilisateur
     */
    private void initAuthentification() {
        //Lorsque l'utilisateur clique sur le bouton de connexion
        loginPanel.getConnect().setOnAction(e -> {
            //Demande d'authentification sur le serveur avec le pseudo choisit
            client.writeToSocket("LOGIN " + loginPanel.getUsernameText());
            //Récupération du résultat
            String result = getLastResult();
            //Message de retour succès
            if (isSuccess(result)) {
                //Affichage de la fenêtre de chat
                setWindow(chatPanel);
                //Initialisation du chat
                initChat();
            }
            //Affichage d'un message d'erreur
            else loginPanel.showError(chatFunctions.traductResult(result));
        });
    }


    /**
     * Initialisation du chat
     */
    private void initChat() {
        //Demande de récupération des salons présents dans le serveur
        client.writeToSocket("LIST");
        //Initialisation du salon central
        fairsMessages.put("", new HashMap<>());
        //Ajout d'un message par défaut
        addMessage("Bienvenue sur le serveur !", serverMessagesColor);
        //Envoyer un message
        chatPanel.getMessageInput().setOnAction(e -> sendMessage(chatPanel.getInputText()));
        //Créer un nouveau salon
        chatPanel.getCreateFairButton().setOnAction(e -> createNewFair(chatPanel.getFairText()));
        //Rejoindre un salon
        chatPanel.getFairsList().setOnMouseClicked(e -> {
            //Récupération du salon à rejoindre
            String fairToJoin = chatPanel.getFairsList().getSelectionModel().getSelectedItem();
            //Connexion au salon
            joinFair(fairToJoin);
        });
        //Rafraichir la liste des salons
        chatPanel.getRefreshFairsButton().setOnAction(e -> client.writeToSocket("LIST"));
    }

    /**
     * Récupérer le dernier résultat lié à une opération
     * @return résultat
     */
    private String getLastResult() {
        try {
            //Pause dans le programme de 50ms
            TimeUnit.MILLISECONDS.sleep(20);
        } catch (InterruptedException ignored) {}
        //Retour du premier résultat trouvé dans la liste
        return (outResults.peek() != null) ? outResults.poll() : getLastResult();
    }

    /**
     * Vérifie que le résultat est un succès
     * @param result résultat
     * @return booléen
     */
    private boolean isSuccess(String result) {
        //Retourne vrai si le résultat n'est pas une erreur
        return result.equals("[SUCCESS]");
    }

    /**
     * Envoyer un message
     * @param message message
     */
    private void sendMessage(String message) {
        //Si la taille du message est supérieur à 0
        if (message.length() > 0) {
            //Envoi d'un message au serveur
            client.writeToSocket("MESSAGE " + message);
            //Ajout du message
            addMessage(client.getPseudo() + " > " + message, null);
        }
    }

    /**
     * Créer un nouveau salon
     * @param fair salon
     */
    private void createNewFair(String fair) {
        //Envoi d'un message au serveur
        client.writeToSocket("SALON " + fair);
        //Résultat
        String result = getLastResult();
        //Message retour succès
        if (isSuccess(result)) {
            //Ajout du salon dans la liste des salons
            fairsMessages.put(fair, new HashMap<>());
            //Ajout du salon dans l'interface
            chatPanel.addFair(fair);
        }
        //Affichage de l'erreur
        else chatPanel.showError(chatFunctions.traductResult(result));
    }

    /**
     * Rejoindre un salon
     * @param fair salon
     */
    private void joinFair(String fair) {
        //Si le salon à rejoindre est différent de celui du client
        if (!fair.equals(client.getFair())) {
            //Si c'est le salon par défaut
            if (fair.equals("Salon central")) {
                //Envoi d'un message au serveur pour se déconnecter du salon actuel
                client.writeToSocket("QUIT " + client.getFair());
                //Le salon par défaut est une chaine vide
                fair = "";
            }
            else
                //Demande pour rejoindre le salon
                client.writeToSocket("JOIN " + fair);
            //Récupération du résultat
            String result = getLastResult();
            //Message retour succès
            if (isSuccess(result)) {
                //Récupération des messages du salon
                HashMap<String, Color> messages = fairsMessages.get(fair);
                //Affichage des messages du salon dans l'interface
                Platform.runLater(() -> chatPanel.showMessages(messages));
            }
            //Affichage de l'erreur
            else chatPanel.showError(result);
        }
    }

    /**
     * Afficher un nouveau message dans la liste
     * @param message message
     */
    private void addMessage(String message, Color color) {
        //Récupération des messages du salon
        HashMap<String, Color> messages = fairsMessages.get(client.getFair());
        //Si la liste est initialisée
        if (messages != null)
            //Ajout du message à la liste
            messages.put(message, color);
        else
            //Création d'une nouvelle liste de message pour le salon contenant le message
            fairsMessages.put(client.getFair(), new HashMap<String, Color>(){{
                put(message, color);
            }});
        //Affichage du message sur l'interface
        Platform.runLater(() -> chatPanel.addMessage(message, color));
    }


    /**
     * Mettre à jour la liste des salons disponibles
     * @param fairs liste des salons
     */
    private void updateFairsList(String[] fairs) {
        //Ajout des ports dans la liste des ports déjà connectés
        for (String fair : fairs) {
            //Ajout du salon dans la liste des salons
            fairsMessages.put(fair, new HashMap<>());
            if (!chatPanel.getFairsList().getItems().contains(fair))
                //Ajout du salon dans l'interface
                Platform.runLater(() -> chatPanel.addFair(fair));
        }
    }

    /**
     * Afficher une fenêtre à l'écran
     * @param window fenêtre
     */
    private void setWindow(BorderPane window) {
        //Suppression des enfants de la fenêtre globale
        windowContent.getChildren().clear();
        //Ajout de la fenêtre dans la fenêtre globale
        windowContent.getChildren().add(window);
    }

    /**
     * Retourner la fenêtre affichée
     * @return fenêtre
     */
    public Scene getScene() {
        //Retour de la scène
        return new Scene(windowContent, 500, 300);
    }

    /**
     * Réceptionneur des messages en sortie du client
     */
    private class ClientOutputReceiver implements Runnable {
        //Variable pour définir si le thread fonctionne ou pas
        private volatile boolean running = true;

        //Terminer l'exécution du thread
        public void terminate() {
            this.running = false;
        }

        @Override
        public void run() {
            String message;
            while (running) {
                //Récupération du message
                message = bytes.toString().trim();
                //Message de type résultat
                boolean isResult = message.startsWith("[")
                        && message.endsWith("]")
                        && !message.contains(">")
                        && message.length() > 2;
                //Message provenant d'un client
                boolean isClientMessage = !message.startsWith("#")
                        && message.contains(" > ");
                //Message provenant du serveur
                boolean isServerMessage = message.startsWith("# ");
                boolean isListFairs = message.startsWith("LISTFAIRS ") && !message.contains(">");
                //Si le message n'est pas vide
                if (message.length() > 0) {
                    String[] messages;
                    //Si le message contient des retours à la ligne
                    if (message.contains("\n")) {
                        //Séparation du message en plusieurs messages
                        messages = message.split("\n");
                    }
                    //Ajout du message à la liste
                    else messages = new String[]{message};
                    //Pour chaque message
                    for (String element : messages)
                        //Si c'est un message de type résultat
                        if (isResult) {
                            //Ajout du message à la liste des résultats
                            outResults.add(element);
                        }
                        else {
                            //Message contenant la liste des salons
                            if (isListFairs) {
                                //Récupération des salons
                                element = element.substring(message.indexOf('[') + 1, message.lastIndexOf(']'));
                                //Séparation des salons
                                String[] fairs = element.split(", ");
                                //Mise à jour
                                updateFairsList(fairs);
                            }
                            //Message client
                            else if (isClientMessage)
                                addMessage(element, null);
                            else if (isServerMessage) {
                                element = element.substring(2);
                                addMessage(element, serverMessagesColor);
                            }
                        }
                    //Réinitialisation du buffer de bytes contenant le message reçu
                    bytes.reset();
                }
            }
        }
    }
}
