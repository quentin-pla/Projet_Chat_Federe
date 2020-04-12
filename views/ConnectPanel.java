package views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Page d'accueil
 */
public class ConnectPanel extends BorderPane {
    private Label title;
    private TextField ip;
    private TextField port;
    private Button connect;

    public ConnectPanel() {
        setMinSize(500, 300);
        title = new Label("ChatamuCentral");
        ip = new TextField("localhost");
        port = new TextField("12345");
        connect = new Button("Connexion");
        connect.setMinWidth(100);
        ip.setMaxWidth(100);
        ip.setPromptText("Adresse IP");
        port.setMaxWidth(100);
        port.setPromptText("Port");
        VBox gamesButton = new VBox(title, ip, port, connect);
        VBox.setMargin(title, new Insets(10));
        BorderPane.setAlignment(gamesButton, Pos.CENTER);
        setCenter(gamesButton);
        gamesButton.setAlignment(Pos.CENTER);
        gamesButton.setSpacing(10);
    }

    public String getIpText() {
        return ip.getText();
    }

    public String getPortText() {
        return port.getText();
    }

    public Button getConnect() {
        return connect;
    }
}
