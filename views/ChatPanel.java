package views;

import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * Page de chat
 */
public class ChatPanel extends BorderPane {
    private ListView<String>    fairs = new ListView<>();
    private TextField    messageInput = new TextField();
    private TextField       fairInput = new TextField();
    private ListView<String> messages = new ListView<>();
    private Button         createFair = new Button("Nouveau salon");
    private Button  confirmCreateFair = new Button("Valider");
    private Button       refreshFairs = new Button("Rafraichir");
    private Stage           fairStage = new Stage();

    /**
     * Constructeur
     */
    public ChatPanel() {
        //Initialisation
        init();
    }

    /****** Initialisation *******/

    private void init() {
        //Définition de la taille maximale de la fenêtre
        setMinSize(500, 300);
        //Initialisation de la liste des salons
        initFairsList();
        //Initialisation de la liste des messages
        initMessagesList();
        //Initialisation du champ de texte
        initMessageInput();
        //Initialisation du bouton pour créer un nouveau salon
        initCreateFairButton();
        //Initialisation du bouton pour rafraichir la liste des salons
        initRefreshFairsButton();
        //Initialisation des commandes de l'utilisateur
        HBox userCommands = initUserCommands();
        //Initialisation du contenu affiché à gauche
        VBox leftContent = new VBox(fairs, refreshFairs);
        //Placement à gauche
        setLeft(leftContent);
        //Initialisation du contenu affiché à droite
        VBox rightContent = new VBox(messages, userCommands);
        //Placement au centre
        setCenter(rightContent);
        //Initialisation de la fenêtre de création d'un salon
        initConfirmFairStage();
    }

    private void initFairsList() {
        fairs.getItems().add("Salon central");
        fairs.getSelectionModel().select(0);
        fairs.setMinSize(100, 275);
        fairs.setMaxSize(100, 275);
    }

    private void initMessagesList() {
        messages.setMinSize(400, 275);
        messages.setMaxSize(400, 275);
        //Éviter que les messages puissent être sélectionnés
        messages.addEventFilter(MouseEvent.MOUSE_PRESSED, Event::consume);
    }

    private void initMessageInput() {
        messageInput.setPromptText("Message");
        messageInput.setMinSize(300, 25);
        messageInput.setMaxSize(300, 25);
    }

    private void initCreateFairButton() {
        createFair.setMinSize(100, 25);
        createFair.setMaxSize(100, 25);
        createFair.setOnAction(e -> fairStage.show());
    }

    private void initRefreshFairsButton() {
        refreshFairs.setMinSize(100, 25);
        refreshFairs.setMaxSize(100, 25);
    }

    private HBox initUserCommands() {
        HBox userCommands = new HBox(messageInput, createFair);
        userCommands.setMinSize(400, 25);
        userCommands.setMaxSize(400, 25);
        return userCommands;
    }

    private void initConfirmFairStage() {
        fairStage.setTitle("Nouveau salon");
        BorderPane pane = new BorderPane();
        pane.setMinSize(200, 100);
        pane.setMaxSize(200, 100);
        fairInput.setMinSize(100, 25);
        fairInput.setMaxSize(100, 25);
        fairInput.setPromptText("Nom du salon");
        confirmCreateFair.setMinSize(100, 25);
        confirmCreateFair.setMaxSize(100, 25);
        confirmCreateFair.setOnAction(e -> {
            fairStage.close();
            fairInput.clear();
        });
        VBox content = new VBox(fairInput, confirmCreateFair);
        content.setAlignment(Pos.CENTER);
        content.setSpacing(5);
        pane.setCenter(content);
        BorderPane.setAlignment(content, Pos.CENTER);
        fairStage.setScene(new Scene(pane));
    }

    /********** GETTERS **********/

    public String getInputText() {
        return messageInput.getText();
    }

    public TextField getMessageInput() {
        return messageInput;
    }

    public Button getCreateFairButton() {
        return confirmCreateFair;
    }

    public String getFairText() {
        return fairInput.getText();
    }

    public ListView<String> getFairsList() { return fairs; }

    /********* Méthodes publiques ***********/

    public void addFair(String fair) {
        fairs.getItems().add(fair);
        fairInput.clear();
        fairStage.close();
    }

    public void addMessage(String message) {
        messages.getItems().add(message);
        messageInput.clear();
    }

    public void showMessages(ArrayList<String> fairMessages) {
        messages.getItems().clear();
        messages.getItems().addAll(fairMessages);
    }
}
